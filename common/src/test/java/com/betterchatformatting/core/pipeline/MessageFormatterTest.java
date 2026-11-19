package com.betterchatformatting.core.pipeline;

import com.betterchatformatting.core.highlight.HighlightRule;
import com.betterchatformatting.core.highlight.KeywordMatcher;
import com.betterchatformatting.core.pipeline.MessageFormatter.Options;
import com.betterchatformatting.core.pipeline.MessageFormatter.Result;
import com.betterchatformatting.core.text.Segment;
import com.betterchatformatting.core.text.StyleFlags;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageFormatterTest {
	private static final int RED = 0xFF0000;

	private static KeywordMatcher km(HighlightRule... rules) {
		return new KeywordMatcher(List.of(rules));
	}

	@Test
	void markdownAndLinksWorkTogetherAndUrlsAreNotMangled() {
		Result r = MessageFormatter.format("check **this** https://example.com/a_b__c now",
				0, Segment.NO_COLOR, new Options(true, true, null));
		assertEquals("check this https://example.com/a_b__c now", r.text().plainText());

		List<Segment> segs = r.text().segments();
		assertTrue(segs.stream().anyMatch(s -> s.text().equals("this") && s.flags() == StyleFlags.BOLD));
		Segment link = segs.stream().filter(Segment::isLink).findFirst().orElseThrow();
		assertEquals("https://example.com/a_b__c", link.url());
		assertEquals("https://example.com/a_b__c", link.text());
	}

	@Test
	void everythingOffLeavesTextAlone() {
		Result r = MessageFormatter.format("**x** https://example.com", 0, Segment.NO_COLOR, Options.OFF);
		assertEquals("**x** https://example.com", r.text().plainText());
		assertTrue(r.text().segments().stream().noneMatch(Segment::isLink));
		assertFalse(r.highlighted());
	}

	@Test
	void linksWithoutMarkdownStillLink() {
		Result r = MessageFormatter.format("**https://example.com**", 0, Segment.NO_COLOR,
				new Options(false, true, null));
		assertEquals("**https://example.com**", r.text().plainText());
		assertEquals(1, r.text().segments().stream().filter(Segment::isLink).count());
	}

	@Test
	void keywordsAreHighlightedInTheRenderedText() {
		Result r = MessageFormatter.format("hi Steve, **steve** wins", 0, Segment.NO_COLOR,
				new Options(true, true, km(HighlightRule.of("steve", RED, "minecraft:block.note_block.pling"))));
		assertEquals("hi Steve, steve wins", r.text().plainText());
		assertEquals(2, r.matches().size());
		assertTrue(r.highlighted());
		assertEquals("minecraft:block.note_block.pling", r.firstSound());
		assertTrue(r.text().segments().stream()
				.anyMatch(s -> s.text().equals("Steve") && s.color() == RED));
		// the bold word keeps its bold flag while being colored
		assertTrue(r.text().segments().stream()
				.anyMatch(s -> s.text().equals("steve") && s.color() == RED && s.flags() == StyleFlags.BOLD));
	}

	@Test
	void keywordInsideALinkTriggersButDoesNotRecolorTheLink() {
		Result r = MessageFormatter.format("see https://example.com now", 0, Segment.NO_COLOR,
				new Options(true, true, km(HighlightRule.of("example", RED, null))));
		assertEquals(1, r.matches().size());
		Segment link = r.text().segments().stream().filter(Segment::isLink).findFirst().orElseThrow();
		assertEquals(Segment.NO_COLOR, link.color());
		assertEquals("https://example.com", link.text());
	}

	@Test
	void noSoundWhenNoRuleHasOne() {
		Result r = MessageFormatter.format("steve", 0, Segment.NO_COLOR,
				new Options(true, true, km(HighlightRule.of("steve", RED, null))));
		assertNull(r.firstSound());
	}

	@Test
	void emptyBodyGivesEmptyResult() {
		Result r = MessageFormatter.format("", 0, Segment.NO_COLOR, new Options(true, true, null));
		assertTrue(r.text().isEmpty());
		assertTrue(r.matches().isEmpty());
	}
}
