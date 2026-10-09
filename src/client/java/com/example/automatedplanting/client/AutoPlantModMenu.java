package com.example.automatedplanting.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu integration, registered as an optional entrypoint.
 *
 * <p>Mod Menu is a compile-time-only dependency: the mod detects this entrypoint at
 * runtime and everything else keeps working when Mod Menu is not installed.
 */
public class AutoPlantModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> new AutoPlantConfigScreen();
	}
}
