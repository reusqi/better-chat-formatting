package com.betterchatformatting.core;

import com.betterchatformatting.core.io.ConfigLogger;

import java.util.ArrayList;
import java.util.List;

/** Collects log lines so tests can assert on them. */
public final class TestLogger implements ConfigLogger {
	public final List<String> infos = new ArrayList<>();
	public final List<String> warnings = new ArrayList<>();

	@Override
	public void info(String message) {
		infos.add(message);
	}

	@Override
	public void warn(String message) {
		warnings.add(message);
	}
}
