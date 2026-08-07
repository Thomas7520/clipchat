package dev.thomas7520.clipchat.config;

import dev.thomas7520.clipchat.util.ClipChatLog;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Holds the live config and notifies registered listeners whenever it is replaced.
 */
public final class ConfigManager {
	private final ConfigStore store;
	private final List<Consumer<ClipChatConfig>> listeners = new CopyOnWriteArrayList<>();

	private volatile ClipChatConfig current;

	public ConfigManager(ConfigStore store) {
		this.store = store;
		this.current = store.load();
	}

	public ClipChatConfig current() {
		return current;
	}

	public void addListener(Consumer<ClipChatConfig> listener) {
		listeners.add(listener);
		listener.accept(current);
	}

	public void update(ClipChatConfig config) {
		if (config == null || config.equals(current)) {
			return;
		}

		current = config;
		store.save(config);

		for (Consumer<ClipChatConfig> listener : listeners) {
			try {
				listener.accept(config);
			} catch (RuntimeException e) {
				ClipChatLog.LOGGER.warn("[ClipChat] Config listener failed", e);
			}
		}
	}
}
