package com.betterchatformatting.fabric.config;

import com.betterchatformatting.core.classify.MessageType;
import com.betterchatformatting.core.config.ChatConfig;
import com.betterchatformatting.core.config.ConfigIO;
import com.betterchatformatting.core.config.ConfigManager;
import com.betterchatformatting.core.pipeline.MessageFormatter;
import com.betterchatformatting.core.text.Segment;
import com.betterchatformatting.core.text.StyleFlags;
import com.betterchatformatting.core.text.StyledText;
import com.betterchatformatting.core.theme.BuiltInThemes;
import com.betterchatformatting.core.theme.Theme;
import com.betterchatformatting.core.theme.ThemeLoader;
import com.betterchatformatting.core.theme.ThemeShades;
import com.betterchatformatting.fabric.BetterChatFormattingClient;
import com.betterchatformatting.fabric.chat.ChatTheme;
import com.betterchatformatting.fabric.chat.SenderColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * In-game settings screen built only from vanilla widgets.
 *
 * <p>Layout:
 * <ul>
 *   <li>Title: centered at the top, y 10.</li>
 *   <li>Settings list: left side, from y 28 down to {@code height - 36}. Every setting is a row in a
 *       {@link ContainerObjectSelectionList}, which clips its rows and only routes clicks inside
 *       its own rectangle. The scrollbar stays inside the list so it can be dragged.</li>
 *   <li>Live Preview: right half, from y 28 down to {@code height - 36}, drawn outside the list so it
 *       stays visible while the settings scroll. It is clamped to the window width.</li>
 *   <li>Footer: Save &amp; Close, Reset to Defaults, Cancel, at {@code height - 28}.</li>
 * </ul>
 * The preview is drawn from the working copy, so unsaved edits show immediately, including the
 * selected theme.
 *
 * <p>Edits go to a working copy. Nothing is applied until "Save &amp; Close", which goes through
 * {@link ConfigManager#save} and therefore the same validation as a hand-edited file.
 */
public final class ChatConfigScreen extends Screen {
	/** Subtle translucent backing for the preview panel. */
	private static final int PANEL_BG = 0x90101010;
	/**
	 * Text colors must carry full alpha. GuiGraphicsExtractor.text skips any color whose alpha byte
	 * is 0, so a bare 0xRRGGBB value draws nothing.
	 */
	private static final int OPAQUE_TEXT = 0xFFFFFFFF;
	private static final int OPAQUE_LABEL = 0xFFAAAAAA;
	private static final int TITLE_Y = 10;
	private static final int TOP = 28;
	private static final int FOOTER_HEIGHT = 36;
	private static final int LIST_X = 8;
	/** Widest the list is allowed to be. */
	private static final int LIST_WIDTH = 260;
	/**
	 * Narrowest the list may shrink to. {@code AbstractSelectionList} fixes the row width at 220, so
	 * the list needs room beyond that for the scrollbar to stay inside it.
	 */
	private static final int MIN_LIST_WIDTH = 240;
	private static final int PANEL_GAP = 8;
	private static final int ITEM_HEIGHT = 24;
	private static final int BUTTON_WIDTH = 180;
	private static final int GAP = 16;
	private static final int FOOTER_BUTTON_WIDTH = 120;
	private static final int FOOTER_SPACING = 6;
	private static final int LINE_HEIGHT = 9;
	private static final int MAX_SCROLL_LINES = 10;

	private final Screen parent;
	private final List<String> themeIds = new ArrayList<>();
	private ChatConfig edited;

	public ChatConfigScreen(Screen parent) {
		super(Component.literal("Better Chat Formatting"));
		this.parent = parent;
		this.edited = copyOf(BetterChatFormattingClient.config().snapshot().config());
		themeIds.addAll(BetterChatFormattingClient.config().snapshot().themes().keySet());
		if (!themeIds.contains(edited.theme)) {
			themeIds.add(0, edited.theme);
		}
	}

	/** A deep copy made by a round trip through the config format, so the live config is never touched. */
	private static ChatConfig copyOf(ChatConfig source) {
		try {
			return ConfigIO.read(ConfigIO.write(source)).config();
		} catch (Exception e) {
			return new ChatConfig();
		}
	}

	/** Height of the list and the preview: from {@link #TOP} down to the footer. */
	private int bodyHeight() {
		return Math.max(0, height - FOOTER_HEIGHT - TOP);
	}

	/** List width: as wide as allowed, but shrinks on narrow windows so the preview still fits. */
	private int listWidth() {
		return Math.max(MIN_LIST_WIDTH, Math.min(LIST_WIDTH, width / 2 - LIST_X - PANEL_GAP));
	}

	private int previewX() {
		return Math.max(width / 2 + 6, LIST_X + listWidth() + PANEL_GAP);
	}

	/** Preview width: the right half, but never past the right edge of the window. */
	private int previewWidth() {
		return Math.max(0, Math.min(width / 2 - 18, width - 8 - previewX()));
	}

	@Override
	protected void init() {
		OptionList list = new OptionList(minecraft, listWidth(), bodyHeight(), TOP, ITEM_HEIGHT);
		list.updateSizeAndPosition(listWidth(), bodyHeight(), LIST_X, TOP);

		list.add(toggleRow("Theme colors", () -> edited.features.applyTheme, v -> edited.features.applyTheme = v));
		list.add(toggleRow("Name colors", () -> edited.features.nameColors, v -> edited.features.nameColors = v));
		list.add(toggleRow("Highlights", () -> edited.features.highlights, v -> edited.features.highlights = v));
		list.add(toggleRow("Message types", () -> edited.features.messageTypes, v -> edited.features.messageTypes = v));
		list.add(toggleRow("Markdown", () -> edited.features.markdown, v -> edited.features.markdown = v));
		list.add(toggleRow("Links", () -> edited.features.links, v -> edited.features.links = v));
		list.add(toggleRow("Timestamps", () -> edited.features.timestamps, v -> edited.features.timestamps = v));
		list.add(toggleRow("Collapse repeats", () -> edited.features.collapseRepeats, v -> edited.features.collapseRepeats = v));
		list.add(toggleRow("Chat search", () -> edited.features.search, v -> edited.features.search = v));
		list.add(toggleRow("Emoji picker", () -> edited.features.emojiPicker, v -> edited.features.emojiPicker = v));
		list.add(toggleRow("Chat layout", () -> edited.features.layout, v -> edited.features.layout = v));
		list.add(toggleRow("Accent bar", () -> edited.features.accentBar, v -> edited.features.accentBar = v));
		list.add(toggleRow("Clean separator", () -> edited.features.cleanSeparator, v -> edited.features.cleanSeparator = v));

		list.add(new TextRow("Theme and layout"));
		list.add(new ButtonRow(Button.builder(Component.literal("Theme: " + edited.theme), b -> {
			int index = themeIds.indexOf(edited.theme);
			edited.theme = themeIds.get((index + 1) % themeIds.size());
			b.setMessage(Component.literal("Theme: " + edited.theme));
		}).bounds(0, 0, BUTTON_WIDTH, 20).build()));
		list.add(new ButtonRow(Button.builder(Component.literal("Scroll lines: " + edited.layout.scrollLines), b -> {
			edited.layout.scrollLines = edited.layout.scrollLines >= MAX_SCROLL_LINES ? 1 : edited.layout.scrollLines + 1;
			b.setMessage(Component.literal("Scroll lines: " + edited.layout.scrollLines));
		}).bounds(0, 0, BUTTON_WIDTH, 20).build()));
		list.add(toggleRow("Own-name highlight", () -> edited.highlights.ownName, v -> edited.highlights.ownName = v));

		addRenderableWidget(list);

		// Footer buttons share the window width, so they stay on screen when it is narrow.
		int bottom = height - 28;
		int buttonWidth = Math.max(60, Math.min(FOOTER_BUTTON_WIDTH, (width - 2 * GAP - 2 * FOOTER_SPACING) / 3));
		int x = GAP;
		addRenderableWidget(Button.builder(Component.literal("Save & Close"), b -> {
			BetterChatFormattingClient.config().save(edited);
			minecraft.gui.setScreen(parent);
		}).bounds(x, bottom, buttonWidth, 20).build());
		x += buttonWidth + FOOTER_SPACING;
		addRenderableWidget(Button.builder(Component.literal("Reset to Defaults"), b -> {
			edited = new ChatConfig();
			rebuildWidgets();
		}).bounds(x, bottom, buttonWidth, 20).build());
		x += buttonWidth + FOOTER_SPACING;
		addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> minecraft.gui.setScreen(parent))
				.bounds(x, bottom, buttonWidth, 20).build());
	}

	/** A row with an on/off button. The button updates its own label, so the list keeps its scroll position. */
	private Row toggleRow(String label, BooleanSupplier value, Consumer<Boolean> setter) {
		Button button = Button.builder(Component.literal(label + ": " + onOff(value.getAsBoolean())), b -> {
			setter.accept(!value.getAsBoolean());
			b.setMessage(Component.literal(label + ": " + onOff(value.getAsBoolean())));
		}).bounds(0, 0, BUTTON_WIDTH, 20).build();
		return new ButtonRow(button);
	}

	private static String onOff(boolean value) {
		return value ? "On" : "Off";
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// Widgets (list, buttons) come from super. The title and preview are drawn on top, outside the list.
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, TITLE_Y, OPAQUE_TEXT);

		if (previewWidth() > 0) {
			renderPreview(graphics, previewX(), TOP, previewWidth(), bodyHeight());
		}
	}

	/** The theme the chat would use for the working copy. Theme colors off means the vanilla look. */
	private Theme previewTheme() {
		if (!edited.features.applyTheme) {
			return BuiltInThemes.byIdOrDefault("vanilla");
		}
		return ThemeLoader.resolve(BetterChatFormattingClient.config().snapshot().themes(), edited.theme);
	}

	/**
	 * Renders sample lines through the same core formatter as the live chat, using the working copy
	 * and the selected theme (background, padding, line spacing, shadow, text and timestamp colors).
	 */
	private void renderPreview(GuiGraphicsExtractor graphics, int x, int y, int panelWidth, int panelHeight) {
		Theme theme = previewTheme();
		graphics.fill(x, y, x + panelWidth, y + panelHeight, PANEL_BG);
		graphics.text(font, Component.literal("Live Preview"), x + 8, y + 6, OPAQUE_LABEL);
		graphics.enableScissor(x, y, x + panelWidth, y + panelHeight);

		String me = Minecraft.getInstance().getUser().getName();
		if (me == null || me.isBlank()) {
			me = "Player";
		}
		MessageFormatter.Options options = new MessageFormatter.Options(
				edited.features.markdown,
				edited.features.links,
				edited.features.highlights ? edited.highlights.toMatcher(me) : null);

		int bandLeft = x + 8;
		int bandRight = x + panelWidth - 8;
		int textX = bandLeft + theme.paddingX();
		int wrapWidth = Math.max(10, bandRight - textX - theme.paddingX());

		// One line per role, built the same way the chat builds them, so the preview matches the real chat.
		int stampColor = theme.timestampColor() >= 0 ? theme.timestampColor() : 0x808080;
		ThemeShades shades = theme.textColor() >= 0 ? ThemeShades.of(theme) : null;
		SenderColors.Paint paint = ChatTheme.paint(edited, shades, me);
		List<PreviewLine> lines = List.of(
				previewLine("[MVP+] Ranked_Pal: hey, how is everyone doing?", MessageType.PLAYER_CHAT, shades, paint, me, stampColor),
				previewLine("Quiet_Fox: anyone up for a game?", MessageType.PLAYER_CHAT, shades, paint, me, stampColor),
				previewLine(me + ": hey " + me + ", check this out", MessageType.PLAYER_CHAT, shades, paint, me, stampColor),
				previewLine("[Sam -> you] psst, meet at spawn", MessageType.WHISPER, shades, paint, me, stampColor),
				previewLine("Server: Sam joined the lobby!", MessageType.JOIN, shades, paint, me, stampColor));

		int lineY = y + 24;
		for (PreviewLine preview : lines) {
			Component line = preview.component();

			var rows = font.split(line, wrapWidth);
			int bandTop = lineY - theme.paddingY();
			int bandBottom = lineY + rows.size() * LINE_HEIGHT + theme.paddingY();
			if (theme.backgroundOpacity() > 0f) {
				graphics.fill(bandLeft, bandTop, bandRight, bandBottom, theme.backgroundArgb());
			}
			if (preview.accent() != -1) {
				// The same 2px left bar the chat draws (GuiMessageTag indicator), colored by category.
				graphics.fill(bandLeft - 3, bandTop, bandLeft - 1, bandBottom, 0xFF000000 | preview.accent());
			}
			for (int i = 0; i < rows.size(); i++) {
				graphics.text(font, rows.get(i), textX, lineY + i * LINE_HEIGHT, OPAQUE_TEXT, theme.textShadow());
			}
			lineY = bandBottom + 4 + theme.lineSpacing();
		}
		graphics.disableScissor();
	}

	/** Style for one body segment. {@code defaultColor} is the theme text color, or -1 for vanilla white. */
	/** One preview line and the accent-bar color it gets in the chat (-1 for none). */
	private record PreviewLine(Component component, int accent) {
	}

	/**
	 * Builds one preview line the way the chat does: the role color for its type, then the sender and
	 * mention paint, with the timestamp in front. The accent bar follows the same category rules.
	 */
	private PreviewLine previewLine(String text, MessageType type, ThemeShades shades, SenderColors.Paint paint,
			String me, int stampColor) {
		int baseColor = shades != null ? ChatTheme.lineColor(shades, type) : -1;
		Component body = SenderColors.apply(Component.literal(text), paint.withBase(baseColor));

		MutableComponent line = Component.literal("");
		if (edited.features.timestamps) {
			line.append(Component.literal("[12:00] ").withStyle(colorStyle(stampColor)));
		}
		line.append(body);

		int accent = -1;
		if (edited.features.accentBar && shades != null) {
			accent = ChatTheme.categoryColor(shades, type, SenderColors.mentions(text, me));
		}
		return new PreviewLine(line, accent);
	}

	/** A style with the given RGB color, or no color (vanilla white) when it is negative. */
	private static Style colorStyle(int color) {
		return color < 0 ? Style.EMPTY : Style.EMPTY.withColor(color);
	}

	private static Style styleOf(Segment segment, int defaultColor) {
		Style style = Style.EMPTY;
		if ((segment.flags() & StyleFlags.BOLD) != 0) {
			style = style.withBold(true);
		}
		if ((segment.flags() & StyleFlags.ITALIC) != 0) {
			style = style.withItalic(true);
		}
		if ((segment.flags() & StyleFlags.UNDERLINE) != 0) {
			style = style.withUnderlined(true);
		}
		if ((segment.flags() & StyleFlags.STRIKETHROUGH) != 0) {
			style = style.withStrikethrough(true);
		}
		if (defaultColor >= 0) {
			style = style.withColor(defaultColor);
		} else if (segment.hasColor()) {
			style = style.withColor(segment.color());
		}
		return style;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	/** The scrollable settings list. Rows are added with {@link #add}; the list handles clipping and scrolling. */
	private final class OptionList extends ContainerObjectSelectionList<Row> {
		OptionList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
			super(minecraft, width, height, y, itemHeight);
		}

		void add(Row row) {
			addEntry(row);
		}
	}

	/** One list row. Subclasses decide what it contains. */
	private abstract class Row extends ContainerObjectSelectionList.Entry<Row> {
		@Override
		public List<? extends GuiEventListener> children() {
			return List.of();
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of();
		}
	}

	/** A row holding one vanilla button. The list does not position children, so the row does. */
	private final class ButtonRow extends Row {
		private final AbstractWidget widget;

		ButtonRow(AbstractWidget widget) {
			this.widget = widget;
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of(widget);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of(widget);
		}

		@Override
		public void setX(int x) {
			super.setX(x);
			place();
		}

		@Override
		public void setY(int y) {
			super.setY(y);
			place();
		}

		private void place() {
			widget.setX(getContentX());
			widget.setY(getContentY());
			widget.setWidth(BUTTON_WIDTH);
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
			place();
			widget.extractRenderState(graphics, mouseX, mouseY, a);
		}
	}

	/** A section heading row. Not interactive. */
	private final class TextRow extends Row {
		private final Component label;

		TextRow(String text) {
			this.label = Component.literal(text);
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
			graphics.text(font, label, getContentX(), getContentY() + 6, OPAQUE_LABEL);
		}
	}
}
