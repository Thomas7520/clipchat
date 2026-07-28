package dev.thomas7520.clipchat.history;

import dev.thomas7520.clipchat.clipboard.model.ActionResult;
import dev.thomas7520.clipchat.clipboard.model.ClipboardCapabilities;
import dev.thomas7520.clipchat.clipboard.model.ClipboardCapability;
import dev.thomas7520.clipchat.clipboard.model.ClipboardEntry;
import dev.thomas7520.clipchat.clipboard.model.ClipboardSource;
import dev.thomas7520.clipchat.clipboard.model.EntryId;
import dev.thomas7520.clipchat.clipboard.model.ProviderId;
import dev.thomas7520.clipchat.clipboard.model.ProviderState;
import dev.thomas7520.clipchat.clipboard.provider.MutableClipboardHistory;
import dev.thomas7520.clipchat.clipboard.provider.ProviderListener;
import dev.thomas7520.clipchat.persistence.HistoryStore;
import dev.thomas7520.clipchat.util.ClipChatLog;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * The ClipChat-owned clipboard history, available on every operating system.
 *
 * <p>Writes to disk are debounced onto a background thread, so an abrupt exit loses at most one
 * debounce window of history.
 */
public final class MinecraftClipboardProvider implements MutableClipboardHistory {
	private static final ClipboardCapabilities CAPABILITIES = ClipboardCapabilities.of(
			ClipboardCapability.READ,
			ClipboardCapability.TIMESTAMPS,
			ClipboardCapability.SET_AS_SYSTEM_CLIPBOARD,
			ClipboardCapability.ADD_ENTRY,
			ClipboardCapability.DELETE_ENTRY,
			ClipboardCapability.CLEAR_UNPINNED,
			ClipboardCapability.PIN);

	private static final long SAVE_DEBOUNCE_MS = 500L;

	private final ClipboardHistory history;
	private final HistoryStore store;
	private final Consumer<String> clipboardWriter;
	private final List<ProviderListener> listeners = new CopyOnWriteArrayList<>();
	private final AtomicBoolean savePending = new AtomicBoolean();

	private final ScheduledExecutorService io = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, "clipchat-io");
		thread.setDaemon(true);
		return thread;
	});

	private volatile boolean enabled = true;

	public MinecraftClipboardProvider(HistoryStore store, HistoryLimits limits, Consumer<String> clipboardWriter) {
		this.store = store;
		this.history = new ClipboardHistory(limits);
		this.clipboardWriter = clipboardWriter;
	}

	public ClipboardHistory history() {
		return history;
	}

	public void setEnabled(boolean value) {
		this.enabled = value;
		notifyListeners();
	}

	public void load() {
		try {
			history.loadAll(store.load());
			ClipChatLog.LOGGER.info("[ClipChat] Loaded {} clipboard entries", history.size());
		} catch (RuntimeException e) {
			ClipChatLog.LOGGER.warn("[ClipChat] Failed to load clipboard history; starting empty", e);
		}

		notifyListeners();
	}

	@Override
	public ProviderId id() {
		return ProviderId.MINECRAFT;
	}

	@Override
	public ClipboardCapabilities capabilities() {
		return CAPABILITIES;
	}

	@Override
	public List<ClipboardEntry> snapshot() {
		return history.snapshot();
	}

	@Override
	public ProviderState state() {
		if (!enabled) {
			return ProviderState.DISABLED_BY_USER;
		}

		return history.size() == 0 ? ProviderState.EMPTY : ProviderState.READY;
	}

	@Override
	public void requestRefresh() {
		// The in-memory history is always current; nothing to fetch.
	}

	@Override
	public void addText(String rawText, ClipboardSource source) {
		if (!enabled) {
			return;
		}

		Optional<ClipboardEntry> added = history.add(rawText, source, Instant.now());

		if (added.isPresent()) {
			scheduleSave();
			notifyListeners();
		}
	}

	@Override
	public ActionResult pin(EntryId id) {
		return mutate(history.pin(id, Instant.now()));
	}

	@Override
	public ActionResult unpin(EntryId id) {
		return mutate(history.unpin(id));
	}

	@Override
	public CompletableFuture<ActionResult> setAsSystemClipboard(EntryId id) {
		return CompletableFuture.completedFuture(history.byId(id)
				.map(entry -> {
					clipboardWriter.accept(entry.text());
					return ActionResult.OK;
				})
				.orElseGet(() -> ActionResult.failed("clipchat.error.entry_missing")));
	}

	@Override
	public CompletableFuture<ActionResult> delete(EntryId id) {
		return CompletableFuture.completedFuture(mutate(history.delete(id)));
	}

	@Override
	public CompletableFuture<ActionResult> clearUnpinned() {
		return CompletableFuture.completedFuture(mutate(history.clearUnpinned()));
	}

	@Override
	public void addListener(ProviderListener listener) {
		listeners.add(listener);
	}

	@Override
	public void close() {
		io.shutdown();

		// Writes inline; the executor is already shut down, so a debounced task would never run.
		saveNow();
	}

	private ActionResult mutate(ActionResult result) {
		if (result.succeeded()) {
			scheduleSave();
			notifyListeners();
		}

		return result;
	}

	private void scheduleSave() {
		if (!savePending.compareAndSet(false, true)) {
			return;
		}

		try {
			io.schedule(() -> {
				savePending.set(false);
				saveNow();
			}, SAVE_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
		} catch (RuntimeException e) {
			savePending.set(false);
		}
	}

	private void saveNow() {
		try {
			store.save(history.snapshot());
		} catch (IOException | RuntimeException e) {
			ClipChatLog.LOGGER.warn("[ClipChat] Failed to save clipboard history", e);
		}
	}

	private void notifyListeners() {
		ProviderState state = state();

		for (ProviderListener listener : listeners) {
			try {
				listener.onProviderChanged(ProviderId.MINECRAFT, state);
			} catch (RuntimeException e) {
				ClipChatLog.LOGGER.warn("[ClipChat] Provider listener failed", e);
			}
		}
	}
}
