package com.betterchatformatting.core.names;

import com.betterchatformatting.core.text.Colors;
import com.betterchatformatting.core.text.Segment;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Gives every player a stable color derived from their name, with optional per-player overrides.
 *
 * <p>The color is the same on every client and every session (FNV-1a hash of the lower-cased
 * name → hue), so "Steve" is always the same color without any stored state. Saturation and
 * value are configurable to stay readable on light or dark chat backgrounds.
 */
public final class NameColors {
	private final Map<String, Integer> overrides = new HashMap<>();
	private final Map<String, Integer> cache = new HashMap<>();
	private final float saturation;
	private final float value;

	/**
	 * @param overrides  player name → hex color ({@code "#FF8800"}); invalid entries are skipped
	 * @param saturation 0..1 (clamped)
	 * @param value      0..1 (clamped)
	 */
	public NameColors(Map<String, String> overrides, float saturation, float value) {
		this.saturation = clamp01(saturation);
		this.value = clamp01(value);
		if (overrides != null) {
			for (Map.Entry<String, String> e : overrides.entrySet()) {
				int c = Colors.parseHex(e.getValue());
				if (e.getKey() != null && c != Segment.NO_COLOR) {
					this.overrides.put(key(e.getKey()), c);
				}
			}
		}
	}

	public static NameColors withDefaults() {
		return new NameColors(Map.of(), 0.55f, 0.95f);
	}

	/** The color for {@code playerName}: an override if configured, otherwise the hashed color. */
	public int colorFor(String playerName) {
		if (playerName == null || playerName.isEmpty()) {
			return Segment.NO_COLOR;
		}
		String k = key(playerName);
		Integer fixed = overrides.get(k);
		if (fixed != null) {
			return fixed;
		}
		// Bounded cache: a server could show thousands of distinct names over a long session.
		Integer cached = cache.get(k);
		if (cached != null) {
			return cached;
		}
		int color = Colors.hsvToRgb(hash(k) % 360, saturation, value);
		if (cache.size() > 2048) {
			cache.clear();
		}
		cache.put(k, color);
		return color;
	}

	/** 32-bit FNV-1a, folded to a non-negative int. */
	static int hash(String s) {
		int h = 0x811C9DC5;
		for (int i = 0; i < s.length(); i++) {
			h ^= s.charAt(i);
			h *= 0x01000193;
		}
		return h & 0x7FFFFFFF;
	}

	private static String key(String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	private static float clamp01(float f) {
		return Float.isNaN(f) ? 0.5f : Math.max(0f, Math.min(1f, f));
	}
}
