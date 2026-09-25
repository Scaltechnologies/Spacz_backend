package com.spacz.auth.controller.internal;

import com.spacz.auth.config.OpenApiConfig;
import com.spacz.auth.dto.AccountResponse;
import com.spacz.auth.dto.AccountStatsResponse;
import com.spacz.auth.dto.AccountStatusUpdateRequest;
import com.spacz.auth.dto.PageResponse;
import com.spacz.auth.dto.internal.ImportAccountRequest;
import com.spacz.auth.dto.internal.ImportAccountResult;
import com.spacz.auth.entity.AccountStatus;
import com.spacz.auth.security.Role;
import com.spacz.auth.service.AccountManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Called by admin-service (administration) and the legacy migration tool (import).
 */
@RestController
@RequestMapping("/internal/accounts")
@RequiredArgsConstructor
@Tag(name = "Internal - Accounts", description = "Service-to-service API used by admin-service and the migration tool")
@SecurityRequirement(name = OpenApiConfig.INTERNAL_KEY)
public class InternalAccountController {

    private final AccountManagementService accountManagementService;

    @GetMapping
    @Operation(summary = "Search accounts (search matches email or phone)")
    public PageResponse<AccountResponse> search(@RequestParam(required = false) String search,
                                                @RequestParam(required = false) Role role,
                                                @RequestParam(required = false) AccountStatus status,
                                                @ParameterObject @PageableDefault(size = 20, sort = "createdAt",
                                                        direction = Sort.Direction.DESC) Pageable pageable) {
        return accountManagementService.search(search, role, status, pageable);
    }

    @GetMapping("/stats")
    @Operation(summary = "Account statistics for the admin dashboard")
    public AccountStatsResponse stats() {
        return accountManagementService.stats();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one account")
    public AccountResponse get(@PathVariable Long id) {
        return accountManagementService.get(id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change account status (suspend / activate / disable)")
    public AccountResponse updateStatus(@PathVariable Long id, @Valid @RequestBody AccountStatusUpdateRequest request) {
        return accountManagementService.updateStatus(id, request.status());
    }

    @PostMapping("/import")
    @Operation(summary = "Create a password-less account for a legacy identity (idempotent)")
    public ImportAccountResult importAccount(@Valid @RequestBody ImportAccountRequest request) {
        return accountManagementService.importAccount(request);
    }
}
