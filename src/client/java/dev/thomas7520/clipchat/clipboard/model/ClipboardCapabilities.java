package dev.thomas7520.clipchat.clipboard.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public record ClipboardCapabilities(Set<ClipboardCapability> supported) {
	public ClipboardCapabilities {
		supported = Collections.unmodifiableSet(EnumSet.copyOf(supported));
	}

	public static ClipboardCapabilities of(ClipboardCapability... capabilities) {
		EnumSet<ClipboardCapability> set = EnumSet.noneOf(ClipboardCapability.class);
		Collections.addAll(set, capabilities);
		return new ClipboardCapabilities(set);
	}

	public boolean has(ClipboardCapability capability) {
		return supported.contains(capability);
	}
}
