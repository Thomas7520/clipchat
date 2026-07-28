package dev.thomas7520.clipchat.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.thomas7520.clipchat.clipboard.model.ClipboardSource;
import dev.thomas7520.clipchat.persistence.HistoryStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class MinecraftClipboardProviderTest {
	private static MinecraftClipboardProvider provider(Path file) {
		return new MinecraftClipboardProvider(new HistoryStore(file), HistoryLimits.DEFAULT, text -> { });
	}

	@Test
	void capturesIntoMemoryWhilePersistenceIsOff(@TempDir Path directory) {
		Path file = directory.resolve("history.json");
		MinecraftClipboardProvider provider = provider(file);

		provider.setPersistent(false);
		provider.addText("copied", ClipboardSource.CHAT_INPUT);

		assertEquals(1, provider.snapshot().size());

		provider.close();

		assertFalse(Files.exists(file));
	}

	@Test
	void writesTheHistoryWhilePersistenceIsOn(@TempDir Path directory) {
		Path file = directory.resolve("history.json");
		MinecraftClipboardProvider provider = provider(file);

		provider.setPersistent(true);
		provider.addText("copied", ClipboardSource.CHAT_INPUT);
		provider.close();

		assertTrue(Files.exists(file));
	}

	@Test
	void switchingPersistenceOffRemovesTheFile(@TempDir Path directory) throws Exception {
		Path file = directory.resolve("history.json");
		MinecraftClipboardProvider provider = provider(file);

		provider.setPersistent(true);
		provider.addText("copied", ClipboardSource.CHAT_INPUT);
		provider.setPersistent(false);
		provider.close();

		assertEquals(1, provider.snapshot().size());
		assertFalse(Files.exists(file));
	}

	@Test
	void ignoresASavedFileWhilePersistenceIsOff(@TempDir Path directory) {
		Path file = directory.resolve("history.json");
		MinecraftClipboardProvider seed = provider(file);

		seed.setPersistent(true);
		seed.addText("copied", ClipboardSource.CHAT_INPUT);
		seed.close();

		MinecraftClipboardProvider reopened = provider(file);
		reopened.setPersistent(false);
		reopened.load();

		assertEquals(0, reopened.snapshot().size());
	}
}
