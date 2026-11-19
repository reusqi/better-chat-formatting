package com.betterchatformatting.core.io;

import com.betterchatformatting.core.text.Colors;
import com.betterchatformatting.core.text.Segment;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;

import java.io.StringReader;
import java.util.List;

/**
 * Forgiving, field-by-field JSON reading. A missing, mistyped or out-of-range field never fails
 * the whole file: it falls back to the supplied default (or is clamped) and adds a human-readable
 * line to the warnings list, so one typo cannot throw away the rest of a player's settings.
 */
public final class FieldReader {
	private final List<String> warnings;
	private final String prefix;

	public FieldReader(List<String> warnings) {
		this(warnings, "");
	}

	private FieldReader(List<String> warnings, String prefix) {
		this.warnings = warnings;
		this.prefix = prefix;
	}

	/**
	 * Parses lenient JSON (comments with {@code //}, {@code /* *}{@code /} and {@code #}, unquoted keys,
	 * single quotes) whose top level must be an object.
	 */
	public static JsonObject parseObject(String text) throws ConfigException {
		try {
			JsonReader reader = new JsonReader(new StringReader(text == null ? "" : text));
			reader.setStrictness(Strictness.LENIENT);
			JsonElement root = JsonParser.parseReader(reader);
			if (root == null || !root.isJsonObject()) {
				throw new ConfigException("top level must be a { ... } object");
			}
			return root.getAsJsonObject();
		} catch (JsonParseException | IllegalStateException e) {
			throw new ConfigException("syntax error: " + e.getMessage(), e);
		}
	}

	/** A reader whose warnings are prefixed with {@code section.}. */
	public FieldReader at(String section) {
		return new FieldReader(warnings, prefix + section + ".");
	}

	public void warn(String key, String message) {
		warnings.add(prefix + key + ": " + message);
	}

	/** The nested object at {@code key}, or null if absent (silently) or not an object (with a warning). */
	public JsonObject object(JsonObject o, String key) {
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull()) {
			return null;
		}
		if (!e.isJsonObject()) {
			warn(key, "expected an object, ignoring");
			return null;
		}
		return e.getAsJsonObject();
	}

	public boolean bool(JsonObject o, String key, boolean def) {
		JsonPrimitive p = primitive(o, key);
		if (p == null) {
			return def;
		}
		if (p.isBoolean()) {
			return p.getAsBoolean();
		}
		warn(key, "expected true or false, using " + def);
		return def;
	}

	public String string(JsonObject o, String key, String def) {
		JsonPrimitive p = primitive(o, key);
		if (p == null) {
			return def;
		}
		if (p.isString()) {
			return p.getAsString();
		}
		warn(key, "expected text in quotes, using \"" + def + "\"");
		return def;
	}

	public int integer(JsonObject o, String key, int def, int min, int max) {
		JsonPrimitive p = primitive(o, key);
		if (p == null) {
			return def;
		}
		if (!p.isNumber()) {
			warn(key, "expected a number, using " + def);
			return def;
		}
		double d = p.getAsDouble();
		if (Double.isNaN(d)) {
			warn(key, "not a number, using " + def);
			return def;
		}
		int v = (int) Math.max(min, Math.min(max, Math.round(d)));
		if (v != d) {
			warn(key, "value " + p.getAsString() + " adjusted to " + v + " (allowed " + min + ".." + max + ")");
		}
		return v;
	}

	public float decimal(JsonObject o, String key, float def, float min, float max) {
		JsonPrimitive p = primitive(o, key);
		if (p == null) {
			return def;
		}
		if (!p.isNumber()) {
			warn(key, "expected a number, using " + def);
			return def;
		}
		double d = p.getAsDouble();
		if (Double.isNaN(d)) {
			warn(key, "not a number, using " + def);
			return def;
		}
		float v = (float) Math.max(min, Math.min(max, d));
		if (v != (float) d) {
			warn(key, "value " + p.getAsString() + " adjusted to " + v + " (allowed " + min + ".." + max + ")");
		}
		return v;
	}

	/**
	 * A color written as {@code "#RRGGBB"} / {@code "#RGB"}. With {@code allowNone}, {@code null},
	 * {@code ""} and {@code "none"} mean "no color" and return {@link Segment#NO_COLOR}.
	 */
	public int color(JsonObject o, String key, int def, boolean allowNone) {
		JsonElement e = o.get(key);
		if (e == null) {
			return def;
		}
		if (e.isJsonNull()) {
			return allowNone ? Segment.NO_COLOR : def;
		}
		if (e.isJsonPrimitive()) {
			JsonPrimitive p = e.getAsJsonPrimitive();
			if (p.isString()) {
				String s = p.getAsString().strip();
				if (allowNone && (s.isEmpty() || s.equalsIgnoreCase("none"))) {
					return Segment.NO_COLOR;
				}
				int c = Colors.parseHex(s);
				if (c != Segment.NO_COLOR) {
					return c;
				}
			} else if (p.isNumber()) {
				double d = p.getAsDouble();
				if (d >= 0 && d <= 0xFFFFFF && d == Math.floor(d)) {
					return (int) d;
				}
			}
		}
		warn(key, "expected a color like \"#RRGGBB\", using " + (def == Segment.NO_COLOR ? "none" : Colors.toHex(def)));
		return def;
	}

	private JsonPrimitive primitive(JsonObject o, String key) {
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull()) {
			return null;
		}
		if (!e.isJsonPrimitive()) {
			warn(key, "unexpected value type, ignoring");
			return null;
		}
		return e.getAsJsonPrimitive();
	}
}
