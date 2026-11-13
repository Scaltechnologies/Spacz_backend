package com.spacz.studyhall.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * File storage for study hall photos uploaded by vendors (same contract as the user-service document
 * storage). Keys are opaque ({@code <hallId>/<uuid>.<ext>}); files are served read-only through
 * {@code GET /api/studyhalls/media/{hallId}/{file}}.
 */
public interface HallImageStorage {

    /** Stores the content under {@code key}, replacing any existing file with that key. */
    void store(String key, InputStream content) throws IOException;

    InputStream open(String key) throws IOException;

    long size(String key) throws IOException;

    /** Deletes the file if it exists. */
    void delete(String key) throws IOException;
}
