package dev.thomas7520.clipchat.client.mixin;

import dev.thomas7520.clipchat.client.ClipChatClient;

import net.minecraft.client.KeyboardHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reports every clipboard write to ClipChat. All of Minecraft's copy paths — chat, text fields,
 * books, signs, command screens — call {@code setClipboard}; pasting calls {@code getClipboard}
 * and is not hooked.
 */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
	@Inject(method = "setClipboard(Ljava/lang/String;)V", at = @At("HEAD"))
	private void clipchat$captureCopy(String text, CallbackInfo info) {
		ClipChatClient.onClipboardWrite(text);
	}
}
