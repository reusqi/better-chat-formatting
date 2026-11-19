package com.betterchatformatting.fabric.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Colors one chat line: the rank tag and sender name, mentions of the local player, the body, and
 * optionally the separator after the name.
 *
 * <p>Sender matching works on the plain text, not the translation key, because servers such as
 * Hypixel build lines from plain pieces. Recognized shapes:
 * <ul>
 *   <li>{@code <Name> message} (vanilla)</li>
 *   <li>{@code Name: message} and {@code [RANK] Name: message}</li>
 *   <li>{@code [RANK] Name joined the lobby!} and {@code Name left...} (join and leave lines)</li>
 * </ul>
 *
 * <p>Rank and name keep the color the server sent. Only when the server sent none do they take the
 * theme's color (or the name-color feature). Each character keeps its own hover and click events.
 */
public final class SenderColors {
	/** Marks "no color". Real colors are 0..0xFFFFFF. */
	private static final int NONE = -1;
	private static final char CLEAN_SEPARATOR = '›'; // single right-pointing angle quotation mark
	private static final String NAME = "[A-Za-z0-9_]{2,16}";
	private static final Pattern SENDER = Pattern.compile(
			"^(?<rank>(?:>>>\\s*)?(?:\\[[^\\]\\n]{1,24}\\]\\s*)*)"
					+ "(?:<(?<angle>" + NAME + ")>\\s"
					+ "|(?<colon>" + NAME + "):\\s"
					+ "|(?<join>" + NAME + ")(?=\\s(?:joined|left)\\b))");

	/**
	 * What to paint on one line.
	 *
	 * @param rankColor      color for the rank tag when the server sent none, or -1
	 * @param nameColor      maps a sender name to its color when the server sent none, or null
	 * @param boldNames      whether painted sender names are bold
	 * @param ownName        the local player's name, or null
	 * @param mentionColor   color for mentions of {@code ownName} in the body (bold), or -1
	 * @param cleanSeparator whether ":" after a player name becomes "›"
	 * @param baseColor      color for every other character (the theme's line color), or -1 to keep the server's
	 */
	public record Paint(int rankColor, Function<String, Integer> nameColor, boolean boldNames, String ownName,
			int mentionColor, boolean cleanSeparator, int baseColor) {

		/** The same paint with a different base color. */
		public Paint withBase(int color) {
			return new Paint(rankColor, nameColor, boldNames, ownName, mentionColor, cleanSeparator, color);
		}

		/** True when painting would change nothing. */
		public boolean isEmpty() {
			return rankColor == NONE && nameColor == null && mentionColor == NONE && baseColor == NONE
					&& !cleanSeparator;
		}
	}

	private SenderColors() {
	}

	/** Whether {@code plain} mentions {@code name} as a whole word, ignoring case. */
	public static boolean mentions(String plain, String name) {
		return name != null && !name.isEmpty() && mentionPattern(name).matcher(plain).find();
	}

	private static Pattern mentionPattern(String name) {
		return Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(name) + "(?![A-Za-z0-9_])",
				Pattern.CASE_INSENSITIVE);
	}

	/** @return the painted line, or {@code line} itself when nothing applies */
	public static Component apply(Component line, Paint paint) {
		if (paint.isEmpty()) {
			return line;
		}
		String plain = line.getString();
		int n = plain.length();
		char[] chars = plain.toCharArray();

		// The color each character already has from the server (NONE when it has none).
		List<Component> pieces = line.toFlatList();
		int[] server = new int[n];
		Arrays.fill(server, NONE);
		int offset = 0;
		for (Component piece : pieces) {
			var color = piece.getStyle().getColor();
			int len = piece.getString().length();
			if (color != null) {
				Arrays.fill(server, offset, offset + len, color.getValue());
			}
			offset += len;
		}

		int[] painted = new int[n];
		Arrays.fill(painted, NONE);
		boolean[] bold = new boolean[n];
		boolean[] keep = new boolean[n];

		Matcher sender = SENDER.matcher(plain);
		if (sender.find()) {
			String group = sender.group("angle") != null ? "angle" : sender.group("colon") != null ? "colon" : "join";
			String name = sender.group(group);
			boolean own = paint.ownName() != null && name.equalsIgnoreCase(paint.ownName());
			paintSender(painted, bold, keep, server, sender.start("rank"), sender.end("rank"), paint.rankColor(), false);
			int nameColor = own ? paint.mentionColor()
					: paint.nameColor() == null ? NONE : paint.nameColor().apply(name);
			paintSender(painted, bold, keep, server, sender.start(group), sender.end(group), nameColor,
					paint.boldNames() || own);
			if (paint.cleanSeparator() && "colon".equals(group)) {
				chars[sender.end(group)] = CLEAN_SEPARATOR;
			}
		}

		if (paint.ownName() != null && paint.mentionColor() != NONE) {
			Matcher mention = mentionPattern(paint.ownName()).matcher(plain);
			while (mention.find()) {
				for (int i = mention.start(); i < mention.end(); i++) {
					if (!keep[i]) {
						painted[i] = paint.mentionColor();
						bold[i] = true;
					}
				}
			}
		}

		// Resolve each character: kept server color, then painted color, then the base color, then the server color.
		int[] finalColor = new int[n];
		boolean[] finalBold = new boolean[n];
		for (int i = 0; i < n; i++) {
			if (keep[i]) {
				finalColor[i] = server[i];
			} else if (painted[i] != NONE) {
				finalColor[i] = painted[i];
				finalBold[i] = bold[i];
			} else if (paint.baseColor() != NONE) {
				finalColor[i] = paint.baseColor();
			} else {
				finalColor[i] = server[i];
			}
		}

		MutableComponent out = Component.literal("");
		offset = 0;
		for (Component piece : pieces) {
			String text = piece.getString();
			Style style = piece.getStyle();
			int runStart = 0;
			for (int i = 1; i <= text.length(); i++) {
				boolean boundary = i == text.length()
						|| finalColor[offset + i] != finalColor[offset + runStart]
						|| finalBold[offset + i] != finalBold[offset + runStart];
				if (boundary) {
					String run = new String(chars, offset + runStart, i - runStart);
					out.append(run(run, style, finalColor[offset + runStart], finalBold[offset + runStart]));
					runStart = i;
				}
			}
			offset += text.length();
		}
		return out;
	}

	/**
	 * Paints a rank or name range. A character the server already colored is kept as it is. Otherwise
	 * it takes {@code color}, when there is one.
	 */
	private static void paintSender(int[] painted, boolean[] bold, boolean[] keep, int[] server,
			int start, int end, int color, boolean makeBold) {
		for (int i = start; i < end; i++) {
			if (server[i] != NONE) {
				keep[i] = true;
			} else if (color != NONE) {
				painted[i] = color;
				bold[i] = makeBold;
			}
		}
	}

	private static Component run(String text, Style style, int color, boolean bold) {
		Style s = color == NONE ? style : style.withColor(color);
		if (bold) {
			s = s.withBold(true);
		}
		return Component.literal(text).setStyle(s);
	}
}
