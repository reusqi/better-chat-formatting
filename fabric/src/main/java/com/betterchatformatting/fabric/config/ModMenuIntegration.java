package com.betterchatformatting.fabric.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu entrypoint. Mod Menu loads this class only when it is installed, so the mod runs
 * without it; nothing else in the mod references Mod Menu types.
 */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ChatConfigScreen::new;
	}
}
