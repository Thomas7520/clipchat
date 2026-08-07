package dev.thomas7520.clipchat.clipboard.model;

/**
 * The condition a provider reports instead of throwing. The panel renders one message per state.
 */
public enum ProviderState {
	READY,
	LOADING,
	EMPTY,
	/** Not supported on this operating system; the tab is hidden. */
	UNSUPPORTED_OS,
	/** Turned off in ClipChat's settings. */
	DISABLED_BY_USER,
	/** Windows clipboard history is off; the tab shows how to enable it. */
	DISABLED_BY_OS,
	/** Windows refused the read, usually because the game window is not focused. */
	ACCESS_DENIED,
	ERROR;

	public boolean canShowEntries() {
		return this == READY || this == LOADING || this == EMPTY;
	}
}
