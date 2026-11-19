package com.betterchatformatting.fabric;

import com.betterchatformatting.core.config.ConfigManager;
import com.betterchatformatting.fabric.chat.ChatView;
import com.betterchatformatting.fabric.config.ChatKeys;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric client entrypoint. Loads the config, starts the hot-reload tick and exposes the config
 * manager to the chat hooks. Everything else lives in {@code core} (Minecraft-free) or in the mixin
 * documented in {@code docs/MIXINS.md}.
 */
public final class BetterChatFormattingClient implements ClientModInitializer {
	public static final String MOD_ID = "betterchatformatting";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Check the config files for changes every 40 ticks (2 seconds at 20 TPS). */
	private static final int RELOAD_CHECK_TICKS = 40;

	private static ConfigManager configManager;
	private static int tickCounter;

	/** The config manager. Only valid after {@link #onInitializeClient()} has run. */
	public static ConfigManager config() {
		return configManager;
	}

	@Override
	public void onInitializeClient() {
		configManager = new ConfigManager(FabricLoader.getInstance().getConfigDir(), new Slf4jConfigLogger(LOGGER));
		configManager.load();
		// Layout changes (chat width/height) need the chat re-wrapped.
		configManager.addListener(snapshot -> ChatView.refreshLayout());

		// Hot reload: a cheap signature check on the client thread, no extra threads.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (++tickCounter % RELOAD_CHECK_TICKS == 0) {
				configManager.reloadIfChanged();
			}
		});

		// Keybinding that opens the in-game settings screen.
		ChatKeys.register();

		LOGGER.info("Better Chat Formatting loaded (client-side only)");
	}
}
