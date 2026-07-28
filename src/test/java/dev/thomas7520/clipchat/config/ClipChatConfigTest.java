package dev.thomas7520.clipchat.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.Map;

class ClipChatConfigTest {
	@Test
	void bothPrivacySensitiveFeaturesAreOffByDefault() {
		assertFalse(ClipChatConfig.DEFAULT.captureEnabled());
		assertFalse(ClipChatConfig.DEFAULT.windowsHistoryEnabled());
	}

	@Test
	void clampsLimitsThroughTheSameRulesAsTheHistory() {
		ClipChatConfig config = ClipChatConfig.DEFAULT.withLimits(0, 0, 0);

		assertEquals(1, config.maxUnpinned());
		assertEquals(1, config.maxPinned());
		assertEquals(16, config.maxEntryLength());
		assertEquals(config.maxUnpinned(), config.limits().maxUnpinned());
	}

	@Test
	void anOverrideWinsOverThePreset() {
		ClipChatConfig config = ClipChatConfig.DEFAULT.withOverride(ColorSlot.TEXT, 0xFF00FF00);

		assertTrue(config.isOverridden(ColorSlot.TEXT));
		assertEquals(0xFF00FF00, config.color(ColorSlot.TEXT));
		assertFalse(config.isOverridden(ColorSlot.BACKGROUND));
		assertEquals(ThemePreset.DARK.color(ColorSlot.BACKGROUND), config.color(ColorSlot.BACKGROUND));
	}

	@Test
	void aNullOverrideFallsBackToThePreset() {
		ClipChatConfig config = ClipChatConfig.DEFAULT
				.withOverride(ColorSlot.TEXT, 0xFF00FF00)
				.withOverride(ColorSlot.TEXT, null);

		assertFalse(config.isOverridden(ColorSlot.TEXT));
		assertEquals(ThemePreset.DARK.color(ColorSlot.TEXT), config.color(ColorSlot.TEXT));
	}

	/** Overrides outrank the preset, so switching theme leaves them in place. */
	@Test
	void changingPresetKeepsOverrides() {
		ClipChatConfig config = ClipChatConfig.DEFAULT
				.withOverride(ColorSlot.TEXT, 0xFF00FF00)
				.withTheme(ThemePreset.LIGHT);

		assertEquals(ThemePreset.LIGHT, config.theme());
		assertEquals(0xFF00FF00, config.color(ColorSlot.TEXT));
	}

	@Test
	void clearingOverridesReturnsEveryColourToThePreset() {
		ClipChatConfig config = ClipChatConfig.DEFAULT
				.withOverride(ColorSlot.TEXT, 0xFF00FF00)
				.withoutOverrides();

		assertTrue(config.overrides().isEmpty());
		assertEquals(ThemePreset.DARK.color(ColorSlot.TEXT), config.color(ColorSlot.TEXT));
	}

	@Test
	void overridesCannotBeMutatedThroughTheAccessor() {
		ClipChatConfig config = ClipChatConfig.DEFAULT.withOverride(ColorSlot.TEXT, 0xFF00FF00);

		assertThrows(UnsupportedOperationException.class, () -> config.overrides().clear());
	}

	@Test
	void toleratesANullOverrideMap() {
		ClipChatConfig config = new ClipChatConfig(null, null, false, false, true, 100, 50, 4096);

		assertEquals(ThemePreset.DARK, config.theme());
		assertEquals(Map.of(), config.overrides());
	}
}
