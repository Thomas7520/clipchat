package dev.thomas7520.clipchat.capture;

import dev.thomas7520.clipchat.clipboard.model.ClipboardSource;
import dev.thomas7520.clipchat.clipboard.provider.MutableClipboardHistory;
import dev.thomas7520.clipchat.util.ClipChatLog;

import java.util.function.Supplier;

/**
 * Receives every clipboard write Minecraft performs and decides whether to record it. Only writes
 * arrive here, so text pasted in from another application is never recorded.
 */
public final class ClipboardCapture {
	private final MutableClipboardHistory history;

	private volatile boolean paused;
	private volatile String suppressOnce;
	private volatile Supplier<ClipboardSource> sourceResolver = () -> ClipboardSource.UNKNOWN;

	public ClipboardCapture(MutableClipboardHistory history) {
		this.history = history;
	}

	public boolean isPaused() {
		return paused;
	}

	public void setPaused(boolean value) {
		this.paused = value;
	}

	/**
	 * Sets the supplier consulted for the source of each captured copy. A null resolver, or none
	 * at all, attributes every copy to {@code UNKNOWN}.
	 */
	public void setSourceResolver(Supplier<ClipboardSource> resolver) {
		this.sourceResolver = resolver == null ? () -> ClipboardSource.UNKNOWN : resolver;
	}

	/** Skips the next capture of exactly this text. Called before ClipChat writes it itself. */
	public void suppressNext(String text) {
		this.suppressOnce = text;
	}

	public void onMinecraftCopy(String text) {
		try {
			if (paused || text == null || text.isEmpty()) {
				return;
			}

			String suppressed = suppressOnce;

			if (suppressed != null && suppressed.equals(text)) {
				suppressOnce = null;
				return;
			}

			history.addText(text, sourceResolver.get());
		} catch (Throwable t) {
			ClipChatLog.LOGGER.warn("[ClipChat] Clipboard capture failed for {}", ClipChatLog.redact(text), t);
		}
	}
}
