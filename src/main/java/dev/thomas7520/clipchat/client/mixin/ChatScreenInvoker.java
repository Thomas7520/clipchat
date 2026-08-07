package dev.thomas7520.clipchat.client.mixin;

import net.minecraft.client.gui.screens.ChatScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChatScreen.class)
public interface ChatScreenInvoker {
	@Invoker("insertText")
	void clipchat$insertText(String text, boolean overwrite);
}
