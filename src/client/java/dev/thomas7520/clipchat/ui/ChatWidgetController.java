package dev.thomas7520.clipchat.ui;

import dev.thomas7520.clipchat.clipboard.model.ClipboardSource;
import dev.thomas7520.clipchat.clipboard.provider.ClipboardProvider;
import dev.thomas7520.clipchat.config.ClipChatConfig;
import dev.thomas7520.clipchat.client.mixin.ScreenInsertAccessor;
import dev.thomas7520.clipchat.history.MinecraftClipboardProvider;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;

import java.lang.ref.WeakReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Attaches the widget to the chat screen and tracks which screen is open. The reference to the
 * current screen is weak, so a discarded screen stays collectable.
 */
public final class ChatWidgetController {
	private final UiStateStore uiStore;
	private final ClipboardWidget widget;

	private WeakReference<Screen> currentScreen = new WeakReference<>(null);

	public ChatWidgetController(MinecraftClipboardProvider history, ClipboardProvider windows,
			UiStateStore uiStore, Supplier<ClipChatConfig> config, Consumer<Screen> openSettings) {
		this.uiStore = uiStore;
		this.widget = new ClipboardWidget(history, windows, config, uiStore.load(), this::insertIntoChat,
				() -> openSettings.accept(currentScreen.get()));
	}

	public ClipboardSource currentSource() {
		return ScreenSourceResolver.sourceFor(currentScreen.get());
	}

	public void register() {
		ScreenEvents.AFTER_INIT.register(this::onScreenInit);
	}

	public void flush() {
		if (widget.consumeGeometryDirty()) {
			uiStore.save(widget.geometry());
		}
	}

	private void onScreenInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
		currentScreen = new WeakReference<>(screen);

		ScreenEvents.remove(screen).register(removed -> {
			if (currentScreen.get() == removed) {
				currentScreen.clear();
			}

			flush();
		});

		if (!(screen instanceof ChatScreen)) {
			return;
		}

		widget.onChatOpened();

		ScreenEvents.afterExtract(screen).register((rendered, graphics, mouseX, mouseY, tickDelta) -> {
			widget.mouseDragged(mouseX, mouseY, rendered.width, rendered.height);
			widget.render(graphics, client.font, rendered.width, rendered.height, mouseX, mouseY);
		});

		ScreenMouseEvents.allowMouseClick(screen).register((clicked, event) ->
				!widget.mouseClicked(event.x(), event.y(), event.button(), clicked.width, clicked.height));

		ScreenMouseEvents.allowMouseRelease(screen).register((released, event) ->
				!widget.mouseReleased());

		ScreenMouseEvents.allowMouseScroll(screen).register((scrolled, mouseX, mouseY, horizontal, vertical) ->
				!widget.mouseScrolled(mouseX, mouseY, vertical, scrolled.width, scrolled.height));

		ScreenKeyboardEvents.allowKeyPress(screen).register((pressed, event) ->
				!widget.keyPressed(event.key(), event.modifiers(), pressed.height));
	}

	private void insertIntoChat(String text) {
		if (currentScreen.get() instanceof ChatScreen chat) {
			((ScreenInsertAccessor) chat).clipchat$insertText(text.replace('\n', ' '), false);
		}
	}
}
