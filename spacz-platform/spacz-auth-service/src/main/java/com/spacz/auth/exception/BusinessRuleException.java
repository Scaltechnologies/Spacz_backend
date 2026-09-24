package com.spacz.auth.exception;

import org.springframework.http.HttpStatus;

/**
 * A request that is well-formed but not allowed in the current state (HTTP 422).
 */
public class BusinessRuleException extends SpaczException {

    public BusinessRuleException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION", message);
    }

    public BusinessRuleException(String errorCode, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, errorCode, message);
    }
}
