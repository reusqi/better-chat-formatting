package com.betterchatformatting.core.links;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Finds web links in untrusted chat text with a hand-written scanner (no regex, so it is cheap
 * and cannot be hit by catastrophic backtracking).
 *
 * <p>Security rules, all enforced here so callers cannot forget them:
 * <ul>
 *   <li>only {@code http} and {@code https} URLs are ever returned; {@code www.} prefixes are
 *       upgraded to {@code https://}</li>
 *   <li>URLs with credentials ({@code https://good.com@evil.com}) are rejected</li>
 *   <li>URLs with non-ASCII hosts (homograph look-alikes) are rejected</li>
 *   <li>URLs longer than {@link #MAX_URL_LENGTH} are rejected</li>
 * </ul>
 * The result is only a candidate: the UI must still ask the player before opening anything.
 */
public final class UrlDetector {
	public static final int MAX_URL_LENGTH = 2048;

	/** A detected link: {@code [start, end)} in the scanned text, and the normalized URL. */
	public record Match(int start, int end, String url) {
	}

	private UrlDetector() {
	}

	public static List<Match> find(String text) {
		if (text == null || text.length() < 4) {
			return List.of();
		}
		List<Match> out = null;
		int n = text.length();
		int i = 0;
		while (i < n) {
			char c = text.charAt(i);
			if ((c == 'h' || c == 'H' || c == 'w' || c == 'W') && (i == 0 || !isWordChar(text.charAt(i - 1)))) {
				int prefixLen = prefixLength(text, i);
				if (prefixLen > 0) {
					int end = scanEnd(text, i);
					end = trimTrailing(text, i, end);
					if (end - i > prefixLen) {
						String candidate = text.substring(i, end);
						String url = normalize(candidate);
						if (isSafeUrl(url)) {
							if (out == null) {
								out = new ArrayList<>(2);
							}
							out.add(new Match(i, end, url));
							i = end;
							continue;
						}
					}
				}
			}
			i++;
		}
		return out == null ? List.of() : out;
	}

	/** Returns true if {@code url} is an http(s) URL with a plausible host and no credentials. */
	public static boolean isSafeUrl(String url) {
		if (url == null || url.length() > MAX_URL_LENGTH) {
			return false;
		}
		String lower = url.toLowerCase(Locale.ROOT);
		if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
			return false;
		}
		URI uri;
		try {
			uri = new URI(url);
		} catch (URISyntaxException e) {
			return false;
		}
		if (uri.getUserInfo() != null) {
			return false;
		}
		String host = uri.getHost();
		if (host == null || host.isEmpty() || host.startsWith(".") || host.endsWith(".") || host.contains("..")) {
			return false;
		}
		// Non-ASCII hosts are refused outright: look-alike (homograph) domains are a classic phishing
		// trick, and the confirmation prompt could not show them unambiguously. Punycode ("xn--")
		// hosts are plain ASCII and still link; the prompt shows them as-is.
		for (int i = 0; i < host.length(); i++) {
			if (host.charAt(i) > 127) {
				return false;
			}
		}
		return host.equalsIgnoreCase("localhost") || host.indexOf('.') > 0;
	}

	/** The host of an already-validated URL, lower-cased, for the confirmation prompt ("" if unparsable). */
	public static String displayHost(String url) {
		try {
			String host = new URI(url).getHost();
			return host == null ? "" : host.toLowerCase(Locale.ROOT);
		} catch (URISyntaxException e) {
			return "";
		}
	}

	private static String normalize(String candidate) {
		return candidate.regionMatches(true, 0, "www.", 0, 4) ? "https://" + candidate : candidate;
	}

	/** Length of the URL prefix at {@code i} ("https://", "http://", "www."), or 0. */
	private static int prefixLength(String t, int i) {
		if (t.regionMatches(true, i, "https://", 0, 8)) {
			return 8;
		}
		if (t.regionMatches(true, i, "http://", 0, 7)) {
			return 7;
		}
		if (t.regionMatches(true, i, "www.", 0, 4)) {
			return 4;
		}
		return 0;
	}

	private static int scanEnd(String t, int start) {
		int i = start;
		int n = t.length();
		while (i < n) {
			char c = t.charAt(i);
			if (c <= ' ' || c == 127 || c == '<' || c == '>' || c == '"' || c == '`' || c == '\\' || c == '§') {
				break;
			}
			i++;
		}
		return i;
	}

	/** Strips trailing sentence punctuation, markdown markers and unbalanced closing brackets. */
	private static int trimTrailing(String t, int start, int end) {
		while (end > start) {
			char c = t.charAt(end - 1);
			switch (c) {
				case '.', ',', ';', ':', '!', '?', '\'', '*', '_', '~' -> end--;
				case ')', ']', '}' -> {
					char open = c == ')' ? '(' : c == ']' ? '[' : '{';
					if (count(t, start, end, c) > count(t, start, end, open)) {
						end--;
					} else {
						return end;
					}
				}
				default -> {
					return end;
				}
			}
		}
		return end;
	}

	private static int count(String t, int from, int to, char c) {
		int k = 0;
		for (int i = from; i < to; i++) {
			if (t.charAt(i) == c) {
				k++;
			}
		}
		return k;
	}

	private static boolean isWordChar(char c) {
		// '_' is deliberately not a word char so "__https://x.com__" (markdown underline) still links.
		return Character.isLetterOrDigit(c);
	}
}
