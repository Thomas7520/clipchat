package dev.thomas7520.clipchat.clipboard.provider;

import dev.thomas7520.clipchat.clipboard.model.ActionResult;
import dev.thomas7520.clipchat.clipboard.model.ClipboardCapabilities;
import dev.thomas7520.clipchat.clipboard.model.ClipboardEntry;
import dev.thomas7520.clipchat.clipboard.model.EntryId;
import dev.thomas7520.clipchat.clipboard.model.ProviderId;
import dev.thomas7520.clipchat.clipboard.model.ProviderState;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A source of clipboard entries.
 *
 * <p>Reading is split in two: {@link #snapshot()} returns the last published list without
 * blocking, and {@link #requestRefresh()} starts an asynchronous reload that notifies listeners
 * when it publishes.
 *
 * <p>Actions report failure as an {@link ActionResult} rather than throwing.
 */
public interface ClipboardProvider {
	ProviderId id();

	ClipboardCapabilities capabilities();

	/** The last published list. Immutable and safe to call every frame. */
	List<ClipboardEntry> snapshot();

	ProviderState state();

	/** Returns immediately. Repeat calls while a refresh is in flight are ignored. */
	void requestRefresh();

	CompletableFuture<ActionResult> setAsSystemClipboard(EntryId id);

	CompletableFuture<ActionResult> delete(EntryId id);

	CompletableFuture<ActionResult> clearUnpinned();

	void addListener(ProviderListener listener);

	default void close() {
	}
}
