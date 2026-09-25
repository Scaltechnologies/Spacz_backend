package com.spacz.auth.service.impl;

import com.spacz.auth.audit.AuditEvents;
import com.spacz.auth.client.StudyHallServiceClient;
import com.spacz.auth.client.UserServiceClient;
import com.spacz.auth.client.dto.CreateUserProfileRequest;
import com.spacz.auth.client.dto.CreateVendorProfileRequest;
import com.spacz.auth.config.OtpProperties;
import com.spacz.auth.config.SecurityProperties;
import com.spacz.auth.dto.AuthResponse;
import com.spacz.auth.dto.OtpRequestResponse;
import com.spacz.auth.dto.OtpVerifyRequest;
import com.spacz.auth.entity.OtpChallenge;
import com.spacz.auth.entity.UserAccount;
import com.spacz.auth.exception.BusinessRuleException;
import com.spacz.auth.exception.ForbiddenException;
import com.spacz.auth.exception.SpaczException;
import com.spacz.auth.repository.OtpChallengeRepository;
import com.spacz.auth.repository.UserAccountRepository;
import com.spacz.auth.security.Role;
import com.spacz.auth.service.OtpService;
import com.spacz.auth.service.TokenService;
import com.spacz.auth.service.sms.SmsSender;
import com.spacz.auth.service.support.PhoneNumbers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Codes are random (SecureRandom), stored as an HMAC keyed with a server secret, valid for a few
 * minutes, limited in attempts, rate-limited per phone, and single use.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpChallengeRepository challengeRepository;
    private final UserAccountRepository accountRepository;
    private final TokenService tokenService;
    private final SmsSender smsSender;
    private final UserServiceClient userServiceClient;
    private final StudyHallServiceClient studyHallServiceClient;
    private final OtpProperties properties;
    private final SecurityProperties securityProperties;
    private final AuditEvents auditEvents;
    private final Clock clock;

    @Override
    @Transactional
    public OtpRequestResponse requestCode(String rawPhone, String requesterIp) {
        String phone = PhoneNumbers.normalize(rawPhone, properties.defaultCountryCode());
        Instant now = clock.instant();
        challengeRepository.findFirstByPhoneOrderByCreatedAtDescIdDesc(phone).ifPresent(last -> {
            Duration since = Duration.between(last.getCreatedAt(), now);
            if (since.compareTo(properties.resendCooldown()) < 0) {
                long wait = properties.resendCooldown().minus(since).toSeconds() + 1;
                throw new SpaczException(HttpStatus.TOO_MANY_REQUESTS, "OTP_TOO_SOON",
                        "Please wait " + wait + " seconds before requesting another code");
            }
        });
        if (challengeRepository.countByPhoneAndCreatedAtAfter(phone, now.minus(Duration.ofHours(1))) >= properties.maxPerHour()) {
            throw new SpaczException(HttpStatus.TOO_MANY_REQUESTS, "OTP_RATE_LIMITED",
                    "Too many codes requested for this number. Try again later.");
        }
        challengeRepository.consumeAllOpen(phone, now);
        String code = generateCode();
        challengeRepository.save(OtpChallenge.issue(phone, hash(phone, code), now.plus(properties.ttl()), requesterIp));
        smsSender.send(phone, "Your SPACZ code is " + code + ". It expires in " + properties.ttl().toMinutes()
                + " minutes. Do not share it.");
        log.info("OTP issued for {}", PhoneNumbers.mask(phone));
        return new OtpRequestResponse(PhoneNumbers.mask(phone), properties.ttl().toSeconds(),
                properties.resendCooldown().toSeconds(), properties.exposeCodeInResponse() ? code : null);
    }

    /**
     * Wrong codes count against the challenge (the transaction commits the attempt counter). A
     * missing registration role is reported before the code is consumed, so the client can retry.
     */
    @Override
    @Transactional(noRollbackFor = {InvalidCredentialsException.class})
    public AuthResponse verify(OtpVerifyRequest request) {
        String phone = PhoneNumbers.normalize(request.phone(), properties.defaultCountryCode());
        Instant now = clock.instant();
        OtpChallenge challenge = challengeRepository.findLatestOpenForUpdate(phone)
                .filter(c -> c.isUsable(now, properties.maxAttempts()))
                .orElseThrow(() -> new InvalidCredentialsException("The code is invalid or has expired"));
        if (!MessageDigest.isEqual(challenge.getCodeHash().getBytes(StandardCharsets.UTF_8),
                hash(phone, request.code()).getBytes(StandardCharsets.UTF_8))) {
            challenge.registerFailedAttempt();
            throw new InvalidCredentialsException("The code is invalid or has expired");
        }

        UserAccount account = accountRepository.findByPhone(phone).orElse(null);
        if (account == null && request.role() == null) {
            throw new BusinessRuleException("REGISTRATION_REQUIRED",
                    "No account for this phone number yet. Send role (USER or VENDOR) to register.");
        }
        challenge.consume(now);
        if (account == null) {
            account = register(phone, request);
        } else {
            AuthServiceImpl.ensureActive(account);
        }
        account.registerSuccessfulLogin(now);
        return tokenService.issueTokens(account);
    }

    /** Removes challenges older than a day. */
    @Scheduled(cron = "${spacz.otp.cleanup-cron:0 15 * * * *}")
    @Transactional
    public void purgeOldChallenges() {
        challengeRepository.deleteCreatedBefore(clock.instant().minus(Duration.ofDays(1)));
    }

    private UserAccount register(String phone, OtpVerifyRequest request) {
        Role role = request.role();
        if (role == Role.ADMIN) {
            throw new ForbiddenException("ADMIN accounts cannot be registered");
        }
        if (role == Role.USER && (request.firstName() == null || request.firstName().isBlank())) {
            throw new SpaczException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "firstName is required to register as USER");
        }
        UserAccount account = accountRepository.saveAndFlush(UserAccount.create(null, phone, null, role));
        if (role == Role.USER) {
            userServiceClient.createProfile(new CreateUserProfileRequest(account.getId(), null,
                    request.firstName().trim(), trimToNull(request.lastName()), phone, trimToNull(request.city())));
            auditEvents.record(account.getId(), role.name(), "USER_REGISTERED", "USER_ACCOUNT", account.getId(),
                    "Student registered with phone OTP");
        } else {
            studyHallServiceClient.createVendorProfile(new CreateVendorProfileRequest(account.getId(), null,
                    trimToNull(request.businessName()), trimToNull(request.contactName()), phone,
                    trimToNull(request.city())));
            auditEvents.record(account.getId(), role.name(), "VENDOR_REGISTERED", "VENDOR", account.getId(),
                    "Vendor registered with phone OTP");
        }
        log.info("Registered {} account {} via phone OTP", role, account.getId());
        return account;
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < properties.length(); i++) {
            code.append(RANDOM.nextInt(10));
        }
        return code.toString();
    }

    private String hash(String phone, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(securityProperties.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((phone + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 is not available", ex);
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
