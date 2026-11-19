package com.betterchatformatting.core.markdown;

import com.betterchatformatting.core.text.Segment;
import com.betterchatformatting.core.text.StyleFlags;
import com.betterchatformatting.core.text.StyledText;

import java.util.ArrayList;
import java.util.List;

/**
 * Tiny hand-written parser for the chat flavour of markdown:
 * <pre>
 *   *italic*   **bold**   __underline__   ~~strike~~   `code`
 * </pre>
 * Delimiters that are matched are removed from the output and turned into style flags;
 * unmatched delimiters stay as literal text, so ordinary chat such as {@code 2 * 3 * 4} or
 * {@code snake_case_name} is untouched. A lone {@code _} or {@code ~} is never a marker. A backslash escapes a marker ({@code \*}).
 *
 * <p>Design notes:
 * <ul>
 *   <li>No regex and no allocation per character, so it is safe to run once per incoming
 *       message. Results are cached by the caller; nothing runs per frame.</li>
 *   <li>"Literal" ranges (detected URLs) are opaque: markdown never looks inside them, so
 *       {@code https://x.com/a_b__c} survives intact, and they become link segments.</li>
 *   <li>Code spans are not parsed further, and delimiters inside them cannot close an outer span.</li>
 *   <li>Nesting is limited to {@link #MAX_DEPTH} and inputs longer than {@link #MAX_LENGTH}
 *       are not parsed at all (links still work), bounding the worst case.</li>
 *   <li>Known limitation: a single {@code *} cannot close directly against a {@code **} run
 *       ({@code *a **b***}); such input stays literal.</li>
 * </ul>
 */
public final class MarkdownParser {
	public static final int MAX_LENGTH = 4096;
	public static final int MAX_DEPTH = 4;

	/** An opaque range {@code [start, end)} of the input that becomes one link segment. */
	public record Literal(int start, int end, String url) {
	}

	private MarkdownParser() {
	}

	public static StyledText parse(String text) {
		return parse(text, 0, Segment.NO_COLOR, List.of());
	}

	/**
	 * @param text      the raw message text
	 * @param baseFlags style flags every segment starts with (e.g. from the source component)
	 * @param baseColor color every non-link segment starts with, or {@link Segment#NO_COLOR}
	 * @param literals  sorted, non-overlapping opaque ranges (links); may be empty
	 */
	public static StyledText parse(String text, int baseFlags, int baseColor, List<Literal> literals) {
		return parse(text, baseFlags, baseColor, literals, true);
	}

	/**
	 * Same as above; with {@code markdownEnabled == false} only the literal (link) ranges are split
	 * out and every marker stays plain text. This lets "links on, markdown off" share one code path.
	 */
	public static StyledText parse(String text, int baseFlags, int baseColor, List<Literal> literals,
			boolean markdownEnabled) {
		if (text == null || text.isEmpty()) {
			return StyledText.EMPTY;
		}
		int depth = (!markdownEnabled || text.length() > MAX_LENGTH) ? 0 : MAX_DEPTH;
		Run run = new Run(text, baseColor, literals, depth);
		run.parseRange(0, text.length(), baseFlags, 0);
		return StyledText.of(run.out);
	}

	/** Mutable state for one parse call. */
	private static final class Run {
		final String text;
		final int baseColor;
		final List<Literal> literals;
		final int maxDepth;
		final List<Segment> out = new ArrayList<>();

		Run(String text, int baseColor, List<Literal> literals, int maxDepth) {
			this.text = text;
			this.baseColor = baseColor;
			this.literals = literals;
			this.maxDepth = maxDepth;
		}

