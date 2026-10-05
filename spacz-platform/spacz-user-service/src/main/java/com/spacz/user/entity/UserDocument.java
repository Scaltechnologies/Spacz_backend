package com.spacz.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Metadata of an uploaded identity document. The file itself is in document storage under
 * {@code storageKey}; it is only ever served to its owner through an authenticated endpoint.
 */
@Entity
@Table(name = "user_documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDocument {

    public enum Type { AADHAAR_FRONT, AADHAAR_BACK }

    public enum VerificationStatus { PENDING, VERIFIED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30, updatable = false)
    private Type documentType;

    @Column(name = "storage_key", nullable = false, length = 255)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static UserDocument create(Long userId, Type type) {
        UserDocument document = new UserDocument();
        document.userId = userId;
        document.documentType = type;
        return document;
    }

    /** Points the document at a newly stored file; a re-upload needs verification again. */
    public void replaceFile(String storageKey, String contentType, long sizeBytes, String originalFilename, Instant now) {
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.originalFilename = originalFilename;
        this.verificationStatus = VerificationStatus.PENDING;
        this.uploadedAt = now;
    }
}
