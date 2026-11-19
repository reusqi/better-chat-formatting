package com.betterchatformatting.core.config;

import com.betterchatformatting.core.classify.MessageType;
import com.betterchatformatting.core.highlight.HighlightRule;
import com.betterchatformatting.core.io.ConfigException;
import com.betterchatformatting.core.io.FieldReader;
import com.betterchatformatting.core.text.Colors;
import com.betterchatformatting.core.text.Segment;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads and writes the config file text.
 *
 * <p><b>Reading</b> is lenient (comments, unquoted keys) and forgiving: each field is validated on
 * its own, so a typo costs only that field, which falls back to its default (see
 * {@link FieldReader}). Only a file that is not JSON at all throws {@link ConfigException}.
 *
 * <p><b>Writing</b> produces the commented file the player edits. Comments are generated, so
 * hand-written comments are replaced the next time the config screen saves.
 */
public final class ConfigIO {
	public static final int MAX_KEYWORDS = 64;
	public static final int MAX_OVERRIDES = 256;
	public static final int MAX_KEYWORD_LENGTH = 64;
	public static final int MAX_ICON_LENGTH = 8;

	public record ParseResult(ChatConfig config, List<String> warnings) {
	}

	private ConfigIO() {
	}

	// ------------------------------------------------------------------ reading

	public static ParseResult read(String text) throws ConfigException {
		JsonObject root = FieldReader.parseObject(text);
		List<String> warnings = new ArrayList<>();
		FieldReader r = new FieldReader(warnings);
		ChatConfig c = new ChatConfig();

		c.enabled = r.bool(root, "enabled", c.enabled);
		String theme = r.string(root, "theme", c.theme).strip().toLowerCase(Locale.ROOT);
		c.theme = theme.isEmpty() ? c.theme : theme;

		JsonObject f = r.object(root, "features");
		if (f != null) {
			FieldReader fr = r.at("features");
			ChatConfig.Features d = c.features;
			d.applyTheme = fr.bool(f, "applyTheme", d.applyTheme);
			d.nameColors = fr.bool(f, "nameColors", d.nameColors);
			d.highlights = fr.bool(f, "highlights", d.highlights);
			d.messageTypes = fr.bool(f, "messageTypes", d.messageTypes);
			d.accentBar = fr.bool(f, "accentBar", d.accentBar);
			d.cleanSeparator = fr.bool(f, "cleanSeparator", d.cleanSeparator);
			d.markdown = fr.bool(f, "markdown", d.markdown);
			d.links = fr.bool(f, "links", d.links);
			d.timestamps = fr.bool(f, "timestamps", d.timestamps);
			d.collapseRepeats = fr.bool(f, "collapseRepeats", d.collapseRepeats);
			d.search = fr.bool(f, "search", d.search);
			d.emojiPicker = fr.bool(f, "emojiPicker", d.emojiPicker);
			d.layout = fr.bool(f, "layout", d.layout);
		}

		JsonObject ts = r.object(root, "timestamps");
		if (ts != null) {
			String fmt = r.at("timestamps").string(ts, "format", c.timestamps.format);
			c.timestamps.format = fmt.isBlank() ? c.timestamps.format : fmt.strip();
		}

		JsonObject names = r.object(root, "names");
		if (names != null) {
			FieldReader nr = r.at("names");
			c.names.saturation = nr.decimal(names, "saturation", c.names.saturation, 0f, 1f);
			c.names.value = nr.decimal(names, "value", c.names.value, 0f, 1f);
			readOverrides(nr, names, c.names.overrides);
		}

		JsonObject hl = r.object(root, "highlights");
		if (hl != null) {
			FieldReader hr = r.at("highlights");
			ChatConfig.Highlights h = c.highlights;
			h.ownName = hr.bool(hl, "ownName", h.ownName);
			h.ownNameColor = hr.color(hl, "ownNameColor", h.ownNameColor, false);
			h.ownNameSound = readSound(hr, hl, "ownNameSound", h.ownNameSound);
			h.soundVolume = hr.decimal(hl, "soundVolume", h.soundVolume, 0f, 1f);
			readKeywords(hr, hl, h.keywords);
		}

		JsonObject types = r.object(root, "messageTypes");
		if (types != null) {
			FieldReader tr = r.at("messageTypes");
			for (MessageType t : MessageType.values()) {
				if (t == MessageType.PLAYER_CHAT) {
					continue; // player chat is styled by name colors/highlights, not a type color
				}
				String key = typeKey(t);
				JsonObject o = tr.object(types, key);
				if (o == null) {
					continue;
				}
				FieldReader sr = tr.at(key);
				ChatConfig.TypeStyle s = c.messageTypes.get(t);
				s.enabled = sr.bool(o, "enabled", s.enabled);
				s.color = sr.color(o, "color", s.color, true);
				s.icon = sanitizeIcon(sr.string(o, "icon", s.icon));
			}
		}

		JsonObject lo = r.object(root, "layout");
		if (lo != null) {
			FieldReader lr = r.at("layout");
			c.layout.chatWidth = zeroOrAtLeast(lr, "chatWidth", lr.integer(lo, "chatWidth", 0, 0, 800), 80);
			c.layout.chatHeight = zeroOrAtLeast(lr, "chatHeight", lr.integer(lo, "chatHeight", 0, 0, 600), 40);
			c.layout.scrollLines = lr.integer(lo, "scrollLines", c.layout.scrollLines, 1, 10);
		}
		return new ParseResult(c, List.copyOf(warnings));
	}

