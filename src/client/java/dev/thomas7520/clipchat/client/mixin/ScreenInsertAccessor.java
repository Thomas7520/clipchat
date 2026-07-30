package dev.thomas7520.clipchat.client.mixin;

import net.minecraft.client.gui.screens.Screen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Screen.class)
public interface ScreenInsertAccessor {
	@Invoker("insertText")
	void clipchat$insertText(String text, boolean overwrite);
}
