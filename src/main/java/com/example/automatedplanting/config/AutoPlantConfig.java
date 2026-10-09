package com.example.automatedplanting.config;

import com.example.automatedplanting.AutomatedPlanting;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Settings for auto-planting, stored as JSON in {@code config/automated_planting.json}.
 *
 * <p>There is deliberately no in-game settings screen: this is a personal-use mod and
 * editing the file is enough. Changes apply with {@code /autoplant reload}, or the next
 * time the game is started.
 */
public final class AutoPlantConfig {
	/**
	 * The solid the scan covers, as a true three-dimensional region around the anchor
	 * point. {@code radius} is the reach along each distance measure.
	 */
	public enum Shape {
		/** Ball: {@code dx² + dy² + dz² <= radius²}. */
		SPHERE,
		/** Cube: {@code max(|dx|, |dy|, |dz|) <= radius}. */
		CUBE,
		/** Octahedron: {@code |dx| + |dy| + |dz| <= radius}. */
		OCTAHEDRON
	}

	/** Whether to also plant one block below/above the feet level (3x3x3 instead of a flat slice). */
	public enum SeedSource {
		/** Only the main hand is used. */
		MAIN_HAND,
		/** The main hand is tried first, then the offhand. */
		BOTH_HANDS,
		/** Only the offhand is used. */
		OFF_HAND
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = Path.of("config", "automated_planting.json");

	/** Largest accepted radius; a typo in the file must not stall the server. */
	public static final int MAX_RADIUS = 16;

	private static AutoPlantConfig instance;

	// ------------------------------------------------------------------
	// Persisted fields. The defaults here are only used when the file is absent.
	// ------------------------------------------------------------------

	/** Reach of the scan, in blocks, along each distance measure of {@link #shape}. */
	public int radius = 4;

	/** Which solid the scan covers. */
	public Shape shape = Shape.SPHERE;

	/**
	 * How many ticks to wait after a planting attempt before planting again.
	 * {@code 1} means an attempt on every server tick (20 per second);
	 * 20 ticks is one second.
	 */
	public int intervalTicks = 10;

	/**
	 * How many blocks to plant in a single attempt.
	 *
	 * <p>With a small {@code intervalTicks} a single planting per attempt is already very
	 * fast; this is what makes filling a whole field feel instant.
	 */
	public int plantsPerCycle = 1;

	/** Upper bound accepted for {@link #plantsPerCycle}. */
	public static final int MAX_PLANTS_PER_CYCLE = 64;

	/** Which hand(s) the seeds are taken from. */
	public SeedSource seedSource = SeedSource.BOTH_HANDS;

	/** Whether to log what each planting attempt is doing. */
	public boolean debugLog = false;

	// ------------------------------------------------------------------

	/** Loads the config once, falling back to defaults if the file is missing or broken. */
	public static AutoPlantConfig get() {
		AutoPlantConfig current = instance;

		if (current == null) {
			current = load();
			instance = current;
		}

		return current;
	}

	/** Re-reads the file from disk. Used by {@code /autoplant reload}. */
	public static void reload() {
		instance = load();
	}

	/**
	 * Applies settings that came from the config screen and writes them back to disk.
	 *
	 * <p>The server owns these values, so the screen never edits the file directly.
	 */
	public static AutoPlantConfig apply(AutoPlantSettings settings) {
		AutoPlantSettings safe = settings.validated();
		AutoPlantConfig updated = new AutoPlantConfig();
		updated.radius = safe.radius();
		updated.shape = safe.shape();
		updated.intervalTicks = safe.intervalTicks();
		updated.plantsPerCycle = safe.plantsPerCycle();
		updated.seedSource = safe.seedSource();
		updated.debugLog = safe.debugLog();
		updated.sanitise();
		updated.save();
		instance = updated;

		return updated;
	}

	private static AutoPlantConfig load() {
		if (!Files.exists(PATH)) {
			AutoPlantConfig fresh = new AutoPlantConfig();
			fresh.save();
			AutomatedPlanting.LOGGER.info("Created default config at {}", PATH.toAbsolutePath());

			return fresh;
		}

		try {
			String json = Files.readString(PATH, StandardCharsets.UTF_8);
			JsonObject root = JsonParser.parseString(json).getAsJsonObject();
			migrate(root);
			AutoPlantConfig loaded = GSON.fromJson(root, AutoPlantConfig.class);

			if (loaded == null) {
				throw new IOException("config file is empty");
			}

			loaded.sanitise();

			return loaded;
		} catch (Exception e) {
			// A broken config must not stop the mod from loading.
			AutomatedPlanting.LOGGER.error("Could not read {}, using defaults", PATH, e);

			return new AutoPlantConfig();
		}
	}

	/**
	 * Rewrites names from older version of this file so an upgrade does not silently
	 * reset the settings. The old shapes were flat; they map to the closest solid.
	 */
	private static void migrate(JsonObject root) {
		if (!root.has("shape")) {
			return;
		}

		String shape = root.get("shape").getAsString();
		String replacement = switch (shape) {
			case "CIRCLE" -> "SPHERE";
			case "SQUARE" -> "CUBE";
			case "DIAMOND" -> "OCTAHEDRON";
			default -> null;
		};

		if (replacement != null) {
			AutomatedPlanting.LOGGER.info("Config shape {} is from an older version, using {}", shape, replacement);
			root.addProperty("shape", replacement);
		}

		// The flat shapes had a separate vertical window; the solids do not need it.
		root.remove("includeVertical");
		root.remove("verticalReach");
	}

	/** Clamps values into a sane range so a typo in the file cannot hang the server. */
	private void sanitise() {
		if (radius < 0) {
			radius = 0;
		}

		if (radius > MAX_RADIUS) {
			AutomatedPlanting.LOGGER.warn("radius {} is too large, clamping to {}", radius, MAX_RADIUS);
			radius = MAX_RADIUS;
		}

		if (shape == null) {
			shape = Shape.SPHERE;
		}

		if (seedSource == null) {
			seedSource = SeedSource.BOTH_HANDS;
		}

		if (intervalTicks < 1) {
			intervalTicks = 1;
		}

		if (plantsPerCycle < 1) {
			plantsPerCycle = 1;
		}

		if (plantsPerCycle > MAX_PLANTS_PER_CYCLE) {
			AutomatedPlanting.LOGGER.warn("plantsPerCycle {} is too large, clamping to {}",
					plantsPerCycle, MAX_PLANTS_PER_CYCLE);
			plantsPerCycle = MAX_PLANTS_PER_CYCLE;
		}
	}

	private void save() {
		try {
			Files.createDirectories(PATH.getParent());
			Files.writeString(PATH, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			AutomatedPlanting.LOGGER.error("Could not write {}", PATH, e);
		}
	}

	/** Whether this attempt should be logged. */
	public boolean showDebug() {
		return debugLog;
	}

	/**
	 * @return true when the given offset from the scan anchor is inside the configured solid.
	 */
	public boolean covers(int dx, int dy, int dz) {
		return switch (shape) {
			case SPHERE -> (long) dx * dx + (long) dy * dy + (long) dz * dz <= (long) radius * radius;
			case CUBE -> Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) <= radius;
			case OCTAHEDRON -> Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= radius;
		};
	}
}
