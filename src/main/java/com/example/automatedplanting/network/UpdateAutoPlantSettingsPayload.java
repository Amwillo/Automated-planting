package com.example.automatedplanting.network;

import com.example.automatedplanting.AutomatedPlanting;
import com.example.automatedplanting.config.AutoPlantSettings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The config screen's "apply" button: sends the edited settings to the server.
 *
 * <p>The server owns the settings, so the screen never writes the file itself. The server
 * validates what arrives, applies it, and answers with {@link SyncAutoPlantSettingsPayload}.
 */
public record UpdateAutoPlantSettingsPayload(AutoPlantSettings settings) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<UpdateAutoPlantSettingsPayload> ID =
			new CustomPacketPayload.Type<>(AutomatedPlanting.id("update_settings"));

	public static final StreamCodec<RegistryFriendlyByteBuf, UpdateAutoPlantSettingsPayload> CODEC =
			StreamCodec.composite(
					AutoPlantSettingsCodec.CODEC, UpdateAutoPlantSettingsPayload::settings,
					UpdateAutoPlantSettingsPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
