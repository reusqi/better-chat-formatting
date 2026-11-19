package com.betterchatformatting.fabric.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Plays highlight sounds through the UI sound channel, with a small minimum gap so a burst of
 * matching messages cannot produce a wall of noise.
 */
final class ChatSounds {
	private static final long MIN_GAP_MILLIS = 250L;
	private static long lastPlayed;

	private ChatSounds() {
	}

	/** Client thread only. Silently does nothing for blank or unparseable ids. */
	static void play(String soundId, float volume) {
		if (soundId == null || soundId.isBlank()) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastPlayed < MIN_GAP_MILLIS) {
			return;
		}
		Identifier id = Identifier.tryParse(soundId);
		Minecraft mc = Minecraft.getInstance();
		if (id == null || mc == null) {
			return;
		}
		mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(id), 1.0f, volume));
		lastPlayed = now;
	}
}
