package dev.thomas7520.clipchat.windows;

import dev.thomas7520.clipchat.clipboard.model.ActionResult;
import dev.thomas7520.clipchat.clipboard.model.ClipboardCapabilities;
import dev.thomas7520.clipchat.clipboard.model.ClipboardCapability;
import dev.thomas7520.clipchat.clipboard.model.ClipboardEntry;
import dev.thomas7520.clipchat.clipboard.model.EntryId;
import dev.thomas7520.clipchat.clipboard.model.ProviderId;
import dev.thomas7520.clipchat.clipboard.model.ProviderState;
import dev.thomas7520.clipchat.clipboard.provider.ClipboardProvider;
import dev.thomas7520.clipchat.clipboard.provider.ProviderListener;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.IntSupplier;

/**
 * Reads the Windows clipboard history on demand. The snapshot is held in memory only; its entries
 * carry {@code ClipboardSource.WINDOWS_HISTORY}, which the history store refuses to write.
 *
 * <p>{@link ClipboardCapability#PIN} is absent: the WinRT API exposes only {@code Id},
 * {@code Timestamp} and {@code Content} on a history item, with no pinned state to read or set.
 */
public final class WindowsClipboardProvider implements ClipboardProvider {
	private static final ClipboardCapabilities CAPABILITIES = ClipboardCapabilities.of(
			ClipboardCapability.READ,
			ClipboardCapability.REFRESH,
			ClipboardCapability.TIMESTAMPS,
			ClipboardCapability.SET_AS_SYSTEM_CLIPBOARD,
			ClipboardCapability.DELETE_ENTRY,
			ClipboardCapability.CLEAR_UNPINNED);

	private static final ClipboardCapabilities NONE = ClipboardCapabilities.of();

	private final List<ProviderListener> listeners = new CopyOnWriteArrayList<>();
	private final AtomicBoolean refreshing = new AtomicBoolean();
	private final IntSupplier maxEntryLength;
	private final WindowsClipboardBridge bridge;
	private final boolean supported;

	private volatile List<ClipboardEntry> entries = List.of();
	private volatile ProviderState state;

	/**
	 * Whether this OS can support the provider. Must not reference {@link WindowsClipboardBridge},
	 * whose static initialiser loads the foreign-function linker.
	 */
	public static boolean osSupported() {
		return System.getProperty("os.name", "").startsWith("Windows");
	}

	public WindowsClipboardProvider(IntSupplier maxEntryLength) {
		this.maxEntryLength = maxEntryLength;
		this.supported = osSupported();
		this.bridge = supported ? new WindowsClipboardBridge() : null;
		this.state = supported ? ProviderState.LOADING : ProviderState.UNSUPPORTED_OS;
	}

	@Override
	public ProviderId id() {
		return ProviderId.WINDOWS;
	}

	@Override
	public ClipboardCapabilities capabilities() {
		return supported ? CAPABILITIES : NONE;
	}

	@Override
	public List<ClipboardEntry> snapshot() {
		return entries;
	}

	@Override
	public ProviderState state() {
		return state;
	}

	@Override
	public void requestRefresh() {
		// Coalesced: a call made while a refresh is in flight is dropped.
		if (!supported || !refreshing.compareAndSet(false, true)) {
			return;
		}

		bridge.readHistory(maxEntryLength.getAsInt()).whenComplete((snapshot, failure) -> {
			refreshing.set(false);

			if (snapshot == null) {
				publish(List.of(), ProviderState.ERROR);
			} else {
				publish(snapshot.entries(), snapshot.state());
			}
		});
	}

	@Override
	public CompletableFuture<ActionResult> setAsSystemClipboard(EntryId id) {
		return act(id, ClipboardCapability.SET_AS_SYSTEM_CLIPBOARD, bridge::setAsContent);
	}

	@Override
	public CompletableFuture<ActionResult> delete(EntryId id) {
		return act(id, ClipboardCapability.DELETE_ENTRY, bridge::delete);
	}

	@Override
	public CompletableFuture<ActionResult> clearUnpinned() {
		if (!supported) {
			return CompletableFuture.completedFuture(
					ActionResult.unsupported(ClipboardCapability.CLEAR_UNPINNED));
		}

		return bridge.clearHistory().thenApply(this::toResult);
	}

	/** Drops the in-memory snapshot. Called when the Windows tab is switched off. */
	public void forget() {
		publish(List.of(), supported ? ProviderState.LOADING : ProviderState.UNSUPPORTED_OS);
	}

	@Override
	public void addListener(ProviderListener listener) {
		listeners.add(listener);
	}

	@Override
	public void close() {
		if (bridge != null) {
			bridge.close();
		}
	}

	private CompletableFuture<ActionResult> act(EntryId id, ClipboardCapability capability,
			Function<String, CompletableFuture<Boolean>> action) {
		if (!supported || id.provider() != ProviderId.WINDOWS) {
			return CompletableFuture.completedFuture(ActionResult.unsupported(capability));
		}

		return action.apply(id.value()).thenApply(this::toResult);
	}

	private ActionResult toResult(boolean succeeded) {
		if (!succeeded) {
			return ActionResult.failed("clipchat.error.windows_action_failed");
		}

		requestRefresh();
		return ActionResult.OK;
	}

	private void publish(List<ClipboardEntry> snapshot, ProviderState next) {
		entries = List.copyOf(snapshot);
		state = next;

		for (ProviderListener listener : listeners) {
			listener.onProviderChanged(ProviderId.WINDOWS, next);
		}
	}
}
