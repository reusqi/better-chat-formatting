package com.betterchatformatting.core.classify;

/** Kinds of chat line that can get their own color/icon. */
public enum MessageType {
	PLAYER_CHAT,
	WHISPER,
	JOIN,
	LEAVE,
	DEATH,
	ADVANCEMENT,
	/** Anything else that is not a player message: server announcements, command output... */
	SYSTEM
}
