package com.spacz.auth.config;

import com.spacz.auth.entity.AccountStatus;
import com.spacz.auth.entity.UserAccount;
import com.spacz.auth.repository.UserAccountRepository;
import com.spacz.auth.security.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Creates the first platform administrator from environment variables. There is intentionally no
 * public endpoint that can create an ADMIN account.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final int MIN_ADMIN_PASSWORD_LENGTH = 12;

    private final AuthProperties authProperties;
    private final UserAccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AuthProperties.BootstrapAdmin admin = authProperties.bootstrapAdmin();
        if (!StringUtils.hasText(admin.email()) || !StringUtils.hasText(admin.password())) {
            log.info("No bootstrap admin configured (BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD)");
            return;
        }
        String email = admin.email().trim().toLowerCase(Locale.ROOT);
        if (accountRepository.existsByEmail(email)) {
            return;
        }
        if (admin.password().length() < MIN_ADMIN_PASSWORD_LENGTH) {
            log.error("Bootstrap admin NOT created: password must be at least {} characters", MIN_ADMIN_PASSWORD_LENGTH);
            return;
        }
        UserAccount account = UserAccount.create(email, null, passwordEncoder.encode(admin.password()), Role.ADMIN);
        account.setStatus(AccountStatus.ACTIVE);
        accountRepository.save(account);
        log.info("Bootstrap admin account created for {}", email);
    }
}
