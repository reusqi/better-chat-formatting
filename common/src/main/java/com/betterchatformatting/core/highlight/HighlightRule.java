package com.betterchatformatting.core.highlight;

/**
 * One keyword to highlight.
 *
 * @param keyword       text to look for (blank rules are ignored by the matcher)
 * @param color         RGB highlight color
 * @param sound         sound event id (e.g. {@code minecraft:entity.experience_orb.pickup}) or null for silent
 * @param caseSensitive match case exactly
 * @param wholeWord     only match when not surrounded by letters/digits ("Bob" does not match "Bobby")
 */
public record HighlightRule(String keyword, int color, String sound, boolean caseSensitive, boolean wholeWord) {

	public HighlightRule {
		keyword = keyword == null ? "" : keyword.strip();
		if (sound != null && sound.isBlank()) {
			sound = null;
		}
	}

	/** Case-insensitive whole-word rule, the sensible default for names and keywords. */
	public static HighlightRule of(String keyword, int color, String sound) {
		return new HighlightRule(keyword, color, sound, false, true);
	}

	public boolean isUsable() {
		return !keyword.isEmpty();
	}
}
