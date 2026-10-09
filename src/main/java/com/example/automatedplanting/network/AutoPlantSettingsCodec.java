package com.example.automatedplanting.network;

import com.example.automatedplanting.config.AutoPlantConfig;
import com.example.automatedplanting.config.AutoPlantSettings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Wire format for {@link AutoPlantSettings}, shared by the client and server packets.
 *
 * <p>The shape is sent as its ordinal and clamped on both ends, so a mismatched or
 * hostile value can never index outside the enum.
 */
public final class AutoPlantSettingsCodec {
	/**
	 * Sends the shape as a var-int ordinal, wrapping any out-of-range value so a
	 * mismatched or hostile index can never escape the enum.
	 */
	public static final StreamCodec<RegistryFriendlyByteBuf, AutoPlantConfig.Shape> SHAPE =
			StreamCodec.of(
					(buf, shape) -> buf.writeVarInt(shape.ordinal()),
					buf -> AutoPlantConfig.Shape.values()[
							Math.floorMod(buf.readVarInt(), AutoPlantConfig.Shape.values().length)]);

	public static final StreamCodec<RegistryFriendlyByteBuf, AutoPlantConfig.SeedSource> SEED_SOURCE =
			StreamCodec.of(
					(buf, source) -> buf.writeVarInt(source.ordinal()),
					buf -> AutoPlantConfig.SeedSource.values()[
							Math.floorMod(buf.readVarInt(), AutoPlantConfig.SeedSource.values().length)]);

	public static final StreamCodec<RegistryFriendlyByteBuf, AutoPlantSettings> CODEC =
			StreamCodec.composite(
					ByteBufCodecs.VAR_INT, AutoPlantSettings::radius,
					SHAPE, AutoPlantSettings::shape,
					ByteBufCodecs.VAR_INT, AutoPlantSettings::intervalTicks,
					ByteBufCodecs.VAR_INT, AutoPlantSettings::plantsPerCycle,
					SEED_SOURCE, AutoPlantSettings::seedSource,
					ByteBufCodecs.BOOL, AutoPlantSettings::debugLog,
					AutoPlantSettings::new);

	private AutoPlantSettingsCodec() {
	}
}
