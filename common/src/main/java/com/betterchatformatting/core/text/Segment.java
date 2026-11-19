package com.betterchatformatting.core.text;

/**
 * One run of text sharing a single style. This is the Minecraft-free stand-in for a text
 * {@code Component} leaf; the loader modules convert lists of segments into real components.
 *
 * @param text  the visible text (never null)
 * @param flags bit set of {@link StyleFlags}
 * @param color RGB color (0xRRGGBB) or {@link #NO_COLOR} to inherit
 * @param url   link target for clickable segments, or null. Only ever an http/https URL
 *              produced by {@code UrlDetector}; never a command.
 */
public record Segment(String text, int flags, int color, String url) {
	public static final int NO_COLOR = -1;

	public Segment {
		if (text == null) {
			throw new IllegalArgumentException("text");
		}
	}

	public static Segment plain(String text) {
		return new Segment(text, 0, NO_COLOR, null);
	}

	public boolean hasColor() {
		return color != NO_COLOR;
	}

	public boolean isLink() {
		return url != null;
	}

	public Segment withText(String newText) {
		return new Segment(newText, flags, color, url);
	}

	public Segment withColor(int newColor) {
		return new Segment(text, flags, newColor, url);
	}

	public Segment withFlags(int newFlags) {
		return new Segment(text, newFlags, color, url);
	}

	/** True when this segment and {@code o} differ only in their text, so they can be merged. */
	boolean sameStyle(Segment o) {
		return flags == o.flags && color == o.color && java.util.Objects.equals(url, o.url);
	}
}
