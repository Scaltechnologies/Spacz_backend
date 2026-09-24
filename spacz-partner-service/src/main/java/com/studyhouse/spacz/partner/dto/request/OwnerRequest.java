package com.studyhouse.spacz.partner.dto.request;

import com.studyhouse.spacz.partner.dto.LoginRef;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Owner (partner). Same fields as the legacy API. On PUT, name/email/phone/address are "
        + "replaced with the values sent; `userLogin` is only used on create.")
public record OwnerRequest(
        @Schema(example = "Ravi Kumar") @Size(max = 255) String ownerName,
        @Schema(example = "ravi@example.com") @Size(max = 255) String ownerEmail,
        @Schema(example = "9876543210") @Size(max = 255) String ownerPhoneNumber,
        @Schema(example = "12 MG Road, Hyderabad") @Size(max = 255) String address,
        @Schema(description = "Optional link to the owner's login record") LoginRef userLogin) {

    public Long loginId() {
        return userLogin == null ? null : userLogin.loginId();
    }
}
