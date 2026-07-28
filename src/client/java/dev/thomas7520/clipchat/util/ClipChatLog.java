package dev.thomas7520.clipchat.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * ClipChat's logger. Clipboard text must never be logged directly; pass it through
 * {@link #redact(String)}, which yields only its length and a short hash.
 */
public final class ClipChatLog {
	public static final Logger LOGGER = LoggerFactory.getLogger("clipchat");

	private ClipChatLog() {
	}

	public static String redact(String text) {
		if (text == null) {
			return "<null>";
		}

		if (text.isEmpty()) {
			return "<empty>";
		}

		return "<" + text.codePointCount(0, text.length()) + " chars, sha256:" + shortHash(text) + ">";
	}

	private static String shortHash(String text) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash, 0, 4);
		} catch (NoSuchAlgorithmException e) {
			return "unavailable";
		}
	}
}
