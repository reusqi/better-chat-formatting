package com.betterchatformatting.fabric.chat;

import com.betterchatformatting.core.classify.MessageClassifier;
import com.betterchatformatting.core.classify.MessageType;
import com.betterchatformatting.core.config.ChatConfig;
import com.betterchatformatting.core.config.ConfigManager;
import com.betterchatformatting.core.qol.RepeatCollapser;
import com.betterchatformatting.core.qol.TimestampFormatter;
import com.betterchatformatting.core.text.Segment;
import com.betterchatformatting.core.theme.Theme;
import com.betterchatformatting.core.theme.ThemeShades;
import com.betterchatformatting.fabric.BetterChatFormattingClient;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Decides how every incoming chat line looks: text and markdown, theme roles, sender and mention
 * colors, the separator, type styling, timestamp, repeat count and the accent bar. Returns the new
 * content and tag for the caller to display.
 *
 * <p>Runs for every line, so it does nothing expensive: parsing is cached in {@link ChatRestyler},
 * and the timestamp formatter is rebuilt only when its pattern changes.
 */
public final class ChatPipeline {
	/** Used for the timestamp when the theme has no timestamp color. */
	private static final int TIMESTAMP_FALLBACK_COLOR = 0x808080;
	private static final int REPEAT_COLOR = 0xAAAAAA;
	private static final int NO_COLOR = Segment.NO_COLOR;

	private static final RepeatCollapser REPEATS = new RepeatCollapser();
	private static String formatterPattern;
	private static TimestampFormatter formatter;

	/**
	 * @param content   the content to display (the original instance when {@code changed} is false)
	 * @param tag       the tag to display with it (the original tag when {@code changed} is false)
	 * @param changed   whether the line must be replaced
	 * @param collapsed whether this line repeats the previous one and must replace it on screen
	 */
	public record Outcome(Component content, GuiMessageTag tag, boolean changed, boolean collapsed) {
	}

	private ChatPipeline() {
	}

	/**
	 * @param hasPrevious whether chat currently holds a line that a repeat could replace
	 */
	public static synchronized Outcome process(Component contents, GuiMessageSource source, GuiMessageTag tag,
			boolean hasPrevious) {
		ChatConfig c = BetterChatFormattingClient.config().snapshot().config();
		if (!c.enabled) {
			REPEATS.reset();
			return unchanged(contents, tag);
		}
		try {
			return apply(contents, source, tag, c, hasPrevious);
		} catch (RuntimeException e) {
			BetterChatFormattingClient.LOGGER.warn("Could not decorate a chat message, showing it unchanged", e);
			REPEATS.reset();
			return unchanged(contents, tag);
		}
	}

