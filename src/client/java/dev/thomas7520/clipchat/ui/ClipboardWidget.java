package dev.thomas7520.clipchat.ui;

import dev.thomas7520.clipchat.clipboard.model.ClipboardCapability;
import dev.thomas7520.clipchat.clipboard.model.ClipboardEntry;
import dev.thomas7520.clipchat.clipboard.model.ProviderId;
import dev.thomas7520.clipchat.clipboard.model.ProviderState;
import dev.thomas7520.clipchat.clipboard.provider.ClipboardProvider;
import dev.thomas7520.clipchat.config.ClipChatConfig;
import dev.thomas7520.clipchat.config.ColorSlot;
import dev.thomas7520.clipchat.history.MinecraftClipboardProvider;
import dev.thomas7520.clipchat.ui.model.WidgetGeometry;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The clipboard panel drawn inside the chat screen. The frame is built from filled rectangles;
 * the two glyph textures are white masks, tinted at draw time to the active theme.
 */
public final class ClipboardWidget {
	public static final int TITLE_HEIGHT = 12;

	private static final int TAB_HEIGHT = 12;
	private static final int ROW_HEIGHT = 11;
	private static final int PADDING = 3;
	private static final int ICON_SIZE = 8;
	private static final int ICON_TEXTURE_SIZE = 16;
	private static final int ICON_GAP = 2;
	private static final int GRIP_SIZE = 6;
	private static final int SCROLLBAR_WIDTH = 3;
	private static final int MIN_THUMB_HEIGHT = 8;

	private static final ResourceLocation PIN_ICON = ResourceLocation.fromNamespaceAndPath("clipchat", "textures/gui/pin.png");
	private static final ResourceLocation SETTINGS_ICON =
			ResourceLocation.fromNamespaceAndPath("clipchat", "textures/gui/settings.png");

	private static final Component TITLE = Component.translatable("clipchat.widget.title");
	private static final Component EMPTY = Component.translatable("clipchat.widget.empty");
	private static final Component TAB_MINECRAFT = Component.translatable("clipchat.tab.minecraft");
	private static final Component TAB_WINDOWS = Component.translatable("clipchat.tab.windows");

	private final MinecraftClipboardProvider history;
	private final ClipboardProvider windows;
	private final Supplier<ClipChatConfig> config;
	private final Consumer<String> onInsert;
	private final Runnable onOpenSettings;

	private ProviderId source = ProviderId.MINECRAFT;

	private WidgetGeometry geometry;
	private boolean geometryDirty;

	private int scroll;
	private int selected = -1;
	private boolean dragging;
	private int dragGrabX;
	private int dragGrabY;
	private boolean resizing;
	private int resizeGrabX;
	private int resizeGrabY;
	private boolean scrubbing;

	public ClipboardWidget(MinecraftClipboardProvider history, ClipboardProvider windows,
			Supplier<ClipChatConfig> config, WidgetGeometry geometry, Consumer<String> onInsert,
			Runnable onOpenSettings) {
		this.history = history;
		this.windows = windows;
		this.config = config;
		this.geometry = geometry;
		this.onInsert = onInsert;
		this.onOpenSettings = onOpenSettings;
	}

	/** Refreshes the Windows history when the chat screen opens, while that tab is selected. */
	public void onChatOpened() {
		if (tabsVisible() && source == ProviderId.WINDOWS) {
			windows.requestRefresh();
		}
	}

	public WidgetGeometry geometry() {
		return geometry;
	}

	public boolean consumeGeometryDirty() {
		boolean was = geometryDirty;
		geometryDirty = false;
		return was;
	}

	private int color(ColorSlot slot) {
		return config.get().color(slot);
	}

	private boolean hidden() {
		return !config.get().widgetVisible();
	}

	/** True only when a second source exists; with one source the tab strip is not drawn. */
	private boolean tabsVisible() {
		return windows != null && config.get().windowsHistoryEnabled();
	}

	private ClipboardProvider active() {
		return tabsVisible() && source == ProviderId.WINDOWS ? windows : history;
	}

	private int listTop(int y) {
		return y + TITLE_HEIGHT + (tabsVisible() ? TAB_HEIGHT : 0);
	}

