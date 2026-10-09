package com.example.automatedplanting.server;

import com.example.automatedplanting.AutomatedPlanting;
import com.example.automatedplanting.config.AutoPlantConfig;
import com.example.automatedplanting.config.AutoPlantSettings;
import com.example.automatedplanting.network.SyncAutoPlantSettingsPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side auto-planting.
 *
 * <p>While a player has it switched on, every {@code intervalTicks} the server looks for
 * farmland near the ground they are standing on and plants the crop held in their main
 * hand, consuming one item per planting exactly like a manual right-click would.
 *
 * <p>The scan is anchored on the ground surface under the player rather than their exact
 * feet position, so standing on a block above the field (or jumping) does not stop it
 * from finding the farmland.
 *
 * <p>Planting is delegated to {@link BlockItem#place} rather than writing the block
 * directly, so all of the vanilla rules (is this seed valid on farmland, light level,
 * farmland moisture, the place sound) are applied by the game itself.
 */
public final class AutoPlantHandler {
	/** How far below the player's feet to look for the ground surface. */
	private static final int MAX_STEPS_DOWN = 8;

	/** How far below the anchor to follow one column looking for farmland. */
	private static final int MAX_FARMLAND_DROP = 24;

	/** How many solid layers that column search may pass through before giving up. */
	private static final int MAX_SOLID_LAYERS = 4;

	/** Players who have auto-planting switched on. */
	private static final Map<UUID, Boolean> ENABLED = new HashMap<>();

	/** Tick at which each player may next plant. */
	private static final Map<UUID, Long> NEXT_ACTION = new HashMap<>();

	private AutoPlantHandler() {
	}

	/** Wires up the per-tick processing. Called once from the mod initialiser. */
	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long now = server.getTickCount();

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				process(player, now);
			}
		});

		// The settings live on the server, so the client learns them when it joins.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				sendSettings(handler.getPlayer()));

		// Dropping the entries on disconnect keeps the maps from growing forever and
		// means the feature starts switched off again after a rejoin.
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.getPlayer().getUUID();
			ENABLED.remove(id);
			NEXT_ACTION.remove(id);
		});
	}

	/** Sends the current settings to one player, so the config screen shows what is stored. */
	public static void sendSettings(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, SyncAutoPlantSettingsPayload.ID)) {
			ServerPlayNetworking.send(player,
					new SyncAutoPlantSettingsPayload(AutoPlantSettings.from(AutoPlantConfig.get())));
		}
	}

	/**
	 * Flips the feature for one player.
	 *
	 * @return the state after flipping.
	 */
	public static boolean toggle(ServerPlayer player) {
		UUID id = player.getUUID();
		boolean now = !ENABLED.getOrDefault(id, false);

		if (now) {
			ENABLED.put(id, true);
			// Allow an immediate first planting when switched on.
			NEXT_ACTION.put(id, 0L);
		} else {
			ENABLED.remove(id);
			NEXT_ACTION.remove(id);
		}

		return now;
	}

	public static boolean isEnabled(ServerPlayer player) {
		return ENABLED.getOrDefault(player.getUUID(), false);
	}

	/**
	 * Forces the feature into a specific state.
	 *
	 * @return true when the state actually changed.
	 */
	public static boolean setEnabled(ServerPlayer player, boolean enabled) {
		if (isEnabled(player) == enabled) {
			return false;
		}

		toggle(player);

		return true;
	}

	private static void process(ServerPlayer player, long now) {
		if (!isEnabled(player)) {
			return;
		}

		if (NEXT_ACTION.getOrDefault(player.getUUID(), 0L) > now) {
			return;
		}

		AutoPlantConfig config = AutoPlantConfig.get();

		// Set the cooldown whether or not a spot was found, so an empty field does not
		// cause a full scan on every single tick.
		NEXT_ACTION.put(player.getUUID(), now + config.intervalTicks);

		tryPlant(player, config);
	}

	/**
	 * Attempts a single planting action for the given player.
	 *
	 * <p>Public so a caller can drive it directly without waiting for ticks.
	 */
	public static void tryPlant(ServerPlayer player, AutoPlantConfig config) {
		tryPlant(player, player, player.level(), config);
	}

	/**
	 * Attempts a single planting action on behalf of any entity.
	 *
	 * <p>Taking a {@link LivingEntity} rather than a player keeps the whole scan path
	 * usable by the automated tests, which drive it with a spawned mob because a dedicated
	 * server has no player to drive it with. Vanilla's {@code BlockPlaceContext} still
	 * insists on a {@link Player}, so one is supplied purely as the acting context.
	 */
	public static void tryPlant(LivingEntity entity, Player actor, ServerLevel level, AutoPlantConfig config) {
		if (level.isClientSide()) {
			return;
		}

		ItemStack held = pickSeeds(entity, config);

		if (held.isEmpty()) {
			if (config.showDebug()) {
				AutomatedPlanting.LOGGER.info("[autoplant] no plantable item on hand (seedSource={})",
						config.seedSource);
			}

			return;
		}

		if (!isPlantable(held)) {
			if (config.showDebug()) {
				AutomatedPlanting.LOGGER.info("[autoplant] held item is not a plantable block ({})",
						held.getItem());
			}

			return;
		}

		BlockItem blockItem = (BlockItem) held.getItem();
		BlockPos feet = entity.blockPosition();
		BlockPos anchor = findGroundBelow(level, feet);

		if (config.showDebug()) {
			AutomatedPlanting.LOGGER.info(
					"[autoplant] scan anchor={} (feet={}) shape={} radius={} item={}",
					anchor, feet, config.shape, config.radius, held.getItem());
		}

		BlockState cropState = blockItem.getBlock().defaultBlockState();
		int radius = config.radius;

		// Normal case: scan the configured solid around the ground under the entity.
		ScanResult result = scan(entity, actor, held, config, level, anchor, cropState, radius);

		if (result.planted > 0) {
			if (config.showDebug()) {
				AutomatedPlanting.LOGGER.info("[autoplant] planted {}/{} block(s), {} candidate(s) seen",
						result.planted, config.plantsPerCycle, result.candidates);
			}

			return;
		}

		// Fallback: the entity may be standing on a platform further above the field than
		// the configured radius reaches. Follow the column below them instead.
		BlockPos below = findFarmlandBelow(level, anchor);

		if (below != null) {
			if (config.showDebug()) {
				AutomatedPlanting.LOGGER.info("[autoplant] found farmland {} below the anchor", below);
			}

			if (cropState.canSurvive(level, below) && plant(entity, actor, held, below, config)) {
				return;
			}

			if (config.showDebug()) {
				AutomatedPlanting.LOGGER.info("[autoplant]   {} refused: {} cannot survive there",
						below, cropState);
			}
		}

		if (config.showDebug()) {
			AutomatedPlanting.LOGGER.info("[autoplant] nothing planted; {} candidate(s) in the scan window",
					result.candidates);
		}
	}

	/** What one scan pass saw and did. */
	private record ScanResult(int candidates, int planted) {
	}

	/**
	 * Scans the configured solid, planting up to {@code plantsPerCycle} blocks.
	 *
	 * @return the number of farmland candidates seen and the number actually planted.
	 */
	private static ScanResult scan(LivingEntity entity, Player actor, ItemStack held, AutoPlantConfig config,
			ServerLevel level, BlockPos anchor, BlockState cropState, int radius) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int candidates = 0;
		int planted = 0;
		int limit = config.plantsPerCycle;

		for (int dy = -radius; dy <= radius; dy++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) {
						continue;
					}

					if (!config.covers(dx, dy, dz)) {
						continue;
					}

					cursor.set(anchor.getX() + dx, anchor.getY() + dy, anchor.getZ() + dz);

					if (!isFreeFarmlandSpot(level, cursor)) {
						continue;
					}

					candidates++;

					// The crop has to be allowed to exist here before we spend the item.
					if (!cropState.canSurvive(level, cursor)) {
						if (config.showDebug()) {
							AutomatedPlanting.LOGGER.info(
									"[autoplant]   {} is farmland but {} cannot survive there",
									cursor, cropState);
						}

						continue;
					}

					if (plant(entity, actor, held, cursor, config)) {
						planted++;

						if (planted >= limit) {
							return new ScanResult(candidates, planted);
						}
					} else if (held.isEmpty()) {
						// Out of seeds: there is no point scanning the rest.
						if (config.showDebug()) {
							AutomatedPlanting.LOGGER.info("[autoplant]   ran out of items after {} planting(s)",
									planted);
						}

						return new ScanResult(candidates, planted);
					} else if (config.showDebug()) {
						AutomatedPlanting.LOGGER.info("[autoplant]   {} refused by vanilla placement", cursor);
					}
				}
			}
		}

		return new ScanResult(candidates, planted);
	}

	/**
	 * Picks the stack to plant from, honouring the configured {@code seedSource}.
	 *
	 * @return a non-empty stack only when it actually holds something plantable.
	 */
	private static ItemStack pickSeeds(LivingEntity entity, AutoPlantConfig config) {
		return switch (config.seedSource) {
			case MAIN_HAND -> plantableOrEmpty(entity.getMainHandItem());
			case OFF_HAND -> plantableOrEmpty(entity.getOffhandItem());
			case BOTH_HANDS -> {
				ItemStack main = plantableOrEmpty(entity.getMainHandItem());

				yield main.isEmpty() ? plantableOrEmpty(entity.getOffhandItem()) : main;
			}
		};
	}

	private static ItemStack plantableOrEmpty(ItemStack stack) {
		return isPlantable(stack) ? stack : ItemStack.EMPTY;
	}

	/**
	 * @return true when the stack holds a block that can actually be planted, so a stack
	 *         of dirt is ignored instead of being reported as a failed planting.
	 */
	public static boolean isPlantable(ItemStack stack) {
		if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
			return false;
		}

		return isPlantBlock(blockItem.getBlock());
	}

	/**
	 * Whether a block counts as something you plant.
	 *
	 * <p>{@link VegetationBlock} covers crops, stems (pumpkin and melon), saplings, bushes
	 * and the other plants that sit on farmland or dirt. Sugar cane and bamboo are the
	 * exceptions that hang off {@code Block} directly.
	 */
	private static boolean isPlantBlock(Block block) {
		return block instanceof VegetationBlock
				|| block instanceof SugarCaneBlock
				|| block instanceof BambooStalkBlock;
	}

	/**
	 * Finds the surface the player is standing on by walking down from their feet.
	 *
	 * <p>This is what makes the feature work when the player stands on a block beside or
	 * above the field instead of directly on it.
	 */
	private static BlockPos findGroundBelow(ServerLevel level, BlockPos feet) {
		BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos(feet.getX(), feet.getY(), feet.getZ());

		for (int i = 0; i <= MAX_STEPS_DOWN; i++) {
			if (!level.getBlockState(probe).isAir()) {
				// This block is what the player is standing on; scan from its surface.
				return probe.above().immutable();
			}

			probe.move(Direction.DOWN);
		}

		return feet;
	}

	/**
	 * Finds the first farmland surface below the anchor.
	 *
	 * <p>Used while auto-planting is on so that standing on a platform above a field still
	 * finds it: the normal scan window is short, but the player is usually directly above
	 * the field they mean. The result must still be inside the configured footprint.
	 */
	private static BlockPos findFarmlandBelow(ServerLevel level, BlockPos anchor) {
		BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos(anchor.getX(), anchor.getY() - 1, anchor.getZ());
		int solidLayers = 0;

		for (int i = 0; i < MAX_FARMLAND_DROP; i++) {
			if (isFreeFarmlandSpot(level, probe)) {
				return probe.immutable();
			}

			// Allow passing through a few solid layers (the platform the player stands on
			// and the ground under it) before giving up.
			if (!level.getBlockState(probe).isAir()) {
				solidLayers++;

				if (solidLayers > MAX_SOLID_LAYERS) {
					return null;
				}
			}

			probe.move(Direction.DOWN);
		}

		return null;
	}

	/**
	 * @return true when the position is farmland with nothing on top of it, i.e. a spot
	 *         where a crop could be planted.
	 */
	private static boolean isFreeFarmlandSpot(ServerLevel level, BlockPos pos) {
		BlockState below = level.getBlockState(pos.below());

		if (!(below.getBlock() instanceof FarmlandBlock)) {
			return false;
		}

		return level.getBlockState(pos).isAir();
	}

	/**
	 * Plants one item at the given position by driving the normal block placement path.
	 *
	 * @return true when the block was planted and one item was consumed.
	 */
	private static boolean plant(LivingEntity entity, Player actor, ItemStack held, BlockPos target,
			AutoPlantConfig config) {
		if (!(held.getItem() instanceof BlockItem blockItem)) {
			return false;
		}

		// The placement path is told "put this on that block" by pointing at the block
		// underneath the target, at its top face.
		Vec3 hitLocation = new Vec3(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
		BlockHitResult hit = new BlockHitResult(hitLocation, Direction.UP, target.below(), false);
		// Vanilla reacts to the acting player even when the item being placed comes from a
		// different entity, so the context uses the stack the placement is spending.
		BlockPlaceContext context = new BlockPlaceContext(actor, InteractionHand.MAIN_HAND, held, hit);

		if (!blockItem.place(context).consumesAction()) {
			return false;
		}

		// In creative mode the item is not consumed, matching manual planting.
		if (!actor.isCreative()) {
			held.shrink(1);
		}

		if (config.showDebug()) {
			AutomatedPlanting.LOGGER.info("[autoplant] {} planted at {}",
					entity.getName().getString(), target);
		}

		return true;
	}
}
