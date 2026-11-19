package com.betterchatformatting.fabric.config;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * Registers the keybinding that opens {@link ChatConfigScreen} in-game. The key is rebindable in
 * Options &gt; Controls under the "Better Chat Formatting" category.
 */
public final class ChatKeys {
	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath("betterchatformatting", "general"));

	private ChatKeys() {
	}

	public static void register() {
		KeyMapping openConfig = new KeyMapping(
				"key.betterchatformatting.open_config",
				InputConstants.Type.KEYBOARD,
				InputConstants.KEY_K,
				CATEGORY);
		KeyMappingHelper.registerKeyMapping(openConfig);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openConfig.consumeClick()) {
				if (client.gui.screen() == null) {
					client.gui.setScreen(new ChatConfigScreen(null));
				}
			}
		});
	}
}
