package dev.thomas7520.clipchat.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.thomas7520.clipchat.persistence.AtomicFileWriter;
import dev.thomas7520.clipchat.util.ClipChatLog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

public final class ConfigStore {
	private static final int SCHEMA_VERSION = 1;

	private final Path file;

	public ConfigStore(Path file) {
		this.file = file;
	}

	public ClipChatConfig load() {
		if (!Files.exists(file)) {
			return ClipChatConfig.DEFAULT;
		}

		try {
			JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();

			if (optInt(root, "schemaVersion", 0) > SCHEMA_VERSION) {
				ClipChatLog.LOGGER.warn("[ClipChat] Config is newer than this build supports; using defaults");
				return ClipChatConfig.DEFAULT;
			}

			return new ClipChatConfig(
					ThemePreset.byName(optString(root, "theme"), ClipChatConfig.DEFAULT.theme()),
					readOverrides(root),
					root.has("captureEnabled") && root.get("captureEnabled").getAsBoolean(),
					root.has("windowsHistoryEnabled") && root.get("windowsHistoryEnabled").getAsBoolean(),
					!root.has("widgetVisible") || root.get("widgetVisible").getAsBoolean(),
					optInt(root, "maxUnpinned", ClipChatConfig.DEFAULT.maxUnpinned()),
					optInt(root, "maxPinned", ClipChatConfig.DEFAULT.maxPinned()),
					optInt(root, "maxEntryLength", ClipChatConfig.DEFAULT.maxEntryLength()));
		} catch (IOException | RuntimeException e) {
			ClipChatLog.LOGGER.warn("[ClipChat] Could not read the config; using defaults", e);
			return ClipChatConfig.DEFAULT;
		}
	}

	public void save(ClipChatConfig config) {
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", SCHEMA_VERSION);
		root.addProperty("theme", config.theme().name());
		root.addProperty("captureEnabled", config.captureEnabled());
		root.addProperty("windowsHistoryEnabled", config.windowsHistoryEnabled());
		root.addProperty("widgetVisible", config.widgetVisible());
		root.addProperty("maxUnpinned", config.maxUnpinned());
		root.addProperty("maxPinned", config.maxPinned());
		root.addProperty("maxEntryLength", config.maxEntryLength());

		JsonObject colors = new JsonObject();

		for (Map.Entry<ColorSlot, Integer> entry : config.overrides().entrySet()) {
			colors.addProperty(entry.getKey().serializedName(), String.format("#%08X", entry.getValue()));
		}

		root.add("colorOverrides", colors);

		try {
			AtomicFileWriter.write(file, root.toString());
		} catch (IOException | RuntimeException e) {
			ClipChatLog.LOGGER.warn("[ClipChat] Could not save the config", e);
		}
	}

	private static Map<ColorSlot, Integer> readOverrides(JsonObject root) {
		Map<ColorSlot, Integer> overrides = new EnumMap<>(ColorSlot.class);

		if (!root.has("colorOverrides") || !root.get("colorOverrides").isJsonObject()) {
			return overrides;
		}

		JsonObject colors = root.getAsJsonObject("colorOverrides");

		for (ColorSlot slot : ColorSlot.values()) {
			if (!colors.has(slot.serializedName())) {
				continue;
			}

			Integer parsed = parseArgb(colors.get(slot.serializedName()).getAsString());

			if (parsed != null) {
				overrides.put(slot, parsed);
			}
		}

		return overrides;
	}

	/**
	 * Accepts {@code #AARRGGBB} and {@code #RRGGBB}, the latter treated as fully opaque.
	 */
	public static Integer parseArgb(String raw) {
		if (raw == null) {
			return null;
		}

		String text = raw.trim();

		if (text.startsWith("#")) {
			text = text.substring(1);
		}

		if (text.length() != 6 && text.length() != 8) {
			return null;
		}

		try {
			long value = Long.parseUnsignedLong(text, 16);
			return (int) (text.length() == 6 ? value | 0xFF000000L : value);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public static String formatArgb(int argb) {
		return String.format("#%08X", argb);
	}

	private static String optString(JsonObject root, String key) {
		return root.has(key) && root.get(key).isJsonPrimitive() ? root.get(key).getAsString() : null;
	}

	private static int optInt(JsonObject root, String key, int fallback) {
		try {
			return root.has(key) && root.get(key).isJsonPrimitive() ? root.get(key).getAsInt() : fallback;
		} catch (RuntimeException e) {
			return fallback;
		}
	}
}
