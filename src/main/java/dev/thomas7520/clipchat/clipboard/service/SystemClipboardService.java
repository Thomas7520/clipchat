package dev.thomas7520.clipchat.clipboard.service;

import dev.thomas7520.clipchat.capture.ClipboardCapture;
import dev.thomas7520.clipchat.util.ClipChatLog;

import net.minecraft.client.Minecraft;

/**
 * Reads and writes the live system clipboard through Minecraft's keyboard handler.
 */
public final class SystemClipboardService {
	private final ClipboardCapture capture;

	public SystemClipboardService(ClipboardCapture capture) {
		this.capture = capture;
	}

	public void set(String text) {
		if (text == null) {
			return;
		}

		try {
			// Suppresses the capture hook for this write, so restoring an entry does not re-record it.
			capture.suppressNext(text);
			Minecraft.getInstance().keyboardHandler.setClipboard(text);
		} catch (RuntimeException e) {
			ClipChatLog.LOGGER.warn("[ClipChat] Failed to write to the system clipboard", e);
		}
	}

	public String get() {
		try {
			return Minecraft.getInstance().keyboardHandler.getClipboard();
		} catch (RuntimeException e) {
			return "";
		}
	}
}
