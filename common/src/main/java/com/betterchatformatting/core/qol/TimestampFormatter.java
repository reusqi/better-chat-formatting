package com.betterchatformatting.core.qol;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Formats message times for the optional timestamp prefix. The pattern uses
 * {@link DateTimeFormatter} syntax ({@code HH:mm}, {@code HH:mm:ss}, {@code h:mm a}...). An invalid
 * pattern silently falls back to {@link #DEFAULT_PATTERN} so a typo in the config cannot break chat.
 */
public final class TimestampFormatter {
	public static final String DEFAULT_PATTERN = "HH:mm";

	private final DateTimeFormatter formatter;
	private final ZoneId zone;

	public TimestampFormatter(String pattern, ZoneId zone) {
		this.zone = zone == null ? ZoneId.systemDefault() : zone;
		this.formatter = build(pattern);
	}

	private static DateTimeFormatter build(String pattern) {
		try {
			if (pattern != null && !pattern.isBlank()) {
				return DateTimeFormatter.ofPattern(pattern);
			}
		} catch (IllegalArgumentException ignored) {
			// fall through to the default
		}
		return DateTimeFormatter.ofPattern(DEFAULT_PATTERN);
	}

	public String format(long epochMillis) {
		return formatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone));
	}
}
