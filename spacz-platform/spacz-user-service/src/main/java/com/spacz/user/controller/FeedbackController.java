package com.spacz.user.controller;

import com.spacz.user.config.OpenApiConfig;
import com.spacz.user.dto.FeedbackRequest;
import com.spacz.user.dto.FeedbackResponse;
import com.spacz.user.dto.PageResponse;
import com.spacz.user.exception.ApiError;
import com.spacz.user.security.AuthenticatedUser;
import com.spacz.user.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

@RestController
@RequestMapping("/api/users/me/feedback")
@RequiredArgsConstructor
@Tag(name = "Feedback", description = "App feedback from students")
@SecurityRequirement(name = OpenApiConfig.BEARER)
@ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class)))
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit feedback", description = "rating 1-5, comment up to 1000 characters. "
            + "An identical submission within a minute returns the earlier one (no duplicate).")
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public FeedbackResponse submit(@AuthenticationPrincipal AuthenticatedUser caller,
                                   @Valid @RequestBody FeedbackRequest request) {
        return feedbackService.submit(caller.userId(), request);
    }

    @GetMapping
    @Operation(summary = "My feedback, newest first")
    public PageResponse<FeedbackResponse> mine(@AuthenticationPrincipal AuthenticatedUser caller,
                                               @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return feedbackService.mine(caller.userId(), pageable);
    }
}
