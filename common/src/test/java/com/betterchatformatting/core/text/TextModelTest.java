package com.betterchatformatting.core.text;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextModelTest {

	@Test
	void adjacentSegmentsWithSameStyleMergeAndEmptiesDrop() {
		StyledText t = StyledText.of(List.of(
				Segment.plain("ab"), Segment.plain(""), Segment.plain("cd"),
				new Segment("ef", StyleFlags.BOLD, Segment.NO_COLOR, null)));
		assertEquals(2, t.segments().size());
		assertEquals("abcd", t.segments().get(0).text());
		assertEquals("abcdef", t.plainText());
	}

	@Test
	void colorRangeSplitsSegments() {
		StyledText t = StyledText.ofPlain("hello world").withColorRange(6, 11, 0xFF0000);
		assertEquals(2, t.segments().size());
		assertEquals("hello ", t.segments().get(0).text());
		assertEquals("world", t.segments().get(1).text());
		assertEquals(0xFF0000, t.segments().get(1).color());
		assertEquals(Segment.NO_COLOR, t.segments().get(0).color());
	}

	@Test
	void colorRangeCanSpanSegments() {
		StyledText t = StyledText.of(List.of(
						Segment.plain("ab"),
						new Segment("cd", StyleFlags.BOLD, Segment.NO_COLOR, null)))
				.withColorRange(1, 3, 0x00FF00);
		List<Segment> s = t.segments();
		assertEquals(4, s.size());
		assertEquals("a", s.get(0).text());
		assertEquals("b", s.get(1).text());
		assertEquals(0x00FF00, s.get(1).color());
		assertEquals("c", s.get(2).text());
		assertEquals(StyleFlags.BOLD, s.get(2).flags());
		assertEquals(0x00FF00, s.get(2).color());
		assertEquals("d", s.get(3).text());
		assertEquals(Segment.NO_COLOR, s.get(3).color());
	}

	@Test
	void colorRangeSkipsLinksAndIgnoresEmptyRanges() {
		StyledText t = StyledText.of(List.of(new Segment("link", 0, Segment.NO_COLOR, "https://a.com")));
		assertEquals(t, t.withColorRange(0, 4, 0xFF0000));
		StyledText p = StyledText.ofPlain("abc");
		assertEquals(p, p.withColorRange(2, 2, 0xFF0000));
	}

	@Test
	void hexColorParsing() {
		assertEquals(0xFF8800, Colors.parseHex("#FF8800"));
		assertEquals(0xFF8800, Colors.parseHex("ff8800"));
		assertEquals(0xFF8800, Colors.parseHex("#f80"));
		assertEquals(Segment.NO_COLOR, Colors.parseHex("#GG0000"));
		assertEquals(Segment.NO_COLOR, Colors.parseHex("#12345"));
		assertEquals(Segment.NO_COLOR, Colors.parseHex(null));
		assertEquals("#00FF7F", Colors.toHex(0x00FF7F));
	}

	@Test
	void hsvConversion() {
		assertEquals(0xFF0000, Colors.hsvToRgb(0, 1, 1));
		assertEquals(0x00FF00, Colors.hsvToRgb(120, 1, 1));
		assertEquals(0x0000FF, Colors.hsvToRgb(240, 1, 1));
		assertEquals(0xFFFFFF, Colors.hsvToRgb(77, 0, 1));
		assertEquals(Colors.hsvToRgb(10, 1, 1), Colors.hsvToRgb(370, 1, 1));
		assertTrue(Colors.hsvToRgb(-30, 0.5f, 0.5f) >= 0);
	}
}
