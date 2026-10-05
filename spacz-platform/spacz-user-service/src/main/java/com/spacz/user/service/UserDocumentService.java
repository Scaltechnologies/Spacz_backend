package com.spacz.user.service;

import com.spacz.user.dto.UserDocumentResponse;
import com.spacz.user.entity.UserDocument;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

public interface UserDocumentService {

    List<UserDocumentResponse> list(Long userId);

    /** Uploads or replaces the user's document of this type (JPEG/PNG only). */
    UserDocumentResponse upload(Long userId, UserDocument.Type type, MultipartFile file);

    /** The stored file of the caller's own document. */
    StoredFile open(Long userId, UserDocument.Type type);

    record StoredFile(String contentType, long sizeBytes, InputStream content) {
    }
}
