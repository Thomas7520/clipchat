package dev.thomas7520.clipchat.history;

import dev.thomas7520.clipchat.clipboard.model.ActionResult;
import dev.thomas7520.clipchat.clipboard.model.ClipboardEntry;
import dev.thomas7520.clipchat.clipboard.model.ClipboardSource;
import dev.thomas7520.clipchat.clipboard.model.EntryId;
import dev.thomas7520.clipchat.clipboard.model.TextNormalizer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Minecraft-managed clipboard history: ordering, de-duplication, pinning and limits.
 * Holds no Minecraft types and performs no I/O.
 *
 * <p>Ordering invariant, re-established after every mutation: pinned entries first, most recently
 * pinned first; then unpinned entries, most recently copied first.
 */
public final class ClipboardHistory {
	private static final Comparator<ClipboardEntry> ORDER = (a, b) -> {
		if (a.pinned() != b.pinned()) {
			return a.pinned() ? -1 : 1;
		}

		Instant left = a.pinned() ? a.pinnedAt() : a.createdAt();
		Instant right = b.pinned() ? b.pinnedAt() : b.createdAt();

		if (left == null || right == null) {
			return left == right ? 0 : (left == null ? 1 : -1);
		}

		return right.compareTo(left);
	};

	private final List<ClipboardEntry> entries = new ArrayList<>();
	private final Map<String, EntryId> byText = new HashMap<>();
	private final Map<EntryId, ClipboardEntry> byId = new HashMap<>();

	private HistoryLimits limits;
	private List<ClipboardEntry> snapshot = List.of();

	public ClipboardHistory(HistoryLimits limits) {
		this.limits = limits;
	}

	public HistoryLimits limits() {
		return limits;
	}

	public void setLimits(HistoryLimits value) {
		this.limits = value;
		enforceLimits();
		rebuildSnapshot();
	}

	/** The ordered entries. Immutable, and rebuilt only when the history changes. */
	public List<ClipboardEntry> snapshot() {
		return snapshot;
	}

	public int size() {
		return entries.size();
	}

	public Optional<ClipboardEntry> byId(EntryId id) {
		return Optional.ofNullable(byId.get(id));
	}

	/**
	 * Records copied text. A duplicate moves the existing entry back to the top instead of
	 * creating a second copy, and a duplicate of a pinned entry stays pinned.
	 *
	 * @return the stored entry, or empty when the text was rejected
	 */
	public Optional<ClipboardEntry> add(String rawText, ClipboardSource source, Instant now) {
		Optional<TextNormalizer.Normalized> normalized =
				TextNormalizer.normalize(rawText, limits.maxEntryLength(), limits.rejectBlank());

		if (normalized.isEmpty()) {
			return Optional.empty();
		}

		TextNormalizer.Normalized value = normalized.get();
		EntryId existingId = byText.get(value.text());

		if (existingId != null) {
			ClipboardEntry existing = byId.get(existingId);

			if (existing != null) {
				ClipboardEntry moved = existing.withCreatedAt(now);
				replace(existing, moved);
				enforceLimits();
				rebuildSnapshot();
				return Optional.of(moved);
			}

			byText.remove(value.text());
		}

		ClipboardEntry entry = ClipboardEntry.create(value.text(), source, value.originalLength(), now);
		entries.add(entry);
		byId.put(entry.id(), entry);
		byText.put(entry.text(), entry.id());

		enforceLimits();
		rebuildSnapshot();
		return Optional.of(entry);
	}

	public ActionResult pin(EntryId id, Instant now) {
		ClipboardEntry entry = byId.get(id);

		if (entry == null) {
			return ActionResult.failed("clipchat.error.entry_missing");
		}

		if (entry.pinned()) {
			return ActionResult.OK;
		}

		if (countPinned() >= limits.maxPinned()) {
			// At the limit the request fails; an existing pin is never evicted to make room.
			return ActionResult.failed("clipchat.error.pin_limit_reached");
		}

		replace(entry, entry.withPinned(true, now));
		rebuildSnapshot();
		return ActionResult.OK;
	}

	public ActionResult unpin(EntryId id) {
		ClipboardEntry entry = byId.get(id);

		if (entry == null) {
			return ActionResult.failed("clipchat.error.entry_missing");
		}

		if (!entry.pinned()) {
			return ActionResult.OK;
		}

		replace(entry, entry.withPinned(false, null));
		enforceLimits();
		rebuildSnapshot();
		return ActionResult.OK;
	}

	public ActionResult delete(EntryId id) {
		ClipboardEntry entry = byId.remove(id);

		if (entry == null) {
			return ActionResult.failed("clipchat.error.entry_missing");
		}

		entries.remove(entry);
		byText.remove(entry.text(), id);
		rebuildSnapshot();
		return ActionResult.OK;
	}

	public ActionResult clearUnpinned() {
		List<ClipboardEntry> removed = entries.stream().filter(entry -> !entry.pinned()).toList();

		for (ClipboardEntry entry : removed) {
			byId.remove(entry.id());
			byText.remove(entry.text(), entry.id());
		}

		entries.removeAll(removed);
		rebuildSnapshot();
		return ActionResult.OK;
	}

	public int countPinned() {
		int count = 0;

		for (ClipboardEntry entry : entries) {
			if (entry.pinned()) {
				count++;
			}
		}

		return count;
	}

	/** Replaces the history wholesale. Null, empty and duplicate entries are skipped individually. */
	public void loadAll(List<ClipboardEntry> loaded) {
		entries.clear();
		byId.clear();
		byText.clear();

		for (ClipboardEntry entry : loaded) {
			if (entry == null || entry.text().isEmpty() || byText.containsKey(entry.text())) {
				continue;
			}

			entries.add(entry);
			byId.put(entry.id(), entry);
			byText.put(entry.text(), entry.id());
		}

		enforceLimits();
		rebuildSnapshot();
	}

	private void replace(ClipboardEntry oldEntry, ClipboardEntry newEntry) {
		entries.set(entries.indexOf(oldEntry), newEntry);
		byId.put(newEntry.id(), newEntry);
		byText.put(newEntry.text(), newEntry.id());
	}

	private void enforceLimits() {
		int unpinned = entries.size() - countPinned();

		if (unpinned <= limits.maxUnpinned()) {
			return;
		}

		entries.sort(ORDER);

		for (int i = entries.size() - 1; i >= 0 && unpinned > limits.maxUnpinned(); i--) {
			ClipboardEntry entry = entries.get(i);

			if (entry.pinned()) {
				continue;
			}

			entries.remove(i);
			byId.remove(entry.id());
			byText.remove(entry.text(), entry.id());
			unpinned--;
		}
	}

	private void rebuildSnapshot() {
		entries.sort(ORDER);
		snapshot = Collections.unmodifiableList(new ArrayList<>(entries));
	}
}
