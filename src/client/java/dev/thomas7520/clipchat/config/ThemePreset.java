package dev.thomas7520.clipchat.config;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Built-in colour sets, each supplying an ARGB value for every {@link ColorSlot}.
 */
public enum ThemePreset {
	DARK(0xE0121212, 0xF0242424, 0xFF3F3F3F, 0x40FFFFFF, 0xFFE8E8E8, 0xFF909090, 0xFFE7B44A),
	// Backgrounds stay near-opaque: chat text showing through would wash out the dark glyphs.
	LIGHT(0xFAF2F2F2, 0xFFDCDCDC, 0xFF8A8A8A, 0x30000000, 0xFF141414, 0xFF5A5A5A, 0xFFB07800),
	HIGH_CONTRAST(0xFF000000, 0xFF000000, 0xFFFFFFFF, 0x60FFFFFF, 0xFFFFFFFF, 0xFFC8C8C8, 0xFFFFFF00),
	CLASSIC(0xF0100010, 0xF01E0A2A, 0xFF3A2A5A, 0x40FFFFFF, 0xFFFFFFFF, 0xFFAAAAAA, 0xFFFFAA00);

	private final Map<ColorSlot, Integer> colors;

	ThemePreset(int background, int titleBar, int border, int rowHover, int text, int textDim, int pin) {
		Map<ColorSlot, Integer> values = new EnumMap<>(ColorSlot.class);
		values.put(ColorSlot.BACKGROUND, background);
		values.put(ColorSlot.TITLE_BAR, titleBar);
		values.put(ColorSlot.BORDER, border);
		values.put(ColorSlot.ROW_HOVER, rowHover);
		values.put(ColorSlot.TEXT, text);
		values.put(ColorSlot.TEXT_DIM, textDim);
		values.put(ColorSlot.PIN, pin);
		this.colors = Collections.unmodifiableMap(values);
	}

	public int color(ColorSlot slot) {
		return colors.get(slot);
	}

	public String translationKey() {
		return "clipchat.theme." + name().toLowerCase(Locale.ROOT);
	}

	public static ThemePreset byName(String name, ThemePreset fallback) {
		if (name == null) {
			return fallback;
		}

		for (ThemePreset preset : values()) {
			if (preset.name().equalsIgnoreCase(name)) {
				return preset;
			}
		}

		return fallback;
	}
}
