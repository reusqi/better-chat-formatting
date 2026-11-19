package com.betterchatformatting.core.qol;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Chat search filter: a message matches when it contains <em>every</em> whitespace-separated term
 * of the query, ignoring case. An empty query matches everything.
 */
public final class ChatSearch {
	public static final int MAX_TERMS = 8;

	private final String[] terms;

	private ChatSearch(String[] terms) {
		this.terms = terms;
	}

	public static ChatSearch parse(String query) {
		if (query == null || query.isBlank()) {
			return new ChatSearch(new String[0]);
		}
		List<String> out = new ArrayList<>();
		for (String t : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
			if (!t.isEmpty() && out.size() < MAX_TERMS) {
				out.add(t);
			}
		}
		return new ChatSearch(out.toArray(new String[0]));
	}

	public boolean isEmpty() {
		return terms.length == 0;
	}

	/** Tests an already lower-cased message text (cache that per message; do not lower-case per frame). */
	public boolean matchesLowerCase(String lowerCaseText) {
		for (String t : terms) {
			if (!lowerCaseText.contains(t)) {
				return false;
			}
		}
		return true;
	}

	/** Convenience for tests and one-off use. */
	public boolean matches(String text) {
		return matchesLowerCase(text.toLowerCase(Locale.ROOT));
	}
}
