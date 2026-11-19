package com.betterchatformatting.core.qol;

/**
 * Counts consecutive identical messages so the UI can show "message x3" instead of three lines.
 * Purely a bookkeeping helper: the loader decides how to replace the previous line.
 */
public final class RepeatCollapser {
	private String lastKey;
	private int count;

	/**
	 * Registers a message.
	 *
	 * @param key the message's plain text (callers may include the type/sender to be stricter)
	 * @return 1 for a new message, 2 for the first repeat, 3 for the next, ...
	 */
	public int register(String key) {
		if (key != null && key.equals(lastKey)) {
			return ++count;
		}
		lastKey = key;
		count = 1;
		return 1;
	}

	/**
	 * Normalizes a message's plain text for comparison: trims it and collapses every run of
	 * whitespace to one space. Null becomes the empty string.
	 */
	public static String normalize(String text) {
		return text == null ? "" : text.strip().replaceAll("\\s+", " ");
	}

	/** Forget the previous message (e.g. when chat is cleared or the player changes server). */
	public void reset() {
		lastKey = null;
		count = 0;
	}

	/** Suffix to append to the line: empty for a single message, {@code " x3"} otherwise. */
	public static String suffix(int count) {
		return count > 1 ? " x" + count : "";
	}
}
