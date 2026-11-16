package com.spacz.notification.controller.internal;

import com.spacz.notification.config.OpenApiConfig;
import com.spacz.notification.dto.admin.AdminNotificationResponse;
import com.spacz.notification.dto.internal.CreateNotificationRequest;
import com.spacz.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/notifications")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.INTERNAL_KEY)
@Tag(name = "Internal", description = "Service-to-service only (X-Internal-Api-Key)")
public class InternalNotificationController {

    private final NotificationService notificationService;

    @PostMapping
    @Operation(summary = "Create a notification (and push it)",
            description = "201 when created; 200 with the original when the dedupKey was already used")
    public ResponseEntity<AdminNotificationResponse> create(
            @RequestHeader(name = "X-Source-Service", required = false) String sourceService,
            @Valid @RequestBody CreateNotificationRequest request) {
        NotificationService.Created created = notificationService.create(request, sourceService, null);
        return ResponseEntity.status(created.isNew() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(AdminNotificationResponse.from(created.notification()));
    }
}
