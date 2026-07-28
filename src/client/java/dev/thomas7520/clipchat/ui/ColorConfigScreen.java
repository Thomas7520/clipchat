package dev.thomas7520.clipchat.ui;

import dev.thomas7520.clipchat.config.ClipChatConfig;
import dev.thomas7520.clipchat.config.ColorSlot;
import dev.thomas7520.clipchat.config.ConfigStore;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Editor for per-slot colour overrides. A blank field clears the override and falls back to the
 * theme; it does not mean transparent.
 */
public class ColorConfigScreen extends Screen {
	private static final int ROW_WIDTH = 240;
	private static final int BOX_WIDTH = 80;
	private static final int ROW_HEIGHT = 22;
	private static final int SWATCH_SIZE = 11;
	private static final int LABEL_COLOR = 0xFFA0A0A0;

	private final Screen parent;
	private final Consumer<ClipChatConfig> onApply;
	private final Map<ColorSlot, EditBox> boxes = new EnumMap<>(ColorSlot.class);

	private ClipChatConfig working;

	public ColorConfigScreen(Screen parent, ClipChatConfig config, Consumer<ClipChatConfig> onApply) {
		super(Component.translatable("clipchat.config.colors"));
		this.parent = parent;
		this.working = config;
		this.onApply = onApply;
	}

	@Override
	protected void init() {
		boxes.clear();

		int boxX = left() + ROW_WIDTH - BOX_WIDTH;

		for (ColorSlot slot : ColorSlot.values()) {
			EditBox box = new EditBox(font, boxX, rowY(slot), BOX_WIDTH, 18, Component.empty());
			box.setMaxLength(9);
			box.setValue(working.isOverridden(slot) ? ConfigStore.formatArgb(working.color(slot)) : "");
			box.setHint(Component.literal(ConfigStore.formatArgb(working.theme().color(slot))));

			boxes.put(slot, addRenderableWidget(box));
		}

		addRenderableWidget(Button.builder(Component.translatable("clipchat.config.reset"), button -> resetAll())
				.bounds(width / 2 - 102, height - 28, 100, 20)
				.build());

		addRenderableWidget(Button.builder(Component.translatable("clipchat.config.done"), button -> onClose())
				.bounds(width / 2 + 2, height - 28, 100, 20)
				.build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
		super.extractRenderState(graphics, mouseX, mouseY, tickDelta);

		int left = left();
		graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);

		for (ColorSlot slot : ColorSlot.values()) {
			int y = rowY(slot);
			graphics.fill(left, y + 4, left + SWATCH_SIZE, y + 4 + SWATCH_SIZE, working.color(slot));
			graphics.text(font, Component.translatable(slot.translationKey()), left + SWATCH_SIZE + 5, y + 5,
					LABEL_COLOR);
		}
	}

	private int left() {
		return width / 2 - ROW_WIDTH / 2;
	}

	@Override
	public void onClose() {
		applyBoxes();
		onApply.accept(working);
		minecraft.setScreenAndShow(parent);
	}

	private void resetAll() {
		working = working.withoutOverrides();
		rebuildWidgets();
	}

	private void applyBoxes() {
		for (Map.Entry<ColorSlot, EditBox> entry : boxes.entrySet()) {
			String raw = entry.getValue().getValue().trim();

			if (raw.isEmpty()) {
				working = working.withOverride(entry.getKey(), null);
				continue;
			}

			Integer parsed = ConfigStore.parseArgb(raw);

			if (parsed != null) {
				working = working.withOverride(entry.getKey(), parsed);
			}
		}
	}

	private int rowY(ColorSlot slot) {
		return 34 + slot.ordinal() * ROW_HEIGHT;
	}
}
