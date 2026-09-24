package com.spacz.auth.repository;

import com.spacz.auth.entity.AccountStatus;
import com.spacz.auth.entity.UserAccount;
import com.spacz.auth.security.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long>, JpaSpecificationExecutor<UserAccount> {

    Optional<UserAccount> findByEmail(String email);

    Optional<UserAccount> findByPhone(String phone);

    Optional<UserAccount> findByLegacyLoginId(Long legacyLoginId);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    long countByRole(Role role);

    long countByRoleAndStatus(Role role, AccountStatus status);
}
