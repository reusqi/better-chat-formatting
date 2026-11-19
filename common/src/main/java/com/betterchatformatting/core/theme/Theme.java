package com.betterchatformatting.core.theme;

/**
 * Visual style of the chat. Immutable; the constructor clamps every value into a sane range so
 * a hand-edited theme file can never produce something unrenderable.
 *
 * @param id              unique lower-case id ({@code midnight}); user themes use their file name
 * @param name            display name
 * @param backgroundColor RGB color behind each message line
 * @param backgroundOpacity 0..1; 0 disables the background entirely
 * @param paddingX        horizontal padding inside the line background, pixels (0..16)
 * @param paddingY        vertical padding inside the line background, pixels (0..8)
 * @param lineSpacing     extra pixels between lines (0..8)
 * @param textShadow      draw the vanilla text shadow
 * @param textColor       default text color for lines with no color of their own, or -1 for vanilla white
 * @param timestampColor  color of the optional timestamp prefix
 * @param font            font resource id (e.g. {@code minecraft:default}) or null for the default font
 */
public record Theme(
		String id,
		String name,
		int backgroundColor,
		float backgroundOpacity,
		int paddingX,
		int paddingY,
		int lineSpacing,
		boolean textShadow,
		int textColor,
		int timestampColor,
		String font) {

	public Theme {
		id = (id == null || id.isBlank()) ? "custom" : id.strip().toLowerCase(java.util.Locale.ROOT);
		name = (name == null || name.isBlank()) ? id : name.strip();
		backgroundColor &= 0xFFFFFF;
		backgroundOpacity = Float.isNaN(backgroundOpacity) ? 0.5f : Math.max(0f, Math.min(1f, backgroundOpacity));
		paddingX = Math.max(0, Math.min(16, paddingX));
		paddingY = Math.max(0, Math.min(8, paddingY));
		lineSpacing = Math.max(0, Math.min(8, lineSpacing));
		textColor = textColor < 0 ? -1 : textColor & 0xFFFFFF;
		timestampColor &= 0xFFFFFF;
		font = (font == null || font.isBlank()) ? null : font.strip();
	}

	/** ARGB value ready to pass to a fill call. */
	public int backgroundArgb() {
		int alpha = Math.round(backgroundOpacity * 255f) & 0xFF;
		return (alpha << 24) | backgroundColor;
	}
}
