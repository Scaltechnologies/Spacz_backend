package com.spacz.admin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.spacz.admin.client.dto.AccountDto;
import com.spacz.admin.dto.AdminUserDetailResponse;
import com.spacz.admin.dto.OptionalReasonRequest;
import com.spacz.admin.dto.PageResponse;
import com.spacz.admin.dto.ReasonRequest;
import com.spacz.admin.security.AuthenticatedUser;
import com.spacz.admin.service.UserAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Users", description = "All accounts (students, vendors, admins)")
public class AdminUserController {

    private final UserAdminService userAdminService;

    @GetMapping
    @Operation(summary = "Search accounts")
    public PageResponse<AccountDto> search(@Parameter(description = "Email or phone contains") @RequestParam(required = false) String search,
                                           @Parameter(description = "ADMIN, USER or VENDOR") @RequestParam(required = false) String role,
                                           @Parameter(description = "ACTIVE, SUSPENDED or DISABLED") @RequestParam(required = false) String status,
                                           @ParameterObject @PageableDefault(size = 20, sort = "createdAt",
                                                   direction = Sort.Direction.DESC) Pageable pageable) {
        return userAdminService.search(search, role, status, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Account with profile, exam choices and hall memberships (students) or vendor profile")
    public AdminUserDetailResponse get(@PathVariable Long id) {
        return userAdminService.get(id);
    }

    @GetMapping("/{id}/activity")
    @Operation(summary = "A student's activity history")
    public JsonNode activity(@PathVariable Long id,
                             @ParameterObject @PageableDefault(size = 20, sort = "createdAt",
                                     direction = Sort.Direction.DESC) Pageable pageable) {
        return userAdminService.activity(id, pageable);
    }

    @PostMapping("/{id}/suspend")
    @Operation(summary = "Suspend an account (blocks login and refresh); reason required")
    public AccountDto suspend(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long id,
                              @Valid @RequestBody ReasonRequest request) {
        return userAdminService.suspend(admin, id, request.reason());
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Re-activate a suspended account")
    public AccountDto activate(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long id,
                               @Valid @RequestBody(required = false) OptionalReasonRequest request) {
        return userAdminService.activate(admin, id, request == null ? null : request.reason());
    }
}
