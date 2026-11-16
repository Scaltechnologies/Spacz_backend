package com.spacz.notification.dto.admin;

import com.spacz.notification.entity.Audience;
import com.spacz.notification.entity.NotificationCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * A notification written by an administrator: to specific accounts ({@code userIds}) or to every
 * account of an {@code audience}.
 */
public record SendNotificationRequest(
        @Schema(description = "USER (students), VENDOR (partners), ADMIN or ALL. Ignored when userIds is given.")
        Audience audience,
        @Schema(description = "Send to these accounts only (max 500)")
        @Size(max = 500) List<@NotNull Long> userIds,
        @Schema(description = "Default ANNOUNCEMENT")
        NotificationCategory category,
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 1000) String body,
        @Size(max = 500) String imageUrl,
        Map<String, String> data,
        @Schema(description = "Also push to devices (default true)")
        Boolean push,
        Boolean highPriority) {

    @Schema(hidden = true)
    @AssertTrue(message = "Give either an audience or a list of userIds")
    public boolean isTargeted() {
        return audience != null || (userIds != null && !userIds.isEmpty());
    }
}
