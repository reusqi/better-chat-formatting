package com.betterchatformatting.core.emoji;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmojiShortcodesTest {

	@Test
	void everyGlyphIsASingleBmpCharVanillaCanSend() {
		for (Map.Entry<String, String> e : EmojiShortcodes.all().entrySet()) {
			String g = e.getValue();
			assertEquals(1, g.length(), e.getKey() + " must be one UTF-16 char");
			char c = g.charAt(0);
			assertFalse(Character.isSurrogate(c), e.getKey() + " must not be a surrogate (non-BMP)");
			assertTrue(c >= ' ' && c != 127, e.getKey() + " must not be a control char");
			assertTrue(c != '§', e.getKey() + " must not be the section sign");
			assertFalse(Character.isISOControl(c));
		}
	}

	@Test
	void namesAreValidShortcodeTokens() {
		for (String name : EmojiShortcodes.all().keySet()) {
			assertTrue(name.matches("[a-z0-9_]+"), name);
		}
	}

	@Test
	void lookupIsCaseInsensitive() {
		assertNotNull(EmojiShortcodes.lookup("smile"));
		assertEquals(EmojiShortcodes.lookup("smile"), EmojiShortcodes.lookup("SMILE"));
		assertNull(EmojiShortcodes.lookup("definitely_not_an_emoji"));
		assertNull(EmojiShortcodes.lookup(null));
	}

	@Test
	void suggestionsFilterByPrefixAndRespectLimit() {
		List<String> s = EmojiShortcodes.suggest("sta", 10);
		assertTrue(s.contains("star"));
		assertTrue(s.contains("star_outline"));
		assertTrue(s.stream().allMatch(n -> n.startsWith("sta")));
		assertEquals(new ArrayList<>(s).stream().sorted().toList(), s);
		assertEquals(1, EmojiShortcodes.suggest("s", 1).size());
		assertTrue(EmojiShortcodes.suggest("zzzz", 5).isEmpty());
	}

	@Test
	void completesAShortcodeWhenTheClosingColonIsTyped() {
		EmojiShortcodes.Replacement r = EmojiShortcodes.completeAt(":heart:", 7);
		assertEquals(new EmojiShortcodes.Replacement(0, 7, EmojiShortcodes.lookup("heart")), r);

		String text = "hi :heart:";
		EmojiShortcodes.Replacement r2 = EmojiShortcodes.completeAt(text, text.length());
		assertEquals(3, r2.start());
		assertEquals(10, r2.end());
	}

	@Test
	void doesNotCompleteUnknownOrEmbeddedShortcodes() {
		assertNull(EmojiShortcodes.completeAt(":unknown_thing:", 15));
		assertNull(EmojiShortcodes.completeAt("12:30:", 6));      // a clock time
		assertNull(EmojiShortcodes.completeAt("a:smile:", 8));    // not at a token start
		assertNull(EmojiShortcodes.completeAt("::", 2));
		assertNull(EmojiShortcodes.completeAt("hello", 5));
		assertNull(EmojiShortcodes.completeAt(null, 0));
	}

	@Test
	void pendingPrefixReportsWhatIsBeingTyped() {
		assertEquals("smi", EmojiShortcodes.pendingPrefix(":smi", 4));
		assertEquals("s", EmojiShortcodes.pendingPrefix("hey :s", 6));
		assertNull(EmojiShortcodes.pendingPrefix("hello", 5));
		assertNull(EmojiShortcodes.pendingPrefix(":", 1));
		assertNull(EmojiShortcodes.pendingPrefix("x :", 3));
		assertNull(EmojiShortcodes.pendingPrefix("a:b", 3)); // colon not at a token start
	}
}
