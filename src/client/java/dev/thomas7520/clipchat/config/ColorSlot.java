package dev.thomas7520.clipchat.config;

import java.util.Locale;

/**
 * A recolourable part of the panel. Each slot supplies its own config key and translation key,
 * so the colour screen can render the full list by iterating the values.
 */
public enum ColorSlot {
	BACKGROUND,
	TITLE_BAR,
	BORDER,
	ROW_HOVER,
	TEXT,
	TEXT_DIM,
	PIN;

	public String serializedName() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String translationKey() {
		return "clipchat.color." + serializedName();
	}

	public static ColorSlot byName(String name, ColorSlot fallback) {
		if (name == null) {
			return fallback;
		}

		for (ColorSlot slot : values()) {
			if (slot.name().equalsIgnoreCase(name)) {
				return slot;
			}
		}

		return fallback;
	}
}
