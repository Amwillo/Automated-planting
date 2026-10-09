package com.example.automatedplanting.network;

import com.example.automatedplanting.AutomatedPlanting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Sent from the client to the server when the player presses the toggle key.
 *
 * <p>It carries no data: the server owns the actual on/off state and simply flips it.
 * That keeps the two sides from disagreeing about whether auto-planting is running.
 *
 * <p>This lives in the main source set because both the client and the dedicated server
 * have to know about it.
 */
public record ToggleAutoPlantPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ToggleAutoPlantPayload> ID =
			new CustomPacketPayload.Type<>(AutomatedPlanting.id("toggle_auto_plant"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ToggleAutoPlantPayload> CODEC =
			StreamCodec.unit(new ToggleAutoPlantPayload());

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
