package com.spacz.user.dto;

import com.spacz.user.entity.UserDocument;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * @param fileUrl gateway path of the image; requires the owner's bearer token
 */
public record UserDocumentResponse(Long id, UserDocument.Type documentType, String contentType, long sizeBytes,
                                   UserDocument.VerificationStatus verificationStatus, Instant uploadedAt,
                                   Instant updatedAt,
                                   @Schema(example = "/api/users/me/documents/AADHAAR_FRONT/file") String fileUrl) {

    public static UserDocumentResponse from(UserDocument document) {
        return new UserDocumentResponse(document.getId(), document.getDocumentType(), document.getContentType(),
                document.getSizeBytes(), document.getVerificationStatus(), document.getUploadedAt(),
                document.getUpdatedAt(), "/api/users/me/documents/" + document.getDocumentType() + "/file");
    }
}
