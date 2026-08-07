package dev.thomas7520.clipchat.clipboard.provider;

import dev.thomas7520.clipchat.clipboard.model.ProviderId;
import dev.thomas7520.clipchat.clipboard.model.ProviderState;

@FunctionalInterface
public interface ProviderListener {
	void onProviderChanged(ProviderId id, ProviderState state);
}
