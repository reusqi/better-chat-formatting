package com.betterchatformatting.core.theme;

import java.util.LinkedHashMap;
import java.util.Map;

/** The themes that ship with the mod. User themes (stage 2) may override these by id. */
public final class BuiltInThemes {
	public static final Theme VANILLA = new Theme("vanilla", "Vanilla",
			0x000000, 0.5f, 0, 0, 0, true, -1, 0x808080, null);

	public static final Theme MIDNIGHT = new Theme("midnight", "Midnight",
			0x0B1026, 0.78f, 3, 1, 1, true, 0xD6DEFF, 0x6C7AB8, null);

	public static final Theme PASTEL = new Theme("pastel", "Pastel",
			0xFFE4F1, 0.35f, 3, 1, 1, false, 0x5A4A6A, 0xA58FB8, null);

	public static final Theme TERMINAL = new Theme("terminal", "Terminal",
			0x000000, 0.85f, 2, 0, 0, false, 0x33FF66, 0x1E9944, null);

	public static final Theme MINIMAL = new Theme("minimal", "Minimal",
			0x000000, 0.0f, 0, 0, 0, true, -1, 0x909090, null);

	private static final Map<String, Theme> BY_ID = new LinkedHashMap<>();

	static {
		for (Theme t : new Theme[] {VANILLA, MIDNIGHT, PASTEL, TERMINAL, MINIMAL}) {
			BY_ID.put(t.id(), t);
		}
	}

	private BuiltInThemes() {
	}

	/** All built-ins in display order. The returned map is a copy. */
	public static Map<String, Theme> all() {
		return new LinkedHashMap<>(BY_ID);
	}

	/** Looks up a theme by id, falling back to {@link #VANILLA} so a bad config never breaks chat. */
	public static Theme byIdOrDefault(String id) {
		if (id == null) {
			return VANILLA;
		}
		return BY_ID.getOrDefault(id.strip().toLowerCase(java.util.Locale.ROOT), VANILLA);
	}
}
