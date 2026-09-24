package com.spacz.user.exception;

import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends SpaczException {

    public DuplicateResourceException(String message) {
        super(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", message);
    }
}
