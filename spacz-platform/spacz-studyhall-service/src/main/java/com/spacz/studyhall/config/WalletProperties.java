package com.spacz.studyhall.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Partner wallet and withdrawals.
 *
 * @param commissionPercent SPACZ commission on the vendor's seat revenue (seat price - discount), per booking
 * @param settlementHold    how long an online earning stays "pending" before it can be withdrawn
 * @param minWithdrawal     smallest withdrawal request (INR)
 * @param maxWithdrawal     largest single withdrawal request (INR)
 * @param maxPayoutAccounts active UPI IDs + bank accounts per vendor
 */
@Validated
@ConfigurationProperties(prefix = "spacz.wallet")
public record WalletProperties(
        @NotNull @DecimalMin("0") @DecimalMax("100") @DefaultValue("0") BigDecimal commissionPercent,
        @NotNull @DefaultValue("48h") Duration settlementHold,
        @NotNull @DecimalMin("1") @DefaultValue("100") BigDecimal minWithdrawal,
        @NotNull @DecimalMin("1") @DefaultValue("200000") BigDecimal maxWithdrawal,
        @Min(1) @DefaultValue("5") int maxPayoutAccounts) {
}
