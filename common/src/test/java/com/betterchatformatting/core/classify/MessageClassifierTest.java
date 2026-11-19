package com.betterchatformatting.core.classify;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageClassifierTest {

	@Test
	void joinAndLeave() {
		assertEquals(MessageType.JOIN, MessageClassifier.classifyKey("multiplayer.player.joined"));
		assertEquals(MessageType.JOIN, MessageClassifier.classifyKey("multiplayer.player.joined.renamed"));
		assertEquals(MessageType.LEAVE, MessageClassifier.classifyKey("multiplayer.player.left"));
	}

	@Test
	void deaths() {
		assertEquals(MessageType.DEATH, MessageClassifier.classifyKey("death.attack.player"));
		assertEquals(MessageType.DEATH, MessageClassifier.classifyKey("death.fell.accident.generic"));
	}

	@Test
	void advancements() {
		assertEquals(MessageType.ADVANCEMENT, MessageClassifier.classifyKey("chat.type.advancement.task"));
		assertEquals(MessageType.ADVANCEMENT, MessageClassifier.classifyKey("chat.type.advancement.goal"));
		assertEquals(MessageType.ADVANCEMENT, MessageClassifier.classifyKey("chat.type.advancement.challenge"));
	}

	@Test
	void whispers() {
		assertEquals(MessageType.WHISPER, MessageClassifier.classifyKey("commands.message.display.incoming"));
		assertEquals(MessageType.WHISPER, MessageClassifier.classifyKey("commands.message.display.outgoing"));
	}

	@Test
	void playerChat() {
		assertEquals(MessageType.PLAYER_CHAT, MessageClassifier.classifyKey("chat.type.text"));
		assertEquals(MessageType.PLAYER_CHAT, MessageClassifier.classifyKey("chat.type.team.text"));
		assertEquals(MessageType.PLAYER_CHAT, MessageClassifier.classifyKey("chat.type.emote"));
	}

	@Test
	void unknownOrMissingKeysAreSystem() {
		assertEquals(MessageType.SYSTEM, MessageClassifier.classifyKey(null));
		assertEquals(MessageType.SYSTEM, MessageClassifier.classifyKey(""));
		assertEquals(MessageType.SYSTEM, MessageClassifier.classifyKey("chat.type.announcement"));
		assertEquals(MessageType.SYSTEM, MessageClassifier.classifyKey("commands.give.success.single"));
	}

	@Test
	void literalFallbackIsConservative() {
		assertEquals(MessageType.JOIN, MessageClassifier.classifyLiteral("Steve joined the game"));
		assertEquals(MessageType.LEAVE, MessageClassifier.classifyLiteral("Steve left the game"));
		assertEquals(MessageType.SYSTEM, MessageClassifier.classifyLiteral("Steve joined the game, hi!"));
		assertEquals(MessageType.SYSTEM, MessageClassifier.classifyLiteral(null));
	}
}
