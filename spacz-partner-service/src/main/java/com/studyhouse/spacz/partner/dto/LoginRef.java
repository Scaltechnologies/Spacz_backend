package com.studyhouse.spacz.partner.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The owner's {@code userLogin}, as in the legacy API ({@code "userLogin": {"loginId": 1}}).
 * The login record itself belongs to the Auth service, so only its ID is kept here.
 */
@Schema(description = "Reference to the owner's login record (owned by the Auth service)")
public record LoginRef(@Schema(example = "1") Long loginId) {

    public static LoginRef of(Long loginId) {
        return loginId == null ? null : new LoginRef(loginId);
    }
}
