package dev.thomas7520.clipchat.clipboard.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.thomas7520.clipchat.clipboard.model.TextNormalizer.Normalized;

import org.junit.jupiter.api.Test;

import java.util.Optional;

class TextNormalizerTest {
	@Test
	void rejectsNothingWorthStoring() {
		assertTrue(TextNormalizer.normalize(null, 100, true).isEmpty());
		assertTrue(TextNormalizer.normalize("", 100, true).isEmpty());
		assertTrue(TextNormalizer.normalize("   \n\t ", 100, true).isEmpty());
	}

	@Test
	void keepsBlankTextWhenNotRejectingIt() {
		assertEquals("   ", TextNormalizer.normalize("   ", 100, false).orElseThrow().text());
	}

	@Test
	void collapsesEveryLineEndingToNewline() {
		assertEquals("a\nb\nc", normalize("a\r\nb\rc", 100).text());
	}

	/** Truncation counts code points, so a surrogate pair is never cut in half. */
	@Test
	void truncatesOnCodePointsNotCharacters() {
		Normalized result = normalize("😀😀😀", 2);

		assertEquals("😀😀", result.text());
		assertEquals(4, result.text().length());
	}

	@Test
	void reportsTheLengthBeforeTruncation() {
		Normalized result = normalize("abcdef", 3);

		assertEquals("abc", result.text());
		assertEquals(6, result.originalLength());
		assertTrue(result.truncated());
	}

	@Test
	void doesNotReportUntruncatedTextAsTruncated() {
		assertFalse(normalize("abc", 100).truncated());
	}

	@Test
	void treatsANonPositiveLimitAsNoLimit() {
		assertEquals("abcdef", normalize("abcdef", 0).text());
	}

	private static Normalized normalize(String raw, int maxCodePoints) {
		Optional<Normalized> result = TextNormalizer.normalize(raw, maxCodePoints, true);
		return result.orElseThrow();
	}
}
