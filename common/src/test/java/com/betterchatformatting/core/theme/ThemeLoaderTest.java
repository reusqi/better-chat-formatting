package com.betterchatformatting.core.theme;

import com.betterchatformatting.core.TestLogger;
import com.betterchatformatting.core.io.ConfigException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeLoaderTest {

	private static Theme parse(String json, List<String> warnings) throws ConfigException {
		return ThemeLoader.parse("mine", json, id -> BuiltInThemes.all().get(id), warnings);
	}

	@Test
	void missingFieldsComeFromTheBaseTheme() throws Exception {
		List<String> w = new ArrayList<>();
		Theme t = parse("{ \"base\": \"midnight\", \"textShadow\": false }", w);
		Theme m = BuiltInThemes.MIDNIGHT;
		assertTrue(w.isEmpty());
		assertEquals("mine", t.id());
		assertEquals("mine", t.name());
		assertEquals(m.backgroundColor(), t.backgroundColor());
		assertEquals(m.backgroundOpacity(), t.backgroundOpacity());
		assertEquals(m.textColor(), t.textColor());
		assertFalse(t.textShadow());
	}

	@Test
	void defaultBaseIsVanilla() throws Exception {
		Theme t = parse("{}", new ArrayList<>());
		assertEquals(BuiltInThemes.VANILLA.backgroundColor(), t.backgroundColor());
		assertEquals(BuiltInThemes.VANILLA.paddingX(), t.paddingX());
	}

	@Test
	void unknownBaseWarnsAndUsesVanilla() throws Exception {
		List<String> w = new ArrayList<>();
		Theme t = parse("{ \"base\": \"nope\" }", w);
		assertEquals(1, w.size());
		assertEquals(BuiltInThemes.VANILLA.backgroundColor(), t.backgroundColor());
	}

	@Test
	void everyFieldCanBeSet() throws Exception {
		Theme t = parse("""
				{ "name": "Mine", "backgroundColor": "#102030", "backgroundOpacity": 0.25,
				  "paddingX": 4, "paddingY": 2, "lineSpacing": 3, "textShadow": false,
				  "textColor": "#ABCDEF", "timestampColor": "#123", "font": "minecraft:uniform" }
				""", new ArrayList<>());
		assertEquals("Mine", t.name());
		assertEquals(0x102030, t.backgroundColor());
		assertEquals(0.25f, t.backgroundOpacity());
		assertEquals(4, t.paddingX());
		assertEquals(2, t.paddingY());
		assertEquals(3, t.lineSpacing());
		assertFalse(t.textShadow());
		assertEquals(0xABCDEF, t.textColor());
		assertEquals(0x112233, t.timestampColor());
		assertEquals("minecraft:uniform", t.font());
	}

	@Test
	void noneAndNullResetInheritedValues() throws Exception {
		Theme t = parse("{ \"base\": \"midnight\", \"textColor\": \"none\", \"font\": null }", new ArrayList<>());
		assertEquals(-1, t.textColor());
		assertNull(t.font());
	}

	@Test
	void badValuesAreClampedOrIgnoredWithWarnings() throws Exception {
		List<String> w = new ArrayList<>();
		Theme t = parse("{ \"base\": \"midnight\", \"paddingX\": 99, \"backgroundColor\": \"blue-ish\" }", w);
		assertEquals(16, t.paddingX());
		assertEquals(BuiltInThemes.MIDNIGHT.backgroundColor(), t.backgroundColor());
		assertEquals(2, w.size());
	}

	@Test
	void syntaxErrorsThrow() {
		assertThrows(ConfigException.class, () -> parse("{ nope", new ArrayList<>()));
		assertThrows(ConfigException.class, () -> parse("[]", new ArrayList<>()));
	}

	@Test
	void theShippedExampleIsValid() throws Exception {
		List<String> w = new ArrayList<>();
		Theme t = ThemeLoader.parse("example", ThemeLoader.EXAMPLE_THEME, id -> BuiltInThemes.all().get(id), w);
		assertTrue(w.isEmpty(), w.toString());
		assertEquals("Example", t.name());
		assertEquals(0.7f, t.backgroundOpacity());
		assertNull(t.font());
	}

	@Test
	void directoryLoadingSkipsBadFilesAndKeepsGoodOnes(@TempDir Path dir) throws Exception {
		write(dir, "a.json", "{ \"name\": \"A\", \"paddingX\": 5 }");
		write(dir, "zchild.json", "{ \"base\": \"a\" }");           // sorts after a, may use it as base
		write(dir, "midnight.json", "{ \"paddingX\": 9 }");           // overrides the built-in
		write(dir, "broken.json", "{ this is not json");
		write(dir, "Bad Name.json", "{}");
		write(dir, "huge.json", "{ \"name\": \"" + "x".repeat((int) ThemeLoader.MAX_FILE_BYTES) + "\" }");
		write(dir, "notes.txt", "ignored");
		TestLogger log = new TestLogger();

		Map<String, Theme> all = ThemeLoader.loadDirectory(dir, log);

		assertEquals("A", all.get("a").name());
		assertEquals(5, all.get("zchild").paddingX(), "inherits from user theme 'a'");
		assertEquals(9, all.get("midnight").paddingX());
		assertTrue(all.containsKey("terminal"), "built-ins still present");
		assertFalse(all.containsKey("broken"));
		assertFalse(all.containsKey("bad name"));
		assertFalse(all.containsKey("huge"));
		assertEquals(3, log.warnings.size(), log.warnings.toString());
	}

	@Test
	void missingOrNullDirectoryGivesBuiltIns(@TempDir Path dir) {
		assertEquals(BuiltInThemes.all().keySet(), ThemeLoader.loadDirectory(null, new TestLogger()).keySet());
		assertEquals(BuiltInThemes.all().keySet(),
				ThemeLoader.loadDirectory(dir.resolve("does-not-exist"), new TestLogger()).keySet());
	}

	@Test
	void resolveFallsBackToVanilla() {
		Map<String, Theme> all = BuiltInThemes.all();
		assertSame(BuiltInThemes.PASTEL, ThemeLoader.resolve(all, " Pastel "));
		assertSame(BuiltInThemes.VANILLA, ThemeLoader.resolve(all, "missing"));
		assertSame(BuiltInThemes.VANILLA, ThemeLoader.resolve(all, null));
	}

	private static void write(Path dir, String name, String text) throws Exception {
		Files.writeString(dir.resolve(name), text, StandardCharsets.UTF_8);
	}
}
