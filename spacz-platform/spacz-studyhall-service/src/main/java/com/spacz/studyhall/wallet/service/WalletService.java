package com.spacz.studyhall.wallet.service;

import com.spacz.studyhall.config.WalletProperties;
import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.entity.VendorProfile;
import com.spacz.studyhall.exception.BusinessRuleException;
import com.spacz.studyhall.exception.ConflictException;
import com.spacz.studyhall.exception.ResourceNotFoundException;
import com.spacz.studyhall.repository.VendorProfileRepository;
import com.spacz.studyhall.wallet.dto.WalletRequests.CreateWithdrawalRequest;
import com.spacz.studyhall.wallet.dto.WalletRequests.MarkPaidRequest;
import com.spacz.studyhall.wallet.dto.WalletRequests.MarkProcessingRequest;
import com.spacz.studyhall.wallet.dto.WalletRequests.RejectRequest;
import com.spacz.studyhall.wallet.dto.WalletResponses.AdminWithdrawalView;
import com.spacz.studyhall.wallet.dto.WalletResponses.WalletEntryResponse;
import com.spacz.studyhall.wallet.dto.WalletResponses.WalletSummaryResponse;
import com.spacz.studyhall.wallet.dto.WalletResponses.WithdrawalResponse;
import com.spacz.studyhall.wallet.entity.PayoutAccount;
import com.spacz.studyhall.wallet.entity.WalletEntry;
import com.spacz.studyhall.wallet.entity.WalletEntryType;
import com.spacz.studyhall.wallet.entity.Withdrawal;
import com.spacz.studyhall.wallet.entity.WithdrawalStatus;
import com.spacz.studyhall.wallet.repository.PayoutAccountRepository;
import com.spacz.studyhall.wallet.repository.WalletEntryRepository;
import com.spacz.studyhall.wallet.repository.WithdrawalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Vendor wallet balance, statement and withdrawal requests (vendor side), and withdrawal processing (admin side,
 * via admin-service). Paying out is manual: an admin sends the money by UPI / bank transfer and records the UTR.
 *
 * <p>Requesting a withdrawal reserves the amount at once (a WITHDRAWAL debit entry), under a lock on the vendor's
 * profile row, so two concurrent requests can never spend the same balance. Rejecting or cancelling adds it back.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final WalletEntryRepository entries;
    private final WithdrawalRepository withdrawals;
    private final PayoutAccountRepository payoutAccounts;
    private final VendorProfileRepository vendors;
    private final WalletProperties properties;
    private final WalletNotifier notifier;
    private final Clock clock;

    // ── balance and statement ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public WalletSummaryResponse summary(Long vendorId) {
        VendorProfile vendor = vendor(vendorId);
        Instant now = clock.instant();
        BigDecimal available = money(entries.sumAvailable(vendorId, now));
        Optional<Withdrawal> open = withdrawals.findFirstByVendorIdAndStatusIn(vendorId, WithdrawalStatus.OPEN);

        String blocked = null;
        if (!vendor.isOperational()) {
            blocked = "VENDOR_NOT_APPROVED";
        } else if (open.isPresent()) {
            blocked = "WITHDRAWAL_IN_PROGRESS";
        } else if (payoutAccounts.countByVendorIdAndActiveTrue(vendorId) == 0) {
            blocked = "NO_PAYOUT_ACCOUNT";
        } else if (available.compareTo(properties.minWithdrawal()) < 0) {
            blocked = "BELOW_MINIMUM";
        }
        return new WalletSummaryResponse("INR", available,
                money(entries.sumPending(vendorId, now)),
                money(withdrawals.sumByVendorAndStatusIn(vendorId, WithdrawalStatus.OPEN)),
                money(entries.sumByType(vendorId, WalletEntryType.BOOKING_EARNING)),
                money(withdrawals.sumByVendorAndStatusIn(vendorId, java.util.List.of(WithdrawalStatus.PAID))),
                money(properties.minWithdrawal()), money(properties.maxWithdrawal()),
                properties.settlementHold().toHours(), blocked == null, blocked,
                open.map(WithdrawalResponse::from).orElse(null));
    }

    @Transactional(readOnly = true)
    public PageResponse<WalletEntryResponse> statement(Long vendorId, WalletEntryType type, int page, int size) {
        vendor(vendorId);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<WalletEntry> result = type == null
                ? entries.findByVendorIdOrderByCreatedAtDescIdDesc(vendorId, pageable)
                : entries.findByVendorIdAndTypeOrderByCreatedAtDescIdDesc(vendorId, type, pageable);
        Instant now = clock.instant();
        return PageResponse.from(result, e -> WalletEntryResponse.from(e, now));
    }

    // ── vendor: withdrawals ────────────────────────────────────────────────

    @Transactional
    public WithdrawalResponse requestWithdrawal(Long vendorId, CreateWithdrawalRequest request) {
        // Serializes this vendor's withdrawals: the balance check and the reservation below are atomic.
        VendorProfile vendor = vendors.findByVendorIdForUpdate(vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor profile not found. Create it first."));
        if (!vendor.isOperational()) {
            throw new BusinessRuleException("VENDOR_NOT_APPROVED",
                    "Withdrawals are available once your partner account is approved and active");
        }
        BigDecimal amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
        if (amount.compareTo(properties.minWithdrawal()) < 0) {
            throw new BusinessRuleException("WITHDRAWAL_BELOW_MINIMUM",
                    "The minimum withdrawal is Rs." + money(properties.minWithdrawal()));
        }
        if (amount.compareTo(properties.maxWithdrawal()) > 0) {
            throw new BusinessRuleException("WITHDRAWAL_ABOVE_MAXIMUM",
                    "The maximum withdrawal per request is Rs." + money(properties.maxWithdrawal()));
        }
        if (withdrawals.findFirstByVendorIdAndStatusIn(vendorId, WithdrawalStatus.OPEN).isPresent()) {
            throw new ConflictException("WITHDRAWAL_IN_PROGRESS",
                    "You already have a withdrawal in progress. Wait for it to complete or cancel it.");
        }
        PayoutAccount account = payoutAccounts.findByIdAndVendorIdAndActiveTrue(request.payoutAccountId(), vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout account", request.payoutAccountId()));
        Instant now = clock.instant();
        BigDecimal available = money(entries.sumAvailable(vendorId, now));
        if (amount.compareTo(available) > 0) {
            throw new BusinessRuleException("INSUFFICIENT_BALANCE",
                    "You can withdraw up to Rs." + available.max(BigDecimal.ZERO.setScale(2)) + " right now");
        }

        Withdrawal withdrawal;
        try {
            withdrawal = withdrawals.saveAndFlush(Withdrawal.request(newReference(), vendorId, amount, account,
                    trim(request.note()), now));
        } catch (DataIntegrityViolationException e) {
            // uq_withdrawals_one_open_per_vendor: a concurrent request won (should not happen under the row lock).
            throw new ConflictException("WITHDRAWAL_IN_PROGRESS", "You already have a withdrawal in progress");
        }
        entries.save(WalletEntry.builder()
                .vendorId(vendorId)
                .type(WalletEntryType.WITHDRAWAL)
                .amount(amount.negate())
                .availableAt(now)
                .withdrawalId(withdrawal.getId())
                .description("Withdrawal " + withdrawal.getReference())
                .build());
        notifier.requested(withdrawal, vendor.displayName());
        log.info("Withdrawal {} requested: vendor {} amount {} via {}", withdrawal.getReference(), vendorId, amount,
                withdrawal.getPayoutMethod());
        return WithdrawalResponse.from(withdrawal);
    }

    @Transactional
    public WithdrawalResponse cancel(Long vendorId, Long withdrawalId) {
        // Lock first: a plain read before it would leave a possibly stale instance in the persistence context.
        Withdrawal withdrawal = lock(withdrawalId);
        if (!withdrawal.getVendorId().equals(vendorId)) {
            throw new ResourceNotFoundException("Withdrawal", withdrawalId);
        }
        withdrawal.cancelByVendor(clock.instant());
        release(withdrawal, "Withdrawal " + withdrawal.getReference() + " cancelled");
        notifier.cancelled(withdrawal);
        return WithdrawalResponse.from(withdrawal);
    }

    @Transactional(readOnly = true)
    public PageResponse<WithdrawalResponse> myWithdrawals(Long vendorId, WithdrawalStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<Withdrawal> result = status == null
                ? withdrawals.findByVendorIdOrderByRequestedAtDescIdDesc(vendorId, pageable)
                : withdrawals.findByVendorIdAndStatusOrderByRequestedAtDescIdDesc(vendorId, status, pageable);
        return PageResponse.from(result, WithdrawalResponse::from);
    }

    @Transactional(readOnly = true)
    public WithdrawalResponse myWithdrawal(Long vendorId, Long withdrawalId) {
        return withdrawals.findByIdAndVendorId(withdrawalId, vendorId).map(WithdrawalResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Withdrawal", withdrawalId));
    }

    // ── admin (through admin-service /internal) ────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<AdminWithdrawalView> adminList(WithdrawalStatus status, Long vendorId, Pageable pageable) {
        Specification<Withdrawal> spec = (root, query, cb) -> cb.and(
                status == null ? cb.conjunction() : cb.equal(root.get("status"), status),
                vendorId == null ? cb.conjunction() : cb.equal(root.get("vendorId"), vendorId));
        Pageable sorted = pageable.getSort().isSorted() ? pageable : PageRequest.of(pageable.getPageNumber(),
                pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "requestedAt", "id"));
        Page<Withdrawal> result = withdrawals.findAll(spec, sorted);
        Map<Long, VendorProfile> byVendor = result.getContent().stream().map(Withdrawal::getVendorId).distinct()
                .map(vendors::findByVendorId).flatMap(Optional::stream)
                .collect(Collectors.toMap(VendorProfile::getVendorId, Function.identity()));
        return PageResponse.from(result, w -> adminView(w, byVendor.get(w.getVendorId())));
    }

    @Transactional(readOnly = true)
    public AdminWithdrawalView adminGet(Long withdrawalId) {
        Withdrawal w = withdrawals.findById(withdrawalId)
                .orElseThrow(() -> new ResourceNotFoundException("Withdrawal", withdrawalId));
        return adminView(w, vendors.findByVendorId(w.getVendorId()).orElse(null));
    }

    @Transactional
    public AdminWithdrawalView markProcessing(Long withdrawalId, MarkProcessingRequest request) {
        Withdrawal w = lock(withdrawalId);
        w.markProcessing(request.adminId(), trim(request.note()), clock.instant());
        notifier.processing(w);
        return adminGetLoaded(w);
    }

    @Transactional
    public AdminWithdrawalView markPaid(Long withdrawalId, MarkPaidRequest request) {
        Withdrawal w = lock(withdrawalId);
        w.markPaid(request.adminId(), request.payoutReference().trim(), trim(request.note()), clock.instant());
        notifier.paid(w);
        log.info("Withdrawal {} PAID by admin {}: {} ref {}", w.getReference(), request.adminId(), w.getAmount(),
                w.getPayoutReference());
        return adminGetLoaded(w);
    }

    @Transactional
    public AdminWithdrawalView reject(Long withdrawalId, RejectRequest request) {
        Withdrawal w = lock(withdrawalId);
        w.reject(request.adminId(), request.reason().trim(), clock.instant());
        release(w, "Withdrawal " + w.getReference() + " declined: " + w.getRejectionReason());
        notifier.rejected(w);
        return adminGetLoaded(w);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    /** Gives a rejected / cancelled withdrawal's amount back to the wallet (once). */
    private void release(Withdrawal w, String description) {
        if (entries.findByWithdrawalIdAndType(w.getId(), WalletEntryType.WITHDRAWAL_REVERSAL).isPresent()) {
            return;
        }
        entries.save(WalletEntry.builder()
                .vendorId(w.getVendorId())
                .type(WalletEntryType.WITHDRAWAL_REVERSAL)
                .amount(w.getAmount())
                .availableAt(clock.instant())
                .withdrawalId(w.getId())
                .description(description.length() > 500 ? description.substring(0, 500) : description)
                .build());
    }

    private Withdrawal lock(Long withdrawalId) {
        return withdrawals.findByIdForUpdate(withdrawalId)
                .orElseThrow(() -> new ResourceNotFoundException("Withdrawal", withdrawalId));
    }

    private AdminWithdrawalView adminGetLoaded(Withdrawal w) {
        withdrawals.flush();
        return adminView(w, vendors.findByVendorId(w.getVendorId()).orElse(null));
    }

    private static AdminWithdrawalView adminView(Withdrawal w, VendorProfile vendor) {
        return AdminWithdrawalView.from(w, vendor != null ? vendor.displayName() : null,
                vendor != null ? vendor.getPhone() : null);
    }

    private VendorProfile vendor(Long vendorId) {
        return vendors.findByVendorId(vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor profile not found. Create it first."));
    }

    private static BigDecimal money(BigDecimal value) {
        return (value != null ? value : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static String newReference() {
        StringBuilder reference = new StringBuilder("WDR-");
        for (int i = 0; i < 12; i++) {
            reference.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return reference.toString();
    }
}
