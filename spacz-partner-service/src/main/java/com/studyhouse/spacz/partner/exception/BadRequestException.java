package com.studyhouse.spacz.partner.exception;

/** Thrown for semantically invalid requests (e.g. a missing parent ID). Mapped to 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
