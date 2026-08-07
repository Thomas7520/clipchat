package dev.thomas7520.clipchat.clipboard.model;

/**
 * Where a clipboard entry came from, inferred at capture time from the open screen.
 */
public enum ClipboardSource {
	CHAT_INPUT,
	TEXT_FIELD,
	BOOK,
	SIGN,
	COMMAND_SCREEN,
	/** {@code ClickEvent.CopyToClipboard} on a chat component. */
	COMPONENT_CLICK,
	OTHER_MOD,
	UNKNOWN,
	/** Read live from Windows. Excluded from persistence by {@link #isPersistable()}. */
	WINDOWS_HISTORY;

	public static ClipboardSource byName(String name, ClipboardSource fallback) {
		if (name == null) {
			return fallback;
		}

		for (ClipboardSource source : values()) {
			if (source.name().equalsIgnoreCase(name)) {
				return source;
			}
		}

		return fallback;
	}

	public boolean isPersistable() {
		return this != WINDOWS_HISTORY;
	}
}
