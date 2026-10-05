package com.spacz.admin.controller;

import com.spacz.admin.config.OpenApiConfig;
import com.spacz.admin.dto.NotificationResponse;
import com.spacz.admin.dto.OfferResponse;
import com.spacz.admin.exception.ApiError;
import com.spacz.admin.repository.OfferRepository;
import com.spacz.admin.repository.PlatformUpdateRepository;
import com.spacz.admin.service.OfferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Read-only, student-facing view of admin-managed content (reached through the gateway).
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Student content", description = "Live offers and notifications for the student app")
public class StudentContentController {

    private final OfferService offerService;
    private final OfferRepository offerRepository;
    private final PlatformUpdateRepository updateRepository;
    private final Clock clock;

    @GetMapping("/api/offers")
    @Operation(summary = "Live offers (public)", description = "Active, not archived, now within startAt..endAt; highest priority first")
    public List<OfferResponse> liveOffers() {
        return offerService.live();
    }

    @GetMapping("/api/notifications")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(summary = "Notifications for the signed-in student",
            description = "OFFER = live offers, UPDATE = published platform updates; newest first. Omit type for both.")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @Transactional(readOnly = true)
    public List<NotificationResponse> notifications(@Parameter(description = "OFFER or UPDATE")
                                                    @RequestParam(required = false) NotificationResponse.Type type) {
        List<NotificationResponse> result = new ArrayList<>();
        if (type == null || type == NotificationResponse.Type.OFFER) {
            offerRepository.findLive(clock.instant()).forEach(o -> result.add(NotificationResponse.from(o)));
        }
        if (type == null || type == NotificationResponse.Type.UPDATE) {
            updateRepository.findByPublishedTrueAndArchivedFalseOrderByPublishedAtDesc()
                    .forEach(u -> result.add(NotificationResponse.from(u)));
        }
        if (type == null) {
            result.sort(Comparator.comparing(NotificationResponse::createdAt).reversed());
        }
        return result;
    }
}
