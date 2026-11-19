package com.betterchatformatting.core.theme;

/**
 * Colors for each role in the chat, derived from one theme's text color so every theme gets a
 * palette without new theme file fields. Roles are chosen for contrast, not just lightness:
 *
 * <ul>
 *   <li>{@code body}: the theme's primary text color.</li>
 *   <li>{@code sender}: the complementary hue at full strength, for other players' names and rank tags.
 *       Shown bold.</li>
 *   <li>{@code mention}: a fixed warm gold, for your own name and mentions of it. Shown bold.</li>
 *   <li>{@code whisper}: the body hue rotated by a third of the color wheel.</li>
 *   <li>{@code system}: the body hue, desaturated and dimmed, so server spam stays quiet.</li>
 *   <li>{@code timestamp}: the theme's timestamp color (muted).</li>
 * </ul>
 * All values are RGB (no alpha byte).
 */
public record ThemeShades(int timestamp, int sender, int body, int whisper, int system, int mention) {

	/** Warm gold hue (about 40 degrees), in 0..1 hue units. */
	static final float MENTION_HUE = 0.11f;

	/** The shades for {@code theme}. A theme with no text color uses white as its base. */
	public static ThemeShades of(Theme theme) {
		int base = theme.textColor() < 0 ? 0xFFFFFF : theme.textColor();
		float[] hsv = toHsv(base);
		float h = hsv[0];
		float s = hsv[1];
		float v = hsv[2];

		int sender = fromHsv(h + 0.5f, Math.max(s, 0.75f), Math.max(v, 0.95f));
		int mention = fromHsv(MENTION_HUE, 0.9f, 1f);
		int whisper = fromHsv(h + 1f / 3f, Math.max(s, 0.6f), Math.max(v, 0.85f));
		int system = fromHsv(h, s * 0.35f, v * 0.7f);
		return new ThemeShades(theme.timestampColor() & 0xFFFFFF, sender, base, whisper, system, mention);
	}

	/** Linear blend of two RGB colors. {@code t} = 0 gives {@code from}, 1 gives {@code to}. */
	static int mix(int from, int to, float t) {
		int r = mixChannel((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
		int g = mixChannel((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
		int b = mixChannel(from & 0xFF, to & 0xFF, t);
		return (r << 16) | (g << 8) | b;
	}

	private static int mixChannel(int a, int b, float t) {
		return Math.round(a + (b - a) * t);
	}

	/** RGB to {hue 0..1, saturation 0..1, value 0..1}. */
	static float[] toHsv(int rgb) {
		float r = ((rgb >> 16) & 0xFF) / 255f;
		float g = ((rgb >> 8) & 0xFF) / 255f;
		float b = (rgb & 0xFF) / 255f;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float delta = max - min;
		float hue = 0f;
		if (delta > 0f) {
			if (max == r) {
				hue = ((g - b) / delta) % 6f;
			} else if (max == g) {
				hue = (b - r) / delta + 2f;
			} else {
				hue = (r - g) / delta + 4f;
			}
			hue /= 6f;
			if (hue < 0f) {
				hue += 1f;
			}
		}
		float saturation = max == 0f ? 0f : delta / max;
		return new float[] {hue, saturation, max};
	}

	/** {hue, saturation, value} to RGB. Hue wraps; saturation and value are clamped. */
	static int fromHsv(float hue, float saturation, float value) {
		float h = ((hue % 1f) + 1f) % 1f * 6f;
		float s = Math.max(0f, Math.min(1f, saturation));
		float v = Math.max(0f, Math.min(1f, value));
		int sector = (int) Math.floor(h);
		float f = h - sector;
		float p = v * (1f - s);
		float q = v * (1f - s * f);
		float t = v * (1f - s * (1f - f));
		float r;
		float g;
		float b;
		switch (sector) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return (Math.round(r * 255f) << 16) | (Math.round(g * 255f) << 8) | Math.round(b * 255f);
	}
}
