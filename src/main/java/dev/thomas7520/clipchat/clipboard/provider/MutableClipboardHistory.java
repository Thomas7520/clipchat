package dev.thomas7520.clipchat.clipboard.provider;

import dev.thomas7520.clipchat.clipboard.model.ActionResult;
import dev.thomas7520.clipchat.clipboard.model.ClipboardSource;
import dev.thomas7520.clipchat.clipboard.model.EntryId;

/**
 * A history ClipChat stores itself and can add to, pin and unpin. Providers that only read a
 * clipboard owned by the operating system implement {@link ClipboardProvider} instead.
 */
public interface MutableClipboardHistory extends ClipboardProvider {
	void addText(String rawText, ClipboardSource source);

	ActionResult pin(EntryId id);

	ActionResult unpin(EntryId id);
}
