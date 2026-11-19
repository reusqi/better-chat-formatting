package com.betterchatformatting.core.config;

import com.betterchatformatting.core.classify.MessageType;
import com.betterchatformatting.core.highlight.HighlightRule;
import com.betterchatformatting.core.highlight.KeywordMatcher;
import com.betterchatformatting.core.names.NameColors;
import com.betterchatformatting.core.qol.TimestampFormatter;
import com.betterchatformatting.core.text.Segment;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The whole user configuration as plain data. Field defaults here are the defaults of the config
 * file. Instances are mutable so the config screen can edit a working copy, but a copy only
 * becomes active after {@code ConfigManager.save}, which re-validates everything.
 *
 * <p>Parsing and writing live in {@link ConfigIO}; nothing here touches Minecraft or the disk.
 */
public final class ChatConfig {
	/** Master switch: false turns every feature off, leaving vanilla chat. */
	public boolean enabled = true;
	/** Id of the active theme (built-in, or a file in the themes folder). */
	public String theme = "midnight";

	public final Features features = new Features();
	public final Timestamps timestamps = new Timestamps();
	public final Names names = new Names();
	public final Highlights highlights = new Highlights();
	public final Map<MessageType, TypeStyle> messageTypes = defaultTypeStyles();
	public final Layout layout = new Layout();

	/** Individual feature toggles, so players can switch off whatever conflicts with another mod. */
	public static final class Features {
		public boolean applyTheme = true;
		public boolean nameColors = true;
		public boolean highlights = true;
		public boolean messageTypes = true;
		public boolean markdown = true;
		public boolean links = true;
		public boolean timestamps = false;
		public boolean collapseRepeats = true;
		public boolean search = true;
		public boolean emojiPicker = true;
		public boolean layout = true;
		/** A 2px colored bar at the left edge of each line, colored by category (player, mention, whisper, system). */
		public boolean accentBar = true;
		/** Shows "Name › message" instead of "Name: message" on player lines. */
		public boolean cleanSeparator = true;
	}

	public static final class Timestamps {
		/** {@link java.time.format.DateTimeFormatter} pattern. */
		public String format = TimestampFormatter.DEFAULT_PATTERN;
	}

	public static final class Names {
		public float saturation = 0.55f;
		public float value = 0.95f;
		/** Player name → "#RRGGBB". Insertion-ordered so the file stays stable. */
		public Map<String, String> overrides = new LinkedHashMap<>();

		public NameColors toNameColors() {
			return new NameColors(overrides, saturation, value);
		}
	}

	public static final class Highlights {
		public boolean ownName = true;
		public int ownNameColor = 0xFFD700;
		/** Sound event id, or "" for silent. Used for the own-name highlight. */
		public String ownNameSound = "minecraft:block.note_block.pling";
		public float soundVolume = 0.6f;
		public List<HighlightRule> keywords = new ArrayList<>();

		/**
		 * Builds the matcher for the current player: their own name first (so it wins overlaps),
		 * then the custom keywords. Build once per config/name change, not per message.
		 */
		public KeywordMatcher toMatcher(String playerName) {
			List<HighlightRule> rules = new ArrayList<>();
			if (ownName && playerName != null && !playerName.isBlank()) {
				rules.add(HighlightRule.of(playerName, ownNameColor, ownNameSound.isBlank() ? null : ownNameSound));
			}
			rules.addAll(keywords);
			return new KeywordMatcher(rules);
		}
	}

	/** Color and icon for one message type. */
	public static final class TypeStyle {
		public boolean enabled;
		/** RGB or {@link Segment#NO_COLOR} to keep the message's own colors. */
		public int color;
		/** Short text shown before the line (may be empty). Display only; never sent anywhere. */
		public String icon;

		public TypeStyle(boolean enabled, int color, String icon) {
			this.enabled = enabled;
			this.color = color;
			this.icon = icon;
		}
	}

	public static final class Layout {
		/** Chat width in pixels; 0 keeps the vanilla setting. */
		public int chatWidth = 0;
		/** Chat height in pixels; 0 keeps the vanilla setting. */
		public int chatHeight = 0;
		/** Lines scrolled per mouse-wheel notch, 1..10. 7 matches vanilla. */
		public int scrollLines = 7;
	}

	private static Map<MessageType, TypeStyle> defaultTypeStyles() {
		Map<MessageType, TypeStyle> m = new EnumMap<>(MessageType.class);
		m.put(MessageType.PLAYER_CHAT, new TypeStyle(false, Segment.NO_COLOR, ""));
		m.put(MessageType.SYSTEM, new TypeStyle(true, 0xAAAAAA, ""));
		m.put(MessageType.JOIN, new TypeStyle(true, 0x55FF55, "+"));
		m.put(MessageType.LEAVE, new TypeStyle(true, 0xFF7777, "-"));
		m.put(MessageType.DEATH, new TypeStyle(true, 0xCC6666, "☠"));
		m.put(MessageType.ADVANCEMENT, new TypeStyle(true, 0xFFD700, "★"));
		m.put(MessageType.WHISPER, new TypeStyle(true, 0xFF88FF, "✉"));
		return m;
	}
}
