package com.spacz.notification.dto.internal;

import com.spacz.notification.entity.Audience;
import com.spacz.notification.entity.NotificationCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Sent by other SPACZ services. A null {@code recipientUserId} broadcasts to every account of the audience.
 */
public record CreateNotificationRequest(
        @Schema(description = "Auth account ID of the recipient; null = broadcast to the audience")
        Long recipientUserId,
        @Schema(description = "Recipient's role (personal) or the broadcast target; ALL only for broadcasts")
        @NotNull Audience audience,
        @NotNull NotificationCategory category,
        @NotBlank @Size(max = 60) @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "must be UPPER_SNAKE_CASE") String type,
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 1000) String body,
        @Size(max = 500) String imageUrl,
        @Size(max = 40) String entityType,
        Long entityId,
        Map<String, String> data,
        @Schema(description = "Also send a push to the recipients' devices (default true)")
        Boolean push,
        Boolean highPriority,
        @Schema(description = "Idempotency key: a repeated request with the same key returns the first notification")
        @Size(max = 120) String dedupKey) {
}
