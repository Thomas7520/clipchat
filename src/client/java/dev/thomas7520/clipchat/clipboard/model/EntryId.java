package dev.thomas7520.clipchat.clipboard.model;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Identifies an entry within one provider. Minecraft ids are generated locally; Windows ids are
 * the opaque strings the operating system supplies.
 */
public record EntryId(ProviderId provider, String value) {
	public EntryId {
		Objects.requireNonNull(provider, "provider");
		Objects.requireNonNull(value, "value");
	}

	public static EntryId newMinecraftId() {
		long time = System.currentTimeMillis();
		long random = ThreadLocalRandom.current().nextLong();
		return new EntryId(ProviderId.MINECRAFT,
				Long.toString(time, 36) + "-" + Long.toUnsignedString(random, 36));
	}

	public static EntryId windows(String nativeId) {
		return new EntryId(ProviderId.WINDOWS, nativeId);
	}
}
