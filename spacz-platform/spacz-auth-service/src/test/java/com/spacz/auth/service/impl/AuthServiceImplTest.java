package com.spacz.auth.service.impl;

import com.spacz.auth.audit.AuditEvents;
import com.spacz.auth.client.StudyHallServiceClient;
import com.spacz.auth.client.UserServiceClient;
import com.spacz.auth.client.dto.CreateUserProfileRequest;
import com.spacz.auth.config.AuthProperties;
import com.spacz.auth.config.OtpProperties;
import com.spacz.auth.dto.AuthResponse;
import com.spacz.auth.dto.LoginRequest;
import com.spacz.auth.dto.RegisterUserRequest;
import com.spacz.auth.entity.AccountStatus;
import com.spacz.auth.entity.RefreshToken;
import com.spacz.auth.entity.UserAccount;
import com.spacz.auth.exception.DuplicateResourceException;
import com.spacz.auth.exception.ForbiddenException;
import com.spacz.auth.exception.ServiceUnavailableException;
import com.spacz.auth.mapper.AccountMapper;
import com.spacz.auth.repository.RefreshTokenRepository;
import com.spacz.auth.repository.UserAccountRepository;
import com.spacz.auth.security.Role;
import com.spacz.auth.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");

    @Mock
    private UserAccountRepository accountRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private TokenService tokenService;
    @Mock
    private UserServiceClient userServiceClient;
    @Mock
    private StudyHallServiceClient studyHallServiceClient;
    @Mock
    private AuditEvents auditEvents;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties(Duration.ofMinutes(15), Duration.ofDays(7), 3,
                Duration.ofMinutes(15), new AuthProperties.BootstrapAdmin(null, null));
        authService = new AuthServiceImpl(accountRepository, refreshTokenRepository, passwordEncoder, tokenService,
                userServiceClient, studyHallServiceClient, new AccountMapper(), properties,
                new OtpProperties(6, Duration.ofMinutes(5), 5, Duration.ofSeconds(30), 5, "+91", "log", false),
                auditEvents, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void registerUserCreatesAccountThenProfileAndIssuesTokens() {
        when(accountRepository.existsByEmail("asha@example.com")).thenReturn(false);
        when(accountRepository.saveAndFlush(any(UserAccount.class))).thenAnswer(inv -> withId(inv.getArgument(0), 42L));
        AuthResponse issued = new AuthResponse("access", "refresh", "Bearer", 900, null);
        when(tokenService.issueTokens(any())).thenReturn(issued);

        AuthResponse response = authService.registerUser(new RegisterUserRequest(
                "  Asha@Example.com ", "Secret123", "Asha", "Rao", "+919876543210", "Hyderabad"));

        assertThat(response).isSameAs(issued);
        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(accountRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("asha@example.com");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.USER);
        assertThat(passwordEncoder.matches("Secret123", saved.getValue().getPasswordHash())).isTrue();

        ArgumentCaptor<CreateUserProfileRequest> profile = ArgumentCaptor.forClass(CreateUserProfileRequest.class);
        verify(userServiceClient).createProfile(profile.capture());
        assertThat(profile.getValue().userId()).isEqualTo(42L);
        assertThat(profile.getValue().firstName()).isEqualTo("Asha");
    }

    @Test
    void registerWithExistingEmailIsRejected() {
        when(accountRepository.existsByEmail("asha@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.registerUser(new RegisterUserRequest(
                "asha@example.com", "Secret123", "Asha", null, null, null)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(userServiceClient, never()).createProfile(any());
    }

    @Test
    void registrationFailsWhenProfileServiceIsDownSoTheTransactionRollsBack() {
        when(accountRepository.existsByEmail(anyString())).thenReturn(false);
        when(accountRepository.saveAndFlush(any(UserAccount.class))).thenAnswer(inv -> withId(inv.getArgument(0), 7L));
        doThrow(new ServiceUnavailableException("user-service", "down")).when(userServiceClient).createProfile(any());

        assertThatThrownBy(() -> authService.registerUser(new RegisterUserRequest(
                "x@example.com", "Secret123", "X", null, null, null)))
                .isInstanceOf(ServiceUnavailableException.class);
        verify(tokenService, never()).issueTokens(any());
    }

    @Test
    void loginWithCorrectPasswordSucceeds() {
        UserAccount account = account("asha@example.com", "Secret123", AccountStatus.ACTIVE);
        when(accountRepository.findByEmail("asha@example.com")).thenReturn(Optional.of(account));
        when(tokenService.issueTokens(account)).thenReturn(new AuthResponse("a", "r", "Bearer", 900, null));

        authService.login(new LoginRequest("ASHA@example.com", "Secret123"));

        assertThat(account.getLastLoginAt()).isEqualTo(NOW);
        assertThat(account.getFailedLoginAttempts()).isZero();
    }

    @Test
    void wrongPasswordIsRejectedAndLocksAfterMaxAttempts() {
        UserAccount account = account("asha@example.com", "Secret123", AccountStatus.ACTIVE);
        when(accountRepository.findByEmail("asha@example.com")).thenReturn(Optional.of(account));
        LoginRequest wrong = new LoginRequest("asha@example.com", "Wrong1234");

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> authService.login(wrong)).isInstanceOf(InvalidCredentialsException.class);
        }
        assertThat(account.isLocked(NOW)).isTrue();
        // Even the right password is refused while locked.
        assertThatThrownBy(() -> authService.login(new LoginRequest("asha@example.com", "Secret123")))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo("ACCOUNT_LOCKED");
    }

    @Test
    void unknownEmailGetsTheSameGenericError() {
        when(accountRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "Secret123")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void suspendedAccountCannotLogIn() {
        UserAccount account = account("v@example.com", "Secret123", AccountStatus.SUSPENDED);
        when(accountRepository.findByEmail("v@example.com")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> authService.login(new LoginRequest("v@example.com", "Secret123")))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode").isEqualTo("ACCOUNT_SUSPENDED");
        verify(tokenService, never()).issueTokens(any());
    }

    @Test
    void reusingARevokedRefreshTokenRevokesAllSessions() {
        UserAccount account = withId(account("asha@example.com", "Secret123", AccountStatus.ACTIVE), 5L);
        RefreshToken token = RefreshToken.issue(account, "hash", NOW.plus(Duration.ofDays(1)));
        token.revoke(NOW.minusSeconds(60));
        when(tokenService.hash("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHashForUpdate("hash")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.refresh("raw")).isInstanceOf(InvalidCredentialsException.class);
        verify(refreshTokenRepository).revokeAllActive(eq(5L), any());
        verify(tokenService, never()).issueTokens(any());
    }

    @Test
    void refreshRotatesTheToken() {
        UserAccount account = withId(account("asha@example.com", "Secret123", AccountStatus.ACTIVE), 5L);
        RefreshToken token = RefreshToken.issue(account, "hash", NOW.plus(Duration.ofDays(1)));
        when(tokenService.hash("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHashForUpdate("hash")).thenReturn(Optional.of(token));
        when(tokenService.issueTokens(account)).thenReturn(new AuthResponse("a2", "r2", "Bearer", 900, null));

        AuthResponse response = authService.refresh("raw");

        assertThat(response.refreshToken()).isEqualTo("r2");
        assertThat(token.isRevoked()).isTrue();
    }

    private UserAccount account(String email, String password, AccountStatus status) {
        UserAccount account = UserAccount.create(email, null, passwordEncoder.encode(password), Role.USER);
        account.setStatus(status);
        return account;
    }

    private static UserAccount withId(UserAccount account, Long id) {
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }
}
