package dev.thomas7520.clipchat.history;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HistoryLimitsTest {
	@Test
	void clampsValuesFromAHandEditedConfig() {
		HistoryLimits tooSmall = new HistoryLimits(0, 0, 0, true);

		assertEquals(1, tooSmall.maxUnpinned());
		assertEquals(1, tooSmall.maxPinned());
		assertEquals(16, tooSmall.maxEntryLength());

		HistoryLimits tooLarge = new HistoryLimits(999_999, 999_999, 999_999, true);

		assertEquals(1000, tooLarge.maxUnpinned());
		assertEquals(500, tooLarge.maxPinned());
		assertEquals(65536, tooLarge.maxEntryLength());
	}

	@Test
	void leavesValuesInRangeAlone() {
		HistoryLimits limits = new HistoryLimits(42, 7, 2048, true);

		assertEquals(42, limits.maxUnpinned());
		assertEquals(7, limits.maxPinned());
		assertEquals(2048, limits.maxEntryLength());
	}

	@Test
	void clampsNegativesRatherThanWrapping() {
		HistoryLimits limits = new HistoryLimits(-5, -5, -5, true);

		assertEquals(1, limits.maxUnpinned());
		assertEquals(1, limits.maxPinned());
		assertEquals(16, limits.maxEntryLength());
	}
}
