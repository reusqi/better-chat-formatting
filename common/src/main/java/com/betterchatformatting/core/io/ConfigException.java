package com.betterchatformatting.core.io;

/** A config or theme file that cannot be used at all (syntax error, wrong top-level shape...). */
public final class ConfigException extends Exception {
	private static final long serialVersionUID = 1L;

	public ConfigException(String message) {
		super(message);
	}

	public ConfigException(String message, Throwable cause) {
		super(message, cause);
	}
}
