package com.spacz.user.controller;

import com.spacz.user.client.dto.EnrollmentDto;
import com.spacz.user.config.OpenApiConfig;
import com.spacz.user.dto.ActivityResponse;
import com.spacz.user.dto.PageResponse;
import com.spacz.user.dto.UpdateUserProfileRequest;
import com.spacz.user.dto.UserProfileResponse;
import com.spacz.user.dto.UserProgramRequest;
import com.spacz.user.dto.UserProgramResponse;
import com.spacz.user.entity.ActivityType;
import com.spacz.user.exception.ApiError;
import com.spacz.user.security.AuthenticatedUser;
import com.spacz.user.security.OwnershipGuard;
import com.spacz.user.service.ActivityService;
import com.spacz.user.service.EnrollmentQueryService;
import com.spacz.user.service.UserProfileService;
import com.spacz.user.service.UserProgramService;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * {@code /me} endpoints act on the caller; {@code /{userId}} endpoints are for the user themself or an ADMIN.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Student profile, exam/program choices, study-hall enrollments and activity")
@SecurityRequirement(name = OpenApiConfig.BEARER)
@ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "403", description = "Not your profile", content = @Content(schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "404", description = "Profile not found", content = @Content(schema = @Schema(implementation = ApiError.class)))
public class UserController {

    private final UserProfileService profileService;
    private final UserProgramService programService;
    private final EnrollmentQueryService enrollmentService;
    private final ActivityService activityService;

    // ------------------------------------------------------------------ /me

    @GetMapping("/me")
    @Operation(summary = "My profile")
    public UserProfileResponse me(@AuthenticationPrincipal AuthenticatedUser caller) {
        return profileService.getProfile(caller.userId());
    }

    @PutMapping("/me")
    @Operation(summary = "Update my profile")
    public UserProfileResponse updateMe(@AuthenticationPrincipal AuthenticatedUser caller,
                                        @Valid @RequestBody UpdateUserProfileRequest request) {
        return profileService.updateProfile(caller.userId(), request);
    }

    @GetMapping("/me/programs")
    @Operation(summary = "Exams / courses I am preparing for")
    public List<UserProgramResponse> myPrograms(@AuthenticationPrincipal AuthenticatedUser caller) {
        return programService.list(caller.userId());
    }

    @PostMapping("/me/programs")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start preparing for an exam / course (ID from GET /api/programs)")
    @ApiResponse(responseCode = "409", description = "Already chosen", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public UserProgramResponse addProgram(@AuthenticationPrincipal AuthenticatedUser caller,
                                          @Valid @RequestBody UserProgramRequest request) {
        return programService.add(caller.userId(), request);
    }

    @PutMapping("/me/programs/{id}")
    @Operation(summary = "Update an exam / course choice")
    public UserProgramResponse updateProgram(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long id,
                                             @Valid @RequestBody UserProgramRequest request) {
        return programService.update(caller.userId(), id, request);
    }

    @DeleteMapping("/me/programs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove an exam / course choice")
    public void removeProgram(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long id) {
        programService.remove(caller.userId(), id);
    }

    @GetMapping("/me/enrollments")
    @Operation(summary = "My study-hall memberships (from studyhall-service)")
    public List<EnrollmentDto> myEnrollments(@AuthenticationPrincipal AuthenticatedUser caller) {
        return enrollmentService.enrollments(caller.userId());
    }

    @GetMapping("/me/activity")
    @Operation(summary = "My activity history, newest first")
    public PageResponse<ActivityResponse> myActivity(@AuthenticationPrincipal AuthenticatedUser caller,
                                                     @RequestParam(required = false) ActivityType type,
                                                     @ParameterObject @PageableDefault(size = 20, sort = "createdAt",
                                                             direction = Sort.Direction.DESC) Pageable pageable) {
        return activityService.list(caller.userId(), type, pageable);
    }

    // ------------------------------------------------------------- /{userId}

    @GetMapping("/{userId}")
    @Operation(summary = "A profile (self or ADMIN)")
    public UserProfileResponse get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long userId) {
        OwnershipGuard.requireSelfOrAdmin(caller, userId);
        return profileService.getProfile(userId);
    }

    @PutMapping("/{userId}")
    @Operation(summary = "Update a profile (self only)")
    public UserProfileResponse update(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long userId,
                                      @Valid @RequestBody UpdateUserProfileRequest request) {
        OwnershipGuard.requireSelf(caller, userId);
        return profileService.updateProfile(userId, request);
    }

    @GetMapping("/{userId}/programs")
    @Operation(summary = "A user's exam / course choices (self or ADMIN)")
    public List<UserProgramResponse> programs(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long userId) {
        OwnershipGuard.requireSelfOrAdmin(caller, userId);
        return programService.list(userId);
    }

    @GetMapping("/{userId}/enrollments")
    @Operation(summary = "A user's study-hall memberships (self or ADMIN)")
    public List<EnrollmentDto> enrollments(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long userId) {
        OwnershipGuard.requireSelfOrAdmin(caller, userId);
        return enrollmentService.enrollments(userId);
    }

    @GetMapping("/{userId}/activity")
    @Operation(summary = "A user's activity history (self or ADMIN)")
    public PageResponse<ActivityResponse> activity(@AuthenticationPrincipal AuthenticatedUser caller,
                                                   @PathVariable Long userId,
                                                   @RequestParam(required = false) ActivityType type,
                                                   @ParameterObject @PageableDefault(size = 20, sort = "createdAt",
                                                           direction = Sort.Direction.DESC) Pageable pageable) {
        OwnershipGuard.requireSelfOrAdmin(caller, userId);
        return activityService.list(userId, type, pageable);
    }
}
