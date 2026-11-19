package com.betterchatformatting.core.markdown;

import com.betterchatformatting.core.text.Segment;
import com.betterchatformatting.core.text.StyleFlags;
import com.betterchatformatting.core.text.StyledText;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.betterchatformatting.core.text.StyleFlags.BOLD;
import static com.betterchatformatting.core.text.StyleFlags.CODE;
import static com.betterchatformatting.core.text.StyleFlags.ITALIC;
import static com.betterchatformatting.core.text.StyleFlags.STRIKETHROUGH;
import static com.betterchatformatting.core.text.StyleFlags.UNDERLINE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownParserTest {

	private static Segment seg(String text, int flags) {
		return new Segment(text, flags, Segment.NO_COLOR, null);
	}

	private static List<Segment> parse(String s) {
		return MarkdownParser.parse(s).segments();
	}

	@Test
	void plainTextIsOneUnstyledSegment() {
		assertEquals(List.of(seg("hello world", 0)), parse("hello world"));
	}

	@Test
	void emptyAndNullGiveEmpty() {
		assertTrue(MarkdownParser.parse("").isEmpty());
		assertTrue(MarkdownParser.parse(null).isEmpty());
	}

	@Test
	void eachMarkerMapsToItsStyle() {
		assertEquals(List.of(seg("b", BOLD)), parse("**b**"));
		assertEquals(List.of(seg("i", ITALIC)), parse("*i*"));
		assertEquals(List.of(seg("u", UNDERLINE)), parse("__u__"));
		assertEquals(List.of(seg("s", STRIKETHROUGH)), parse("~~s~~"));
		assertEquals(List.of(seg("c", CODE)), parse("`c`"));
	}

	@Test
	void styledRunInsideSentence() {
		assertEquals(List.of(seg("a ", 0), seg("b", BOLD), seg(" c", 0)), parse("a **b** c"));
	}

	@Test
	void nestedStylesCombine() {
		assertEquals(
				List.of(seg("bold ", BOLD), seg("both", BOLD | ITALIC), seg(" bold", BOLD)),
				parse("**bold *both* bold**"));
		assertEquals(List.of(seg("x", UNDERLINE | STRIKETHROUGH)), parse("__~~x~~__"));
	}

	@Test
	void tripleStarIsBoldItalic() {
		assertEquals(List.of(seg("x", BOLD | ITALIC)), parse("***x***"));
	}

	@Test
	void unmatchedMarkersStayLiteral() {
		assertEquals(List.of(seg("**bold", 0)), parse("**bold"));
		assertEquals(List.of(seg("*", 0)), parse("*"));
		assertEquals(List.of(seg("a ~~ b", 0)), parse("a ~~ b"));
	}

	@Test
	void mathAndSnakeCaseAreNotFormatted() {
		assertEquals(List.of(seg("2 * 3 * 4", 0)), parse("2 * 3 * 4"));
		assertEquals(List.of(seg("snake_case_name", 0)), parse("snake_case_name"));
		assertEquals(List.of(seg("a_b__c__d", 0)), parse("a_b__c__d"));
	}

	@Test
	void loneUnderscoreAndTildeAreNeverMarkers() {
		assertEquals(List.of(seg("_nope_", 0)), parse("_nope_"));
		assertEquals(List.of(seg("~nope~", 0)), parse("~nope~"));
	}

	@Test
	void backslashEscapesMarkers() {
		assertEquals(List.of(seg("*not*", 0)), parse("\\*not\\*"));
		assertEquals(List.of(seg("**x**", 0)), parse("\\*\\*x\\*\\*"));
	}

	@Test
	void codeContentIsNeverParsed() {
		assertEquals(List.of(seg("**x**", CODE)), parse("`**x**`"));
	}

	@Test
	void codeInsideBold() {
		assertEquals(
				List.of(seg("bold ", BOLD), seg("x", BOLD | CODE), seg(" bold", BOLD)),
				parse("**bold `x` bold**"));
	}

	@Test
	void closerInsideCodeDoesNotCloseOuterSpan() {
		// The ** inside the code span must not close the outer bold, so the bold stays unmatched.
		assertEquals(List.of(seg("**a ", 0), seg("**", CODE), seg(" b", 0)), parse("**a `**` b"));
	}

	@Test
	void baseFlagsAndColorAreInherited() {
		StyledText t = MarkdownParser.parse("x *y*", BOLD, 0x123456, List.of());
		assertEquals(
				List.of(new Segment("x ", BOLD, 0x123456, null), new Segment("y", BOLD | ITALIC, 0x123456, null)),
				t.segments());
	}

	@Test
	void markdownCanBeSwitchedOff() {
		StyledText t = MarkdownParser.parse("**x**", 0, Segment.NO_COLOR, List.of(), false);
		assertEquals(List.of(seg("**x**", 0)), t.segments());
	}

	@Test
	void literalRangesAreOpaqueAndKeepTheirUrl() {
		String text = "see https://a.com/x_y__z ok";
		int start = text.indexOf("https");
		int end = start + "https://a.com/x_y__z".length();
		var lit = new MarkdownParser.Literal(start, end, "https://a.com/x_y__z");
		StyledText t = MarkdownParser.parse(text, 0, Segment.NO_COLOR, List.of(lit));
		assertEquals(
				List.of(seg("see ", 0),
						new Segment("https://a.com/x_y__z", 0, Segment.NO_COLOR, "https://a.com/x_y__z"),
						seg(" ok", 0)),
				t.segments());
	}

	@Test
	void literalInsideStyledSpanKeepsTheStyle() {
		String text = "**go https://a.com now**";
		int start = text.indexOf("https");
		int end = start + "https://a.com".length();
		var lit = new MarkdownParser.Literal(start, end, "https://a.com");
		StyledText t = MarkdownParser.parse(text, 0, Segment.NO_COLOR, List.of(lit));
		assertEquals(
				List.of(seg("go ", BOLD),
						new Segment("https://a.com", BOLD, Segment.NO_COLOR, "https://a.com"),
						seg(" now", BOLD)),
				t.segments());
	}

	@Test
	void overlongInputIsNotParsed() {
		String text = "*" + "a".repeat(MarkdownParser.MAX_LENGTH + 10) + "*";
		assertEquals(List.of(seg(text, 0)), parse(text));
	}

	@Test
	void pathologicalInputFinishesQuickly() {
		// Many openers that never close: each one scans to the end. Stays under MAX_LENGTH so it is parsed.
		String text = "*a ".repeat(600) + "**b ".repeat(500);
		assertTrue(text.length() < MarkdownParser.MAX_LENGTH);
		long t0 = System.nanoTime();
		MarkdownParser.parse(text);
		long ms = (System.nanoTime() - t0) / 1_000_000;
		assertTrue(ms < 2000, "took " + ms + " ms");
	}

	@Test
	void plainTextOfResultDropsMatchedMarkersOnly() {
		assertEquals("a b c *d", MarkdownParser.parse("a **b** *c* *d").plainText());
		assertEquals("hello there", MarkdownParser.parse("**hello** ~~there~~").plainText());
		assertTrue(StyleFlags.has(BOLD | ITALIC, BOLD));
	}
}
