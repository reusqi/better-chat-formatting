package com.betterchatformatting.core.qol;

import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QolTest {

	@Test
	void repeatCollapserCountsConsecutiveDuplicates() {
		RepeatCollapser c = new RepeatCollapser();
		assertEquals(1, c.register("a"));
		assertEquals(2, c.register("a"));
		assertEquals(3, c.register("a"));
		assertEquals(1, c.register("b"));
		assertEquals(1, c.register("a"), "non-consecutive repeat starts over");
	}

	@Test
	void repeatCollapserResetForgetsHistory() {
		RepeatCollapser c = new RepeatCollapser();
		c.register("a");
		c.reset();
		assertEquals(1, c.register("a"));
	}

	@Test
	void repeatSuffix() {
		assertEquals("", RepeatCollapser.suffix(1));
		assertEquals(" x3", RepeatCollapser.suffix(3));
	}

	@Test
	void searchRequiresEveryTermCaseInsensitively() {
		ChatSearch s = ChatSearch.parse("  Steve   DIAMOND ");
		assertTrue(s.matches("steve found a diamond"));
		assertTrue(s.matches("Diamond for STEVE"));
		assertFalse(s.matches("steve found iron"));
		assertFalse(s.matches("nothing"));
	}

	@Test
	void emptySearchMatchesEverything() {
		assertTrue(ChatSearch.parse("").isEmpty());
		assertTrue(ChatSearch.parse("   ").isEmpty());
		assertTrue(ChatSearch.parse(null).isEmpty());
		assertTrue(ChatSearch.parse("").matches("anything"));
	}

	@Test
	void searchLimitsTermCount() {
		ChatSearch s = ChatSearch.parse("a b c d e f g h i j k l");
		// only the first MAX_TERMS (8) terms are used, so "i".."l" are not required
		assertTrue(s.matches("a b c d e f g h"));
	}

	@Test
	void timestampFormatsInGivenZone() {
		TimestampFormatter f = new TimestampFormatter("HH:mm:ss", ZoneOffset.UTC);
		assertEquals("00:00:00", f.format(0));
		assertEquals("01:01:01", f.format(3_661_000L));
	}

	@Test
	void invalidTimestampPatternFallsBackToDefault() {
		assertEquals("00:00", new TimestampFormatter("bbb", ZoneOffset.UTC).format(0));
		assertEquals("00:00", new TimestampFormatter("", ZoneOffset.UTC).format(0));
		assertEquals("00:00", new TimestampFormatter(null, ZoneOffset.UTC).format(0));
	}
}
