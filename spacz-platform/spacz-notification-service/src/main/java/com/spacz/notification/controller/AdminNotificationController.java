package com.spacz.notification.controller;

import com.spacz.notification.config.OpenApiConfig;
import com.spacz.notification.dto.PageResponse;
import com.spacz.notification.dto.admin.AdminNotificationResponse;
import com.spacz.notification.dto.admin.PushStatusResponse;
import com.spacz.notification.dto.admin.SendNotificationRequest;
import com.spacz.notification.push.PushSender;
import com.spacz.notification.repository.DeviceTokenRepository;
import com.spacz.notification.security.AuthenticatedUser;
import com.spacz.notification.security.Role;
import com.spacz.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER)
@Tag(name = "Notifications (admin)", description = "Send announcements and check push delivery (ADMIN)")
public class AdminNotificationController {

    private final NotificationService notificationService;
    private final DeviceTokenRepository deviceTokenRepository;
    private final PushSender pushSender;

    @PostMapping("/send")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Send a notification",
            description = "To every account of an audience (USER = students, VENDOR = partners, ADMIN, ALL) or to listed userIds")
    public List<AdminNotificationResponse> send(@AuthenticationPrincipal AuthenticatedUser admin,
                                                @Valid @RequestBody SendNotificationRequest request) {
        return notificationService.send(admin, request);
    }

    @GetMapping("/sent")
    @Operation(summary = "Notifications sent by administrators, with push delivery status")
    public PageResponse<AdminNotificationResponse> sent(@ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return notificationService.sentByAdmins(pageable);
    }

    @GetMapping("/push-status")
    @Operation(summary = "Is push configured, and how many devices are registered per role")
    public PushStatusResponse pushStatus() {
        Map<String, Long> devices = new LinkedHashMap<>();
        for (Role role : Role.values()) {
            devices.put(role.name(), deviceTokenRepository.countByRole(role));
        }
        return new PushStatusResponse(pushSender.provider(), devices);
    }
}
