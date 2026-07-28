package dev.thomas7520.clipchat.ui;

import dev.thomas7520.clipchat.clipboard.model.ClipboardSource;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractCommandBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.ChatScreen;

/**
 * Maps the screen that was open when text was copied to a {@link ClipboardSource}. Screens that
 * match nothing resolve to {@code UNKNOWN}.
 */
public final class ScreenSourceResolver {
	private ScreenSourceResolver() {
	}

	public static ClipboardSource sourceFor(Screen screen) {
		return switch (screen) {
			case null -> ClipboardSource.UNKNOWN;
			case ChatScreen ignored -> ClipboardSource.CHAT_INPUT;
			case BookEditScreen ignored -> ClipboardSource.BOOK;
			case AbstractSignEditScreen ignored -> ClipboardSource.SIGN;
			case AbstractCommandBlockEditScreen ignored -> ClipboardSource.COMMAND_SCREEN;
			default -> ClipboardSource.UNKNOWN;
		};
	}
}
