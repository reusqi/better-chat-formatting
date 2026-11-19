package com.betterchatformatting.core.theme;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeTest {

	@Test
	void constructorClampsEveryValue() {
		Theme t = new Theme("  MyTheme ", "", 0x112233, 5f, 100, 100, 100, true, -5, 0xFFFFFF, "  ");
		assertEquals("mytheme", t.id());
		assertEquals("mytheme", t.name());
		assertEquals(1f, t.backgroundOpacity());
		assertEquals(16, t.paddingX());
		assertEquals(8, t.paddingY());
		assertEquals(8, t.lineSpacing());
		assertEquals(-1, t.textColor());
		assertNull(t.font());
	}

	@Test
	void negativeAndNanValuesAreSafe() {
		Theme t = new Theme(null, null, 0, Float.NaN, -4, -4, -4, false, 0, 0, null);
		assertEquals("custom", t.id());
		assertEquals(0.5f, t.backgroundOpacity());
		assertEquals(0, t.paddingX());
		assertEquals(0, t.paddingY());
		assertEquals(0, t.lineSpacing());
	}

	@Test
	void argbCombinesOpacityAndColor() {
		assertEquals(0xFF112233, new Theme("a", "a", 0x112233, 1f, 0, 0, 0, true, -1, 0, null).backgroundArgb());
		assertEquals(0x00112233, new Theme("a", "a", 0x112233, 0f, 0, 0, 0, true, -1, 0, null).backgroundArgb());
	}

	@Test
	void builtInsAreAllPresent() {
		Map<String, Theme> all = BuiltInThemes.all();
		for (String id : new String[] {"vanilla", "midnight", "pastel", "terminal", "minimal"}) {
			assertTrue(all.containsKey(id), id);
			assertEquals(id, all.get(id).id());
		}
	}

	@Test
	void lookupFallsBackToVanilla() {
		assertSame(BuiltInThemes.MIDNIGHT, BuiltInThemes.byIdOrDefault("MIDNIGHT"));
		assertSame(BuiltInThemes.VANILLA, BuiltInThemes.byIdOrDefault("nonsense"));
		assertSame(BuiltInThemes.VANILLA, BuiltInThemes.byIdOrDefault(null));
	}
}
