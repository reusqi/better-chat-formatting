package com.betterchatformatting.core.config;

import com.betterchatformatting.core.TestLogger;
import com.betterchatformatting.core.theme.BuiltInThemes;
import com.betterchatformatting.core.theme.ThemeLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTest {
	private int touchCounter;

	private static String read(Path p) throws Exception {
		return Files.readString(p, StandardCharsets.UTF_8);
	}

	/** Writes the file and gives it a modified time that is guaranteed to differ from before. */
	private void edit(Path p, String text) throws Exception {
		Files.writeString(p, text, StandardCharsets.UTF_8);
		Files.setLastModifiedTime(p, FileTime.fromMillis(System.currentTimeMillis() + 10_000L * (++touchCounter)));
	}

	@Test
	void firstRunWritesDefaultsAndAnExampleTheme(@TempDir Path dir) throws Exception {
		TestLogger log = new TestLogger();
		ConfigManager m = new ConfigManager(dir, log);
		m.load();

		assertEquals(ConfigIO.write(new ChatConfig()), read(m.configFile()));
		assertEquals(ConfigIO.write(new ChatConfig()), ConfigIO.write(m.snapshot().config()));
		assertTrue(Files.isRegularFile(m.themesDir().resolve(ThemeLoader.EXAMPLE_FILE_NAME)));
		assertTrue(m.snapshot().themes().containsKey("example"));
		assertEquals(1, m.snapshot().generation());
		assertTrue(log.warnings.isEmpty(), log.warnings.toString());
	}

	@Test
	void existingValidConfigIsLoadedAndNotOverwritten(@TempDir Path dir) throws Exception {
		String text = "{ \"enabled\": false, \"theme\": \"terminal\" } // hand written";
		Files.writeString(dir.resolve(ConfigManager.FILE_NAME), text);
		ConfigManager m = new ConfigManager(dir, new TestLogger());
		m.load();

		assertFalse(m.snapshot().config().enabled);
		assertSame(BuiltInThemes.TERMINAL, m.snapshot().activeTheme());
		assertEquals(text, read(m.configFile()), "a valid file is left exactly as the player wrote it");
	}

	@Test
	void brokenConfigAtStartupFallsBackToDefaultsAndKeepsABackup(@TempDir Path dir) throws Exception {
		String broken = "{ \"enabled\": false, this is broken";
		Files.writeString(dir.resolve(ConfigManager.FILE_NAME), broken);
		TestLogger log = new TestLogger();
		ConfigManager m = new ConfigManager(dir, log);
		m.load();

		assertTrue(m.snapshot().config().enabled, "defaults in effect");
		assertEquals(broken, read(dir.resolve(ConfigManager.BACKUP_NAME)));
		assertEquals(ConfigIO.write(new ChatConfig()), read(m.configFile()), "fresh valid file written");
		assertEquals(1, log.warnings.size());
	}

	@Test
	void unchangedFilesDoNotTriggerAReload(@TempDir Path dir) {
		ConfigManager m = new ConfigManager(dir, new TestLogger());
		m.load();
		long gen = m.snapshot().generation();
		assertFalse(m.reloadIfChanged());
		assertFalse(m.reloadIfChanged());
		assertEquals(gen, m.snapshot().generation());
	}

	@Test
	void editingTheFileHotReloadsAndNotifiesListeners(@TempDir Path dir) throws Exception {
		ConfigManager m = new ConfigManager(dir, new TestLogger());
		m.load();
		AtomicInteger notified = new AtomicInteger();
		m.addListener(s -> notified.incrementAndGet());
		long gen = m.snapshot().generation();

		edit(m.configFile(), "{ \"features\": { \"markdown\": false } }");

		assertTrue(m.reloadIfChanged());
		assertFalse(m.snapshot().config().features.markdown);
		assertEquals(gen + 1, m.snapshot().generation());
		assertEquals(1, notified.get());
		assertFalse(m.reloadIfChanged(), "second poll sees nothing new");
	}

	@Test
	void invalidEditKeepsPreviousSettingsAndIsReportedOnce(@TempDir Path dir) throws Exception {
		ConfigManager m = new ConfigManager(dir, new TestLogger());
		m.load();
		edit(m.configFile(), "{ \"theme\": \"pastel\" }");
		assertTrue(m.reloadIfChanged());
		assertEquals("pastel", m.snapshot().config().theme);

		TestLogger log = new TestLogger();
		ConfigManager m2 = new ConfigManager(dir, log);
		m2.load();
		String midEdit = "{ \"theme\": \"term";
		edit(m2.configFile(), midEdit);

		assertFalse(m2.reloadIfChanged());
		assertEquals("pastel", m2.snapshot().config().theme, "previous settings stay active");
		assertEquals(midEdit, read(m2.configFile()), "the player's half-written file is not touched");
		assertEquals(1, log.warnings.size());
		assertFalse(m2.reloadIfChanged());
		assertEquals(1, log.warnings.size(), "not reported again on the next poll");

		edit(m2.configFile(), "{ \"theme\": \"terminal\" }");
		assertTrue(m2.reloadIfChanged(), "recovers as soon as the file is valid again");
		assertEquals("terminal", m2.snapshot().config().theme);
	}

	@Test
	void newThemeFilesAreHotLoaded(@TempDir Path dir) throws Exception {
		ConfigManager m = new ConfigManager(dir, new TestLogger());
		m.load();
		assertFalse(m.snapshot().themes().containsKey("fresh"));

		edit(m.themesDir().resolve("fresh.json"), "{ \"paddingX\": 7 }");

		assertTrue(m.reloadIfChanged());
		assertEquals(7, m.snapshot().themes().get("fresh").paddingX());
	}

	@Test
	void deletedConfigIsRestoredFromCurrentSettings(@TempDir Path dir) throws Exception {
		ConfigManager m = new ConfigManager(dir, new TestLogger());
		m.load();
		edit(m.configFile(), "{ \"theme\": \"pastel\" }");
		m.reloadIfChanged();

		Files.delete(m.configFile());

		assertTrue(m.reloadIfChanged());
		assertTrue(Files.isRegularFile(m.configFile()));
		assertEquals("pastel", m.snapshot().config().theme);
		assertEquals("pastel", ConfigIO.read(read(m.configFile())).config().theme);
	}

	@Test
	void saveValidatesPersistsAndDoesNotRetriggerReload(@TempDir Path dir) throws Exception {
		ConfigManager m = new ConfigManager(dir, new TestLogger());
		m.load();
		ChatConfig edited = new ChatConfig();
		edited.layout.scrollLines = 999;      // invalid: must be clamped like a hand edit
		edited.theme = "Pastel";
		edited.names.overrides.put("Steve", "#00FF00");
		AtomicInteger notified = new AtomicInteger();
		m.addListener(s -> notified.incrementAndGet());

		m.save(edited);

		assertEquals(10, m.snapshot().config().layout.scrollLines);
		assertEquals("pastel", m.snapshot().config().theme);
		assertEquals(10, ConfigIO.read(read(m.configFile())).config().layout.scrollLines);
		assertEquals("#00FF00", ConfigIO.read(read(m.configFile())).config().names.overrides.get("Steve"));
		assertEquals(1, notified.get());
		assertFalse(m.reloadIfChanged(), "our own write is not mistaken for an external edit");
		assertFalse(Files.exists(dir.resolve(ConfigManager.FILE_NAME + ".tmp")), "temp file cleaned up");
	}

	@Test
	void unknownThemeIdFallsBackToVanilla(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve(ConfigManager.FILE_NAME), "{ \"theme\": \"does-not-exist\" }");
		TestLogger log = new TestLogger();
		ConfigManager m = new ConfigManager(dir, log);
		m.load();
		assertSame(BuiltInThemes.VANILLA, m.snapshot().activeTheme());
		assertTrue(log.warnings.stream().anyMatch(s -> s.contains("does-not-exist")));
	}

	@Test
	void listenerFailuresDoNotBreakTheConfig(@TempDir Path dir) {
		ConfigManager m = new ConfigManager(dir, new TestLogger());
		m.addListener(s -> {
			throw new IllegalStateException("boom");
		});
		m.load();
		assertEquals(1, m.snapshot().generation());
	}
}
