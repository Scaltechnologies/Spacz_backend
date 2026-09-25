package com.spacz.user.exception;

import org.springframework.http.HttpStatus;

/**
 * A downstream SPACZ service could not be reached or failed.
 */
public class ServiceUnavailableException extends SpaczException {

    public ServiceUnavailableException(String service, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                service + " is currently unavailable. Please retry shortly.", cause);
    }

    public ServiceUnavailableException(String service, String detail) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", service + " is currently unavailable: " + detail);
    }
}
