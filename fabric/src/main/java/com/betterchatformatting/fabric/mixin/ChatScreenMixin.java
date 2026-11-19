package com.betterchatformatting.fabric.mixin;

import com.betterchatformatting.core.emoji.EmojiShortcodes;
import com.betterchatformatting.fabric.chat.ChatView;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds the chat search mode, the emoji completion and the wheel-speed setting to the chat screen.
 * See {@code docs/MIXINS.md}. Each feature checks its own toggle and does nothing when it is off.
 *
 * <ul>
 *   <li><b>Search</b> (Ctrl+F): the input box becomes the search box. Typing filters the chat.
 *       Enter does not send. The draft comes back when search closes.</li>
 *   <li><b>Emoji</b>: typing the closing colon of a known {@code :shortcode:} replaces it with the
 *       plain Unicode symbol. Only the input text changes, and only as the player types.</li>
 * </ul>
 */
@Mixin(ChatScreen.class)
abstract class ChatScreenMixin {

	@Shadow
	protected EditBox input;

	@Unique
	private boolean betterchatformatting$search;

	@Unique
	private String betterchatformatting$stashedDraft = "";

	/** Set while this class writes the input box itself, so its own responder does not re-enter. */
	@Unique
	private boolean betterchatformatting$writing;

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void betterchatformatting$onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (event.hasControlDown() && event.key() == InputConstants.KEY_F && ChatView.searchEnabled()) {
			if (betterchatformatting$search) {
				betterchatformatting$exitSearch();
			} else {
				betterchatformatting$enterSearch();
			}
			cir.setReturnValue(true);
		} else if (betterchatformatting$search && event.isConfirmation()) {
			// In search mode Enter must not send the text as a chat message.
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "onEdited", at = @At("HEAD"))
	private void betterchatformatting$onEdited(String value, CallbackInfo ci) {
		if (betterchatformatting$writing) {
			return;
		}
		if (betterchatformatting$search) {
			ChatView.applySearch(value);
			return;
		}
		if (!ChatView.emojiEnabled()) {
			return;
		}
		EmojiShortcodes.Replacement replacement = EmojiShortcodes.completeAt(value, input.getCursorPosition());
		if (replacement == null) {
			return;
		}
		String completed = value.substring(0, replacement.start()) + replacement.text()
				+ value.substring(replacement.end());
		betterchatformatting$write(completed, replacement.start() + replacement.text().length());
	}

	@Inject(method = "removed", at = @At("HEAD"))
	private void betterchatformatting$onRemoved(CallbackInfo ci) {
		// Restore the draft first, so the vanilla code that saves the draft does not save the search text.
		if (betterchatformatting$search) {
			betterchatformatting$exitSearch();
		}
	}

	/** The wheel amount is scaled by the configured lines per notch. Only this call site is changed. */
	@ModifyArg(
			method = "mouseScrolled",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/gui/components/ChatComponent;scrollChat(I)V"),
			index = 0)
	private static int betterchatformatting$scrollAmount(int vanillaAmount) {
		return ChatView.scrollAmount(vanillaAmount);
	}

	@Unique
	private void betterchatformatting$enterSearch() {
		betterchatformatting$stashedDraft = input.getValue();
		betterchatformatting$search = true;
		betterchatformatting$write("", 0);
		input.setHint(Component.literal("Search chat (Ctrl+F to exit)"));
		ChatView.applySearch("");
	}

	@Unique
	private void betterchatformatting$exitSearch() {
		betterchatformatting$search = false;
		ChatView.applySearch("");
		input.setHint(Component.empty());
		betterchatformatting$write(betterchatformatting$stashedDraft, betterchatformatting$stashedDraft.length());
	}

	/** Sets the input text without re-entering {@link #betterchatformatting$onEdited}. */
	@Unique
	private void betterchatformatting$write(String text, int cursor) {
		betterchatformatting$writing = true;
		try {
			input.setValue(text);
			input.setCursorPosition(cursor);
		} finally {
			betterchatformatting$writing = false;
		}
	}
}