	/** True when the colour is light enough for Minecraft's drop shadow to stay legible under it. */
	private static boolean shadowSuits(int argb) {
		int red = (argb >> 16) & 0xFF;
		int green = (argb >> 8) & 0xFF;
		int blue = argb & 0xFF;
		return (red * 2126 + green * 7152 + blue * 722) / 10000 > 128;
	}

	public void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight,
			int mouseX, int mouseY) {
		if (hidden()) {
			return;
		}

		int width = geometry.width();
		int visibleHeight = geometry.visibleHeight(TITLE_HEIGHT);
		int x = geometry.resolveX(screenWidth);
		int y = geometry.resolveY(screenHeight, visibleHeight);

		graphics.fill(x, y, x + width, y + visibleHeight, color(ColorSlot.BACKGROUND));
		graphics.fill(x, y, x + width, y + TITLE_HEIGHT, color(ColorSlot.TITLE_BAR));
		graphics.renderOutline(x, y, width, visibleHeight, color(ColorSlot.BORDER));

		int titleColor = color(ColorSlot.TEXT);
		graphics.drawString(font, TITLE, x + PADDING, y + 2, titleColor, shadowSuits(titleColor));

		drawCollapseButton(graphics, collapseButtonX(x, width), y + 2);
		icon(graphics, SETTINGS_ICON, settingsButtonX(x, width), y + 2, titleColor);

		if (geometry.collapsed()) {
			return;
		}

		if (tabsVisible()) {
			drawTabs(graphics, font, x, y + TITLE_HEIGHT, width);
		}

		ClipboardProvider provider = active();
		List<ClipboardEntry> entries = provider.snapshot();
		int listTop = listTop(y);
		int listBottom = y + visibleHeight - 1;

		if (entries.isEmpty()) {
			graphics.enableScissor(x + 1, listTop, x + width - 1, listBottom);
			graphics.drawWordWrap(font, message(provider.state()), x + PADDING, listTop + PADDING,
					width - PADDING * 2, color(ColorSlot.TEXT_DIM));
			graphics.disableScissor();
			drawResizeGrip(graphics, x + width, y + visibleHeight);
			return;
		}

		int listHeight = listBottom - listTop;
		clampScroll(entries.size(), listHeight);

		int contentRight = contentRight(x, width, entries.size(), listHeight);
		int rowY = listTop - scroll;
		int hovered = rowAt(mouseX, mouseY, x, y, entries.size());

		// Hover takes priority over the keyboard cursor, so only one row is ever highlighted.
		selected = Math.min(selected, entries.size() - 1);
		int highlighted = hovered >= 0 ? hovered : selected;

		int firstUnpinned = firstUnpinned(entries);

		graphics.enableScissor(x + 1, listTop, contentRight, listBottom);

		for (int index = 0; index < entries.size(); index++) {
			if (rowY + ROW_HEIGHT > listTop && rowY < listBottom) {
				drawRow(graphics, font, entries.get(index), x, rowY, contentRight, index == highlighted);

				// Separator between pinned and unpinned rows, drawn over the row's own background.
				if (index == firstUnpinned && index > 0) {
					graphics.fill(x + 1, rowY, contentRight, rowY + 1, color(ColorSlot.BORDER));
				}
			}

			rowY += ROW_HEIGHT;
		}

		graphics.disableScissor();
		drawScrollbar(graphics, x, width, listTop, listBottom, entries.size());
		drawResizeGrip(graphics, x + width, y + visibleHeight);
	}

	/** The line shown in place of the list for a state that has no entries to display. */
	private static Component message(ProviderState state) {
		return switch (state) {
			case LOADING -> Component.translatable("clipchat.state.loading");
			case DISABLED_BY_OS -> Component.translatable("clipchat.state.disabled_by_os");
			case ACCESS_DENIED -> Component.translatable("clipchat.state.access_denied");
			case UNSUPPORTED_OS -> Component.translatable("clipchat.state.unsupported_os");
			case ERROR -> Component.translatable("clipchat.state.error");
			default -> EMPTY;
		};
	}

	private void drawTabs(GuiGraphics graphics, Font font, int x, int top, int width) {
		int half = width / 2;
		drawTab(graphics, font, TAB_MINECRAFT, x + 1, top, half - 1, source == ProviderId.MINECRAFT);
		drawTab(graphics, font, TAB_WINDOWS, x + half, top, width - half - 1, source == ProviderId.WINDOWS);
		graphics.fill(x + 1, top + TAB_HEIGHT - 1, x + width - 1, top + TAB_HEIGHT, color(ColorSlot.BORDER));
	}

	private void drawTab(GuiGraphics graphics, Font font, Component label, int tabX, int top,
			int tabWidth, boolean selected) {
		if (selected) {
			graphics.fill(tabX, top, tabX + tabWidth, top + TAB_HEIGHT - 1, color(ColorSlot.ROW_HOVER));
		}

		int labelColor = selected ? color(ColorSlot.TEXT) : color(ColorSlot.TEXT_DIM);
		graphics.drawString(font, label, tabX + (tabWidth - font.width(label)) / 2, top + 2,
				labelColor, shadowSuits(labelColor));
	}

	private void drawRow(GuiGraphics graphics, Font font, ClipboardEntry entry,
			int x, int rowY, int contentRight, boolean hovered) {
		if (hovered) {
			graphics.fill(x + 1, rowY, contentRight, rowY + ROW_HEIGHT, color(ColorSlot.ROW_HOVER));
		}

		int textX = x + PADDING;

		if (entry.pinned()) {
			graphics.fill(x + 1, rowY + 1, x + 3, rowY + ROW_HEIGHT - 1, color(ColorSlot.PIN));
			textX += 3;
		}

		// Only the hovered row reserves space for the action icons; the rest use the full width.
		int textRight = contentRight - PADDING - (hovered ? actionsWidth() + ICON_GAP : 0);
		int textColor = color(ColorSlot.TEXT);
		String text = flatten(entry.text());

		if (hovered && font.width(text) > textRight - textX) {
			marquee(graphics, font, text, textX, textRight, rowY, textColor);
		} else {
			graphics.drawString(font, font.plainSubstrByWidth(text, textRight - textX), textX, rowY + 2,
					textColor, shadowSuits(textColor));
		}

		if (hovered) {
			if (canPin()) {
				icon(graphics, PIN_ICON, pinButtonX(contentRight), rowY + 2,
						entry.pinned() ? color(ColorSlot.PIN) : color(ColorSlot.TEXT_DIM));
			}

			drawDeleteIcon(graphics, deleteButtonX(contentRight), rowY + 2);
		}
	}

	/** Sweeps text too wide for its row back and forth, pausing at each end. */
	private void marquee(GuiGraphics graphics, Font font, String text,
			int left, int right, int rowY, int textColor) {
		int overflow = font.width(text) - (right - left);
		double seconds = System.nanoTime() / 1_000_000_000.0;
		double period = Math.max(overflow * 0.5, 3.0);
		double phase = Math.sin(Math.PI / 2 * Math.cos(Math.PI * 2 * seconds / period)) / 2.0 + 0.5;

		graphics.enableScissor(left, rowY, right, rowY + ROW_HEIGHT);
		graphics.drawString(font, text, left - (int) Math.round(phase * overflow), rowY + 2,
				textColor, shadowSuits(textColor));
		graphics.disableScissor();
	}

	/** Collapses line breaks and tabs to spaces so a preview fits on the row's single line. */
	private static String flatten(String text) {
		return text.replace('\n', ' ').replace('\t', ' ');
	}

	/** Draws a glyph. The textures are white masks, so {@code tint} becomes the drawn colour. */
	/** Draws a glyph. The textures are white masks, so {@code tint} becomes the drawn colour. */
	/** Draws the full white-mask texture scaled to ICON_SIZE.
	 *  {@code tint} uses 0xAARRGGBB.
	 */
	private static void icon(
			GuiGraphics graphics,
			ResourceLocation texture,
			int x,
			int y,
			int tint
	) {
		float red   = ((tint >>> 16) & 0xFF) / 255.0F;
		float green = ((tint >>> 8)  & 0xFF) / 255.0F;
		float blue  = (tint & 0xFF) / 255.0F;
		float alpha = ((tint >>> 24) & 0xFF) / 255.0F;

		graphics.setColor(red, green, blue, alpha);
		graphics.blit(texture, x, y, ICON_SIZE, ICON_SIZE, 0.0F, 0.0F, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE);
		graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

	}

	private void drawCollapseButton(GuiGraphics graphics, int buttonX, int buttonY) {
		int tint = color(ColorSlot.TEXT);
		// Two pixels thick, so the bar centres exactly in an even-sized box.
		int middleY = buttonY + ICON_SIZE / 2 - 1;
		graphics.fill(buttonX, middleY, buttonX + ICON_SIZE, middleY + 2, tint);

		if (geometry.collapsed()) {
			int middleX = buttonX + ICON_SIZE / 2 - 1;
			graphics.fill(middleX, buttonY, middleX + 2, buttonY + ICON_SIZE, tint);
		}
	}

	private void drawDeleteIcon(GuiGraphics graphics, int iconX, int iconY) {
		int tint = color(ColorSlot.TEXT_DIM);

		for (int step = 0; step < 6; step++) {
			graphics.fill(iconX + 1 + step, iconY + 1 + step, iconX + 2 + step, iconY + 2 + step, tint);
			graphics.fill(iconX + 6 - step, iconY + 1 + step, iconX + 7 - step, iconY + 2 + step, tint);
		}
	}

	private void drawScrollbar(GuiGraphics graphics, int x, int width,
			int listTop, int listBottom, int count) {
		int listHeight = listBottom - listTop;

		if (!overflows(count, listHeight)) {
			return;
		}

		int trackX = x + width - 1 - SCROLLBAR_WIDTH;
		int thumbHeight = thumbHeight(count, listHeight);
		int travel = listHeight - thumbHeight;
		int thumbY = listTop + (travel <= 0 ? 0 : travel * scroll / maxScroll(count, listHeight));

		graphics.fill(trackX, listTop, trackX + SCROLLBAR_WIDTH, listBottom, color(ColorSlot.BORDER));
		graphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, color(ColorSlot.TEXT_DIM));
	}

	private void drawResizeGrip(GuiGraphics graphics, int right, int bottom) {
		int tint = color(ColorSlot.TEXT_DIM);

		for (int step = 1; step <= 3; step++) {
			int offset = step * 2;
			graphics.fill(right - offset, bottom - 2, right - offset + 1, bottom - 1, tint);
			graphics.fill(right - 2, bottom - offset, right - 1, bottom - offset + 1, tint);
		}
	}

	private static boolean overflows(int count, int listHeight) {
		return count * ROW_HEIGHT > listHeight;
	}

	/**
	 * Index of the first unpinned entry, or -1 when every entry is pinned. Entries are ordered
	 * pinned-first, so this is where the separator line is drawn.
	 */
	private static int firstUnpinned(List<ClipboardEntry> entries) {
		for (int index = 0; index < entries.size(); index++) {
			if (!entries.get(index).pinned()) {
				return index;
			}
		}

		return -1;
	}

	private static int maxScroll(int count, int listHeight) {
		return Math.max(0, count * ROW_HEIGHT - listHeight);
	}

	private static int thumbHeight(int count, int listHeight) {
		return Math.clamp((long) listHeight * listHeight / (count * ROW_HEIGHT), MIN_THUMB_HEIGHT, listHeight);
	}

	/** The right edge available to rows, reduced by the scrollbar only when the list overflows. */
	private static int contentRight(int x, int width, int count, int listHeight) {
		return x + width - 1 - (overflows(count, listHeight) ? SCROLLBAR_WIDTH : 0);
	}

	private static int collapseButtonX(int x, int width) {
		return x + width - PADDING - ICON_SIZE;
	}

	private static int settingsButtonX(int x, int width) {
		return collapseButtonX(x, width) - ICON_GAP - ICON_SIZE;
	}

	private boolean canPin() {
		return active().capabilities().has(ClipboardCapability.PIN);
	}

	private int actionsWidth() {
		return canPin() ? ICON_SIZE * 2 + ICON_GAP : ICON_SIZE;
	}

	private static int deleteButtonX(int contentRight) {
		return contentRight - PADDING - ICON_SIZE;
	}

	private static int pinButtonX(int contentRight) {
		return deleteButtonX(contentRight) - ICON_GAP - ICON_SIZE;
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
		if (hidden()) {
			return false;
		}

		int width = geometry.width();
		int visibleHeight = geometry.visibleHeight(TITLE_HEIGHT);
		int x = geometry.resolveX(screenWidth);
		int y = geometry.resolveY(screenHeight, visibleHeight);

		if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + visibleHeight) {
			return false;
		}

		if (button == 0 && !geometry.collapsed()
				&& mouseX >= x + width - GRIP_SIZE && mouseY >= y + visibleHeight - GRIP_SIZE) {
			resizing = true;
			resizeGrabX = (int) mouseX - (x + width);
			resizeGrabY = (int) mouseY - (y + visibleHeight);
			return true;
		}

		if (mouseY < y + TITLE_HEIGHT) {
			if (button == 0 && mouseX >= collapseButtonX(x, width)) {
				geometry = geometry.withCollapsed(!geometry.collapsed());
				geometryDirty = true;
			} else if (button == 0 && mouseX >= settingsButtonX(x, width)) {
				onOpenSettings.run();
			} else if (button == 0) {
				dragging = true;
				dragGrabX = (int) mouseX - x;
				dragGrabY = (int) mouseY - y;
			}

			return true;
		}

		if (tabsVisible() && mouseY < y + TITLE_HEIGHT + TAB_HEIGHT) {
			if (button == 0) {
				selectSource(mouseX < x + width / 2.0 ? ProviderId.MINECRAFT : ProviderId.WINDOWS);
			}

			return true;
		}

		List<ClipboardEntry> entries = active().snapshot();
		int listTop = listTop(y);
		int listHeight = y + visibleHeight - 1 - listTop;

		if (button == 0 && overflows(entries.size(), listHeight)
				&& mouseX >= contentRight(x, width, entries.size(), listHeight)) {
			scrubbing = true;
			scrubTo(mouseY, listTop, listHeight, entries.size());
			return true;
		}

		int index = rowAt(mouseX, mouseY, x, y, entries.size());

		if (index < 0) {
			return true;
		}

		ClipboardEntry entry = entries.get(index);
		int contentRight = contentRight(x, width, entries.size(), listHeight);

		if (button == 0 && mouseX >= deleteButtonX(contentRight)) {
			active().delete(entry.id());
		} else if (button == 0 && canPin() && mouseX >= pinButtonX(contentRight)) {
			togglePin(entry);
		} else if (button == 0) {
			onInsert.accept(entry.text());
		} else if (button == 1 && canPin()) {
			togglePin(entry);
		}

		return true;
	}

	/** Switches tab, resetting scroll and selection and refreshing the newly active provider. */
	private void selectSource(ProviderId next) {
		if (source == next) {
			return;
		}

		source = next;
		scroll = 0;
		selected = -1;
		active().requestRefresh();
	}

	private void togglePin(ClipboardEntry entry) {
		if (entry.pinned()) {
			history.unpin(entry.id());
		} else {
			history.pin(entry.id());
		}
	}

	/** Scrolls so the thumb centres on the cursor, used for both track clicks and thumb drags. */
	private void scrubTo(double mouseY, int listTop, int listHeight, int count) {
		int thumbHeight = thumbHeight(count, listHeight);
		int travel = listHeight - thumbHeight;

		if (travel <= 0) {
			scroll = 0;
			return;
		}

		double thumbTop = mouseY - listTop - thumbHeight / 2.0;
		scroll = (int) Math.round(thumbTop * maxScroll(count, listHeight) / travel);
		clampScroll(count, listHeight);
	}

	public boolean mouseDragged(double mouseX, double mouseY, int screenWidth, int screenHeight) {
		if (resizing) {
			int x = geometry.resolveX(screenWidth);
			int y = geometry.resolveY(screenHeight, geometry.height());
			geometry = geometry.resizedTo((int) mouseX - resizeGrabX - x, (int) mouseY - resizeGrabY - y,
					screenWidth, screenHeight);
			geometryDirty = true;
			return true;
		}

		if (scrubbing) {
			int visibleHeight = geometry.visibleHeight(TITLE_HEIGHT);
			int y = geometry.resolveY(screenHeight, visibleHeight);
			int listTop = listTop(y);
			scrubTo(mouseY, listTop, y + visibleHeight - 1 - listTop, active().snapshot().size());
			return true;
		}

		if (!dragging) {
			return false;
		}

		int visibleHeight = geometry.visibleHeight(TITLE_HEIGHT);
		geometry = geometry.movedTo((int) mouseX - dragGrabX, (int) mouseY - dragGrabY,
				screenWidth, screenHeight, visibleHeight);
		geometryDirty = true;
		return true;
	}

	public boolean mouseReleased() {
		if (!dragging && !resizing && !scrubbing) {
			return false;
		}

		dragging = false;
		resizing = false;
		scrubbing = false;
		return true;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double amount, int screenWidth, int screenHeight) {
		if (hidden() || geometry.collapsed()) {
			return false;
		}

		int width = geometry.width();
		int visibleHeight = geometry.visibleHeight(TITLE_HEIGHT);
		int x = geometry.resolveX(screenWidth);
		int y = geometry.resolveY(screenHeight, visibleHeight);

		if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + visibleHeight) {
			return false;
		}

		scroll -= (int) (amount * ROW_HEIGHT);
		clampScroll(active().snapshot().size(), y + visibleHeight - 1 - listTop(y));
		return true;
	}

	/**
	 * Keyboard equivalents for every mouse action. All require Control: the chat box keeps focus
	 * while the panel is open, and unmodified keys belong to it.
	 */
	public boolean keyPressed(int keyCode, int modifiers, int screenHeight) {
		if (hidden() || geometry.collapsed() || (modifiers & InputConstants.MOD_CONTROL) == 0) {
			return false;
		}

		if (tabsVisible() && (keyCode == InputConstants.KEY_LEFT || keyCode == InputConstants.KEY_RIGHT)) {
			selectSource(keyCode == InputConstants.KEY_LEFT ? ProviderId.MINECRAFT : ProviderId.WINDOWS);
			return true;
		}

		List<ClipboardEntry> entries = active().snapshot();

		if (entries.isEmpty()) {
			return false;
		}

		if (keyCode == InputConstants.KEY_UP || keyCode == InputConstants.KEY_DOWN) {
			selected = Math.clamp(selected + (keyCode == InputConstants.KEY_DOWN ? 1 : -1), 0, entries.size() - 1);
			scrollIntoView(screenHeight, entries.size());
			return true;
		}

		if (selected < 0 || selected >= entries.size()) {
			return false;
		}

		ClipboardEntry entry = entries.get(selected);

		if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
			onInsert.accept(entry.text());
		} else if (keyCode == InputConstants.KEY_DELETE) {
			active().delete(entry.id());
		} else if (keyCode == InputConstants.KEY_P && canPin()) {
			togglePin(entry);
		} else {
			return false;
		}

		return true;
	}

	private void scrollIntoView(int screenHeight, int count) {
		int visibleHeight = geometry.visibleHeight(TITLE_HEIGHT);
		int y = geometry.resolveY(screenHeight, visibleHeight);
		int listHeight = y + visibleHeight - 1 - listTop(y);
		int rowTop = selected * ROW_HEIGHT;
		int highest = rowTop + ROW_HEIGHT - listHeight;

		// When the panel is shorter than one row the bounds invert; the row top takes precedence.
		scroll = highest > rowTop ? rowTop : Math.clamp(scroll, highest, rowTop);
		clampScroll(count, listHeight);
	}

	private int rowAt(double mouseX, double mouseY, int x, int y, int count) {
		int width = geometry.width();
		int visibleHeight = geometry.visibleHeight(TITLE_HEIGHT);
		int listTop = listTop(y);
		int listBottom = y + visibleHeight - 1;

		if (geometry.collapsed() || mouseX < x + 1 || mouseY < listTop || mouseY >= listBottom
				|| mouseX >= contentRight(x, width, count, listBottom - listTop)) {
			return -1;
		}

		int index = (int) ((mouseY - listTop + scroll) / ROW_HEIGHT);
		return index >= 0 && index < count ? index : -1;
	}

	private void clampScroll(int count, int listHeight) {
		scroll = Math.clamp(scroll, 0, maxScroll(count, listHeight));
	}
}
