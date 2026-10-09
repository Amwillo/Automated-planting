package com.example.automatedplanting.config;

/**
 * A validated snapshot of the auto-planting settings.
 *
 * <p>This is what travels between the client and the server, and what the config screen
 * edits. Turning the settings into one flat record keeps the GUI, the network packets and
 * the JSON file from each having their own idea of what is valid.
 */
public record AutoPlantSettings(
		int radius,
		AutoPlantConfig.Shape shape,
		int intervalTicks,
		int plantsPerCycle,
		AutoPlantConfig.SeedSource seedSource,
		boolean debugLog) {

	/** The settings a fresh install starts with. */
	public static AutoPlantSettings defaults() {
		return new AutoPlantSettings(4, AutoPlantConfig.Shape.SPHERE, 10, 1,
				AutoPlantConfig.SeedSource.BOTH_HANDS, false);
	}

	/** Clamps every field into its accepted range, repairing out-of-range network input. */
	public AutoPlantSettings validated() {
		return new AutoPlantSettings(
				clamp(radius, 0, AutoPlantConfig.MAX_RADIUS),
				shape == null ? AutoPlantConfig.Shape.SPHERE : shape,
				clamp(intervalTicks, 1, Integer.MAX_VALUE),
				clamp(plantsPerCycle, 1, AutoPlantConfig.MAX_PLANTS_PER_CYCLE),
				seedSource == null ? AutoPlantConfig.SeedSource.BOTH_HANDS : seedSource,
				debugLog);
	}

	public static AutoPlantSettings from(AutoPlantConfig config) {
		return new AutoPlantSettings(config.radius, config.shape, config.intervalTicks,
				config.plantsPerCycle, config.seedSource, config.debugLog);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
