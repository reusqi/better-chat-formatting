package com.betterchatformatting.core.config;

import com.betterchatformatting.core.io.ConfigException;
import com.betterchatformatting.core.io.ConfigLogger;
import com.betterchatformatting.core.theme.Theme;
import com.betterchatformatting.core.theme.ThemeLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Owns the config file and the themes folder: loads them, writes defaults, saves changes from the
 * config screen, and hot-reloads when the files change on disk.
 *
 * <h2>Failure policy (a bad file must never break chat)</h2>
 * <ul>
 *   <li><b>Startup, file is not valid JSON:</b> defaults are used, the broken file is copied to
 *       {@code betterchatformatting.json5.bak}, and a fresh commented file is written.</li>
 *   <li><b>Hot reload, file is not valid JSON</b> (usually the player is mid-edit): the previous
 *       settings stay active and the file is left alone; a warning is logged once.</li>
 *   <li><b>Individual bad values</b> fall back to their defaults with a warning (see {@code FieldReader}).</li>
 * </ul>
 *
 * <h2>Hot reload without threads</h2>
 * {@link #reloadIfChanged()} compares a cheap signature (name, modified time and size of the config
 * and theme files). Call it from a client tick every second or two; it does no parsing unless
 * something changed. Methods are synchronized; {@link #snapshot()} is lock-free for render code.
 */
public final class ConfigManager {
	public static final String FILE_NAME = "betterchatformatting.json5";
	public static final String BACKUP_NAME = FILE_NAME + ".bak";
	public static final String THEMES_DIR = "themes";

	/** An immutable view of the active settings. {@code generation} increases on every change, for cache invalidation. */
	public record Snapshot(ChatConfig config, Map<String, Theme> themes, long generation) {
		public Theme activeTheme() {
			return ThemeLoader.resolve(themes, config.theme);
		}
	}

	private final Path file;
	private final Path backup;
	private final Path themesDir;
	private final ConfigLogger log;
	private final List<Consumer<Snapshot>> listeners = new CopyOnWriteArrayList<>();

	private volatile Snapshot snapshot;
	private String signature = "";
	private long generation;

	public ConfigManager(Path configDir, ConfigLogger log) {
		this.file = configDir.resolve(FILE_NAME);
		this.backup = configDir.resolve(BACKUP_NAME);
		this.themesDir = configDir.resolve(THEMES_DIR);
		this.log = log == null ? ConfigLogger.NOOP : log;
		this.snapshot = new Snapshot(new ChatConfig(), ThemeLoader.loadDirectory(null, ConfigLogger.NOOP), 0);
	}

	public Path configFile() {
		return file;
	}

	public Path themesDir() {
		return themesDir;
	}

	/** The active settings. Safe to call from any thread, including every frame. */
	public Snapshot snapshot() {
		return snapshot;
	}

	/** Runs {@code listener} after every change (load, reload, save). Called on the thread that caused it. */
	public void addListener(Consumer<Snapshot> listener) {
		listeners.add(listener);
	}

	/** Initial load at startup. Never throws. */
	public synchronized void load() {
		try {
			Files.createDirectories(themesDir);
			writeExampleTheme();
		} catch (IOException e) {
			log.warn("Cannot create config folders: " + e.getMessage());
		}

		ChatConfig config = new ChatConfig();
		if (Files.isRegularFile(file)) {
			try {
				config = parseFile();
			} catch (ConfigException | IOException e) {
				log.warn("Config file is unusable (" + e.getMessage() + "). Using defaults; a copy of the broken file "
						+ "was saved as " + BACKUP_NAME + ".");
				backUpBrokenFile();
				config = new ChatConfig();
				writeConfig(config);
			}
		} else {
			writeConfig(config);
			log.info("Created default config at " + file);
		}
		apply(config);
	}

	/**
	 * Re-reads the files if they changed since the last call.
	 *
	 * @return true if new settings were applied
	 */
	public synchronized boolean reloadIfChanged() {
		String now = computeSignature();
		if (now.equals(signature)) {
			return false;
		}
		signature = now; // remember even on failure so a broken file is reported once, not every poll

		if (!Files.isRegularFile(file)) {
			log.warn("Config file was deleted; restoring it from the current settings.");
			writeConfig(snapshot.config());
			apply(snapshot.config());
			return true;
		}
		try {
			ChatConfig config = parseFile();
			apply(config);
			log.info("Config reloaded.");
			return true;
		} catch (ConfigException | IOException e) {
			log.warn("Config reload ignored, keeping previous settings: " + e.getMessage());
			return false;
		}
	}

	/**
	 * Applies and persists {@code edited} (e.g. from the config screen). The config is round-tripped
	 * through the file format first, so every value is validated and clamped exactly as if the player
	 * had typed it into the file.
	 */
	public synchronized void save(ChatConfig edited) {
		ChatConfig normalized;
		try {
			normalized = ConfigIO.read(ConfigIO.write(edited)).config();
		} catch (ConfigException e) {
			log.warn("Internal error while validating config, not saved: " + e.getMessage());
			return;
		}
		writeConfig(normalized);
		apply(normalized);
	}

	// ---------------------------------------------------------------- internals

	private ChatConfig parseFile() throws ConfigException, IOException {
		String text = Files.readString(file, StandardCharsets.UTF_8);
		ConfigIO.ParseResult result = ConfigIO.read(text);
		for (String w : result.warnings()) {
			log.warn("Config: " + w);
		}
		return result.config();
	}

	/** Loads themes, publishes a new snapshot and notifies listeners. */
	private void apply(ChatConfig config) {
		Map<String, Theme> themes = ThemeLoader.loadDirectory(themesDir, log);
		if (config.features.applyTheme && !themes.containsKey(config.theme)) {
			log.warn("Theme \"" + config.theme + "\" not found, using vanilla.");
		}
		Snapshot next = new Snapshot(config, themes, ++generation);
		snapshot = next;
		signature = computeSignature();
		for (Consumer<Snapshot> l : listeners) {
			try {
				l.accept(next);
			} catch (RuntimeException e) {
				log.warn("Config listener failed: " + e);
			}
		}
	}

	private void writeConfig(ChatConfig config) {
		try {
			writeAtomically(file, ConfigIO.write(config));
		} catch (IOException e) {
			log.warn("Cannot write " + file + ": " + e.getMessage());
		}
	}

	private void backUpBrokenFile() {
		try {
			Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			log.warn("Cannot back up the broken config: " + e.getMessage());
		}
	}

	private void writeExampleTheme() throws IOException {
		Path example = themesDir.resolve(ThemeLoader.EXAMPLE_FILE_NAME);
		boolean empty;
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(themesDir)) {
			empty = !ds.iterator().hasNext();
		}
		if (empty) {
			Files.writeString(example, ThemeLoader.EXAMPLE_THEME, StandardCharsets.UTF_8);
		}
	}

	private static void writeAtomically(Path target, String text) throws IOException {
		Path dir = target.toAbsolutePath().getParent();
		if (dir != null) {
			Files.createDirectories(dir);
		}
		Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
		Files.writeString(tmp, text, StandardCharsets.UTF_8);
		try {
			Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/** Name, modified time and size of the config and every theme file; changes when any of them is edited. */
	private String computeSignature() {
		StringBuilder sb = new StringBuilder();
		stat(sb, file);
		List<Path> themes = new ArrayList<>();
		if (Files.isDirectory(themesDir)) {
			try (DirectoryStream<Path> ds = Files.newDirectoryStream(themesDir, "*.json")) {
				ds.forEach(themes::add);
			} catch (IOException ignored) {
				// an unreadable folder just yields a shorter signature
			}
		}
		themes.sort((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));
		for (Path p : themes) {
			stat(sb, p);
		}
		return sb.toString();
	}

	private static void stat(StringBuilder sb, Path p) {
		try {
			sb.append(p.getFileName()).append('|')
					.append(Files.getLastModifiedTime(p).toMillis()).append('|')
					.append(Files.size(p)).append(';');
		} catch (IOException e) {
			sb.append(p.getFileName()).append("|missing;");
		}
	}
}
