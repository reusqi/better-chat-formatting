package com.betterchatformatting.fabric.mixin;

import com.betterchatformatting.fabric.chat.ChatPipeline;
import com.betterchatformatting.fabric.chat.ChatTheme;
import com.betterchatformatting.fabric.chat.ChatView;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Hooks the chat component: message decoration, repeat collapsing and the layout overrides. See
 * {@code docs/MIXINS.md} for why it exists and what it touches.
 *
 * <p>Every line passes through {@code addMessage} once. The head hook asks {@link ChatPipeline} for
 * the decorated content. If nothing changes, vanilla runs untouched. Otherwise the original call is
 * cancelled and the decorated line is added through the same method, with a guard so the hook does
 * not run twice. For a collapsed repeat, the previous line is removed from the display first.
 */
@Mixin(ChatComponent.class)
abstract class ChatComponentMixin {

	/** Set only while this class re-dispatches a decorated line. Chat runs on the client thread. */
	@Unique
	private static boolean betterchatformatting$redispatching;

	@Shadow
	@Final
	private List<GuiMessage> allMessages;

	@Shadow
	@Final
	private List<GuiMessage.Line> trimmedMessages;

	@Shadow
	private void addMessage(Component contents, MessageSignature signature, GuiMessageSource source,
			GuiMessageTag tag) {
	}

	@Inject(method = "addMessage", at = @At("HEAD"), cancellable = true)
	private void betterchatformatting$onAddMessage(Component contents, MessageSignature signature,
			GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
		if (betterchatformatting$redispatching) {
			return; // our own re-dispatch: let vanilla add the line
		}
		ChatPipeline.Outcome outcome = ChatPipeline.process(contents, source, tag, !allMessages.isEmpty());
		if (!outcome.changed()) {
			return;
		}
		ci.cancel();
		if (outcome.collapsed()) {
			betterchatformatting$removeLastMessage();
		}
		betterchatformatting$redispatching = true;
		try {
			addMessage(outcome.content(), signature, source, outcome.tag());
		} finally {
			betterchatformatting$redispatching = false;
		}
	}

	/**
	 * Removes the newest message and its wrapped lines from the display. Lines are added to the front
	 * of {@code trimmedMessages}, so the newest message's lines are the first entries.
	 */
	/** Chat width override (layout feature). {@code getWidth()} is the private no-argument overload. */
	@Inject(method = "getWidth()I", at = @At("HEAD"), cancellable = true)
	private void betterchatformatting$width(CallbackInfoReturnable<Integer> cir) {
		int width = ChatView.width();
		if (width > 0) {
			cir.setReturnValue(width);
		}
	}

	/**
	 * Line background (theme feature). In 26.3 the per-line background is one {@code fill} call inside
	 * the lambda {@code lambda$extractRenderState$1}; its color argument (index 4) is replaced with the
	 * theme's color and opacity, keeping vanilla's fade.
	 */
	@ModifyArg(method = "lambda$extractRenderState$1",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;fill(IIIII)V"),
			index = 4)
	private static int betterchatformatting$lineBackground(int color) {
		return ChatTheme.lineBackground(color);
	}

	/** Chat height override (layout feature). Applies to both focused and unfocused chat. */
	@Inject(method = "getHeight()I", at = @At("HEAD"), cancellable = true)
	private void betterchatformatting$height(CallbackInfoReturnable<Integer> cir) {
		int height = ChatView.height();
		if (height > 0) {
			cir.setReturnValue(height);
		}
	}

	@Unique
	private void betterchatformatting$removeLastMessage() {
		if (allMessages.isEmpty()) {
			return;
		}
		GuiMessage last = allMessages.removeFirst();
		while (!trimmedMessages.isEmpty() && trimmedMessages.getFirst().parent() == last) {
			trimmedMessages.removeFirst();
		}
	}
}
