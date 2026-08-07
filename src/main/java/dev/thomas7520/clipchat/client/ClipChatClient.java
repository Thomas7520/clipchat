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

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

import java.nio.file.Path;

@Mod(value = "clipchat", dist = Dist.CLIENT)
public class ClipChatClient {
	private static volatile MinecraftClipboardProvider provider;
	private static volatile ClipboardCapture capture;
	private static volatile SystemClipboardService clipboard;
	private static volatile ConfigManager config;

	private final MinecraftClipboardProvider history;
	private final WindowsClipboardProvider windowsHistory;
	private final ChatWidgetController controller;
	private final KeyMapping openConfigKey;

	public ClipChatClient(IEventBus modEventBus, ModContainer modContainer) {
		Path directory = FMLPaths.CONFIGDIR.get().resolve("clipchat");
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

		this.history = history;
		this.windowsHistory = windowsHistory;
		this.controller = controller;
		this.openConfigKey = new KeyMapping("key.clipchat.config", InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MISC);

		modEventBus.addListener(this::onRegisterKeyMappings);
		NeoForge.EVENT_BUS.addListener(this::onClientTick);
		NeoForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
		NeoForge.EVENT_BUS.addListener(this::onGameShuttingDown);

		modContainer.registerExtensionPoint(IConfigScreenFactory.class,
				(ignoredContainer, parent) -> new ClipChatConfigScreen(parent, configManager));

		ClipChatLog.LOGGER.info("[ClipChat] Client initialised");
	}

	private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
		event.register(openConfigKey);
	}

	private void onClientTick(ClientTickEvent.Post event) {
		Minecraft client = Minecraft.getInstance();

		while (openConfigKey.consumeClick()) {
			openConfig(client);
		}
	}

	private void onRegisterClientCommands(RegisterClientCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("clipchat").executes(context -> {
			Minecraft client = Minecraft.getInstance();
			client.execute(() -> openConfig(client));
			return 1;
		}));
	}

	private void onGameShuttingDown(GameShuttingDownEvent event) {
		controller.flush();
		history.close();
		windowsHistory.close();
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
	 * Entry point for the {@code KeyboardHandler} mixin. Does nothing if invoked before ClipChat has
	 * finished constructing its client entrypoint.
	 */
	public static void onClipboardWrite(String text) {
		ClipboardCapture current = capture;

		if (current != null) {
			current.onMinecraftCopy(text);
		}
	}
}
