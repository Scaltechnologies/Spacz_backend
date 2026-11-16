package com.spacz.notification.controller;

import com.spacz.notification.config.OpenApiConfig;
import com.spacz.notification.dto.CategoryResponse;
import com.spacz.notification.dto.DeviceResponse;
import com.spacz.notification.dto.NotificationResponse;
import com.spacz.notification.dto.PageResponse;
import com.spacz.notification.dto.PreferenceResponse;
import com.spacz.notification.dto.RegisterDeviceRequest;
import com.spacz.notification.dto.UnreadCountResponse;
import com.spacz.notification.dto.UnregisterDeviceRequest;
import com.spacz.notification.dto.UpdatePreferencesRequest;
import com.spacz.notification.entity.NotificationCategory;
import com.spacz.notification.security.AuthenticatedUser;
import com.spacz.notification.service.DeviceService;
import com.spacz.notification.service.NotificationService;
import com.spacz.notification.service.PreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The same endpoints serve every app: the student app (USER), the partner app (VENDOR) and the
 * admin panel (ADMIN). Everything acts on the signed-in account.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app inbox, push devices and push preferences of the signed-in account")
public class NotificationController {

    private final NotificationService notificationService;
    private final DeviceService deviceService;
    private final PreferenceService preferenceService;

    @GetMapping("/categories")
    @Operation(summary = "Notification categories (public)", description = "Labels and Android channel IDs, for settings screens")
    public List<CategoryResponse> categories() {
        return Arrays.stream(NotificationCategory.values()).map(CategoryResponse::from).toList();
    }

    // ---- inbox ----

    @GetMapping("/inbox")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(summary = "My notifications, newest first", description = "Personal notifications plus announcements for my role")
    public PageResponse<NotificationResponse> inbox(@AuthenticationPrincipal AuthenticatedUser caller,
                                                    @RequestParam(required = false) NotificationCategory category,
                                                    @Parameter(description = "Only unread notifications")
                                                    @RequestParam(defaultValue = "false") boolean unreadOnly,
                                                    @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return notificationService.inbox(caller, category, unreadOnly, pageable);
    }

    @GetMapping("/inbox/unread-count")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(summary = "Number of unread notifications (badge)")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal AuthenticatedUser caller) {
        return new UnreadCountResponse(notificationService.unreadCount(caller));
    }

    @GetMapping("/inbox/{id}")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(summary = "One notification (e.g. opened from a push)")
    public NotificationResponse get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long id) {
        return notificationService.get(caller, id);
    }

    @PostMapping("/inbox/{id}/read")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(summary = "Mark one notification as read")
    public NotificationResponse markRead(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long id) {
        return notificationService.markRead(caller, id);
    }

    @PostMapping("/inbox/read-all")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(summary = "Mark every notification as read")
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal AuthenticatedUser caller) {
        return Map.of("updated", notificationService.markAllRead(caller));
    }

    @DeleteMapping("/inbox/{id}")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(summary = "Remove a notification from my inbox")
    public ResponseEntity<Void> dismiss(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long id) {
        notificationService.dismiss(caller, id);
        return ResponseEntity.noContent().build();
    }

    // ---- push devices ----

    @PostMapping("/devices")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(summary = "Register / refresh this device for push",
            description = "Call after login, on every app start and when Firebase issues a new token")
    public DeviceResponse registerDevice(@AuthenticationPrincipal AuthenticatedUser caller,
                                         @Valid @RequestBody RegisterDeviceRequest request) {
        return deviceService.register(caller, request);
    }

    @PostMapping("/devices/unregister")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(summary = "Stop push to this device (call on logout, before discarding the access token)")
    public ResponseEntity<Void> unregisterDevice(@AuthenticationPrincipal AuthenticatedUser caller,
                                                 @Valid @RequestBody UnregisterDeviceRequest request) {
        deviceService.unregister(caller, request.token());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/devices")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(summary = "My registered devices")
    public List<DeviceResponse> devices(@AuthenticationPrincipal AuthenticatedUser caller) {
        return deviceService.mine(caller);
    }

    // ---- preferences ----

    @GetMapping("/preferences")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(summary = "Push on/off per category", description = "In-app notifications are always kept")
    public List<PreferenceResponse> preferences(@AuthenticationPrincipal AuthenticatedUser caller) {
        return preferenceService.list(caller.userId());
    }

    @PutMapping("/preferences")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(summary = "Turn push on/off for some categories")
    public List<PreferenceResponse> updatePreferences(@AuthenticationPrincipal AuthenticatedUser caller,
                                                      @Valid @RequestBody UpdatePreferencesRequest request) {
        return preferenceService.update(caller.userId(), request);
    }
}
