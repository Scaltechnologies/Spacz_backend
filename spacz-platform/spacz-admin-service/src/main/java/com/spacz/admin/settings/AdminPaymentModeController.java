package com.spacz.admin.settings;

import com.spacz.admin.entity.AuditAction;
import com.spacz.admin.exception.BusinessRuleException;
import com.spacz.admin.security.AuthenticatedUser;
import com.spacz.admin.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

/**
 * Lets an ADMIN take Razorpay checkout out of rotation (PAY_AT_HALL_ONLY) while live keys aren't
 * provisioned yet, restore it (BOTH), or go the other way (ONLINE_ONLY), without a redeploy. Read by
 * studyhall-service's PaymentModeConfigClient (polled and cached), which is what payment creation
 * actually enforces; this is the write side plus a typed read for the admin panel.
 */
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Settings", description = "Platform settings")
public class AdminPaymentModeController {

    public static final String PAYMENT_MODE = "PAYMENT_MODE";
    public static final String DEFAULT_PAYMENT_MODE = "BOTH";
    private static final Set<String> VALID = Set.of("ONLINE_ONLY", "PAY_AT_HALL_ONLY", "BOTH");

    private final PlatformSettingRepository repository;
    private final AuditService auditService;

    @GetMapping("/payment-mode")
    @Operation(summary = "Current platform-wide payment mode")
    public Map<String, String> get() {
        return Map.of("paymentMode", repository.findBySettingKey(PAYMENT_MODE)
                .map(PlatformSetting::getSettingValue).orElse(DEFAULT_PAYMENT_MODE));
    }

    @PatchMapping("/payment-mode")
    @Transactional
    @Operation(summary = "Set which payment method(s) checkout accepts: ONLINE_ONLY, PAY_AT_HALL_ONLY or BOTH",
            description = "studyhall-service applies the change within about a minute (cache TTL).")
    public Map<String, String> set(@RequestBody Map<String, String> body,
                                   @AuthenticationPrincipal AuthenticatedUser admin) {
        String mode = body == null ? null : body.get("paymentMode");
        if (mode == null || !VALID.contains(mode)) {
            throw new BusinessRuleException("INVALID_PAYMENT_MODE", "paymentMode must be one of " + VALID);
        }
        PlatformSetting setting = repository.findBySettingKey(PAYMENT_MODE).orElseGet(() -> PlatformSetting.builder()
                .settingKey(PAYMENT_MODE).description("Which payment method(s) checkout accepts").build());
        String old = setting.getSettingValue() != null ? setting.getSettingValue() : DEFAULT_PAYMENT_MODE;
        setting.setSettingValue(mode);
        setting.setUpdatedBy(admin != null ? admin.userId() : null);
        setting = repository.saveAndFlush(setting);
        auditService.record(admin, AuditAction.PAYMENT_MODE_CHANGED, "PLATFORM_SETTING", setting.getId(),
                "Payment mode " + old + " -> " + mode);
        return Map.of("paymentMode", mode);
    }
}
