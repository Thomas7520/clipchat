package dev.thomas7520.clipchat.clipboard.model;

public enum ProviderId {
	MINECRAFT("minecraft"),
	WINDOWS("windows");

	private final String serializedName;

	ProviderId(String serializedName) {
		this.serializedName = serializedName;
	}

	public String serializedName() {
		return serializedName;
	}

	public static ProviderId byName(String name, ProviderId fallback) {
		for (ProviderId id : values()) {
			if (id.serializedName.equalsIgnoreCase(name)) {
				return id;
			}
		}

		return fallback;
	}
}
