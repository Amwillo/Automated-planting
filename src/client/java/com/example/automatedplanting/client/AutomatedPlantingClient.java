package com.example.automatedplanting.client;

import com.example.automatedplanting.config.AutoPlantConfig;
import com.example.automatedplanting.network.SyncAutoPlantSettingsPayload;
import com.example.automatedplanting.network.ToggleAutoPlantPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Client entry point.
 *
 * <p>The key that toggles auto-planting can only be registered on the client, and the
 * server is the only side allowed to actually place blocks, so the toggling code here
 * does nothing more than notice the key press and tell the server about it.
 */
public class AutomatedPlantingClient implements ClientModInitializer {
	/** Translation key / id of the toggle key. */
	public static final String TOGGLE_KEY_ID = "key.automated_planting.toggle";

	/** Translation key / id of the key that opens the settings screen. */
	public static final String CONFIG_KEY_ID = "key.automated_planting.config";

	private static KeyMapping toggleKey;
	private static KeyMapping configKey;

	@Override
	public void onInitializeClient() {
		AutoPlantConfig.get();

		Identifier categoryId = Identifier.fromNamespaceAndPath(
				com.example.automatedplanting.AutomatedPlanting.MOD_ID, "controls");
		KeyMapping.Category category = KeyMapping.Category.register(categoryId);

		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				TOGGLE_KEY_ID, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, category));

		configKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				CONFIG_KEY_ID, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, category));

		ClientPlayNetworking.registerGlobalReceiver(SyncAutoPlantSettingsPayload.ID, (payload, context) ->
				context.client().execute(() -> AutoPlantConfigScreen.acceptServerSettings(payload.settings())));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// The settings screen does not need a world, so it can be opened from the
			// title screen too. It only becomes useful once connected, since the server
			// owns the values.
			while (configKey.consumeClick()) {
				client.setScreen(new AutoPlantConfigScreen());
			}

			// consumeClick() drains every press queued since the last tick, so holding
			// the key does not flip the toggle back and forth.
			while (toggleKey.consumeClick()) {
				if (client.player == null) {
					continue;
				}

				if (ClientPlayNetworking.canSend(ToggleAutoPlantPayload.ID)) {
					ClientPlayNetworking.send(new ToggleAutoPlantPayload());
				}
			}
		});
	}
}
