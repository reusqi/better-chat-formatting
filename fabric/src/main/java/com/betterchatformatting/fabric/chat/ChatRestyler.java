package com.betterchatformatting.fabric.chat;

import com.betterchatformatting.core.config.ChatConfig;
import com.betterchatformatting.core.config.ConfigManager;
import com.betterchatformatting.core.highlight.KeywordMatcher;
import com.betterchatformatting.core.pipeline.MessageFormatter;
import com.betterchatformatting.core.text.Segment;
import com.betterchatformatting.core.text.StyleFlags;
import com.betterchatformatting.core.text.StyledText;
import com.betterchatformatting.fabric.BetterChatFormattingClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rebuilds an incoming player chat line with markdown, links and keyword highlights applied.
 *
 * <p>Vanilla chat is a tree: the sender name and body are separate components, often with hover and
 * click events. This class only rewrites <b>plain-text leaves</b>. Every other node keeps its own
 * contents and style, so vanilla hover, click and colors survive. Markdown therefore applies within
 * one leaf: a {@code *} that opens in the name and closes in the body is not matched.
 *
 * <p>Results are cached per (local name, leaf text, link permission), bounded to {@value #CACHE_SIZE}
 * entries and cleared whenever the config changes. Any exception returns the original message, so a
 * bug here can never hide chat.
 */
public final class ChatRestyler {
	private static final int CACHE_SIZE = 256;
	/** Inline code has no native Minecraft look, so it gets a fixed warm color. */
	private static final int CODE_COLOR = 0xFFAA55;

	/** Output of {@link #restyle}: the (possibly new) component and the sound of the first highlight, or null. */
	public record Restyled(Component component, String sound) {
	}

	private record Leaf(List<Component> pieces, String sound) {
	}

	private static final Map<String, Leaf> CACHE = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Leaf> eldest) {
			return size() > CACHE_SIZE;
		}
	};
	private static long cachedGeneration = -1;

	private ChatRestyler() {
	}

	/** Returns the original component (same instance) when restyling is off or the line has no plain text. */
	public static synchronized Restyled restyle(Component original) {
		try {
			ConfigManager.Snapshot snapshot = BetterChatFormattingClient.config().snapshot();
			ChatConfig c = snapshot.config();
			if (!c.enabled || !(c.features.markdown || c.features.links || c.features.highlights)) {
				return new Restyled(original, null);
			}
			if (snapshot.generation() != cachedGeneration) {
				CACHE.clear();
				cachedGeneration = snapshot.generation();
			}
			Context ctx = new Context(c, localPlayerName(), new ArrayList<>());
			Component rebuilt = rebuild(original, ctx);
			String sound = ctx.sounds().isEmpty() ? null : ctx.sounds().get(0);
			return new Restyled(rebuilt, sound);
		} catch (RuntimeException e) {
			BetterChatFormattingClient.LOGGER.warn("Could not restyle a chat message, showing it unchanged", e);
			return new Restyled(original, null);
		}
	}

	private record Context(ChatConfig config, String playerName, List<String> sounds) {
	}

	/** Rebuilds a component node: plain leaves are restyled, other nodes are kept with rebuilt children. */
	private static Component rebuild(Component node, Context ctx) {
		MutableComponent root;
		if (node.getContents() instanceof PlainTextContents plain) {
			root = Component.literal("");
			root.setStyle(node.getStyle());
			boolean linkAllowed = ctx.config().features.links && node.getStyle().getClickEvent() == null;
			Leaf leaf = leafFor(plain.text(), linkAllowed, ctx);
			if (leaf.sound() != null) {
				ctx.sounds().add(leaf.sound());
			}
			for (Component piece : leaf.pieces()) {
				root.append(piece);
			}
		} else if (node.getContents() instanceof TranslatableContents translatable) {
			Object[] args = translatable.getArgs();
			Object[] newArgs = new Object[args.length];
			for (int i = 0; i < args.length; i++) {
				newArgs[i] = args[i] instanceof Component argComponent ? rebuild(argComponent, ctx) : args[i];
			}
			root = Component.translatable(translatable.getKey(), newArgs);
			root.setStyle(node.getStyle());
		} else {
			// Keybinds, scores, selectors and so on are left exactly as the server sent them.
			return node;
		}
		for (Component sibling : node.getSiblings()) {
			root.append(rebuild(sibling, ctx));
		}
		return root;
	}

	/** The styled pieces and highlight sound for one leaf. Cached, because the same messages repeat in busy chat. */
	private static Leaf leafFor(String text, boolean linkAllowed, Context ctx) {
		String key = ctx.playerName() + '\n' + (linkAllowed ? '1' : '0') + '\n' + text;
		Leaf cached = CACHE.get(key);
		if (cached != null) {
			return cached;
		}
		ChatConfig c = ctx.config();
		MessageFormatter.Options options = new MessageFormatter.Options(
				c.features.markdown,
				linkAllowed,
				c.features.highlights ? c.highlights.toMatcher(ctx.playerName()) : null);
		MessageFormatter.Result result = MessageFormatter.format(text, 0, Segment.NO_COLOR, options);
		StyledText styled = result.text();

		List<Component> pieces = new ArrayList<>();
		for (Segment s : styled.segments()) {
			pieces.add(Component.literal(s.text()).withStyle(styleOf(s, linkAllowed)));
		}
		Leaf leaf = new Leaf(pieces, firstSound(result.matches()));
		CACHE.put(key, leaf);
		return leaf;
	}

	/** The first matching rule with a non-blank sound. Keywords without a sound never hide a later one. */
	private static String firstSound(List<KeywordMatcher.Match> matches) {
		for (KeywordMatcher.Match m : matches) {
			String sound = m.rule().sound();
			if (sound != null && !sound.isBlank()) {
				return sound;
			}
		}
		return null;
	}

	/** Only the fields we change are set; everything else is inherited from the leaf's own style. */
	private static Style styleOf(Segment s, boolean linkAllowed) {
		Style style = Style.EMPTY;
		int f = s.flags();
		if ((f & StyleFlags.BOLD) != 0) {
			style = style.withBold(true);
		}
		if ((f & StyleFlags.ITALIC) != 0) {
			style = style.withItalic(true);
		}
		if ((f & StyleFlags.UNDERLINE) != 0) {
			style = style.withUnderlined(true);
		}
		if ((f & StyleFlags.STRIKETHROUGH) != 0) {
			style = style.withStrikethrough(true);
		}
		if (s.hasColor()) {
			style = style.withColor(s.color());
		} else if ((f & StyleFlags.CODE) != 0) {
			style = style.withColor(CODE_COLOR);
		}
		if (linkAllowed && s.isLink()) {
			try {
				style = style.withUnderlined(true).withClickEvent(new ClickEvent.OpenUrl(URI.create(s.url())));
			} catch (IllegalArgumentException ignored) {
				// UrlDetector only accepts URLs that parse; if one ever does not, it stays plain text.
			}
		}
		return style;
	}

	private static String localPlayerName() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.getUser() == null) {
			return "";
		}
		return mc.getUser().getName();
	}
}