	private static int zeroOrAtLeast(FieldReader r, String key, int value, int min) {
		if (value > 0 && value < min) {
			r.warn(key, "values between 1 and " + (min - 1) + " are too small, using " + min);
			return min;
		}
		return value;
	}

	private static void readOverrides(FieldReader nr, JsonObject names, Map<String, String> out) {
		JsonObject o = nr.object(names, "overrides");
		if (o == null) {
			return;
		}
		out.clear();
		for (Map.Entry<String, JsonElement> e : o.entrySet()) {
			if (out.size() >= MAX_OVERRIDES) {
				nr.warn("overrides", "more than " + MAX_OVERRIDES + " entries, ignoring the rest");
				break;
			}
			String name = e.getKey().strip();
			JsonElement v = e.getValue();
			int color = v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()
					? Colors.parseHex(v.getAsString()) : Segment.NO_COLOR;
			if (name.isEmpty() || name.length() > 32 || color == Segment.NO_COLOR) {
				nr.warn("overrides." + e.getKey(), "needs a player name and a color like \"#RRGGBB\", skipped");
				continue;
			}
			out.put(name, Colors.toHex(color));
		}
	}

	private static void readKeywords(FieldReader hr, JsonObject hl, List<HighlightRule> out) {
		JsonElement e = hl.get("keywords");
		if (e == null || e.isJsonNull()) {
			return;
		}
		if (!e.isJsonArray()) {
			hr.warn("keywords", "expected a list [ ... ], ignoring");
			return;
		}
		out.clear();
		int index = 0;
		for (JsonElement item : e.getAsJsonArray()) {
			String where = "keywords[" + index++ + "]";
			if (out.size() >= MAX_KEYWORDS) {
				hr.warn("keywords", "more than " + MAX_KEYWORDS + " entries, ignoring the rest");
				break;
			}
			HighlightRule rule = null;
			if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
				rule = HighlightRule.of(item.getAsString(), 0x55FFFF, null);
			} else if (item.isJsonObject()) {
				JsonObject o = item.getAsJsonObject();
				FieldReader kr = hr.at(where);
				rule = new HighlightRule(
						kr.string(o, "keyword", ""),
						kr.color(o, "color", 0x55FFFF, false),
						readSound(kr, o, "sound", ""),
						kr.bool(o, "caseSensitive", false),
						kr.bool(o, "wholeWord", true));
			}
			if (rule == null || !rule.isUsable() || rule.keyword().length() > MAX_KEYWORD_LENGTH) {
				hr.warn(where, "needs a non-empty \"keyword\" of at most " + MAX_KEYWORD_LENGTH + " characters, skipped");
				continue;
			}
			out.add(rule);
		}
	}

	/** Sound ids look like {@code minecraft:block.note_block.pling}; "" means silent. */
	private static String readSound(FieldReader r, JsonObject o, String key, String def) {
		String s = r.string(o, key, def).strip().toLowerCase(Locale.ROOT);
		if (s.isEmpty() || isValidSoundId(s)) {
			return s;
		}
		r.warn(key, "\"" + s + "\" is not a valid sound id, using \"" + def + "\"");
		return def;
	}

	static boolean isValidSoundId(String s) {
		if (s.length() > 128) {
			return false;
		}
		int colon = s.indexOf(':');
		if (colon != s.lastIndexOf(':')) {
			return false;
		}
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.'
					|| (c == '/' && i > colon) || (c == ':' && i == colon && i > 0 && i < s.length() - 1);
			if (!ok) {
				return false;
			}
		}
		return !s.isEmpty();
	}

	/** Icons are short display text: no control characters, no section signs, bounded length. */
	static String sanitizeIcon(String icon) {
		if (icon == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < icon.length() && sb.length() < MAX_ICON_LENGTH; i++) {
			char c = icon.charAt(i);
			if (c >= ' ' && c != 127 && c != '§') {
				sb.append(c);
			}
		}
		if (!sb.isEmpty() && Character.isHighSurrogate(sb.charAt(sb.length() - 1))) {
			sb.setLength(sb.length() - 1); // never leave half of a surrogate pair
		}
		return sb.toString().strip();
	}

	static String typeKey(MessageType t) {
		return t.name().toLowerCase(Locale.ROOT);
	}

	// ------------------------------------------------------------------ writing

	public static String write(ChatConfig c) {
		Out o = new Out();
		o.begin(
				"Better Chat Formatting configuration.\n"
						+ "This file is hot-reloaded: save it and the changes apply in-game within a couple of seconds.\n"
						+ "If it contains a syntax error, the previous settings stay active and a warning is logged.\n"
						+ "Comments (// and #) are allowed. Colors are written like \"#RRGGBB\".\n"
						+ "Saving from the in-game config screen rewrites this file (and its comments).");

		o.item("Master switch. false turns every feature off and leaves vanilla chat.", "enabled", bool(c.enabled));
		o.item("Active theme id: vanilla, midnight, pastel, terminal, minimal, or the file name of a theme in the\n"
				+ "themes/ folder (without .json). Unknown ids fall back to vanilla.", "theme", str(c.theme));

		o.open("Turn individual features on or off, e.g. if another chat mod conflicts.", "features");
		ChatConfig.Features f = c.features;
		o.item(null, "applyTheme", bool(f.applyTheme));
		o.item(null, "nameColors", bool(f.nameColors));
		o.item(null, "highlights", bool(f.highlights));
		o.item(null, "messageTypes", bool(f.messageTypes));
		o.item("Colored bar at the left edge of each line, by category. Needs a theme.", "accentBar", bool(f.accentBar));
		o.item("Show \"Name › message\" instead of \"Name: message\" on player lines.", "cleanSeparator",
				bool(f.cleanSeparator));
		o.item("Render *italic* **bold** __underline__ ~~strike~~ `code` from other players (this client only).",
				"markdown", bool(f.markdown));
		o.item("Make web links clickable. Opening always asks for confirmation first.", "links", bool(f.links));
		o.item(null, "timestamps", bool(f.timestamps));
		o.item("Merge identical consecutive messages into one line with \"x3\".", "collapseRepeats",
				bool(f.collapseRepeats));
		o.item(null, "search", bool(f.search));
		o.item("Suggest :shortcode: emoji while typing (plain Unicode symbols vanilla can send).", "emojiPicker",
				bool(f.emojiPicker));
		o.item("Apply the chat width/height/scroll options in the \"layout\" section.", "layout", bool(f.layout));
		o.close();

		o.open(null, "timestamps");
		o.item("Pattern for the time prefix, e.g. HH:mm, HH:mm:ss, h:mm a. Only used when features.timestamps is on.",
				"format", str(c.timestamps.format));
		o.close();

		o.open("Colored player names: each name gets a stable color derived from the name.", "names");
		o.item("Color saturation 0..1 (lower = paler).", "saturation", String.valueOf(c.names.saturation));
		o.item("Color brightness 0..1.", "value", String.valueOf(c.names.value));
		o.open("Fixed colors for specific players, e.g. \"Steve\": \"#FF8800\".", "overrides");
		for (Map.Entry<String, String> e : c.names.overrides.entrySet()) {
			o.item(null, e.getKey(), str(e.getValue()));
		}
		o.close();
		o.close();

		o.open("Highlight messages that mention you or your keywords.", "highlights");
		ChatConfig.Highlights h = c.highlights;
		o.item("Highlight messages containing your own name.", "ownName", bool(h.ownName));
		o.item(null, "ownNameColor", str(Colors.toHex(h.ownNameColor)));
		o.item("Sound event id played for an own-name mention, or \"\" for silence.", "ownNameSound",
				str(h.ownNameSound));
		o.item("Volume of highlight sounds, 0..1.", "soundVolume", String.valueOf(h.soundVolume));
		List<String> kws = new ArrayList<>();
		for (HighlightRule k : h.keywords) {
			kws.add("{ \"keyword\": " + str(k.keyword()) + ", \"color\": " + str(Colors.toHex(k.color()))
					+ ", \"sound\": " + str(k.sound() == null ? "" : k.sound())
					+ ", \"caseSensitive\": " + k.caseSensitive() + ", \"wholeWord\": " + k.wholeWord() + " }");
		}
		o.array("Extra keywords, each with its own color and optional sound. A plain string such as \"diamond\"\n"
				+ "is also accepted when reading. Example:\n"
				+ "{ \"keyword\": \"diamond\", \"color\": \"#55FFFF\", \"sound\": \"minecraft:entity.experience_orb.pickup\", "
				+ "\"caseSensitive\": false, \"wholeWord\": true }", "keywords", kws);
		o.close();

		o.open("Color and icon per message type (detected from the message's translation key). "
				+ "color may be \"none\".", "messageTypes");
		for (MessageType t : MessageType.values()) {
			if (t == MessageType.PLAYER_CHAT) {
				continue;
			}
			ChatConfig.TypeStyle s = c.messageTypes.get(t);
			o.item(null, typeKey(t), "{ \"enabled\": " + s.enabled + ", \"color\": "
					+ str(s.color == Segment.NO_COLOR ? "none" : Colors.toHex(s.color))
					+ ", \"icon\": " + str(s.icon) + " }");
		}
		o.close();

		o.open("Chat window tweaks (need features.layout).", "layout");
		o.item("Width in pixels, 80..800. 0 keeps the vanilla chat width setting.", "chatWidth",
				String.valueOf(c.layout.chatWidth));
		o.item("Height in pixels, 40..600. 0 keeps the vanilla chat height setting.", "chatHeight",
				String.valueOf(c.layout.chatHeight));
		o.item("Lines scrolled per mouse-wheel notch, 1..10. 7 matches vanilla.", "scrollLines", String.valueOf(c.layout.scrollLines));
		o.close();

		return o.finish();
	}

	private static String bool(boolean b) {
		return b ? "true" : "false";
	}

	private static String str(String s) {
		return new JsonPrimitive(s == null ? "" : s).toString();
	}

	/** Tiny pretty-printer that can put comments in front of any entry. */
	private static final class Out {
		private final StringBuilder sb = new StringBuilder();
		private final boolean[] hasItem = new boolean[8];
		private int depth;

		void begin(String headerComment) {
			sb.append('{');
			depth = 1;
			hasItem[1] = false;
			for (String line : headerComment.split("\n")) {
				sb.append("\n  // ").append(line);
			}
		}

		/** One {@code "key": value} entry; {@code rawValue} is already valid JSON text. */
		void item(String comment, String key, String rawValue) {
			start(comment);
			sb.append(indent()).append(str(key)).append(": ").append(rawValue);
		}

		void open(String comment, String key) {
			start(comment);
			sb.append(indent()).append(str(key)).append(": {");
			depth++;
			hasItem[depth] = false;
		}

		void close() {
			depth--;
			sb.append('\n').append(indent()).append('}');
		}

		void array(String comment, String key, List<String> inlineItems) {
			start(comment);
			sb.append(indent()).append(str(key)).append(": [");
			for (int i = 0; i < inlineItems.size(); i++) {
				sb.append('\n').append(indent()).append("  ").append(inlineItems.get(i));
				if (i < inlineItems.size() - 1) {
					sb.append(',');
				}
			}
			if (!inlineItems.isEmpty()) {
				sb.append('\n').append(indent());
			}
			sb.append(']');
		}

		String finish() {
			return sb.append("\n}\n").toString();
		}

		private void start(String comment) {
			if (hasItem[depth]) {
				sb.append(',');
			}
			hasItem[depth] = true;
			sb.append('\n');
			if (comment != null) {
				sb.append('\n');
				for (String line : comment.split("\n")) {
					sb.append(indent()).append("// ").append(line).append('\n');
				}
			}
		}

		private String indent() {
			return "  ".repeat(depth);
		}
	}
}
