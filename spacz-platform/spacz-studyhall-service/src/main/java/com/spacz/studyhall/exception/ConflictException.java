package com.spacz.studyhall.exception;

import org.springframework.http.HttpStatus;

/**
 * The request conflicts with the current state of a resource (HTTP 409), e.g. a seat that is already booked.
 */
public class ConflictException extends SpaczException {

    public ConflictException(String errorCode, String message) {
        super(HttpStatus.CONFLICT, errorCode, message);
    }
}
