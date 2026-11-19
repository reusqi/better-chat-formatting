package com.betterchatformatting.core.classify;

/**
 * Decides a message's {@link MessageType} from its translation key (reliable, language
 * independent) or, as a last resort, from literal English text sent by plugins.
 *
 * <p>The loader passes the key of the top-level translatable component, if there is one. A
 * message that arrived as a signed player message but has an unknown key is still a
 * {@link MessageType#PLAYER_CHAT}; that decision is made by the caller, not here.
 */
public final class MessageClassifier {
	private MessageClassifier() {
	}

	/**
	 * @param translationKey key of the message's translatable component, or null if it is literal text
	 * @return the type; {@link MessageType#SYSTEM} when the key is unknown or null
	 */
	public static MessageType classifyKey(String translationKey) {
		if (translationKey == null || translationKey.isEmpty()) {
			return MessageType.SYSTEM;
		}
		String k = translationKey;
		if (k.startsWith("multiplayer.player.joined")) {
			return MessageType.JOIN;
		}
		if (k.startsWith("multiplayer.player.left")) {
			return MessageType.LEAVE;
		}
		if (k.startsWith("death.")) {
			return MessageType.DEATH;
		}
		if (k.startsWith("chat.type.advancement.")) {
			return MessageType.ADVANCEMENT;
		}
		if (k.startsWith("commands.message.display.")) {
			return MessageType.WHISPER; // /msg, /tell, /w (incoming and outgoing)
		}
		if (k.startsWith("chat.type.text") || k.startsWith("chat.type.team.") || k.startsWith("chat.type.emote")) {
			return MessageType.PLAYER_CHAT;
		}
		return MessageType.SYSTEM;
	}

	/**
	 * English-only fallback for servers/plugins that send join, leave and death lines as plain
	 * text. Deliberately conservative: only exact vanilla phrasings, and only for text that
	 * has no translation key.
	 */
	public static MessageType classifyLiteral(String plainText) {
		if (plainText == null) {
			return MessageType.SYSTEM;
		}
		if (plainText.endsWith(" joined the game")) {
			return MessageType.JOIN;
		}
		if (plainText.endsWith(" left the game")) {
			return MessageType.LEAVE;
		}
		return MessageType.SYSTEM;
	}
}
