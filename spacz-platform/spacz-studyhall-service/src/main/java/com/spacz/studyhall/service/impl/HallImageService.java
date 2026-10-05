package com.spacz.studyhall.service.impl;

import com.spacz.studyhall.config.StudyHallProperties;
import com.spacz.studyhall.dto.hall.StudyHallDetailResponse;
import com.spacz.studyhall.entity.StudyHall;
import com.spacz.studyhall.entity.StudyHallImage;
import com.spacz.studyhall.exception.BusinessRuleException;
import com.spacz.studyhall.exception.ResourceNotFoundException;
import com.spacz.studyhall.exception.SpaczException;
import com.spacz.studyhall.repository.StudyHallRepository;
import com.spacz.studyhall.service.support.VendorAccess;
import com.spacz.studyhall.storage.HallImageStorage;
import com.spacz.studyhall.storage.HallImageStorageProperties;
import com.spacz.studyhall.storage.MediaUrls;
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
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Study hall photos uploaded by the vendor app: validated (JPEG/PNG by content), stored in
 * {@link HallImageStorage} and attached to the hall as a host-agnostic {@code /api/studyhalls/media/...}
 * path. Mirrors the user-service document upload.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HallImageService {

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final Pattern FILE_NAME = Pattern.compile("^[0-9a-f-]{36}[.](jpg|png)$");

    private final VendorAccess vendorAccess;
    private final StudyHallRepository studyHallRepository;
    private final HallViewAssembler assembler;
    private final StudyHallProperties properties;
    private final HallImageStorageProperties storageProperties;
    private final HallImageStorage storage;

    public record StoredImage(String contentType, long sizeBytes, InputStream content) {
    }

    @Transactional
    public StudyHallDetailResponse upload(Long vendorId, Long studyHallId, MultipartFile file, String caption, boolean cover) {
        StudyHall hall = vendorAccess.writableHall(vendorId, studyHallId);
        if (hall.getImages().size() >= properties.maxImages()) {
            throw new BusinessRuleException("A study hall can have at most " + properties.maxImages() + " images");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("EMPTY_FILE", "Please choose an image to upload");
        }
        if (file.getSize() > storageProperties.maxImageBytes()) {
            throw new SpaczException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                    "The image must be at most " + (storageProperties.maxImageBytes() / 1024) + " KB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessRuleException("UPLOAD_FAILED", "The file could not be read");
        }
        String extension = startsWith(bytes, JPEG_MAGIC) ? "jpg" : startsWith(bytes, PNG_MAGIC) ? "png" : null;
        if (extension == null) {
            throw new BusinessRuleException("UNSUPPORTED_FILE_TYPE", "Only JPEG and PNG images are allowed");
        }

        String key = hall.getId() + "/" + UUID.randomUUID() + "." + extension;
        try {
            storage.store(key, new ByteArrayInputStream(bytes));
        } catch (IOException ex) {
            log.error("Storing image for study hall {} failed", hall.getId(), ex);
            throw new SpaczException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", "The image could not be saved. Please try again.");
        }
        // the file is removed again if the database change does not commit
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteQuietly(key);
                }
            }
        });

        boolean makeCover = cover || hall.getImages().isEmpty();
        if (makeCover) {
            hall.getImages().forEach(image -> image.setCover(false));
        }
        hall.getImages().add(StudyHallImage.of(hall, MediaUrls.forKey(key), trimToNull(caption), hall.getImages().size(), makeCover));
        log.info("Image stored for study hall {} ({} bytes, {})", hall.getId(), bytes.length, extension);
        return assembler.detail(studyHallRepository.saveAndFlush(hall));
    }

    /** An uploaded photo, public like the hall's other details. */
    @Transactional(readOnly = true)
    public StoredImage open(Long studyHallId, String fileName) {
        if (!FILE_NAME.matcher(fileName).matches()) {
            throw new ResourceNotFoundException("Image", fileName);
        }
        String key = studyHallId + "/" + fileName;
        try {
            return new StoredImage(fileName.endsWith(".png") ? "image/png" : "image/jpeg", storage.size(key), storage.open(key));
        } catch (IOException ex) {
            throw new ResourceNotFoundException("Image", fileName);
        }
    }

    /** Deletes an uploaded photo's file after its row is gone (external URLs are left alone). */
    public void deleteFileAfterCommit(String storedUrl) {
        String key = MediaUrls.keyOf(storedUrl);
        if (key == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteQuietly(key);
                }
            });
        } else {
            deleteQuietly(key);
        }
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (IOException | IllegalArgumentException ex) {
            log.warn("Could not delete hall image {}", key, ex);
        }
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
