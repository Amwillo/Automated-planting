package com.example.automatedplanting.network;

import com.example.automatedplanting.AutomatedPlanting;
import com.example.automatedplanting.config.AutoPlantSettings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The server's answer to {@link UpdateAutoPlantSettingsPayload}, carrying the settings it
 * actually stored.
 *
 * <p>The client displays these, so a value the server clamped is immediately visible in the
 * screen instead of silently disagreeing with the file.
 */
public record SyncAutoPlantSettingsPayload(AutoPlantSettings settings) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SyncAutoPlantSettingsPayload> ID =
			new CustomPacketPayload.Type<>(AutomatedPlanting.id("sync_settings"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SyncAutoPlantSettingsPayload> CODEC =
			StreamCodec.composite(
					AutoPlantSettingsCodec.CODEC, SyncAutoPlantSettingsPayload::settings,
					SyncAutoPlantSettingsPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
