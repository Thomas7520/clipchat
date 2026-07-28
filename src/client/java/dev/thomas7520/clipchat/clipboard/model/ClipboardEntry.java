package dev.thomas7520.clipchat.clipboard.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A single clipboard item. Immutable: every mutation returns a new instance.
 *
 * @param createdAt      when the entry was captured, or null if the provider has no timestamps
 * @param pinnedAt       when the entry was pinned, or null when not pinned
 * @param originalLength codepoint count before truncation; greater than the stored text
 *                       length means the entry was truncated at capture
 */
public record ClipboardEntry(
		EntryId id,
		String text,
		Instant createdAt,
		boolean pinned,
		Instant pinnedAt,
		ClipboardSource source,
		int originalLength
) {
	public ClipboardEntry {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(text, "text");
		Objects.requireNonNull(source, "source");

		if (!pinned) {
			pinnedAt = null;
		}
	}

	public static ClipboardEntry create(String text, ClipboardSource source, int originalLength, Instant now) {
		return new ClipboardEntry(EntryId.newMinecraftId(), text, now, false, null, source, originalLength);
	}

	public boolean isTruncated() {
		return originalLength > text.codePointCount(0, text.length());
	}

	public ClipboardEntry withPinned(boolean value, Instant now) {
		if (value == pinned) {
			return this;
		}

		return new ClipboardEntry(id, text, createdAt, value, value ? now : null, source, originalLength);
	}

	/** Restamps the entry so it sorts to the top again when the same text is copied twice. */
	public ClipboardEntry withCreatedAt(Instant value) {
		return new ClipboardEntry(id, text, value, pinned, pinnedAt, source, originalLength);
	}
}
