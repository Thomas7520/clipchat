package dev.thomas7520.clipchat.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ColorParsingTest {
	@Test
	void treatsSixDigitColoursAsFullyOpaque() {
		assertEquals(0xFF112233, ConfigStore.parseArgb("#112233"));
	}

	@Test
	void keepsTheAlphaOfEightDigitColours() {
		assertEquals(0x80112233, ConfigStore.parseArgb("#80112233"));
	}

	@Test
	void acceptsColoursWithoutAHashAndWithSurroundingSpace() {
		assertEquals(0xFF112233, ConfigStore.parseArgb("  112233  "));
	}

	@Test
	void isCaseInsensitive() {
		assertEquals(ConfigStore.parseArgb("#aabbcc"), ConfigStore.parseArgb("#AABBCC"));
	}

	@Test
	void rejectsAnythingThatIsNotAColour() {
		assertNull(ConfigStore.parseArgb(null));
		assertNull(ConfigStore.parseArgb(""));
		assertNull(ConfigStore.parseArgb("#12345"));
		assertNull(ConfigStore.parseArgb("#123456789"));
		assertNull(ConfigStore.parseArgb("#GGGGGG"));
	}

	/** An alpha above 0x7F makes the packed int negative and must still round trip. */
	@Test
	void roundTripsColoursWithTheHighBitSet() {
		assertEquals("#FF000000", ConfigStore.formatArgb(0xFF000000));
		assertEquals(0xFF000000, ConfigStore.parseArgb(ConfigStore.formatArgb(0xFF000000)));
		assertEquals(0xFFFFFFFF, ConfigStore.parseArgb(ConfigStore.formatArgb(0xFFFFFFFF)));
	}
}
