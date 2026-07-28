package dev.thomas7520.clipchat.clipboard.model;

/**
 * What a provider supports. The UI shows a control only when the active provider reports the
 * matching capability.
 */
public enum ClipboardCapability {
	READ,
	REFRESH,
	TIMESTAMPS,
	SET_AS_SYSTEM_CLIPBOARD,
	ADD_ENTRY,
	DELETE_ENTRY,
	CLEAR_UNPINNED,
	/** Pinning. Reported only by the Minecraft provider; the Windows API exposes no pinned state. */
	PIN
}
