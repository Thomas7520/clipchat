package dev.thomas7520.clipchat.clipboard.model;

import java.util.Optional;

/**
 * Converts raw copied text into the canonical form ClipChat stores: line endings normalised to
 * {@code \n}, blank input rejected, and length capped in codepoints.
 */
public final class TextNormalizer {
	private TextNormalizer() {
	}

	/**
	 * @param text           canonical text, capped to the configured codepoint limit
	 * @param originalLength codepoint count of the input before truncation
	 */
	public record Normalized(String text, int originalLength) {
		public boolean truncated() {
			return originalLength > text.codePointCount(0, text.length());
		}
	}

	public static Optional<Normalized> normalize(String raw, int maxCodePoints, boolean rejectBlank) {
		if (raw == null || raw.isEmpty()) {
			return Optional.empty();
		}

		String text = raw.replace("\r\n", "\n").replace('\r', '\n');

		if (text.isEmpty() || (rejectBlank && text.isBlank())) {
			return Optional.empty();
		}

		int codePoints = text.codePointCount(0, text.length());

		if (maxCodePoints > 0 && codePoints > maxCodePoints) {
			// offsetByCodePoints keeps surrogate pairs intact; substring(0, max) would not.
			text = text.substring(0, text.offsetByCodePoints(0, maxCodePoints));
		}

		return Optional.of(new Normalized(text, codePoints));
	}
}
