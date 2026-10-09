package com.example.automatedplanting.server;

import com.example.automatedplanting.config.AutoPlantConfig;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.Vec3;

/**
 * Implementation of the {@code /autoplant} command.
 *
 * <p>It exists so the feature can be driven without the key binding: useful from the
 * server console, for debugging, and for anyone who remaps or cannot press the key.
 */
public final class AutoPlantCommand {
	private AutoPlantCommand() {
	}

	/**
	 * Runs one of the {@code /autoplant} sub-actions.
	 *
	 * @param action one of {@code on}, {@code off}, {@code toggle}, {@code status}, {@code reload}.
	 * @return the Brigadier result code.
	 */
	public static int run(CommandSourceStack source, String action) {
		return switch (action) {
			case "on" -> setState(source, true);
			case "off" -> setState(source, false);
			case "toggle" -> toggle(source);
			case "reload" -> reload(source);
			case "info" -> info(source);
			case "test" -> test(source);
			default -> status(source);
		};
	}

	/**
	 * Diagnostic: drives one planting attempt at the command position using a spawned mob
	 * as the actor.
	 *
	 * <p>All of the planting logic is shared with the real thing — only the entity that
	 * holds the seeds differs — so this can prove the scan and placement work on a server
	 * that has no player connected.
	 */
	public static int test(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		Vec3 origin = source.getPosition();
		BlockPos at = BlockPos.containing(origin);

		// A FakePlayer is a real ServerPlayer, which is what vanilla's placement context
		// insists on, without needing anyone connected. It is only the acting context;
		// the mob below is the entity whose surroundings are scanned.
		FakePlayer actor = FakePlayer.get(level);
		Zombie subject = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);

		if (subject == null) {
			source.sendFailure(Component.literal("could not create the test actor"));

			return 0;
		}

		actor.setPos(origin.x, origin.y, origin.z);
		subject.setPos(origin.x, origin.y, origin.z);
		// A full stack, so one command can exercise the whole plantsPerCycle setting.
		subject.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT_SEEDS, 64));
		level.addFreshEntity(subject);

		AutoPlantHandler.tryPlant(subject, actor, level, AutoPlantConfig.get());

		// Report what the attempt produced, which is what makes the command useful.
		int crops = countNearbyCrops(level, at, 12);

		subject.discard();

		source.sendSuccess(() -> Component.translatable("command.automated_planting.test",
				at.toShortString(), crops), false);

		return 1;
	}

	private static int countNearbyCrops(ServerLevel level, BlockPos center, int radius) {
		int count = 0;

		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
				center.offset(radius, radius, radius))) {
			if (level.getBlockState(pos).getBlock() instanceof CropBlock) {
				count++;
			}
		}

		return count;
	}

	/** Reports the loaded config. Works from the server console, where there is no player. */
	private static int info(CommandSourceStack source) {
		AutoPlantConfig config = AutoPlantConfig.get();

		source.sendSuccess(() -> Component.translatable("command.automated_planting.info",
				config.shape.name().toLowerCase(),
				config.radius,
				config.intervalTicks,
				config.plantsPerCycle,
				config.seedSource.name().toLowerCase(),
				config.debugLog), false);

		return 1;
	}

	private static int setState(CommandSourceStack source, boolean wanted) {
		ServerPlayer player = source.getPlayer();

		if (player == null) {
			source.sendFailure(Component.translatable("command.automated_planting.players_only"));

			return 0;
		}

		if (AutoPlantHandler.setEnabled(player, wanted)) {
			source.sendSuccess(() -> stateText(wanted), false);
		} else {
			source.sendSuccess(() -> Component.translatable("command.automated_planting.already",
					stateText(wanted)), false);
		}

		return 1;
	}

	private static int toggle(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();

		if (player == null) {
			source.sendFailure(Component.translatable("command.automated_planting.players_only"));

			return 0;
		}

		boolean enabled = AutoPlantHandler.toggle(player);

		source.sendSuccess(() -> stateText(enabled), false);

		return 1;
	}

	private static int status(CommandSourceStack source) {
		AutoPlantConfig config = AutoPlantConfig.get();
		ServerPlayer player = source.getPlayer();
		boolean enabled = player != null && AutoPlantHandler.isEnabled(player);

		source.sendSuccess(() -> Component.translatable("command.automated_planting.status",
				stateText(enabled),
				config.shape.name().toLowerCase(),
				config.radius,
				config.intervalTicks,
				config.plantsPerCycle,
				config.seedSource.name().toLowerCase()), false);

		return 1;
	}

	private static int reload(CommandSourceStack source) {
		AutoPlantConfig.reload();

		source.sendSuccess(() -> Component.translatable("command.automated_planting.reloaded"), false);

		return 1;
	}

	private static Component stateText(boolean enabled) {
		return Component.translatable(enabled
				? "message.automated_planting.enabled"
				: "message.automated_planting.disabled");
	}
}
