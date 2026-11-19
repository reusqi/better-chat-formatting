package com.betterchatformatting.fabric.chat;

import com.betterchatformatting.core.classify.MessageType;
import com.betterchatformatting.core.config.ChatConfig;
import com.betterchatformatting.core.config.ConfigManager;
import com.betterchatformatting.core.names.NameColors;
import com.betterchatformatting.core.theme.BuiltInThemes;
import com.betterchatformatting.core.theme.Theme;
import com.betterchatformatting.core.theme.ThemeShades;
import com.betterchatformatting.fabric.BetterChatFormattingClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;

/**
 * The theme the chat actually uses. With "Theme colors" off, the vanilla look is used, the same as
 * the settings preview.
 */
public final class ChatTheme {
	private static final String VANILLA_ID = "vanilla";

	private ChatTheme() {
	}

	public static Theme active(ChatConfig config, ConfigManager.Snapshot snapshot) {
		return config.features.applyTheme ? snapshot.activeTheme() : BuiltInThemes.VANILLA;
	}

	/** The shades for the active theme, or null when the theme sets no text color (vanilla colors are kept). */
	public static ThemeShades shades(ChatConfig config, ConfigManager.Snapshot snapshot) {
		Theme theme = active(config, snapshot);
		return theme.textColor() < 0 ? null : ThemeShades.of(theme);
	}

	/** The line color for one kind of chat line: whispers, the body, or the dimmer system shade. */
	public static int lineColor(ThemeShades shades, MessageType type) {
		if (type == MessageType.WHISPER) {
			return shades.whisper();
		}
		if (type == MessageType.PLAYER_CHAT) {
			return shades.body();
		}
		return shades.system();
	}

	/**
	 * Category color for the accent bar. A mention of the local player wins, then whispers, then other
	 * players, then everything else (system, join, leave, death, advancement).
	 */
	public static int categoryColor(ThemeShades shades, MessageType type, boolean mentionsMe) {
		if (mentionsMe) {
			return shades.mention();
		}
		if (type == MessageType.WHISPER) {
			return shades.whisper();
		}
		if (type == MessageType.PLAYER_CHAT) {
			return shades.sender();
		}
		return shades.system();
	}

	/** A tag that draws only the accent bar: no icon, no tooltip. */
	public static GuiMessageTag accentTag(int color) {
		return new GuiMessageTag(color, null, null, "betterchatformatting");
	}

	/** The local player's name, or null when no account is loaded. */
	public static String ownName() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.getUser() == null) {
			return null;
		}
		return mc.getUser().getName();
	}

	/**
	 * What to paint on sender lines. With a theme, other players get the accent (bold), the rank tag gets
	 * the same accent, and mentions of {@code ownName} get the mention color. Without a theme, names use the
	 * name-color feature. The separator option applies either way.
	 */
	public static SenderColors.Paint paint(ChatConfig c, ThemeShades shades, String ownName) {
		NameColors names = c.features.nameColors ? c.names.toNameColors() : null;
		java.util.function.Function<String, Integer> nameColor = null;
		if (shades != null) {
			nameColor = name -> shades.sender();
		} else if (names != null) {
			nameColor = names::colorFor;
		}
		return new SenderColors.Paint(
				shades != null ? shades.sender() : -1,
				nameColor,
				shades != null,
				ownName,
				shades != null ? shades.mention() : -1,
				c.features.cleanSeparator,
				-1);
	}

	/**
	 * Background for one chat line, as ARGB. Vanilla computes the line's fade alpha and black color;
	 * this keeps that fade and swaps in the theme's color and opacity. The vanilla theme returns the
	 * input unchanged, so the default look is exactly vanilla.
	 */
	public static int lineBackground(int vanillaArgb) {
		try {
			ConfigManager.Snapshot snapshot = BetterChatFormattingClient.config().snapshot();
			ChatConfig config = snapshot.config();
			if (!config.enabled || !config.features.applyTheme) {
				return vanillaArgb;
			}
			Theme theme = snapshot.activeTheme();
			if (VANILLA_ID.equals(theme.id())) {
				return vanillaArgb;
			}
			int alpha = Math.round((vanillaArgb >>> 24) * theme.backgroundOpacity());
			return (alpha << 24) | theme.backgroundColor();
		} catch (RuntimeException e) {
			return vanillaArgb;
		}
	}
}
