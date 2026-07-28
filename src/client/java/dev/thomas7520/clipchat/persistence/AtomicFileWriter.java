package dev.thomas7520.clipchat.persistence;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Writes to a sibling temp file, forces it to disk, then renames over the target, so an
 * interrupted write leaves either the previous file or the new one, never a partial one.
 */
public final class AtomicFileWriter {
	private AtomicFileWriter() {
	}

	public static void write(Path target, String content) throws IOException {
		Path parent = target.getParent();

		if (parent != null) {
			Files.createDirectories(parent);
		}

		Path temp = target.resolveSibling(target.getFileName() + ".tmp");

		try (FileChannel channel = FileChannel.open(temp,
				StandardOpenOption.CREATE,
				StandardOpenOption.WRITE,
				StandardOpenOption.TRUNCATE_EXISTING)) {
			channel.write(StandardCharsets.UTF_8.encode(content));
			channel.force(true);
		}

		try {
			Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/** Moves an unreadable file into a {@code backups} subdirectory and returns its new path. */
	public static Path quarantine(Path target) throws IOException {
		Path backups = target.resolveSibling("backups");
		Files.createDirectories(backups);
		Path destination = backups.resolve(target.getFileName() + ".corrupt-" + System.currentTimeMillis());
		Files.move(target, destination, StandardCopyOption.REPLACE_EXISTING);
		return destination;
	}
}