		void parseRange(int start, int end, int flags, int depth) {
			StringBuilder buf = new StringBuilder();
			int i = start;
			while (i < end) {
				Literal lit = literalAt(i);
				if (lit != null) {
					flush(buf, flags);
					out.add(new Segment(text.substring(lit.start(), lit.end()), flags, Segment.NO_COLOR, lit.url()));
					i = lit.end();
					continue;
				}
				char c = text.charAt(i);
				if (depth < maxDepth) {
					if (c == '\\' && i + 1 < end && isMarker(text.charAt(i + 1))) {
						buf.append(text.charAt(i + 1));
						i += 2;
						continue;
					}
					if (c == '`') {
						int close = findCodeEnd(i + 1, end);
						if (close > 0) {
							flush(buf, flags);
							out.add(new Segment(text.substring(i + 1, close), flags | StyleFlags.CODE, baseColor, null));
							i = close + 1;
							continue;
						}
					} else if (c == '*' || c == '_' || c == '~') {
						int len = (i + 1 < end && text.charAt(i + 1) == c) ? 2 : 1;
						// Only "~~" and "__" are markers; a lone '~' or '_' is ordinary text.
						if ((c == '~' || c == '_') && len == 1) {
							buf.append(c);
							i++;
							continue;
						}
						if (canOpen(c, i, len, end)) {
							int close = findClose(c, len, i + len, end);
							if (close >= 0) {
								flush(buf, flags);
								parseRange(i + len, close, flags | flagFor(c, len), depth + 1);
								i = close + len;
								continue;
							}
						}
						// Unmatched: keep the whole marker run literal so we do not retry inside it.
						for (int k = 0; k < len; k++) {
							buf.append(c);
						}
						i += len;
						continue;
					}
				}
				buf.append(c);
				i++;
			}
			flush(buf, flags);
		}

		/** An opener must be followed by non-space text; '_' must not sit inside a word. */
		boolean canOpen(char c, int i, int len, int end) {
			int next = i + len;
			if (next >= end || Character.isWhitespace(text.charAt(next))) {
				return false;
			}
			return !(c == '_' && i > 0 && Character.isLetterOrDigit(text.charAt(i - 1)));
		}

		/** Returns the index of the first char of the closing delimiter, or -1. */
		int findClose(char c, int len, int from, int end) {
			int j = from;
			while (j < end) {
				Literal l = literalContaining(j);
				if (l != null) {
					j = l.end();
					continue;
				}
				char d = text.charAt(j);
				if (d == '\\') {
					j += 2;
					continue;
				}
				if (d == '`') {
					int cc = findCodeEnd(j + 1, end);
					if (cc > 0) {
						j = cc + 1;
						continue;
					}
				}
				if (d == c) {
					int run = 1;
					while (j + run < end && text.charAt(j + run) == c && literalContaining(j + run) == null) {
						run++;
					}
					boolean fits = len == 1 ? run == 1 : run >= 2;
					if (fits) {
						int closeAt = len == 1 ? j : j + run - len;
						int after = closeAt + len;
						boolean spaceBefore = closeAt > from && Character.isWhitespace(text.charAt(closeAt - 1));
						boolean wordAfter = c == '_' && after < end && Character.isLetterOrDigit(text.charAt(after));
						if (closeAt > from && !spaceBefore && !wordAfter) {
							return closeAt;
						}
					}
					j += run;
					continue;
				}
				j++;
			}
			return -1;
		}

		/** Index of the closing backtick for a span whose content starts at {@code from}, or -1. */
		int findCodeEnd(int from, int end) {
			int j = text.indexOf('`', from);
			return (j > from && j < end) ? j : -1;
		}

		Literal literalAt(int pos) {
			for (int k = 0; k < literals.size(); k++) {
				Literal l = literals.get(k);
				if (l.start() == pos) {
					return l;
				}
				if (l.start() > pos) {
					break;
				}
			}
			return null;
		}

		Literal literalContaining(int pos) {
			for (int k = 0; k < literals.size(); k++) {
				Literal l = literals.get(k);
				if (pos >= l.start() && pos < l.end()) {
					return l;
				}
				if (l.start() > pos) {
					break;
				}
			}
			return null;
		}

		void flush(StringBuilder buf, int flags) {
			if (!buf.isEmpty()) {
				out.add(new Segment(buf.toString(), flags, baseColor, null));
				buf.setLength(0);
			}
		}
	}

	private static boolean isMarker(char c) {
		return c == '*' || c == '_' || c == '~' || c == '`' || c == '\\';
	}

	private static int flagFor(char c, int len) {
		return switch (c) {
			case '*' -> len == 2 ? StyleFlags.BOLD : StyleFlags.ITALIC;
			case '_' -> StyleFlags.UNDERLINE; // '__' only; a single '_' never reaches here
			default -> StyleFlags.STRIKETHROUGH; // '~~'
		};
	}
}
