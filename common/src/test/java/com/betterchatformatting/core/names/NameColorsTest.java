package com.betterchatformatting.core.names;

import com.betterchatformatting.core.text.Segment;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NameColorsTest {

	@Test
	void sameNameAlwaysGivesSameColor() {
		NameColors a = NameColors.withDefaults();
		NameColors b = NameColors.withDefaults();
		assertEquals(a.colorFor("Steve"), a.colorFor("Steve"));
		assertEquals(a.colorFor("Steve"), b.colorFor("Steve"));
	}

	@Test
	void nameLookupIgnoresCase() {
		NameColors n = NameColors.withDefaults();
		assertEquals(n.colorFor("Steve"), n.colorFor("sTEVE"));
	}

	@Test
	void differentNamesSpreadAcrossColors() {
		NameColors n = NameColors.withDefaults();
		Set<Integer> colors = new HashSet<>();
		for (int i = 0; i < 40; i++) {
			colors.add(n.colorFor("Player" + i));
		}
		assertTrue(colors.size() >= 20, "only " + colors.size() + " distinct colors");
	}

	@Test
	void colorsAreValidRgb() {
		NameColors n = NameColors.withDefaults();
		for (int i = 0; i < 200; i++) {
			int c = n.colorFor("n" + i);
			assertTrue(c >= 0 && c <= 0xFFFFFF);
		}
	}

	@Test
	void overridesWinAndAreCaseInsensitive() {
		NameColors n = new NameColors(Map.of("Steve", "#FF0000"), 0.5f, 0.9f);
		assertEquals(0xFF0000, n.colorFor("steve"));
		assertEquals(0xFF0000, n.colorFor("STEVE"));
	}

	@Test
	void invalidOverridesAreIgnored() {
		NameColors n = new NameColors(Map.of("alex", "not-a-color"), 0.5f, 0.9f);
		int c = n.colorFor("alex");
		assertNotEquals(Segment.NO_COLOR, c);
		// falls back to the hashed color, exactly as if no override existed
		assertEquals(new NameColors(Map.of(), 0.5f, 0.9f).colorFor("alex"), c);
	}

	@Test
	void nullOrEmptyNameHasNoColor() {
		NameColors n = NameColors.withDefaults();
		assertEquals(Segment.NO_COLOR, n.colorFor(null));
		assertEquals(Segment.NO_COLOR, n.colorFor(""));
	}

	@Test
	void outOfRangeSaturationIsClamped() {
		NameColors n = new NameColors(Map.of(), 5f, -3f); // value clamped to 0 => black
		assertEquals(0, n.colorFor("anyone"));
	}
}
