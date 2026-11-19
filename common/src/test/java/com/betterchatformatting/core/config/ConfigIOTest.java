package com.betterchatformatting.core.config;

import com.betterchatformatting.core.classify.MessageType;
import com.betterchatformatting.core.highlight.HighlightRule;
import com.betterchatformatting.core.highlight.KeywordMatcher;
import com.betterchatformatting.core.io.ConfigException;
import com.betterchatformatting.core.text.Segment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigIOTest {

	private static ConfigIO.ParseResult read(String text) throws ConfigException {
		return ConfigIO.read(text);
	}

	@Test
	void defaultConfigRoundTripsThroughTheWrittenFile() throws Exception {
		ChatConfig defaults = new ChatConfig();
		String text = ConfigIO.write(defaults);
		ConfigIO.ParseResult r = read(text);
		assertTrue(r.warnings().isEmpty(), "warnings: " + r.warnings());
		// writing the parsed result again must give the identical file
		assertEquals(text, ConfigIO.write(r.config()));
	}

	@Test
	void customizedConfigRoundTrips() throws Exception {
		ChatConfig c = new ChatConfig();
		c.enabled = false;
		c.theme = "terminal";
		c.features.markdown = false;
		c.features.timestamps = true;
		c.timestamps.format = "HH:mm:ss";
		c.names.overrides.put("Steve", "#FF8800");
		c.names.saturation = 0.3f;
		c.highlights.ownNameColor = 0x123456;
		c.highlights.ownNameSound = "";
		c.highlights.keywords.add(new HighlightRule("diamond", 0x55FFFF, "minecraft:entity.experience_orb.pickup", true, false));
		c.highlights.keywords.add(HighlightRule.of("say \"hi\"", 0xFF0000, null)); // quote must survive escaping
		c.messageTypes.get(MessageType.JOIN).icon = "→";
		c.messageTypes.get(MessageType.DEATH).color = Segment.NO_COLOR;
		c.layout.chatWidth = 320;
		c.layout.scrollLines = 4;

		String text = ConfigIO.write(c);
		ChatConfig back = read(text).config();
		assertEquals(text, ConfigIO.write(back));
		assertFalse(back.enabled);
		assertEquals("terminal", back.theme);
		assertFalse(back.features.markdown);
		assertTrue(back.features.timestamps);
		assertEquals("HH:mm:ss", back.timestamps.format);
		assertEquals("#FF8800", back.names.overrides.get("Steve"));
		assertEquals(0x123456, back.highlights.ownNameColor);
		assertEquals(2, back.highlights.keywords.size());
		assertEquals(c.highlights.keywords.get(0), back.highlights.keywords.get(0));
		assertEquals("say \"hi\"", back.highlights.keywords.get(1).keyword());
		assertEquals("→", back.messageTypes.get(MessageType.JOIN).icon);
		assertEquals(Segment.NO_COLOR, back.messageTypes.get(MessageType.DEATH).color);
		assertEquals(320, back.layout.chatWidth);
		assertEquals(4, back.layout.scrollLines);
	}

	@Test
	void commentsAndLooseSyntaxAreAccepted() throws Exception {
		String text = """
				// leading comment
				{
				  # hash comment
				  /* block comment */
				  enabled: false,           // unquoted key
				  'theme': 'PASTEL'         // single quotes, mixed case
				}
				""";
		ChatConfig c = read(text).config();
		assertFalse(c.enabled);
		assertEquals("pastel", c.theme);
	}

	@Test
	void emptyObjectGivesDefaults() throws Exception {
		ChatConfig c = read("{}").config();
		assertEquals(write(new ChatConfig()), write(c));
	}

	private static String write(ChatConfig c) {
		return ConfigIO.write(c);
	}

	@Test
	void unknownKeysAreIgnored() throws Exception {
		ConfigIO.ParseResult r = read("{ \"somethingNew\": 1, \"features\": { \"futureToggle\": true } }");
		assertTrue(r.warnings().isEmpty());
	}

	@Test
	void oneBadFieldDoesNotCostTheRest() throws Exception {
		String text = """
				{
				  "enabled": "yes please",
				  "features": { "markdown": false, "links": 5 },
				  "names": { "saturation": "lots" },
				  "layout": { "scrollLines": 4 }
				}
				""";
		ConfigIO.ParseResult r = read(text);
		ChatConfig c = r.config();
		assertTrue(c.enabled, "bad value falls back to default");
		assertFalse(c.features.markdown, "good sibling kept");
		assertTrue(c.features.links, "bad value falls back to default");
		assertEquals(0.55f, c.names.saturation);
		assertEquals(4, c.layout.scrollLines, "good value in a later section kept");
		assertEquals(3, r.warnings().size(), r.warnings().toString());
		assertTrue(r.warnings().stream().anyMatch(w -> w.startsWith("enabled")));
		assertTrue(r.warnings().stream().anyMatch(w -> w.startsWith("features.links")));
		assertTrue(r.warnings().stream().anyMatch(w -> w.startsWith("names.saturation")));
	}

	@Test
	void outOfRangeValuesAreClamped() throws Exception {
		String text = """
				{
				  "names": { "saturation": 7, "value": -2 },
				  "highlights": { "soundVolume": 9 },
				  "layout": { "chatWidth": 5, "chatHeight": 99999, "scrollLines": 100 }
				}
				""";
		ConfigIO.ParseResult r = read(text);
		ChatConfig c = r.config();
		assertEquals(1f, c.names.saturation);
		assertEquals(0f, c.names.value);
		assertEquals(1f, c.highlights.soundVolume);
		assertEquals(80, c.layout.chatWidth);
		assertEquals(600, c.layout.chatHeight);
		assertEquals(10, c.layout.scrollLines);
		assertFalse(r.warnings().isEmpty());
	}

	@Test
	void invalidColorsFallBackWithAWarning() throws Exception {
		ConfigIO.ParseResult r = read("{ \"highlights\": { \"ownNameColor\": \"banana\" } }");
		assertEquals(0xFFD700, r.config().highlights.ownNameColor);
		assertEquals(1, r.warnings().size());
	}

	@Test
	void invalidSoundIdsAreRejected() throws Exception {
		ChatConfig c = read("{ \"highlights\": { \"ownNameSound\": \"Not A Sound!\" } }").config();
		assertEquals("minecraft:block.note_block.pling", c.highlights.ownNameSound);
		assertEquals("", read("{ \"highlights\": { \"ownNameSound\": \"\" } }").config().highlights.ownNameSound);
		assertEquals("custom:my/sound.x",
				read("{ \"highlights\": { \"ownNameSound\": \"custom:my/sound.x\" } }").config().highlights.ownNameSound);
	}

	@Test
	void keywordsAcceptStringsAndObjectsAndSkipBadEntries() throws Exception {
		String text = """
				{ "highlights": { "keywords": [
				  "diamond",
				  { "keyword": "creeper", "color": "#00FF00", "sound": "minecraft:entity.creeper.primed" },
				  { "color": "#FF0000" },
				  42,
				  { "keyword": "" }
				] } }
				""";
		ConfigIO.ParseResult r = read(text);
		List<HighlightRule> k = r.config().highlights.keywords;
		assertEquals(2, k.size());
		assertEquals("diamond", k.get(0).keyword());
		assertEquals(0x00FF00, k.get(1).color());
		assertEquals("minecraft:entity.creeper.primed", k.get(1).sound());
		assertEquals(3, r.warnings().size(), r.warnings().toString());
	}

	@Test
	void keywordCountIsBounded() throws Exception {
		StringBuilder sb = new StringBuilder("{ \"highlights\": { \"keywords\": [");
		for (int i = 0; i < ConfigIO.MAX_KEYWORDS + 10; i++) {
			sb.append(i == 0 ? "" : ",").append("\"k").append(i).append('"');
		}
		sb.append("] } }");
		assertEquals(ConfigIO.MAX_KEYWORDS, read(sb.toString()).config().highlights.keywords.size());
	}

	@Test
	void nameOverridesValidateColors() throws Exception {
		ChatConfig c = read("{ \"names\": { \"overrides\": { \"Steve\": \"#FF0000\", \"Alex\": \"nope\", \"\": \"#000\" } } }")
				.config();
		assertEquals(1, c.names.overrides.size());
		assertEquals("#FF0000", c.names.overrides.get("Steve"));
	}

	@Test
	void messageTypeStylesAreEditable() throws Exception {
		ChatConfig c = read("""
				{ "messageTypes": { "join": { "enabled": false, "color": "none", "icon": "\\u00A7cX" },
				                    "death": { "color": "#102030" } } }
				""").config();
		ChatConfig.TypeStyle join = c.messageTypes.get(MessageType.JOIN);
		assertFalse(join.enabled);
		assertEquals(Segment.NO_COLOR, join.color);
		assertEquals("cX", join.icon, "section sign stripped from icons");
		assertEquals(0x102030, c.messageTypes.get(MessageType.DEATH).color);
		assertTrue(c.messageTypes.get(MessageType.DEATH).enabled, "untouched fields keep defaults");
	}

	@Test
	void iconsAreSanitized() {
		assertEquals("ab", ConfigIO.sanitizeIcon("a\nb§"));
		assertEquals(ConfigIO.MAX_ICON_LENGTH, ConfigIO.sanitizeIcon("x".repeat(50)).length());
		assertEquals("", ConfigIO.sanitizeIcon(null));
	}

	@Test
	void syntaxErrorsThrow() {
		assertThrows(ConfigException.class, () -> read("{ \"enabled\": "));
		assertThrows(ConfigException.class, () -> read("not json at all"));
		assertThrows(ConfigException.class, () -> read(""));
		assertThrows(ConfigException.class, () -> read("[1, 2, 3]"));
		assertThrows(ConfigException.class, () -> read("null"));
	}

	@Test
	void ownNameMatcherComesFirstAndHonorsToggles() {
		ChatConfig c = new ChatConfig();
		c.highlights.keywords.add(HighlightRule.of("steve", 0x00FF00, null));
		KeywordMatcher m = c.highlights.toMatcher("Steve");
		KeywordMatcher.Match first = m.findFirst("hello steve");
		assertEquals(c.highlights.ownNameColor, first.rule().color(), "own-name rule wins the overlap");

		c.highlights.ownName = false;
		assertEquals(0x00FF00, c.highlights.toMatcher("Steve").findFirst("hello steve").rule().color());
		assertTrue(new ChatConfig().highlights.toMatcher("  ").isEmpty());
		assertTrue(new ChatConfig().highlights.toMatcher(null).isEmpty());
	}

	@Test
	void writtenFileExplainsItself() {
		String text = ConfigIO.write(new ChatConfig());
		assertTrue(text.contains("// Master switch"));
		assertTrue(text.contains("hot-reloaded"));
		assertTrue(text.contains("\"markdown\": true"));
	}
}
