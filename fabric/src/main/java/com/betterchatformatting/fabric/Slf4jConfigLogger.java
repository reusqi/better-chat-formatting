package com.betterchatformatting.fabric;

import com.betterchatformatting.core.io.ConfigLogger;
import org.slf4j.Logger;

/** Sends the core config messages to the game log. */
final class Slf4jConfigLogger implements ConfigLogger {
	private final Logger log;

	Slf4jConfigLogger(Logger log) {
		this.log = log;
	}

	@Override
	public void info(String message) {
		log.info(message);
	}

	@Override
	public void warn(String message) {
		log.warn(message);
	}
}