	private static Outcome apply(Component contents, GuiMessageSource source, GuiMessageTag tag, ChatConfig c,
			boolean hasPrevious) {
		ConfigManager.Snapshot snapshot = BetterChatFormattingClient.config().snapshot();
		String raw = contents.getString();
		MessageType type = typeOf(contents, raw, source);
		Theme theme = ChatTheme.active(c, snapshot);

		// 1. Text: markdown, links and highlights. Applies to player chat and to server system
		// messages, because many servers send chat as system packets with pre-colored leaves.
		Component body = contents;
		String sound = null;
		if (c.features.markdown || c.features.links || c.features.highlights) {
			ChatRestyler.Restyled restyled = ChatRestyler.restyle(contents);
			body = restyled.component();
			sound = c.features.highlights ? restyled.sound() : null;
		}

		// 2. Repeats: compared by normalized plain text only. Hover data, click data, colors and the
		// message source are ignored, so server copies with different metadata still collapse.
		boolean collapsed = false;
		int count = 1;
		if (c.features.collapseRepeats) {
			if (!hasPrevious) {
				REPEATS.reset();
			}
			count = REPEATS.register(RepeatCollapser.normalize(raw));
			collapsed = count > 1;
		} else {
			REPEATS.reset();
		}

		// 3. Type color and icon for non-player lines (join, leave, death...).
		ChatConfig.TypeStyle style = c.features.messageTypes ? c.messageTypes.get(type) : null;
		boolean typed = style != null && style.enabled && type != MessageType.PLAYER_CHAT
				&& (style.color != NO_COLOR || (style.icon != null && !style.icon.isBlank()));

		// 4. Theme roles. With a theme text color, every server color on the line is replaced by the role
		// for this kind of line. Otherwise a typed line uses its own type color.
		ThemeShades shades = ChatTheme.shades(c, snapshot);
		int baseColor = NO_COLOR;
		if (shades != null) {
			baseColor = ChatTheme.lineColor(shades, type);
		} else if (typed && style.color != NO_COLOR) {
			baseColor = style.color;
		}

		// 5. Sender, rank, own-name mentions and the separator. Runs last, so it wins over the role color.
		// Matches on the plain text, so it works on Hypixel-style lines as well as vanilla <Name> lines.
		String ownName = ChatTheme.ownName();
		SenderColors.Paint paint = ChatTheme.paint(c, shades, ownName).withBase(baseColor);
		if (!paint.isEmpty()) {
			body = SenderColors.apply(body, paint);
		}

		// 6. Accent bar: a 2px bar at the left edge, colored by category. A tag that is already set
		// (for example "not secure") is kept, so vanilla indicators still show.
		boolean mentionsMe = SenderColors.mentions(raw, ownName);
		GuiMessageTag newTag = tag;
		boolean accent = c.features.accentBar && shades != null && tag == null;
		if (accent) {
			newTag = ChatTheme.accentTag(ChatTheme.categoryColor(shades, type, mentionsMe));
		}

		// 7. Timestamp.
		boolean stamped = c.features.timestamps;

		if (sound != null) {
			ChatSounds.play(sound, c.highlights.soundVolume);
		}

		boolean changed = body != contents;
		if (!changed && !typed && !stamped && !collapsed && !accent) {
			return unchanged(contents, tag);
		}

		MutableComponent wrap = Component.literal("");
		if (stamped) {
			int color = theme.timestampColor();
			if (color == NO_COLOR) {
				color = TIMESTAMP_FALLBACK_COLOR;
			}
			wrap.append(Component.literal("[" + timeFormatter(c.timestamps.format).format(System.currentTimeMillis()) + "] ")
					.withStyle(Style.EMPTY.withColor(color)));
		}
		if (typed) {
			// The body was recolored in step 4, so the type color shows on the whole line.
			MutableComponent typedNode = Component.literal("");
			if (style.color != NO_COLOR) {
				typedNode.setStyle(Style.EMPTY.withColor(style.color));
			}
			if (style.icon != null && !style.icon.isBlank()) {
				typedNode.append(Component.literal(style.icon + " "));
			}
			typedNode.append(body);
			wrap.append(typedNode);
		} else {
			wrap.append(body);
		}
		if (collapsed) {
			wrap.append(Component.literal(RepeatCollapser.suffix(count))
					.withStyle(Style.EMPTY.withColor(REPEAT_COLOR)));
		}
		return new Outcome(wrap, newTag, true, collapsed);
	}

	/** Player lines are always {@link MessageType#PLAYER_CHAT}; everything else is classified by its key. */
	private static MessageType typeOf(Component contents, String raw, GuiMessageSource source) {
		if (source == GuiMessageSource.PLAYER) {
			return MessageType.PLAYER_CHAT;
		}
		if (contents.getContents() instanceof TranslatableContents translatable) {
			return MessageClassifier.classifyKey(translatable.getKey());
		}
		return MessageClassifier.classifyLiteral(raw);
	}

	private static TimestampFormatter timeFormatter(String pattern) {
		if (formatter == null || !pattern.equals(formatterPattern)) {
			formatterPattern = pattern;
			formatter = new TimestampFormatter(pattern, null);
		}
		return formatter;
	}

	private static Outcome unchanged(Component contents, GuiMessageTag tag) {
		return new Outcome(contents, tag, false, false);
	}
}
