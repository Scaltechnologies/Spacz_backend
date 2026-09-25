package com.spacz.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @param devCode only present when {@code spacz.otp.expose-code-in-response=true} (development)
 */
public record OtpRequestResponse(
        @Schema(example = "+91******3210") String phone,
        @Schema(example = "300") long expiresInSeconds,
        @Schema(example = "30") long resendAfterSeconds,
        @JsonInclude(JsonInclude.Include.NON_NULL) String devCode) {
}
