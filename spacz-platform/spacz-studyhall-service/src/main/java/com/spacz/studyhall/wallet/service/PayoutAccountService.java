package com.spacz.studyhall.wallet.service;

import com.spacz.studyhall.config.WalletProperties;
import com.spacz.studyhall.exception.BusinessRuleException;
import com.spacz.studyhall.exception.DuplicateResourceException;
import com.spacz.studyhall.exception.ResourceNotFoundException;
import com.spacz.studyhall.repository.VendorProfileRepository;
import com.spacz.studyhall.wallet.dto.WalletRequests.AddPayoutAccountRequest;
import com.spacz.studyhall.wallet.dto.WalletResponses.PayoutAccountResponse;
import com.spacz.studyhall.wallet.entity.PayoutAccount;
import com.spacz.studyhall.wallet.entity.PayoutMethod;
import com.spacz.studyhall.wallet.repository.PayoutAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** A vendor's UPI IDs and bank accounts for withdrawals. */
@Service
@RequiredArgsConstructor
public class PayoutAccountService {

    /** name@handle, as NPCI allows: letters, digits, dot, hyphen, underscore before the @. */
    static final Pattern UPI = Pattern.compile("^[a-z0-9._-]{2,256}@[a-z][a-z0-9]{1,63}$");
    /** Indian bank account numbers are 9 to 18 digits. */
    static final Pattern ACCOUNT_NUMBER = Pattern.compile("^[0-9]{9,18}$");
    /** 4 letters (bank), 0, 6 letters/digits (branch). */
    static final Pattern IFSC = Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");
    static final Pattern HOLDER_NAME = Pattern.compile("^[\\p{L} .'&()-]{2,120}$");

    private final PayoutAccountRepository accounts;
    private final VendorProfileRepository vendors;
    private final WalletProperties properties;

    @Transactional(readOnly = true)
    public List<PayoutAccountResponse> list(Long vendorId) {
        return accounts.findByVendorIdAndActiveTrueOrderByDefaultAccountDescCreatedAtAsc(vendorId).stream()
                .map(PayoutAccountResponse::from).toList();
    }

    @Transactional
    public PayoutAccountResponse add(Long vendorId, AddPayoutAccountRequest request) {
        // Row lock: two "add" taps cannot both pass the duplicate / limit checks.
        vendors.findByVendorIdForUpdate(vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor profile not found. Create it first."));
        PayoutAccount account = validated(vendorId, request);

        List<PayoutAccount> existing = accounts.findByVendorIdAndActiveTrueOrderByDefaultAccountDescCreatedAtAsc(vendorId);
        if (existing.size() >= properties.maxPayoutAccounts()) {
            throw new BusinessRuleException("PAYOUT_ACCOUNT_LIMIT",
                    "You can save at most " + properties.maxPayoutAccounts() + " payout accounts. Delete one first.");
        }
        boolean duplicate = existing.stream().anyMatch(a -> a.getMethod() == account.getMethod()
                && (account.getMethod() == PayoutMethod.UPI
                ? Objects.equals(a.getUpiId(), account.getUpiId())
                : Objects.equals(a.getAccountNumber(), account.getAccountNumber())
                && Objects.equals(a.getIfscCode(), account.getIfscCode())));
        if (duplicate) {
            throw new DuplicateResourceException("This payout account is already saved");
        }

        boolean makeDefault = existing.isEmpty() || Boolean.TRUE.equals(request.makeDefault());
        if (makeDefault) {
            existing.forEach(a -> a.setDefaultAccount(false));
        }
        account.setDefaultAccount(makeDefault);
        return PayoutAccountResponse.from(accounts.save(account));
    }

    @Transactional
    public PayoutAccountResponse makeDefault(Long vendorId, Long accountId) {
        vendors.findByVendorIdForUpdate(vendorId).orElseThrow(() -> new ResourceNotFoundException("Vendor profile", vendorId));
        PayoutAccount target = owned(vendorId, accountId);
        accounts.findByVendorIdAndActiveTrueOrderByDefaultAccountDescCreatedAtAsc(vendorId)
                .forEach(a -> a.setDefaultAccount(a.getId().equals(target.getId())));
        return PayoutAccountResponse.from(target);
    }

    /** Deactivates the account. Withdrawals already requested to it keep their own copy of the details. */
    @Transactional
    public void delete(Long vendorId, Long accountId) {
        vendors.findByVendorIdForUpdate(vendorId).orElseThrow(() -> new ResourceNotFoundException("Vendor profile", vendorId));
        PayoutAccount account = owned(vendorId, accountId);
        account.setActive(false);
        if (account.isDefaultAccount()) {
            account.setDefaultAccount(false);
            accounts.findByVendorIdAndActiveTrueOrderByDefaultAccountDescCreatedAtAsc(vendorId).stream()
                    .filter(a -> !a.getId().equals(accountId))
                    .findFirst()
                    .ifPresent(a -> a.setDefaultAccount(true));
        }
    }

    PayoutAccount owned(Long vendorId, Long accountId) {
        return accounts.findByIdAndVendorIdAndActiveTrue(accountId, vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout account", accountId));
    }

    private static PayoutAccount validated(Long vendorId, AddPayoutAccountRequest r) {
        if (r.method() == PayoutMethod.UPI) {
            String upi = trim(r.upiId());
            upi = upi == null ? null : upi.toLowerCase(Locale.ROOT);
            if (upi == null || !UPI.matcher(upi).matches()) {
                throw invalid("upiId", "Enter a valid UPI ID, e.g. name@okaxis");
            }
            return PayoutAccount.builder().vendorId(vendorId).method(PayoutMethod.UPI).upiId(upi)
                    .accountHolderName(trim(r.accountHolderName())).build();
        }
        String holder = trim(r.accountHolderName());
        String number = r.accountNumber() == null ? null : r.accountNumber().replaceAll("\\s", "");
        String ifsc = trim(r.ifscCode());
        ifsc = ifsc == null ? null : ifsc.toUpperCase(Locale.ROOT);
        if (holder == null || !HOLDER_NAME.matcher(holder).matches()) {
            throw invalid("accountHolderName", "Enter the account holder's name as on the bank account");
        }
        if (number == null || !ACCOUNT_NUMBER.matcher(number).matches()) {
            throw invalid("accountNumber", "Enter a valid bank account number (9 to 18 digits)");
        }
        if (ifsc == null || !IFSC.matcher(ifsc).matches()) {
            throw invalid("ifscCode", "Enter a valid IFSC code, e.g. HDFC0001234");
        }
        return PayoutAccount.builder().vendorId(vendorId).method(PayoutMethod.BANK_ACCOUNT).accountHolderName(holder)
                .accountNumber(number).ifscCode(ifsc).bankName(trim(r.bankName())).build();
    }

    private static BusinessRuleException invalid(String field, String message) {
        return new BusinessRuleException("INVALID_PAYOUT_ACCOUNT", field + ": " + message);
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
