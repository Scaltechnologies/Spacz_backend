package com.studyhouse.spacz.partner.exception;

/** Thrown when a request conflicts with existing data (e.g. duplicate seat number). Mapped to 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
