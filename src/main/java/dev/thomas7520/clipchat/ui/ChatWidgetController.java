package dev.thomas7520.clipchat.ui;

import dev.thomas7520.clipchat.clipboard.model.ClipboardSource;
import dev.thomas7520.clipchat.clipboard.provider.ClipboardProvider;
import dev.thomas7520.clipchat.config.ClipChatConfig;
import dev.thomas7520.clipchat.history.MinecraftClipboardProvider;
import dev.thomas7520.clipchat.client.mixin.ChatScreenInvoker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

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
		NeoForge.EVENT_BUS.addListener(this::onScreenInit);
		NeoForge.EVENT_BUS.addListener(this::onScreenRender);
		NeoForge.EVENT_BUS.addListener(this::onMousePressed);
		NeoForge.EVENT_BUS.addListener(this::onMouseDragged);
		NeoForge.EVENT_BUS.addListener(this::onMouseReleased);
		NeoForge.EVENT_BUS.addListener(this::onMouseScrolled);
		NeoForge.EVENT_BUS.addListener(this::onKeyPressed);
		NeoForge.EVENT_BUS.addListener(this::onScreenClosing);
	}

	public void flush() {
		if (widget.consumeGeometryDirty()) {
			uiStore.save(widget.geometry());
		}
	}

	private void onScreenInit(ScreenEvent.Init.Post event) {
		Screen screen = event.getScreen();
		currentScreen = new WeakReference<>(screen);

		if (screen instanceof ChatScreen) {
			widget.onChatOpened();
		}
	}

	private void onScreenRender(ScreenEvent.Render.Post event) {
		if (!(event.getScreen() instanceof ChatScreen screen)) {
			return;
		}

		widget.extract(event.getGuiGraphics(), Minecraft.getInstance().font, screen.width, screen.height,
				event.getMouseX(), event.getMouseY());
	}

	private void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
		if (event.getScreen() instanceof ChatScreen screen
				&& widget.mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton(), screen.width, screen.height)) {
			event.setCanceled(true);
		}
	}

	private void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
		if (event.getScreen() instanceof ChatScreen screen
				&& widget.mouseDragged(event.getMouseX(), event.getMouseY(), screen.width, screen.height)) {
			event.setCanceled(true);
		}
	}

	private void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
		if (event.getScreen() instanceof ChatScreen && widget.mouseReleased()) {
			event.setCanceled(true);
		}
	}

	private void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
		if (event.getScreen() instanceof ChatScreen screen
				&& widget.mouseScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDeltaY(), screen.width, screen.height)) {
			event.setCanceled(true);
		}
	}

	private void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
		if (event.getScreen() instanceof ChatScreen screen
				&& widget.keyPressed(event.getKeyCode(), screen.height)) {
			event.setCanceled(true);
		}
	}

	private void onScreenClosing(ScreenEvent.Closing event) {
		if (currentScreen.get() == event.getScreen()) {
			currentScreen.clear();
		}

		flush();
	}

	private void insertIntoChat(String text) {
		if (currentScreen.get() instanceof ChatScreen chat) {
			((ChatScreenInvoker) chat).clipchat$insertText(text.replace('\n', ' '), false);
		}
	}
}
