package com.spacz.auth.service.impl;

import com.spacz.auth.config.OtpProperties;
import com.spacz.auth.dto.AccountResponse;
import com.spacz.auth.dto.AccountStatsResponse;
import com.spacz.auth.dto.PageResponse;
import com.spacz.auth.dto.internal.ImportAccountRequest;
import com.spacz.auth.dto.internal.ImportAccountResult;
import com.spacz.auth.entity.AccountStatus;
import com.spacz.auth.entity.UserAccount;
import com.spacz.auth.exception.BusinessRuleException;
import com.spacz.auth.exception.ResourceNotFoundException;
import com.spacz.auth.mapper.AccountMapper;
import com.spacz.auth.repository.RefreshTokenRepository;
import com.spacz.auth.repository.UserAccountRepository;
import com.spacz.auth.repository.UserAccountSpecifications;
import com.spacz.auth.security.Role;
import com.spacz.auth.service.AccountManagementService;
import com.spacz.auth.service.support.PhoneNumbers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountManagementServiceImpl implements AccountManagementService {

    private final UserAccountRepository accountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountMapper accountMapper;
    private final OtpProperties otpProperties;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> search(String search, Role role, AccountStatus status, Pageable pageable) {
        return PageResponse.from(accountRepository.findAll(
                UserAccountSpecifications.matching(search, role, status), pageable), accountMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse get(Long accountId) {
        return accountMapper.toResponse(find(accountId));
    }

    @Override
    @Transactional
    public AccountResponse updateStatus(Long accountId, AccountStatus status) {
        UserAccount account = find(accountId);
        if (account.getRole() == Role.ADMIN) {
            throw new BusinessRuleException("The status of ADMIN accounts cannot be changed through this API");
        }
        if (account.getStatus() == status) {
            throw new BusinessRuleException("Account is already " + status);
        }
        if (account.getStatus() == AccountStatus.DISABLED) {
            throw new BusinessRuleException("A disabled account cannot be changed");
        }
        account.setStatus(status);
        if (status != AccountStatus.ACTIVE) {
            refreshTokenRepository.revokeAllActive(accountId, clock.instant());
        }
        log.info("Account {} status changed to {}", accountId, status);
        return accountMapper.toResponse(account);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountStatsResponse stats() {
        return new AccountStatsResponse(
                accountRepository.countByRole(Role.USER),
                accountRepository.countByRoleAndStatus(Role.USER, AccountStatus.ACTIVE),
                accountRepository.countByRoleAndStatus(Role.USER, AccountStatus.SUSPENDED),
                accountRepository.countByRole(Role.VENDOR),
                accountRepository.countByRoleAndStatus(Role.VENDOR, AccountStatus.ACTIVE),
                accountRepository.countByRole(Role.ADMIN));
    }

    @Override
    @Transactional
    public ImportAccountResult importAccount(ImportAccountRequest request) {
        if (request.role() == Role.ADMIN) {
            throw new BusinessRuleException("ADMIN accounts cannot be imported");
        }
        String phone = PhoneNumbers.normalize(request.phone(), otpProperties.defaultCountryCode());
        String email = request.email() == null || request.email().isBlank()
                ? null : request.email().trim().toLowerCase(Locale.ROOT);
        if (phone == null && email == null) {
            throw new BusinessRuleException("IDENTIFIER_REQUIRED", "A legacy account needs a phone number or an email");
        }
        Optional<UserAccount> existing = Optional.<UserAccount>empty()
                .or(() -> request.legacyLoginId() == null ? Optional.empty() : accountRepository.findByLegacyLoginId(request.legacyLoginId()))
                .or(() -> phone == null ? Optional.empty() : accountRepository.findByPhone(phone))
                .or(() -> email == null ? Optional.empty() : accountRepository.findByEmail(email));
        if (existing.isPresent()) {
            UserAccount account = existing.get();
            if (account.getLegacyLoginId() == null && request.legacyLoginId() != null) {
                account.setLegacyLoginId(request.legacyLoginId());
            }
            return new ImportAccountResult(account.getId(), false, account.getRole().name());
        }
        UserAccount account = UserAccount.create(email, phone, null, request.role());
        account.setLegacyLoginId(request.legacyLoginId());
        UserAccount saved = accountRepository.saveAndFlush(account);
        return new ImportAccountResult(saved.getId(), true, saved.getRole().name());
    }

    private UserAccount find(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
    }
}
