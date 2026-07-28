package dev.thomas7520.clipchat.persistence;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.thomas7520.clipchat.clipboard.model.ClipboardEntry;
import dev.thomas7520.clipchat.clipboard.model.ClipboardSource;
import dev.thomas7520.clipchat.clipboard.model.EntryId;
import dev.thomas7520.clipchat.clipboard.model.ProviderId;
import dev.thomas7520.clipchat.util.ClipChatLog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes {@code history.json}. Entries are parsed field by field, so a malformed entry
 * is dropped on its own and the rest of the file still loads.
 */
public final class HistoryStore {
	public static final int SCHEMA_VERSION = 1;

	private final Path file;

	public HistoryStore(Path file) {
		this.file = file;
	}

	public Path file() {
		return file;
	}

	public List<ClipboardEntry> load() {
		if (!Files.exists(file)) {
			return List.of();
		}

		String raw;

		try {
			raw = Files.readString(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			ClipChatLog.LOGGER.warn("[ClipChat] Could not read history file, starting empty", e);
			return List.of();
		}

		JsonObject root;

		try {
			root = JsonParser.parseString(raw).getAsJsonObject();
		} catch (RuntimeException e) {
			quarantine("unparseable");
			return List.of();
		}

		int version = optInt(root, "schemaVersion", 0);

		if (version > SCHEMA_VERSION) {
			ClipChatLog.LOGGER.warn("[ClipChat] History schema v{} is newer than supported v{}; ignoring file",
					version, SCHEMA_VERSION);
			quarantine("future-schema");
			return List.of();
		}

		JsonElement entriesElement = root.get("entries");

		if (entriesElement == null || !entriesElement.isJsonArray()) {
			return List.of();
		}

		List<ClipboardEntry> entries = new ArrayList<>();
		int skipped = 0;

		for (JsonElement element : entriesElement.getAsJsonArray()) {
			ClipboardEntry entry = readEntry(element);

			if (entry == null) {
				skipped++;
			} else {
				entries.add(entry);
			}
		}

		if (skipped > 0) {
			ClipChatLog.LOGGER.warn("[ClipChat] Skipped {} unreadable history entries", skipped);
		}

		return entries;
	}

	public void save(List<ClipboardEntry> entries) throws IOException {
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", SCHEMA_VERSION);
		root.addProperty("savedAt", Instant.now().toString());

		JsonArray array = new JsonArray();

		for (ClipboardEntry entry : entries) {
			// Entries read from the Windows history are skipped; they are never written to disk.
			if (!entry.source().isPersistable()) {
				continue;
			}

			array.add(writeEntry(entry));
		}

		root.add("entries", array);
		AtomicFileWriter.write(file, root.toString());
	}

	private static JsonObject writeEntry(ClipboardEntry entry) {
		JsonObject object = new JsonObject();
		object.addProperty("id", entry.id().value());
		object.addProperty("text", entry.text());
		object.addProperty("source", entry.source().name());
		object.addProperty("pinned", entry.pinned());
		object.addProperty("originalLength", entry.originalLength());

		if (entry.createdAt() != null) {
			object.addProperty("createdAt", entry.createdAt().toString());
		}

		if (entry.pinnedAt() != null) {
			object.addProperty("pinnedAt", entry.pinnedAt().toString());
		}

		return object;
	}

	private static ClipboardEntry readEntry(JsonElement element) {
		try {
			if (element == null || !element.isJsonObject()) {
				return null;
			}

			JsonObject object = element.getAsJsonObject();
			String text = optString(object, "text");

			if (text == null || text.isEmpty()) {
				return null;
			}

			ClipboardSource source = ClipboardSource.byName(optString(object, "source"), ClipboardSource.UNKNOWN);

			if (!source.isPersistable()) {
				return null;
			}

			String id = optString(object, "id");
			EntryId entryId = id == null || id.isEmpty()
					? EntryId.newMinecraftId()
					: new EntryId(ProviderId.MINECRAFT, id);

			boolean pinned = object.has("pinned") && object.get("pinned").getAsBoolean();
			Instant createdAt = optInstant(object, "createdAt");
			Instant pinnedAt = pinned ? optInstant(object, "pinnedAt") : null;

			if (createdAt == null) {
				createdAt = Instant.EPOCH;
			}

			if (pinned && pinnedAt == null) {
				pinnedAt = createdAt;
			}

			int originalLength = optInt(object, "originalLength", text.codePointCount(0, text.length()));

			return new ClipboardEntry(entryId, text, createdAt, pinned, pinnedAt, source, originalLength);
		} catch (RuntimeException e) {
			return null;
		}
	}

	private void quarantine(String reason) {
		try {
			Path moved = AtomicFileWriter.quarantine(file);
			ClipChatLog.LOGGER.warn("[ClipChat] History file was {}; moved to {}", reason, moved.getFileName());
		} catch (IOException e) {
			ClipChatLog.LOGGER.warn("[ClipChat] History file was {} and could not be moved aside", reason, e);
		}
	}

	private static String optString(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
	}

	private static int optInt(JsonObject object, String key, int fallback) {
		try {
			JsonElement element = object.get(key);
			return element != null && element.isJsonPrimitive() ? element.getAsInt() : fallback;
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private static Instant optInstant(JsonObject object, String key) {
		String value = optString(object, key);

		if (value == null) {
			return null;
		}

		try {
			return Instant.parse(value);
		} catch (DateTimeParseException e) {
			return null;
		}
	}
}
