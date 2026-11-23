package com.spacz.studyhall.payment.exception;

import com.spacz.studyhall.exception.SpaczException;
import com.spacz.studyhall.payment.enums.PaymentErrorCode;
import lombok.Getter;

/** Rendered by the existing GlobalExceptionHandler as ApiError {status, error = code name, message}. */
@Getter
public class PaymentException extends SpaczException {

    private final PaymentErrorCode code;

    public PaymentException(PaymentErrorCode code, String message) {
        super(code.httpStatus(), code.name(), message);
        this.code = code;
    }
}
