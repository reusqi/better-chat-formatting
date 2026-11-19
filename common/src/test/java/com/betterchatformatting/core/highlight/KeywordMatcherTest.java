package com.betterchatformatting.core.highlight;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeywordMatcherTest {
	private static final int RED = 0xFF0000;
	private static final int BLUE = 0x0000FF;

	private static KeywordMatcher matcher(HighlightRule... rules) {
		return new KeywordMatcher(List.of(rules));
	}

	@Test
	void matchesOwnNameCaseInsensitively() {
		KeywordMatcher m = matcher(HighlightRule.of("Steve", RED, null));
		List<KeywordMatcher.Match> r = m.findAll("hey steve, you there?");
		assertEquals(1, r.size());
		assertEquals(4, r.get(0).start());
		assertEquals(9, r.get(0).end());
	}

	@Test
	void wholeWordRejectsPartialMatches() {
		KeywordMatcher m = matcher(HighlightRule.of("Steve", RED, null));
		assertTrue(m.findAll("Stevenson and mySteve").isEmpty());
		assertEquals(1, m.findAll("(Steve)").size());
		assertEquals(1, m.findAll("Steve").size());
	}

	@Test
	void substringModeMatchesInsideWords() {
		KeywordMatcher m = matcher(new HighlightRule("bob", RED, null, false, false));
		assertEquals(1, m.findAll("bobby").size());
	}

	@Test
	void caseSensitiveRulesRespectCase() {
		KeywordMatcher m = matcher(new HighlightRule("Steve", RED, null, true, true));
		assertTrue(m.findAll("steve").isEmpty());
		assertEquals(1, m.findAll("Steve").size());
	}

	@Test
	void everyOccurrenceIsReported() {
		KeywordMatcher m = matcher(HighlightRule.of("gg", RED, null));
		assertEquals(2, m.findAll("gg wp gg").size());
	}

	@Test
	void perKeywordColorsAndSounds() {
		KeywordMatcher m = matcher(
				HighlightRule.of("diamond", RED, "minecraft:entity.experience_orb.pickup"),
				HighlightRule.of("creeper", BLUE, null));
		List<KeywordMatcher.Match> r = m.findAll("found diamond near a creeper");
		assertEquals(2, r.size());
		assertEquals(RED, r.get(0).rule().color());
		assertEquals("minecraft:entity.experience_orb.pickup", r.get(0).rule().sound());
		assertEquals(BLUE, r.get(1).rule().color());
		assertNull(r.get(1).rule().sound());
	}

	@Test
	void matchesAreOrderedByPosition() {
		KeywordMatcher m = matcher(HighlightRule.of("b", RED, null), HighlightRule.of("a", BLUE, null));
		List<KeywordMatcher.Match> r = m.findAll("a b");
		assertEquals("a", "a b".substring(r.get(0).start(), r.get(0).end()));
		assertEquals("b", "a b".substring(r.get(1).start(), r.get(1).end()));
	}

	@Test
	void earlierRuleWinsOnOverlap() {
		KeywordMatcher m = matcher(
				new HighlightRule("dia", RED, null, false, false),
				new HighlightRule("diamond", BLUE, null, false, false));
		List<KeywordMatcher.Match> r = m.findAll("diamond");
		assertEquals(1, r.size());
		assertEquals("dia", r.get(0).rule().keyword());
	}

	@Test
	void blankKeywordsAreIgnored() {
		KeywordMatcher m = matcher(HighlightRule.of("  ", RED, null), HighlightRule.of("", RED, null));
		assertTrue(m.isEmpty());
		assertTrue(m.findAll("anything").isEmpty());
	}

	@Test
	void noMatchGivesEmptyAndNullFirst() {
		KeywordMatcher m = matcher(HighlightRule.of("steve", RED, null));
		assertTrue(m.findAll("nothing here").isEmpty());
		assertNull(m.findFirst("nothing here"));
		assertTrue(m.findAll("").isEmpty());
		assertTrue(m.findAll(null).isEmpty());
	}

	@Test
	void blankSoundBecomesNull() {
		assertNull(HighlightRule.of("x", RED, "  ").sound());
	}

	@Test
	void uppercaseKeywordMatchesLowercaseText() {
		// Matching lower-cases with Locale.ROOT, so a Turkish default locale cannot break "I".
		KeywordMatcher m = matcher(HighlightRule.of("ILL", RED, null));
		assertEquals(1, m.findAll("i ill").size());
	}
}
