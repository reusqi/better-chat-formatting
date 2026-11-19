package com.betterchatformatting.core.qol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepeatCollapserTest {

	@Test
	void normalizeTrimsAndCollapsesWhitespace() {
		assertEquals("a b c", RepeatCollapser.normalize("  a \t b\n c "));
		assertEquals("", RepeatCollapser.normalize(null));
		assertEquals("", RepeatCollapser.normalize("   "));
	}

	@Test
	void repeatsWithDifferentWhitespaceStillCollapse() {
		RepeatCollapser r = new RepeatCollapser();
		assertEquals(1, r.register(RepeatCollapser.normalize("Hi  there")));
		assertEquals(2, r.register(RepeatCollapser.normalize("Hi there ")));
		assertEquals(3, r.register(RepeatCollapser.normalize("Hi\tthere")));
		assertEquals(" x3", RepeatCollapser.suffix(3));
	}

	@Test
	void differentTextRestartsTheCount() {
		RepeatCollapser r = new RepeatCollapser();
		assertEquals(1, r.register("one"));
		assertEquals(2, r.register("one"));
		assertEquals(1, r.register("two"));
		assertEquals("", RepeatCollapser.suffix(1));
	}
}
