package com.spacz.auth.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base type for all expected (business) errors. The handler turns it into an {@link ApiError}
 * with the given status and machine-readable error code.
 */
@Getter
public class SpaczException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public SpaczException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public SpaczException(HttpStatus status, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }
}
