package com.betterchatformatting.core.links;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlDetectorTest {

	private static List<String> urls(String text) {
		return UrlDetector.find(text).stream().map(UrlDetector.Match::url).toList();
	}

	@Test
	void findsHttpsUrlWithPathAndQuery() {
		String text = "visit https://example.com/path?a=1&b=2 now";
		List<UrlDetector.Match> m = UrlDetector.find(text);
		assertEquals(1, m.size());
		assertEquals("https://example.com/path?a=1&b=2", m.get(0).url());
		assertEquals(6, m.get(0).start());
		assertEquals("https://example.com/path?a=1&b=2", text.substring(m.get(0).start(), m.get(0).end()));
	}

	@Test
	void wwwPrefixIsUpgradedToHttps() {
		String text = "go to www.example.com now";
		List<UrlDetector.Match> m = UrlDetector.find(text);
		assertEquals(1, m.size());
		assertEquals("https://www.example.com", m.get(0).url());
		assertEquals("www.example.com", text.substring(m.get(0).start(), m.get(0).end()));
	}

	@Test
	void schemeIsCaseInsensitive() {
		assertEquals(1, UrlDetector.find("HTTPS://EXAMPLE.COM/x").size());
	}

	@Test
	void trailingPunctuationIsNotPartOfTheUrl() {
		assertEquals(List.of("https://example.com"), urls("see https://example.com."));
		assertEquals(List.of("https://example.com/a"), urls("is it https://example.com/a?"));
		assertEquals(List.of("https://example.com"), urls("(https://example.com)"));
		assertEquals(List.of("https://example.com"), urls("https://example.com, and more"));
	}

	@Test
	void balancedParenthesesStayInTheUrl() {
		assertEquals(List.of("https://en.wikipedia.org/wiki/Foo_(bar)"),
				urls("(https://en.wikipedia.org/wiki/Foo_(bar))"));
	}

	@Test
	void markdownMarkersAroundAUrlAreExcluded() {
		List<UrlDetector.Match> m = UrlDetector.find("**https://example.com**");
		assertEquals(1, m.size());
		assertEquals("https://example.com", m.get(0).url());
		assertEquals(2, m.get(0).start());
		assertEquals(21, m.get(0).end());
		assertEquals(List.of("https://example.com"), urls("__https://example.com__"));
	}

	@Test
	void findsSeveralUrls() {
		assertEquals(List.of("https://a.com", "https://www.b.org"), urls("a https://a.com b www.b.org"));
	}

	@Test
	void localhostIsAllowed() {
		assertEquals(List.of("https://localhost:8080/x"), urls("https://localhost:8080/x"));
	}

	@Test
	void nonWebSchemesAreNeverLinked() {
		assertTrue(urls("javascript:alert(1)").isEmpty());
		assertTrue(urls("ftp://example.com/file").isEmpty());
		assertTrue(urls("file:///etc/passwd").isEmpty());
		assertTrue(urls("data:text/html,<script>x</script>").isEmpty());
		assertTrue(urls("/give @s diamond").isEmpty());
	}

	@Test
	void credentialsInUrlAreRejected() {
		assertTrue(urls("https://good.com@evil.com/login").isEmpty());
		assertTrue(urls("https://user:pass@example.com").isEmpty());
	}

	@Test
	void hostWithoutADotIsRejected() {
		assertTrue(urls("https://nodots").isEmpty());
		assertTrue(urls("http://").isEmpty());
		assertTrue(urls("www.").isEmpty());
	}

	@Test
	void urlMustStartAtAWordBoundary() {
		assertTrue(urls("xhttps://example.com").isEmpty());
		assertTrue(urls("awww.example.com").isEmpty());
	}

	@Test
	void sectionSignEndsTheUrl() {
		assertTrue(urls("https://exam§ple.com").isEmpty()); // "https://exam" has no dot
		assertEquals(List.of("https://example.com"), urls("https://example.com§r"));
	}

	@Test
	void nonAsciiHostsAreRejected() {
		assertFalse(UrlDetector.isSafeUrl("https://bücher.example/x"));
		assertTrue(urls("https://bücher.example/x").isEmpty());
		assertTrue(UrlDetector.isSafeUrl("https://xn--bcher-kva.example/x"));
	}

	@Test
	void overlongUrlsAreRejected() {
		assertTrue(urls("https://a.com/" + "x".repeat(UrlDetector.MAX_URL_LENGTH)).isEmpty());
	}

	@Test
	void isSafeUrlEnforcesScheme() {
		assertTrue(UrlDetector.isSafeUrl("http://example.com"));
		assertTrue(UrlDetector.isSafeUrl("https://example.com"));
		assertFalse(UrlDetector.isSafeUrl("javascript:alert(1)"));
		assertFalse(UrlDetector.isSafeUrl("mailto:a@b.com"));
		assertFalse(UrlDetector.isSafeUrl(null));
		assertFalse(UrlDetector.isSafeUrl(""));
	}

	@Test
	void displayHostIsLowerCased() {
		assertEquals("example.com", UrlDetector.displayHost("https://Example.COM/path"));
	}

	@Test
	void noUrlsInOrdinaryText() {
		assertTrue(UrlDetector.find("hello world, nothing to see").isEmpty());
		assertTrue(UrlDetector.find("").isEmpty());
		assertTrue(UrlDetector.find(null).isEmpty());
		assertTrue(UrlDetector.find("a.b is not a url").isEmpty());
	}
}
