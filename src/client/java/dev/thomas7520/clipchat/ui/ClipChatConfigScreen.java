package dev.thomas7520.clipchat.ui;

import dev.thomas7520.clipchat.config.ClipChatConfig;
import dev.thomas7520.clipchat.config.ConfigManager;
import dev.thomas7520.clipchat.config.ThemePreset;
import dev.thomas7520.clipchat.windows.WindowsClipboardProvider;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ClipChatConfigScreen extends Screen {
	private static final int ROW_WIDTH = 240;
	private static final int ROW_HEIGHT = 22;
	private static final int ROW_COUNT = 8;
	private static final int BOX_WIDTH = 60;
	private static final int LABEL_COLOR = 0xFFA0A0A0;
	private static final int TOP_MARGIN = 32;
	private static final int BOTTOM_BAR = 34;
	private static final int CONTENT_HEIGHT = (ROW_COUNT - 1) * ROW_HEIGHT + 20;

	private final Screen parent;
	private final ConfigManager configManager;

	private int top;
	private ClipChatConfig working;
	private EditBox unpinnedBox;
	private EditBox pinnedBox;
	private EditBox lengthBox;

	public ClipChatConfigScreen(Screen parent, ConfigManager configManager) {
		super(Component.translatable("clipchat.config.title"));
		this.parent = parent;
		this.configManager = configManager;
		this.working = configManager.current();
	}

	@Override
	protected void init() {
		int left = left();

		// Rows are centred in the space between the title and the button bar, never below it.
		top = TOP_MARGIN + Math.max(0, (height - BOTTOM_BAR - TOP_MARGIN - CONTENT_HEIGHT) / 2);

		addRenderableWidget(CycleButton
				.builder((ThemePreset theme) -> Component.translatable(theme.translationKey()))
				.withInitialValue(working.theme())
				.withValues(ThemePreset.values())
				.withTooltip(button -> Tooltip.create(Component.translatable("clipchat.config.theme.tooltip")))
				.create(left, rowY(0), ROW_WIDTH, 20, Component.translatable("clipchat.config.theme"),
						(button, value) -> working = working.withTheme(value)));

		addRenderableWidget(CycleButton.onOffBuilder(working.widgetVisible())
				.withTooltip(button -> Tooltip.create(Component.translatable("clipchat.config.visible.tooltip")))
				.create(left, rowY(1), ROW_WIDTH, 20, Component.translatable("clipchat.config.visible"),
						(button, value) -> working = working.withWidgetVisible(value)));

		addRenderableWidget(CycleButton.onOffBuilder(working.captureEnabled())
				.withTooltip(button -> Tooltip.create(Component.translatable("clipchat.config.capture.tooltip")))
				.create(left, rowY(2), ROW_WIDTH, 20, Component.translatable("clipchat.config.capture"),
						(button, value) -> working = working.withCaptureEnabled(value)));

		CycleButton<Boolean> windows = addRenderableWidget(CycleButton
				.onOffBuilder(working.windowsHistoryEnabled())
				.withTooltip(button -> Tooltip.create(Component.translatable(WindowsClipboardProvider.osSupported()
						? "clipchat.config.windows.tooltip"
						: "clipchat.config.windows.unavailable")))
				.create(left, rowY(3), ROW_WIDTH, 20, Component.translatable("clipchat.config.windows"),
						(button, value) -> working = working.withWindowsHistoryEnabled(value)));

		// Off Windows the row stays visible but disabled, keeping the layout stable.
		windows.active = WindowsClipboardProvider.osSupported();

		unpinnedBox = numberBox(4, working.maxUnpinned(), "clipchat.config.max_unpinned.tooltip");
		pinnedBox = numberBox(5, working.maxPinned(), "clipchat.config.max_pinned.tooltip");
		lengthBox = numberBox(6, working.maxEntryLength(), "clipchat.config.max_length.tooltip");

		addRenderableWidget(Button.builder(Component.translatable("clipchat.config.colors"),
						button -> {
							captureBoxes();
							minecraft.setScreen(
									new ColorConfigScreen(this, working, updated -> working = updated));
						})
				.bounds(left, rowY(7), ROW_WIDTH, 20)
				.build());

		addRenderableWidget(Button.builder(Component.translatable("clipchat.config.save"), button -> applyAndClose())
				.bounds(width / 2 - 102, height - 28, 100, 20)
				.build());

		addRenderableWidget(Button.builder(Component.translatable("clipchat.config.cancel"), button -> onClose())
				.bounds(width / 2 + 2, height - 28, 100, 20)
				.build());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float tickDelta) {
		super.render(graphics, mouseX, mouseY, tickDelta);

		graphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFFFF);

		label(graphics, "clipchat.config.max_unpinned", 4);
		label(graphics, "clipchat.config.max_pinned", 5);
		label(graphics, "clipchat.config.max_length", 6);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	/** Draws a row label at the left edge, with the matching field right-aligned against it. */
	private void label(GuiGraphics graphics, String key, int row) {
		graphics.drawString(font, Component.translatable(key), left(), rowY(row) + 6, LABEL_COLOR);
	}

	private EditBox numberBox(int row, int value, String tooltipKey) {
		EditBox box = new EditBox(font, left() + ROW_WIDTH - BOX_WIDTH, rowY(row), BOX_WIDTH, 18, Component.empty());
		box.setMaxLength(6);
		box.setValue(Integer.toString(value));
		box.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
		return addRenderableWidget(box);
	}

	private int left() {
		return width / 2 - ROW_WIDTH / 2;
	}

	private int rowY(int row) {
		return top + row * ROW_HEIGHT;
	}

	private void captureBoxes() {
		working = working.withLimits(
				parseInt(unpinnedBox, working.maxUnpinned()),
				parseInt(pinnedBox, working.maxPinned()),
				parseInt(lengthBox, working.maxEntryLength()));
	}

	private void applyAndClose() {
		captureBoxes();
		configManager.update(working);
		onClose();
	}

	private static int parseInt(EditBox box, int fallback) {
		try {
			return Integer.parseInt(box.getValue().trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
