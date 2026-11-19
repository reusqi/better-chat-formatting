package com.betterchatformatting.core.emoji;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@code :smile:}-style shortcodes for <em>outgoing</em> messages.
 *
 * <p>Only single-character symbols from the Basic Multilingual Plane are used. Vanilla chat
 * rejects control characters and {@code §}, and its bundled fonts cover BMP symbols but not
 * most supplementary-plane emoji (they would show as boxes), so every value here is a plain
 * one-char Unicode symbol that vanilla can send and every vanilla client can display. A unit
 * test enforces this.
 *
 * <p>The picker edits the chat <em>input box</em> while the player is typing; nothing is ever
 * rewritten at send time, so what the player sees in the box is exactly what is sent.
 */
public final class EmojiShortcodes {
	/** Replacement of {@code [start, end)} in the input text by {@code text}. */
	public record Replacement(int start, int end, String text) {
	}

	private static final Map<String, String> TABLE = new TreeMap<>();

	static {
		put("smile", "☺");
		put("happy", "☺");
		put("blacksmile", "☻");
		put("sad", "☹");
		put("heart", "❤");
		put("hearts", "♥");
		put("spades", "♠");
		put("clubs", "♣");
		put("diamonds", "♦");
		put("star", "★");
		put("star_outline", "☆");
		put("check", "✔");
		put("cross", "✖");
		put("x", "✘");
		put("sun", "☀");
		put("cloud", "☁");
		put("umbrella", "☂");
		put("snowman", "☃");
		put("snowflake", "❄");
		put("note", "♪");
		put("notes", "♫");
		put("phone", "☎");
		put("skull", "☠");
		put("peace", "☮");
		put("yinyang", "☯");
		put("radioactive", "☢");
		put("biohazard", "☣");
		put("anchor", "⚓");
		put("warning", "⚠");
		put("bolt", "⚡");
		put("sword", "⚔");
		put("gear", "⚙");
		put("flag", "⚑");
		put("scissors", "✂");
		put("airplane", "✈");
		put("envelope", "✉");
		put("pencil", "✏");
		put("sparkles", "✨");
		put("flower", "✿");
		put("coffee", "☕");
		put("hot", "♨");
		put("male", "♂");
		put("female", "♀");
		put("victory", "✌");
		put("hand", "✋");
		put("point_right", "☞");
		put("point_left", "☜");
		put("arrow_right", "→");
		put("arrow_left", "←");
		put("arrow_up", "↑");
		put("arrow_down", "↓");
		put("infinity", "∞");
		put("degree", "°");
		put("copyright", "©");
		put("tm", "™");
	}

	private EmojiShortcodes() {
	}

	private static void put(String name, String glyph) {
		TABLE.put(name, glyph);
	}

	/** Unmodifiable view of name → symbol, sorted by name. */
	public static Map<String, String> all() {
		return java.util.Collections.unmodifiableMap(TABLE);
	}

	/** The symbol for {@code name} (without colons, case-insensitive), or null. */
	public static String lookup(String name) {
		return name == null ? null : TABLE.get(name.toLowerCase(Locale.ROOT));
	}

	/** Up to {@code limit} shortcode names starting with {@code prefix}, alphabetical. */
	public static List<String> suggest(String prefix, int limit) {
		String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
		List<String> out = new ArrayList<>();
		for (String name : TABLE.keySet()) {
			if (name.startsWith(p)) {
				out.add(name);
				if (out.size() >= limit) {
					break;
				}
			}
		}
		return out;
	}

	/**
	 * If the player has just typed the closing colon of a known shortcode ending at {@code cursor},
	 * returns the replacement to apply to the input box. Otherwise null.
	 */
	public static Replacement completeAt(String text, int cursor) {
		if (text == null || cursor < 3 || cursor > text.length() || text.charAt(cursor - 1) != ':') {
			return null;
		}
		int open = findOpeningColon(text, cursor - 1);
		if (open < 0) {
			return null;
		}
		String glyph = lookup(text.substring(open + 1, cursor - 1));
		return glyph == null ? null : new Replacement(open, cursor, glyph);
	}

	/**
	 * The partial shortcode currently being typed before {@code cursor} (text after an unclosed
	 * colon, at least one character), for showing suggestions. Null if the player is not typing one.
	 */
	public static String pendingPrefix(String text, int cursor) {
		if (text == null || cursor < 2 || cursor > text.length()) {
			return null;
		}
		int open = findOpeningColon(text, cursor);
		if (open < 0 || open == cursor - 1) {
			return null;
		}
		return text.substring(open + 1, cursor);
	}

	/** Scans back from {@code end} over shortcode characters to a ':' that starts a token. */
	private static int findOpeningColon(String text, int end) {
		int i = end - 1;
		int limit = Math.max(0, end - 33);
		while (i >= limit) {
			char c = text.charAt(i);
			if (c == ':') {
				boolean tokenStart = i == 0 || Character.isWhitespace(text.charAt(i - 1));
				return tokenStart ? i : -1;
			}
			if (!(c >= 'a' && c <= 'z') && !(c >= 'A' && c <= 'Z') && !(c >= '0' && c <= '9') && c != '_') {
				return -1;
			}
			i--;
		}
		return -1;
	}
}
