package dev.thomas7520.clipchat.history;

/**
 * Bounds applied to the Minecraft clipboard history. Out-of-range values are clamped on
 * construction rather than rejected.
 */
public record HistoryLimits(int maxUnpinned, int maxPinned, int maxEntryLength, boolean rejectBlank) {
	public static final HistoryLimits DEFAULT = new HistoryLimits(100, 50, 4096, true);

	public HistoryLimits {
		maxUnpinned = clamp(maxUnpinned, 1, 1000);
		maxPinned = clamp(maxPinned, 1, 500);
		maxEntryLength = clamp(maxEntryLength, 16, 65536);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
