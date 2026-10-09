package com.example.automatedplanting;

import com.example.automatedplanting.config.AutoPlantConfig;
import com.example.automatedplanting.network.SyncAutoPlantSettingsPayload;
import com.example.automatedplanting.network.ToggleAutoPlantPayload;
import com.example.automatedplanting.network.UpdateAutoPlantSettingsPayload;
import com.example.automatedplanting.server.AutoPlantCommand;
import com.example.automatedplanting.server.AutoPlantHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod entry point for Automated Planting.
 *
 * <p>Auto-planting behaviour lives on the server: the client only reports key presses
 * through {@link ToggleAutoPlantPayload}, and {@link AutoPlantHandler} does the actual
 * planting on the server thread.
 */
public class AutomatedPlanting implements ModInitializer {
	/** Mod id, must match {@code fabric.mod.json} and the resource namespace. */
	public static final String MOD_ID = "automated_planting";

	/** Shared logger for the mod. */
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Convenience factory for resource identifiers in this mod's namespace. */
	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		AutoPlantConfig.get();

		PayloadTypeRegistry.serverboundPlay().register(ToggleAutoPlantPayload.ID, ToggleAutoPlantPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(UpdateAutoPlantSettingsPayload.ID,
				UpdateAutoPlantSettingsPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SyncAutoPlantSettingsPayload.ID,
				SyncAutoPlantSettingsPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ToggleAutoPlantPayload.ID, (payload, context) -> {
			boolean enabled = AutoPlantHandler.toggle(context.player());

			// Feedback goes to the action bar so it does not clutter the chat.
			context.player().sendOverlayMessage(Component.translatable(enabled
					? "message.automated_planting.enabled"
					: "message.automated_planting.disabled"));
		});

		ServerPlayNetworking.registerGlobalReceiver(UpdateAutoPlantSettingsPayload.ID, (payload, context) -> {
			// Never trust the wire: validate before storing.
			AutoPlantConfig.apply(payload.settings());
			AutoPlantHandler.sendSettings(context.player());
		});

		AutoPlantHandler.register();

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("autoplant")
						// Changing state affects a player and reloading reads files, so keep
						// it to operators. This is the 26.x replacement for hasPermission(2).
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
						.then(Commands.literal("on")
								.executes(ctx -> AutoPlantCommand.run(ctx.getSource(), "on")))
						.then(Commands.literal("off")
								.executes(ctx -> AutoPlantCommand.run(ctx.getSource(), "off")))
						.then(Commands.literal("toggle")
								.executes(ctx -> AutoPlantCommand.run(ctx.getSource(), "toggle")))
						.then(Commands.literal("status")
								.executes(ctx -> AutoPlantCommand.run(ctx.getSource(), "status")))
						.then(Commands.literal("reload")
								.executes(ctx -> AutoPlantCommand.run(ctx.getSource(), "reload")))
						.then(Commands.literal("info")
								.executes(ctx -> AutoPlantCommand.run(ctx.getSource(), "info")))
						.then(Commands.literal("test")
								.executes(ctx -> AutoPlantCommand.run(ctx.getSource(), "test")))
						.executes(ctx -> AutoPlantCommand.run(ctx.getSource(), "status"))));

		LOGGER.info("Initializing {}", MOD_ID);
	}
}
