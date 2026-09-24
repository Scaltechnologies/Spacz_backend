package com.studyhouse.spacz.partner.exception;

/** Thrown when a requested resource (or a referenced parent) does not exist. Mapped to 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceName, Long id) {
        super(resourceName + " not found with ID: " + id);
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
