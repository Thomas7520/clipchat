package dev.thomas7520.clipchat.client;

import dev.thomas7520.clipchat.capture.ClipboardCapture;
import dev.thomas7520.clipchat.clipboard.service.SystemClipboardService;
import dev.thomas7520.clipchat.config.ConfigManager;
import dev.thomas7520.clipchat.config.ConfigStore;
import dev.thomas7520.clipchat.history.HistoryLimits;
import dev.thomas7520.clipchat.history.MinecraftClipboardProvider;
import dev.thomas7520.clipchat.persistence.HistoryStore;
import dev.thomas7520.clipchat.ui.ChatWidgetController;
import dev.thomas7520.clipchat.ui.ClipChatConfigScreen;
import dev.thomas7520.clipchat.ui.UiStateStore;
import dev.thomas7520.clipchat.util.ClipChatLog;
import dev.thomas7520.clipchat.windows.WindowsClipboardProvider;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;

public class ClipChatClient implements ClientModInitializer {
	private static volatile MinecraftClipboardProvider provider;
	private static volatile ClipboardCapture capture;
	private static volatile SystemClipboardService clipboard;
	private static volatile ConfigManager config;

	@Override
	public void onInitializeClient() {
		Path directory = FabricLoader.getInstance().getConfigDir().resolve("clipchat");
		HistoryStore store = new HistoryStore(directory.resolve("history.json"));

		MinecraftClipboardProvider history = new MinecraftClipboardProvider(store, HistoryLimits.DEFAULT,
				text -> clipboard.set(text));
		ClipboardCapture clipboardCapture = new ClipboardCapture(history);
		ConfigManager configManager = new ConfigManager(new ConfigStore(directory.resolve("config.json")));

		provider = history;
		capture = clipboardCapture;
		clipboard = new SystemClipboardService(clipboardCapture);
		config = configManager;

		WindowsClipboardProvider windowsHistory = new WindowsClipboardProvider(
				() -> configManager.current().maxEntryLength());

		configManager.addListener(current -> {
			history.history().setLimits(current.limits());
			history.setPersistent(current.captureEnabled());

			if (!current.windowsHistoryEnabled()) {
				windowsHistory.forget();
			}
		});

		history.load();

		ChatWidgetController controller = new ChatWidgetController(history, windowsHistory,
				new UiStateStore(directory.resolve("ui.json")), configManager::current,
				parent -> Minecraft.getInstance().execute(() ->
						Minecraft.getInstance().setScreen(new ClipChatConfigScreen(parent, configManager))));
		clipboardCapture.setSourceResolver(controller::currentSource);
		controller.register();

		registerConfigKey();
		registerCommand();

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			controller.flush();
			history.close();
			windowsHistory.close();
		});

		ClipChatLog.LOGGER.info("[ClipChat] Client initialised");
	}

	private static void registerConfigKey() {
		KeyMapping open = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.clipchat.config",
				InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MISC));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (open.consumeClick()) {
				openConfig(client);
			}
		});
	}

	private static void registerCommand() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommands.literal("clipchat").executes(context -> {
					Minecraft client = Minecraft.getInstance();
					client.execute(() -> openConfig(client));
					return 1;
				})));
	}

	// Opens with a null parent, so closing the settings screen returns to the game.
	private static void openConfig(Minecraft client) {
		client.setScreen(new ClipChatConfigScreen(null, config));
	}

	public static MinecraftClipboardProvider history() {
		return provider;
	}

	public static SystemClipboardService clipboard() {
		return clipboard;
	}

	public static ConfigManager config() {
		return config;
	}

	/**
	 * Entry point for the {@code KeyboardHandler} mixin. Does nothing when called before
	 * {@link #onInitializeClient()} has run.
	 */
	public static void onClipboardWrite(String text) {
		ClipboardCapture current = capture;

		if (current != null) {
			current.onMinecraftCopy(text);
		}
	}
}
