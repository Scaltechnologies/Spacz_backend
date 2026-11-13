-- Identity documents uploaded by students (metadata only; the files live in document storage)
-- and app feedback.

CREATE TABLE user_documents (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT       NOT NULL,
    document_type       VARCHAR(30)  NOT NULL,
    storage_key         VARCHAR(255) NOT NULL,
    content_type        VARCHAR(50)  NOT NULL,
    size_bytes          BIGINT       NOT NULL,
    original_filename   VARCHAR(255),
    verification_status VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    uploaded_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_user_documents_type UNIQUE (user_id, document_type),
    CONSTRAINT ck_user_documents_type CHECK (document_type IN ('AADHAAR_FRONT', 'AADHAAR_BACK')),
    CONSTRAINT ck_user_documents_status CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    CONSTRAINT ck_user_documents_size CHECK (size_bytes > 0)
);

CREATE TABLE feedback (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT        NOT NULL,
    rating     SMALLINT      NOT NULL,
    comment    VARCHAR(1000),
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_feedback_rating CHECK (rating BETWEEN 1 AND 5)
);
CREATE INDEX idx_feedback_user ON feedback (user_id, created_at DESC);
