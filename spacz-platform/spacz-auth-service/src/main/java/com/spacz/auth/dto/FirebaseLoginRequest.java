package com.spacz.auth.dto;

import com.spacz.auth.security.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = """
        Logs in with a phone number verified by Firebase Phone Auth. After the user enters the SMS code
        in the app, send `await firebase.auth().currentUser.getIdToken()` as `idToken`. If no account
        exists for the phone, the call answers 422 REGISTRATION_REQUIRED; repeat it with `role`
        (USER or VENDOR) and, for USER, `firstName`. The ID token is valid for an hour.""")
public record FirebaseLoginRequest(
        @Schema(description = "Firebase ID token") @NotBlank @Size(max = 4096) String idToken,
        @Schema(description = "Only for registration") Role role,
        @Size(max = 80) String firstName,
        @Size(max = 80) String lastName,
        @Size(max = 80) String city,
        @Size(max = 150) String businessName,
        @Size(max = 120) String contactName) {
}
