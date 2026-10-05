package com.spacz.user.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Filesystem-backed {@link DocumentStorage}. Writes go to a temp file first and are moved into
 * place atomically, so a failed upload never leaves a half-written document.
 */
@Slf4j
@Component
public class LocalDocumentStorage implements DocumentStorage {

    private final Path root;

    public LocalDocumentStorage(StorageProperties properties) throws IOException {
        this.root = Path.of(properties.documentsDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        log.info("User documents stored in {}", root);
    }

    @Override
    public void store(String key, InputStream content) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
        try {
            Files.copy(content, temp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Override
    public InputStream open(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    /** Rejects keys that would escape the storage root. */
    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }
}
