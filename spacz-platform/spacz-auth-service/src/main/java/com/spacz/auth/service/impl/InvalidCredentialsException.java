package com.spacz.auth.service.impl;

import com.spacz.auth.exception.SpaczException;
import org.springframework.http.HttpStatus;

/**
 * Deliberately generic: never reveals whether the email exists.
 */
public class InvalidCredentialsException extends SpaczException {

    public InvalidCredentialsException(String message) {
        super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", message);
    }
}
