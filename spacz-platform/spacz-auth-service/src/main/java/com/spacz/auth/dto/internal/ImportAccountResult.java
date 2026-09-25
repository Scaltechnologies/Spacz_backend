package com.spacz.auth.dto.internal;

public record ImportAccountResult(Long accountId, boolean created, String role) {
}
