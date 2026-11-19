package com.betterchatformatting.core.text;

/** Small color helpers. All colors are packed {@code 0xRRGGBB} ints without alpha. */
public final class Colors {
	private Colors() {
	}

	/**
	 * Parses {@code #RRGGBB}, {@code RRGGBB} or {@code #RGB}.
	 *
	 * @return the color, or {@link Segment#NO_COLOR} if the input is not a valid hex color
	 */
	public static int parseHex(String s) {
		if (s == null) {
			return Segment.NO_COLOR;
		}
		String h = s.trim();
		if (h.startsWith("#")) {
			h = h.substring(1);
		}
		if (h.length() == 3) {
			h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
		}
		if (h.length() != 6) {
			return Segment.NO_COLOR;
		}
		int v = 0;
		for (int i = 0; i < 6; i++) {
			int d = Character.digit(h.charAt(i), 16);
			if (d < 0) {
				return Segment.NO_COLOR;
			}
			v = (v << 4) | d;
		}
		return v;
	}

	public static String toHex(int rgb) {
		return String.format("#%06X", rgb & 0xFFFFFF);
	}

	/** Standard HSV to RGB. {@code h} in degrees [0,360), {@code s} and {@code v} in [0,1]. */
	public static int hsvToRgb(float h, float s, float v) {
		float hh = ((h % 360f) + 360f) % 360f / 60f;
		int i = (int) Math.floor(hh);
		float f = hh - i;
		float p = v * (1 - s);
		float q = v * (1 - s * f);
		float t = v * (1 - s * (1 - f));
		float r;
		float g;
		float b;
		switch (i) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
	}
}
