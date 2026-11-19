package com.betterchatformatting.core.highlight;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Finds highlight keywords in message text. Build one matcher when the config loads and reuse it:
 * keywords are lower-cased once up front, and matching is a plain {@code indexOf} scan, so the
 * per-message cost is tiny (and nothing runs per frame).
 *
 * <p>Rules are matched independently; where matches overlap, the earlier-listed rule wins, so
 * a player can put specific keywords above general ones.
 */
public final class KeywordMatcher {

	/** A match of {@code rule} at {@code [start, end)} in the scanned text. */
	public record Match(int start, int end, HighlightRule rule) {
	}

	private final List<HighlightRule> rules;
	private final String[] needles; // lower-cased for case-insensitive rules, as-is otherwise

	public KeywordMatcher(List<HighlightRule> rules) {
		List<HighlightRule> usable = new ArrayList<>();
		for (HighlightRule r : rules) {
			if (r != null && r.isUsable()) {
				usable.add(r);
			}
		}
		this.rules = List.copyOf(usable);
		this.needles = new String[this.rules.size()];
		for (int i = 0; i < needles.length; i++) {
			HighlightRule r = this.rules.get(i);
			needles[i] = r.caseSensitive() ? r.keyword() : r.keyword().toLowerCase(Locale.ROOT);
		}
	}

	public boolean isEmpty() {
		return rules.isEmpty();
	}

	/**
	 * All non-overlapping matches in {@code text}, ordered by position.
	 * Returns an empty list (no allocation) when nothing matches.
	 */
	public List<Match> findAll(String text) {
		if (rules.isEmpty() || text == null || text.isEmpty()) {
			return List.of();
		}
		String lower = null; // computed lazily, only if a case-insensitive rule exists
		List<Match> all = null;
		for (int r = 0; r < needles.length; r++) {
			HighlightRule rule = rules.get(r);
			String haystack;
			if (rule.caseSensitive()) {
				haystack = text;
			} else {
				if (lower == null) {
					lower = text.toLowerCase(Locale.ROOT);
				}
				haystack = lower;
			}
			String needle = needles[r];
			// toLowerCase(ROOT) can change length for exotic characters; fall back to exact text then.
			if (haystack.length() != text.length()) {
				haystack = text;
				needle = rule.keyword();
			}
			int from = 0;
			int idx;
			while ((idx = haystack.indexOf(needle, from)) >= 0) {
				int end = idx + needle.length();
				if (!rule.wholeWord() || isWholeWord(text, idx, end)) {
					if (all == null) {
						all = new ArrayList<>(4);
					}
					all.add(new Match(idx, end, rule));
				}
				from = idx + 1;
			}
		}
		return all == null ? List.of() : resolveOverlaps(all, rules);
	}

	/** The first match in text order, or null. Handy when only the sound/trigger matters. */
	public Match findFirst(String text) {
		List<Match> m = findAll(text);
		return m.isEmpty() ? null : m.get(0);
	}

	/** Keeps matches in position order; on overlap the higher-priority (earlier) rule wins. */
	private static List<Match> resolveOverlaps(List<Match> all, List<HighlightRule> order) {
		List<Match> byPriority = new ArrayList<>(all);
		byPriority.sort((a, b) -> {
			int pa = priority(order, a.rule());
			int pb = priority(order, b.rule());
			if (pa != pb) {
				return Integer.compare(pa, pb);
			}
			return Integer.compare(a.start(), b.start());
		});
		List<Match> kept = new ArrayList<>();
		for (Match m : byPriority) {
			boolean clash = false;
			for (Match k : kept) {
				if (m.start() < k.end() && k.start() < m.end()) {
					clash = true;
					break;
				}
			}
			if (!clash) {
				kept.add(m);
			}
		}
		kept.sort((a, b) -> Integer.compare(a.start(), b.start()));
		return kept;
	}

	private static int priority(List<HighlightRule> order, HighlightRule rule) {
		for (int i = 0; i < order.size(); i++) {
			if (order.get(i) == rule) {
				return i;
			}
		}
		return Integer.MAX_VALUE;
	}

	private static boolean isWholeWord(String text, int start, int end) {
		boolean leftOk = start == 0 || !Character.isLetterOrDigit(text.charAt(start - 1));
		boolean rightOk = end >= text.length() || !Character.isLetterOrDigit(text.charAt(end));
		return leftOk && rightOk;
	}
}
