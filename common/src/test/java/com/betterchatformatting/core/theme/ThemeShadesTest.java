package com.betterchatformatting.core.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeShadesTest {

	private static Theme themed(int textColor) {
		return new Theme("t", "T", 0x000000, 0.5f, 0, 0, 0, false, textColor, 0x808080, null);
	}

	@Test
	void bodyIsTheThemeTextColor() {
		assertEquals(0x33FF66, ThemeShades.of(themed(0x33FF66)).body());
	}

	@Test
	void everyRoleIsDistinct() {
		ThemeShades s = ThemeShades.of(themed(0x33FF66));
		int[] roles = {s.body(), s.sender(), s.whisper(), s.system(), s.mention()};
		for (int i = 0; i < roles.length; i++) {
			for (int j = i + 1; j < roles.length; j++) {
				assertNotEquals(roles[i], roles[j], "roles " + i + " and " + j + " match");
			}
		}
	}

	@Test
	void senderIsComplementaryToBody() {
		ThemeShades s = ThemeShades.of(themed(0x33FF66));
		float diff = Math.abs(hue(s.body()) - hue(s.sender()));
		assertTrue(Math.min(diff, 1f - diff) > 0.4f, "sender hue too close to body: " + diff);
	}

	@Test
	void mentionIsWarmGoldRegardlessOfTheme() {
		float hue = hue(ThemeShades.of(themed(0x33FF66)).mention());
		assertTrue(hue > 0.08f && hue < 0.16f, "mention hue " + hue);
		assertEquals(ThemeShades.of(themed(0x0B1026)).mention(), ThemeShades.of(themed(0x33FF66)).mention());
	}

	@Test
	void systemIsDesaturatedAndDimmerThanBody() {
		ThemeShades s = ThemeShades.of(themed(0x33FF66));
		assertTrue(ThemeShades.toHsv(s.system())[1] < ThemeShades.toHsv(s.body())[1]);
		assertTrue(ThemeShades.toHsv(s.system())[2] < ThemeShades.toHsv(s.body())[2], "system should be dimmer");
	}

	@Test
	void whisperHueIsRotatedAwayFromBody() {
		ThemeShades s = ThemeShades.of(themed(0x33FF66));
		float diff = Math.abs(hue(s.body()) - hue(s.whisper()));
		assertTrue(Math.min(diff, 1f - diff) > 0.2f, "hue difference too small: " + diff);
	}

	@Test
	void missingTextColorUsesWhiteBase() {
		assertEquals(0xFFFFFF, ThemeShades.of(themed(-1)).body());
	}

	@Test
	void hsvRoundTripsForPureColors() {
		assertEquals(0xFF0000, ThemeShades.fromHsv(0f, 1f, 1f));
		assertEquals(0x00FF00, ThemeShades.fromHsv(1f / 3f, 1f, 1f));
		assertEquals(0x0000FF, ThemeShades.fromHsv(2f / 3f, 1f, 1f));
		assertEquals(0x808080, ThemeShades.fromHsv(0f, 0f, 0.5f));
	}

	private static float hue(int rgb) {
		return ThemeShades.toHsv(rgb)[0];
	}

	private static int channelSum(int rgb) {
		return ((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF);
	}
}
