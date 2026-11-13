package com.spacz.user.service.impl;

import com.spacz.user.dto.UserDocumentResponse;
import com.spacz.user.entity.UserDocument;
import com.spacz.user.exception.BusinessRuleException;
import com.spacz.user.exception.ResourceNotFoundException;
import com.spacz.user.exception.SpaczException;
import com.spacz.user.repository.UserDocumentRepository;
import com.spacz.user.service.UserDocumentService;
import com.spacz.user.storage.DocumentStorage;
import com.spacz.user.storage.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDocumentServiceImpl implements UserDocumentService {

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private final UserDocumentRepository documentRepository;
    private final DocumentStorage storage;
    private final StorageProperties storageProperties;

    @Override
    @Transactional(readOnly = true)
    public List<UserDocumentResponse> list(Long userId) {
        return documentRepository.findByUserId(userId).stream().map(UserDocumentResponse::from).toList();
    }

    /**
     * The file type is decided from its content (magic bytes), not the client's Content-Type. The new
     * file is stored before the database row changes; the replaced file is deleted only after commit.
     */
    @Override
    @Transactional
    public UserDocumentResponse upload(Long userId, UserDocument.Type type, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("EMPTY_FILE", "Please choose an image to upload");
        }
        if (file.getSize() > storageProperties.maxFileBytes()) {
            throw new SpaczException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                    "The image must be at most " + (storageProperties.maxFileBytes() / 1024) + " KB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessRuleException("UPLOAD_FAILED", "The file could not be read");
        }
        String contentType = detectImageType(bytes);
        if (contentType == null) {
            throw new BusinessRuleException("UNSUPPORTED_FILE_TYPE", "Only JPEG and PNG images are allowed");
        }

        String extension = contentType.equals("image/png") ? "png" : "jpg";
        String key = userId + "/" + type.name().toLowerCase() + "-" + UUID.randomUUID() + "." + extension;
        try {
            storage.store(key, new ByteArrayInputStream(bytes));
        } catch (IOException ex) {
            log.error("Storing document {} for user {} failed", type, userId, ex);
            throw new SpaczException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR",
                    "The document could not be saved. Please try again.");
        }

        UserDocument document = documentRepository.findByUserIdAndDocumentType(userId, type)
                .orElseGet(() -> UserDocument.create(userId, type));
        String previousKey = document.getStorageKey();
        document.replaceFile(key, contentType, bytes.length, safeFilename(file.getOriginalFilename()), Instant.now());
        try {
            document = documentRepository.saveAndFlush(document);
        } catch (RuntimeException ex) {
            deleteQuietly(key);
            throw ex;
        }
        afterCommit(() -> {
            if (previousKey != null && !previousKey.equals(key)) {
                deleteQuietly(previousKey);
            }
        }, () -> deleteQuietly(key));
        // Metadata only — never the image contents.
        log.info("Document {} stored for user {} ({} bytes, {}, {})", type, userId, bytes.length, contentType,
                previousKey == null ? "new" : "replaced");
        return UserDocumentResponse.from(document);
    }

    @Override
    @Transactional(readOnly = true)
    public StoredFile open(Long userId, UserDocument.Type type) {
        UserDocument document = documentRepository.findByUserIdAndDocumentType(userId, type)
                .orElseThrow(() -> new ResourceNotFoundException("Document", type));
        try {
            return new StoredFile(document.getContentType(), document.getSizeBytes(), storage.open(document.getStorageKey()));
        } catch (IOException ex) {
            log.error("Document file {} missing for user {}", document.getStorageKey(), userId, ex);
            throw new ResourceNotFoundException("The document file is missing. Please upload it again.");
        }
    }

    static String detectImageType(byte[] bytes) {
        if (startsWith(bytes, JPEG_MAGIC)) {
            return "image/jpeg";
        }
        if (startsWith(bytes, PNG_MAGIC)) {
            return "image/png";
        }
        return null;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
    }

    private static String safeFilename(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        return base.length() > 255 ? base.substring(0, 255) : base;
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (IOException | RuntimeException ex) {
            log.warn("Could not delete stored document {}", key, ex);
        }
    }

    private static void afterCommit(Runnable onCommit, Runnable onRollback) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            onCommit.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    onCommit.run();
                } else {
                    onRollback.run();
                }
            }
        });
    }
}
