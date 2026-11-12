package es.uma.urbanpulse.incident;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FileSystemPhotoStorage implements PhotoStorage {

	private final Path rootDirectory;

	public FileSystemPhotoStorage(@Value("${urbanpulse.storage.photos-dir:./data/photos}") Path rootDirectory) {
		this.rootDirectory = rootDirectory.toAbsolutePath().normalize();
	}

	@Override
	public void store(String storageKey, byte[] content, String contentType) {
		Path target = resolvePath(storageKey);
		try {
			Files.createDirectories(target.getParent());
			Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		}
		catch (IOException exception) {
			throw new UncheckedIOException("Could not store incident photo at " + storageKey, exception);
		}
	}

	@Override
	public byte[] read(String storageKey) {
		Path target = resolvePath(storageKey);
		try {
			return Files.readAllBytes(target);
		}
		catch (NoSuchFileException exception) {
			throw new UncheckedIOException("Stored incident photo not found at " + storageKey, exception);
		}
		catch (IOException exception) {
			throw new UncheckedIOException("Could not read incident photo at " + storageKey, exception);
		}
	}

	@Override
	public void delete(String storageKey) {
		Path target = resolvePath(storageKey);
		try {
			Files.deleteIfExists(target);
		}
		catch (IOException exception) {
			throw new UncheckedIOException("Could not delete incident photo at " + storageKey, exception);
		}
	}

	private Path resolvePath(String storageKey) {
		if (storageKey == null || storageKey.isBlank()) {
			throw new IllegalArgumentException("Storage key must not be blank");
		}
		Path resolved = this.rootDirectory.resolve(storageKey).normalize();
		if (!resolved.startsWith(this.rootDirectory)) {
			throw new IllegalArgumentException("Storage key escapes root directory: " + storageKey);
		}
		return resolved;
	}

}
