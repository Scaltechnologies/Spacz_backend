package com.spacz.user.controller.internal;

import com.spacz.user.config.OpenApiConfig;
import com.spacz.user.dto.ActivityResponse;
import com.spacz.user.dto.PageResponse;
import com.spacz.user.dto.UserProfileResponse;
import com.spacz.user.dto.UserProgramResponse;
import com.spacz.user.dto.internal.CreateUserProfileRequest;
import com.spacz.user.dto.internal.RecordActivityRequest;
import com.spacz.user.dto.internal.UserSummaryResponse;
import com.spacz.user.service.ActivityService;
import com.spacz.user.service.UserProfileService;
import com.spacz.user.service.UserProgramService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@Validated
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
@Tag(name = "Internal - Users", description = "Used by auth-service (profile creation), studyhall-service and admin-service")
@SecurityRequirement(name = OpenApiConfig.INTERNAL_KEY)
public class InternalUserController {

    private final UserProfileService profileService;
    private final UserProgramService programService;
    private final ActivityService activityService;

    @PostMapping
    @Operation(summary = "Create a profile at registration (idempotent on userId)")
    public ResponseEntity<UserProfileResponse> create(@Valid @RequestBody CreateUserProfileRequest request) {
        UserProfileService.CreationResult result = profileService.createProfile(request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.profile());
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get a full profile")
    public UserProfileResponse get(@PathVariable Long userId) {
        return profileService.getProfile(userId);
    }

    @GetMapping
    @Operation(summary = "Batch lookup of student summaries (max 100 IDs); unknown IDs are omitted")
    public List<UserSummaryResponse> summaries(@RequestParam @Size(max = 100) Set<Long> ids) {
        return profileService.getSummaries(ids);
    }

    @GetMapping("/{userId}/programs")
    @Operation(summary = "A student's exam / course choices (admin-service)")
    public List<UserProgramResponse> programs(@PathVariable Long userId) {
        return programService.list(userId);
    }

    @GetMapping("/{userId}/activity")
    @Operation(summary = "A student's activity history (admin-service)")
    public PageResponse<ActivityResponse> activity(@PathVariable Long userId,
                                                   @ParameterObject @PageableDefault(size = 20, sort = "createdAt",
                                                           direction = Sort.Direction.DESC) Pageable pageable) {
        return activityService.list(userId, null, pageable);
    }

    @PostMapping("/{userId}/activities")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Append an activity (booking events from studyhall-service)")
    public ActivityResponse recordActivity(@PathVariable Long userId, @Valid @RequestBody RecordActivityRequest request) {
        return activityService.record(userId, request);
    }
}
