package com.spacz.auth.repository;

import com.spacz.auth.entity.AccountStatus;
import com.spacz.auth.entity.UserAccount;
import com.spacz.auth.security.Role;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.Locale;

public final class UserAccountSpecifications {

    private UserAccountSpecifications() {
    }

    /** {@code search} matches email or phone. */
    public static Specification<UserAccount> matching(String search, Role role, AccountStatus status) {
        Specification<UserAccount> spec = Specification.where(null);
        if (StringUtils.hasText(search)) {
            String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("email")), pattern),
                    cb.like(root.get("phone"), pattern)));
        }
        if (role != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("role"), role));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        return spec;
    }
}
