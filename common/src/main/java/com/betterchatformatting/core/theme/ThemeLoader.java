package com.betterchatformatting.core.theme;

import com.betterchatformatting.core.io.ConfigException;
import com.betterchatformatting.core.io.ConfigLogger;
import com.betterchatformatting.core.io.FieldReader;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads user themes: every {@code *.json} file in the themes folder is one theme whose id is the
 * file name without extension (lower-cased). A theme only needs the fields it wants to change;
 * everything else comes from its {@code "base"} theme (default {@code vanilla}).
 *
 * <p>A broken theme file is skipped with a warning and never affects other themes or chat.
 */
public final class ThemeLoader {
	public static final int MAX_THEMES = 64;
	public static final long MAX_FILE_BYTES = 64 * 1024;

	/** Written once into a fresh themes folder as a starting point. */
	public static final String EXAMPLE_FILE_NAME = "example.json";
	public static final String EXAMPLE_THEME = """
			{
			  // Copy this file, rename it (the file name becomes the theme id) and edit it.
			  // Select it with "theme": "<id>" in the main config. Every field is optional.

			  // Display name, and the theme to start from (vanilla, midnight, pastel, terminal, minimal,
			  // or another theme file that sorts earlier alphabetically).
			  "name": "Example",
			  "base": "midnight",

			  // Line background: color, opacity 0..1 (0 = none), padding in pixels (x 0..16, y 0..8).
			  "backgroundColor": "#101820",
			  "backgroundOpacity": 0.7,
			  "paddingX": 3,
			  "paddingY": 1,

			  // Extra pixels between lines (0..8) and whether text gets the vanilla drop shadow.
			  "lineSpacing": 1,
			  "textShadow": true,

			  // Default text color ("none" keeps vanilla white) and timestamp color.
			  "textColor": "#E0E8F0",
			  "timestampColor": "#6C7AB8",

			  // Optional font resource id, e.g. "minecraft:default". Leave out for the default font.
			  "font": null
			}
			""";

	private ThemeLoader() {
	}

	/**
	 * Parses one theme file.
	 *
	 * @param id      the theme id (already normalized)
	 * @param baseFor looks up a base theme by id; must return null if unknown
	 */
	public static Theme parse(String id, String text, java.util.function.Function<String, Theme> baseFor,
			List<String> warnings) throws ConfigException {
		JsonObject o = FieldReader.parseObject(text);
		FieldReader r = new FieldReader(warnings);

		String baseId = r.string(o, "base", "vanilla").strip().toLowerCase(Locale.ROOT);
		Theme base = baseFor.apply(baseId);
		if (base == null) {
			r.warn("base", "unknown theme \"" + baseId + "\", starting from vanilla");
			base = BuiltInThemes.VANILLA;
		}

		String font = base.font();
		JsonElement fe = o.get("font");
		if (fe != null) {
			font = fe.isJsonNull() ? null : r.string(o, "font", font);
		}

		return new Theme(
				id,
				r.string(o, "name", id),
				r.color(o, "backgroundColor", base.backgroundColor(), false),
				r.decimal(o, "backgroundOpacity", base.backgroundOpacity(), 0f, 1f),
				r.integer(o, "paddingX", base.paddingX(), 0, 16),
				r.integer(o, "paddingY", base.paddingY(), 0, 8),
				r.integer(o, "lineSpacing", base.lineSpacing(), 0, 8),
				r.bool(o, "textShadow", base.textShadow()),
				r.color(o, "textColor", base.textColor(), true),
				r.color(o, "timestampColor", base.timestampColor(), false),
				font);
	}

	/**
	 * Loads every theme file in {@code dir}, in file-name order. User themes may override built-ins
	 * by using their id. Returns built-ins plus the user themes; problems are logged, not thrown.
	 */
	public static Map<String, Theme> loadDirectory(Path dir, ConfigLogger log) {
		Map<String, Theme> all = BuiltInThemes.all();
		if (dir == null || !Files.isDirectory(dir)) {
			return all;
		}
		List<Path> files = new ArrayList<>();
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.json")) {
			for (Path p : ds) {
				files.add(p);
			}
		} catch (IOException e) {
			log.warn("Cannot read themes folder " + dir + ": " + e.getMessage());
			return all;
		}
		files.sort((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()));

		int loaded = 0;
		for (Path file : files) {
			String fileName = file.getFileName().toString();
			String id = fileName.substring(0, fileName.length() - ".json".length()).toLowerCase(Locale.ROOT);
			if (!isValidId(id)) {
				log.warn("Theme file \"" + fileName + "\" skipped: the name may only use a-z, 0-9, _ and -");
				continue;
			}
			if (loaded >= MAX_THEMES) {
				log.warn("Theme file \"" + fileName + "\" skipped: more than " + MAX_THEMES + " themes");
				continue;
			}
			try {
				if (Files.size(file) > MAX_FILE_BYTES) {
					log.warn("Theme file \"" + fileName + "\" skipped: larger than " + (MAX_FILE_BYTES / 1024) + " KB");
					continue;
				}
				String text = Files.readString(file, StandardCharsets.UTF_8);
				List<String> warnings = new ArrayList<>();
				Theme t = parse(id, text, all::get, warnings);
				for (String w : warnings) {
					log.warn("Theme \"" + fileName + "\": " + w);
				}
				all.put(t.id(), t);
				loaded++;
			} catch (ConfigException e) {
				log.warn("Theme file \"" + fileName + "\" skipped: " + e.getMessage());
			} catch (IOException e) {
				log.warn("Theme file \"" + fileName + "\" unreadable: " + e.getMessage());
			}
		}
		return all;
	}

	static boolean isValidId(String id) {
		if (id.isEmpty() || id.length() > 48) {
			return false;
		}
		for (int i = 0; i < id.length(); i++) {
			char c = id.charAt(i);
			if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-')) {
				return false;
			}
		}
		return true;
	}

	/** Convenience used by tests and the config screen. */
	public static Theme resolve(Map<String, Theme> themes, String id) {
		Theme t = id == null ? null : themes.get(id.strip().toLowerCase(Locale.ROOT));
		return t != null ? t : BuiltInThemes.VANILLA;
	}
}
