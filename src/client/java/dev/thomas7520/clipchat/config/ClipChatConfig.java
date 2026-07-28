package dev.thomas7520.clipchat.config;

import dev.thomas7520.clipchat.history.HistoryLimits;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Everything the player can change. Colour overrides are layered on top of the chosen preset, so
 * switching preset leaves them in place.
 */
public record ClipChatConfig(ThemePreset theme, Map<ColorSlot, Integer> overrides, boolean captureEnabled,
		boolean windowsHistoryEnabled, boolean widgetVisible, int maxUnpinned, int maxPinned, int maxEntryLength) {

	// Windows history and copy recording both default to off; the panel itself defaults to on.
	public static final ClipChatConfig DEFAULT = new ClipChatConfig(ThemePreset.DARK, Map.of(), false, false, true,
			HistoryLimits.DEFAULT.maxUnpinned(), HistoryLimits.DEFAULT.maxPinned(),
			HistoryLimits.DEFAULT.maxEntryLength());

	public ClipChatConfig {
		theme = theme == null ? ThemePreset.DARK : theme;
		overrides = overrides == null || overrides.isEmpty()
				? Map.of()
				: Collections.unmodifiableMap(new EnumMap<>(overrides));

		// Clamped through HistoryLimits so both use identical ranges.
		HistoryLimits clamped = new HistoryLimits(maxUnpinned, maxPinned, maxEntryLength, true);
		maxUnpinned = clamped.maxUnpinned();
		maxPinned = clamped.maxPinned();
		maxEntryLength = clamped.maxEntryLength();
	}

	public int color(ColorSlot slot) {
		Integer override = overrides.get(slot);
		return override != null ? override : theme.color(slot);
	}

	public boolean isOverridden(ColorSlot slot) {
		return overrides.containsKey(slot);
	}

	public HistoryLimits limits() {
		return new HistoryLimits(maxUnpinned, maxPinned, maxEntryLength, true);
	}

	public ClipChatConfig withTheme(ThemePreset value) {
		return new ClipChatConfig(value, overrides, captureEnabled, windowsHistoryEnabled, widgetVisible,
				maxUnpinned, maxPinned, maxEntryLength);
	}

	public ClipChatConfig withCaptureEnabled(boolean value) {
		return new ClipChatConfig(theme, overrides, value, windowsHistoryEnabled, widgetVisible,
				maxUnpinned, maxPinned, maxEntryLength);
	}

	public ClipChatConfig withWindowsHistoryEnabled(boolean value) {
		return new ClipChatConfig(theme, overrides, captureEnabled, value, widgetVisible,
				maxUnpinned, maxPinned, maxEntryLength);
	}

	public ClipChatConfig withWidgetVisible(boolean value) {
		return new ClipChatConfig(theme, overrides, captureEnabled, windowsHistoryEnabled, value,
				maxUnpinned, maxPinned, maxEntryLength);
	}

	public ClipChatConfig withLimits(int unpinned, int pinned, int entryLength) {
		return new ClipChatConfig(theme, overrides, captureEnabled, windowsHistoryEnabled, widgetVisible,
				unpinned, pinned, entryLength);
	}

	public ClipChatConfig withOverride(ColorSlot slot, Integer argb) {
		Map<ColorSlot, Integer> updated = new EnumMap<>(ColorSlot.class);
		updated.putAll(overrides);

		if (argb == null) {
			updated.remove(slot);
		} else {
			updated.put(slot, argb);
		}

		return new ClipChatConfig(theme, updated, captureEnabled, windowsHistoryEnabled, widgetVisible,
				maxUnpinned, maxPinned, maxEntryLength);
	}

	public ClipChatConfig withoutOverrides() {
		return new ClipChatConfig(theme, Map.of(), captureEnabled, windowsHistoryEnabled, widgetVisible,
				maxUnpinned, maxPinned, maxEntryLength);
	}
}
