package com.betterchatformatting.core.text;

/** Bit flags describing text decoration. Combine with {@code |}. */
public final class StyleFlags {
	public static final int BOLD = 1;
	public static final int ITALIC = 1 << 1;
	public static final int UNDERLINE = 1 << 2;
	public static final int STRIKETHROUGH = 1 << 3;
	/** Inline code: the renderer picks a distinct color/background; content is never parsed. */
	public static final int CODE = 1 << 4;

	private StyleFlags() {
	}

	public static boolean has(int flags, int flag) {
		return (flags & flag) != 0;
	}
}
