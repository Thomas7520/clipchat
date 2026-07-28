package dev.thomas7520.clipchat.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import dev.thomas7520.clipchat.client.ClipChatClient;
import dev.thomas7520.clipchat.ui.ClipChatConfigScreen;

public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> new ClipChatConfigScreen(parent, ClipChatClient.config());
	}
}
