package com.spacz.user.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Private file storage for user documents. Keys are opaque; files are never served directly,
 * only through authenticated endpoints that check ownership.
 */
public interface DocumentStorage {

    /** Stores the content under {@code key}, replacing any existing file with that key. */
    void store(String key, InputStream content) throws IOException;

    InputStream open(String key) throws IOException;

    /** Deletes the file if it exists. */
    void delete(String key) throws IOException;
}
