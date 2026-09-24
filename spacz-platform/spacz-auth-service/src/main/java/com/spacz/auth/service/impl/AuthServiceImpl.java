package com.spacz.auth.service.impl;

import com.spacz.auth.audit.AuditEvents;
import com.spacz.auth.client.StudyHallServiceClient;
import com.spacz.auth.client.UserServiceClient;
import com.spacz.auth.client.dto.CreateUserProfileRequest;
import com.spacz.auth.client.dto.CreateVendorProfileRequest;
import com.spacz.auth.config.AuthProperties;
import com.spacz.auth.config.OtpProperties;
import com.spacz.auth.dto.AccountResponse;
import com.spacz.auth.dto.AuthResponse;
import com.spacz.auth.dto.ChangePasswordRequest;
import com.spacz.auth.dto.LoginRequest;
import com.spacz.auth.dto.RegisterRequest;
import com.spacz.auth.dto.RegisterUserRequest;
import com.spacz.auth.dto.RegisterVendorRequest;
import com.spacz.auth.entity.AccountStatus;
import com.spacz.auth.entity.RefreshToken;
import com.spacz.auth.entity.UserAccount;
import com.spacz.auth.exception.BusinessRuleException;
import com.spacz.auth.exception.DuplicateResourceException;
import com.spacz.auth.exception.ForbiddenException;
import com.spacz.auth.exception.ResourceNotFoundException;
import com.spacz.auth.exception.SpaczException;
import com.spacz.auth.mapper.AccountMapper;
import com.spacz.auth.repository.RefreshTokenRepository;
import com.spacz.auth.repository.UserAccountRepository;
import com.spacz.auth.security.Role;
import com.spacz.auth.service.AuthService;
import com.spacz.auth.service.TokenService;
import com.spacz.auth.service.support.PhoneNumbers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private static final String INVALID_LOGIN = "Invalid email or password";
    private static final String INVALID_REFRESH = "Refresh token is invalid or expired";

    private final UserAccountRepository accountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final UserServiceClient userServiceClient;
    private final StudyHallServiceClient studyHallServiceClient;
    private final AccountMapper accountMapper;
    private final AuthProperties authProperties;
    private final OtpProperties otpProperties;
    private final AuditEvents auditEvents;
    private final Clock clock;
    /** Compared against when the identifier is unknown, so response time does not reveal which accounts exist. */
    private final String dummyPasswordHash;

    public AuthServiceImpl(UserAccountRepository accountRepository, RefreshTokenRepository refreshTokenRepository,
                           PasswordEncoder passwordEncoder, TokenService tokenService,
                           UserServiceClient userServiceClient, StudyHallServiceClient studyHallServiceClient,
                           AccountMapper accountMapper, AuthProperties authProperties, OtpProperties otpProperties,
                           AuditEvents auditEvents, Clock clock) {
        this.accountRepository = accountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.userServiceClient = userServiceClient;
        this.studyHallServiceClient = studyHallServiceClient;
        this.accountMapper = accountMapper;
        this.authProperties = authProperties;
        this.otpProperties = otpProperties;
        this.auditEvents = auditEvents;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("spacz-timing-equalisation");
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        return switch (request.role()) {
            case USER -> {
                if (isBlank(request.firstName())) {
                    throw fieldRequired("firstName", "USER");
                }
                yield registerUser(new RegisterUserRequest(request.email(), request.password(), request.firstName(),
                        request.lastName(), request.phone(), request.city()));
            }
            case VENDOR -> {
                if (isBlank(request.businessName()) || isBlank(request.contactName()) || isBlank(request.phone())) {
                    throw fieldRequired("businessName, contactName and phone", "VENDOR");
                }
                yield registerVendor(new RegisterVendorRequest(request.email(), request.password(),
                        request.businessName(), request.contactName(), request.phone(), request.city()));
            }
            case ADMIN -> throw new ForbiddenException("ADMIN accounts cannot be registered");
        };
    }

    /**
     * The profile is created in user-service while this transaction is still open: if that call
     * fails, the account insert is rolled back, so there is never an account without a profile.
     */
    @Override
    @Transactional
    public AuthResponse registerUser(RegisterUserRequest request) {
        UserAccount account = createAccount(request.email(), request.phone(), request.password(), Role.USER);
        userServiceClient.createProfile(new CreateUserProfileRequest(account.getId(), account.getEmail(),
                request.firstName().trim(), trimToNull(request.lastName()), account.getPhone(), trimToNull(request.city())));
        auditEvents.record(account.getId(), Role.USER.name(), "USER_REGISTERED", "USER_ACCOUNT", account.getId(),
                "Student registered with email");
        log.info("Registered USER account {}", account.getId());
        return tokenService.issueTokens(account);
    }

    @Override
    @Transactional
    public AuthResponse registerVendor(RegisterVendorRequest request) {
        UserAccount account = createAccount(request.email(), request.phone(), request.password(), Role.VENDOR);
        studyHallServiceClient.createVendorProfile(new CreateVendorProfileRequest(account.getId(), account.getEmail(),
                request.businessName().trim(), request.contactName().trim(), account.getPhone(),
                trimToNull(request.city())));
        auditEvents.record(account.getId(), Role.VENDOR.name(), "VENDOR_REGISTERED", "VENDOR", account.getId(),
                "Vendor " + request.businessName().trim() + " registered with email");
        log.info("Registered VENDOR account {}", account.getId());
        return tokenService.issueTokens(account);
    }

    @Override
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, ForbiddenException.class})
    public AuthResponse login(LoginRequest request) {
        Instant now = clock.instant();
        UserAccount account = findByIdentifier(request.email()).orElse(null);
        if (account == null || !account.hasPassword()) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw new InvalidCredentialsException(INVALID_LOGIN);
        }
        if (account.isLocked(now)) {
            throw new ForbiddenException("ACCOUNT_LOCKED",
                    "Too many failed login attempts. Try again after " + account.getLockedUntil());
        }
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            account.registerFailedLogin(authProperties.maxFailedLogins(), authProperties.lockDuration(), now);
            throw new InvalidCredentialsException(INVALID_LOGIN);
        }
        // Status is checked only after the password, so it is never revealed to someone without the password.
        ensureActive(account);
        account.registerSuccessfulLogin(now);
        return tokenService.issueTokens(account);
    }

    @Override
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, ForbiddenException.class})
    public AuthResponse refresh(String rawRefreshToken) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokenRepository.findByTokenHashForUpdate(tokenService.hash(rawRefreshToken))
                .orElseThrow(() -> new InvalidCredentialsException(INVALID_REFRESH));
        UserAccount account = token.getAccount();
        if (token.isRevoked()) {
            // A rotated token was presented again: assume theft and log out every session of this account.
            int revoked = refreshTokenRepository.revokeAllActive(account.getId(), now);
            log.warn("Refresh token reuse detected for account {}; revoked {} active tokens", account.getId(), revoked);
            throw new InvalidCredentialsException(INVALID_REFRESH);
        }
        if (token.isExpired(now)) {
            throw new InvalidCredentialsException(INVALID_REFRESH);
        }
        token.revoke(now);
        if (!account.isActive()) {
            refreshTokenRepository.revokeAllActive(account.getId(), now);
        }
        ensureActive(account);
        return tokenService.issueTokens(account);
    }

    @Override
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(tokenService.hash(rawRefreshToken))
                .ifPresent(token -> token.revoke(clock.instant()));
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .map(accountMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
    }

    @Override
    @Transactional
    public void changePassword(Long accountId, ChangePasswordRequest request) {
        UserAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        if (account.hasPassword()) {
            if (request.currentPassword() == null
                    || !passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
                throw new BusinessRuleException("INVALID_CURRENT_PASSWORD", "Current password is incorrect");
            }
            if (passwordEncoder.matches(request.newPassword(), account.getPasswordHash())) {
                throw new BusinessRuleException("New password must be different from the current password");
            }
        }
        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // Sign out every other session.
        refreshTokenRepository.revokeAllActive(accountId, clock.instant());
    }

    private UserAccount createAccount(String rawEmail, String rawPhone, String password, Role role) {
        String email = normalizeEmail(rawEmail);
        String phone = PhoneNumbers.normalize(rawPhone, otpProperties.defaultCountryCode());
        if (accountRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("An account with this email already exists");
        }
        if (phone != null && accountRepository.existsByPhone(phone)) {
            throw new DuplicateResourceException("An account with this phone number already exists");
        }
        // Flush now so a concurrent duplicate fails on the unique index before any downstream call.
        return accountRepository.saveAndFlush(UserAccount.create(email, phone, passwordEncoder.encode(password), role));
    }

    /** An email, or a phone number in any common format. */
    private Optional<UserAccount> findByIdentifier(String identifier) {
        String trimmed = identifier.trim();
        if (trimmed.contains("@")) {
            return accountRepository.findByEmail(normalizeEmail(trimmed));
        }
        try {
            return accountRepository.findByPhone(PhoneNumbers.normalize(trimmed, otpProperties.defaultCountryCode()));
        } catch (SpaczException ex) {
            return Optional.empty();
        }
    }

    static void ensureActive(UserAccount account) {
        if (account.getStatus() == AccountStatus.SUSPENDED) {
            throw new ForbiddenException("ACCOUNT_SUSPENDED", "This account has been suspended. Contact support.");
        }
        if (account.getStatus() == AccountStatus.DISABLED) {
            throw new ForbiddenException("ACCOUNT_DISABLED", "This account has been disabled.");
        }
    }

    private static SpaczException fieldRequired(String fields, String role) {
        return new SpaczException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", fields + " required for role " + role);
    }

    private static String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
