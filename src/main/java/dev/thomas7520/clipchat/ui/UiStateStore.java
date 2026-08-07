package dev.thomas7520.clipchat.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.thomas7520.clipchat.persistence.AtomicFileWriter;
import dev.thomas7520.clipchat.ui.model.Anchor;
import dev.thomas7520.clipchat.ui.model.WidgetGeometry;
import dev.thomas7520.clipchat.util.ClipChatLog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads and writes {@code ui.json}, holding the panel's position, size and collapsed state.
 * An unreadable file yields the default layout instead of an error.
 */
public final class UiStateStore {
	private static final int SCHEMA_VERSION = 1;

	private final Path file;

	public UiStateStore(Path file) {
		this.file = file;
	}

	public WidgetGeometry load() {
		if (!Files.exists(file)) {
			return WidgetGeometry.DEFAULT;
		}

		try {
			JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();

			if (root.get("schemaVersion").getAsInt() > SCHEMA_VERSION) {
				return WidgetGeometry.DEFAULT;
			}

			return new WidgetGeometry(
					Anchor.byName(root.get("anchor").getAsString(), WidgetGeometry.DEFAULT.anchor()),
					root.get("offsetX").getAsInt(),
					root.get("offsetY").getAsInt(),
					root.get("width").getAsInt(),
					root.get("height").getAsInt(),
					root.get("collapsed").getAsBoolean());
		} catch (IOException | RuntimeException e) {
			ClipChatLog.LOGGER.warn("[ClipChat] Could not read the widget layout; using defaults", e);
			return WidgetGeometry.DEFAULT;
		}
	}

	public void save(WidgetGeometry geometry) {
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", SCHEMA_VERSION);
		root.addProperty("anchor", geometry.anchor().name());
		root.addProperty("offsetX", geometry.offsetX());
		root.addProperty("offsetY", geometry.offsetY());
		root.addProperty("width", geometry.width());
		root.addProperty("height", geometry.height());
		root.addProperty("collapsed", geometry.collapsed());

		try {
			AtomicFileWriter.write(file, root.toString());
		} catch (IOException | RuntimeException e) {
			ClipChatLog.LOGGER.warn("[ClipChat] Could not save the widget layout", e);
		}
	}
}
