package com.spacz.studyhall.controller;

import com.spacz.studyhall.config.OpenApiConfig;
import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.dto.program.ProgramRequest;
import com.spacz.studyhall.dto.program.ProgramResponse;
import com.spacz.studyhall.security.AuthenticatedUser;
import com.spacz.studyhall.service.ProgramService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

@RestController
@RequestMapping("/api/programs")
@RequiredArgsConstructor
@Tag(name = "Programs (exams / courses)", description = "Catalog: public reads, ADMIN writes")
@ApiErrorResponses
public class ProgramController {

    private final ProgramService programService;

    @GetMapping
    @Operation(summary = "Active programs, grouped by category then name")
    public List<ProgramResponse> list(@RequestParam(required = false) String search,
                                      @RequestParam(required = false) String category) {
        return programService.listActive(search, category);
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "All programs incl. inactive (admin, paginated)", security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public PageResponse<ProgramResponse> all(@RequestParam(required = false) String search,
                                             @RequestParam(required = false) String category,
                                             @RequestParam(required = false) Boolean active,
                                             @ParameterObject @PageableDefault(size = 50, sort = "name") Pageable pageable) {
        return programService.search(search, category, active, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One active program")
    public ProgramResponse get(@PathVariable Long id) {
        return programService.get(id, false);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a program", security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public ProgramResponse create(@AuthenticationPrincipal AuthenticatedUser admin, @Valid @RequestBody ProgramRequest request) {
        return programService.create(admin, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a program", security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public ProgramResponse update(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long id,
                                  @Valid @RequestBody ProgramRequest request) {
        return programService.update(admin, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate a program (kept for history)", security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public void delete(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long id) {
        programService.deactivate(admin, id);
    }
}
