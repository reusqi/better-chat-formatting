package com.betterchatformatting.fabric.chat;

import com.betterchatformatting.core.config.ChatConfig;
import com.betterchatformatting.core.config.ConfigManager;
import com.betterchatformatting.core.qol.ChatSearch;
import com.betterchatformatting.fabric.BetterChatFormattingClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;

/**
 * Glue between the config and the chat window: layout overrides, scroll speed, search filter and
 * the emoji toggle. Every method is safe to call before the game is fully loaded.
 */
public final class ChatView {
	/** Vanilla scrolls 7 lines per wheel notch (see {@code ChatScreen.mouseScrolled}). */
	private static final int VANILLA_NOTCH_LINES = 7;

	private ChatView() {
	}

	private static ChatConfig config() {
		ConfigManager manager = BetterChatFormattingClient.config();
		return manager == null ? new ChatConfig() : manager.snapshot().config();
	}

	private static ChatComponent chat() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.gui == null) {
			return null;
		}
		return mc.gui.hud.getChat();
	}

	/** Chat width in logical pixels, or 0 to keep the vanilla value. */
	public static int width() {
		ChatConfig c = config();
		return c.enabled && c.features.layout ? c.layout.chatWidth : 0;
	}

	/** Chat height in logical pixels, or 0 to keep the vanilla value. */
	public static int height() {
		ChatConfig c = config();
		return c.enabled && c.features.layout ? c.layout.chatHeight : 0;
	}

	/**
	 * Converts the vanilla wheel amount (7 lines per notch, 1 when Shift is held) to the configured
	 * lines per notch. Shift-scrolling stays precise and is not scaled.
	 */
	public static int scrollAmount(int vanillaAmount) {
		ChatConfig c = config();
		if (!c.enabled || !c.features.layout || vanillaAmount == 0) {
			return vanillaAmount;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc != null && mc.hasShiftDown()) {
			return vanillaAmount;
		}
		int lines = c.layout.scrollLines;
		int magnitude = Math.max(1, Math.round(Math.abs(vanillaAmount) * lines / (float) VANILLA_NOTCH_LINES));
		return Integer.signum(vanillaAmount) * magnitude;
	}

	public static boolean searchEnabled() {
		ChatConfig c = config();
		return c.enabled && c.features.search;
	}

	public static boolean emojiEnabled() {
		ChatConfig c = config();
		return c.enabled && c.features.emojiPicker;
	}

	/** Shows only messages whose text matches every term of {@code query}. An empty query shows everything. */
	public static void applySearch(String query) {
		ChatComponent chat = chat();
		if (chat == null) {
			return;
		}
		ChatSearch search = ChatSearch.parse(query);
		if (search.isEmpty()) {
			chat.setVisibleMessageFilter(message -> true);
		} else {
			chat.setVisibleMessageFilter(message -> search.matches(message.content().getString()));
		}
	}

	/** Re-wraps the chat after a width or height change. Called from the config listener. */
	public static void refreshLayout() {
		ChatComponent chat = chat();
		if (chat != null) {
			chat.rescaleChat();
		}
	}
}
