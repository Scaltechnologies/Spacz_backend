package com.spacz.studyhall.controller.internal;

import com.spacz.studyhall.config.OpenApiConfig;
import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.dto.StatusActionRequest;
import com.spacz.studyhall.dto.hall.StudyHallDetailResponse;
import com.spacz.studyhall.dto.hall.StudyHallSummaryResponse;
import com.spacz.studyhall.entity.StudyHallStatus;
import com.spacz.studyhall.service.StudyHallAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/studyhalls")
@RequiredArgsConstructor
@Tag(name = "Internal - Study halls", description = "Used by admin-service")
@SecurityRequirement(name = OpenApiConfig.INTERNAL_KEY)
public class InternalStudyHallController {

    private final StudyHallAdminService adminService;

    @GetMapping
    @Operation(summary = "Search study halls in any status")
    public PageResponse<StudyHallSummaryResponse> search(@RequestParam(required = false) String search,
                                                         @RequestParam(required = false) StudyHallStatus status,
                                                         @RequestParam(required = false) String city,
                                                         @RequestParam(required = false) Long vendorId,
                                                         @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return adminService.search(search, status, city, vendorId, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One study hall in any status")
    public StudyHallDetailResponse get(@PathVariable Long id) {
        return adminService.get(id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "APPROVE / REJECT / SUSPEND / ACTIVATE a study hall")
    public StudyHallDetailResponse changeStatus(@PathVariable Long id, @Valid @RequestBody StatusActionRequest request) {
        return adminService.applyAction(id, request.action(), request.reason());
    }
}
