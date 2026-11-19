package com.betterchatformatting.core.io;

/** Minimal logging hook so the Minecraft-free core can report problems; loaders adapt it to SLF4J. */
public interface ConfigLogger {
	ConfigLogger NOOP = new ConfigLogger() {
		@Override
		public void info(String message) {
		}

		@Override
		public void warn(String message) {
		}
	};

	void info(String message);

	void warn(String message);
}
