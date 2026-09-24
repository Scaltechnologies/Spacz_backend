package com.spacz.auth.mapper;

import com.spacz.auth.dto.AccountResponse;
import com.spacz.auth.entity.UserAccount;
import org.springframework.stereotype.Component;

@Component
public class AccountMapper {

    public AccountResponse toResponse(UserAccount account) {
        return new AccountResponse(account.getId(), account.getEmail(), account.getPhone(), account.getRole(),
                account.getStatus(), account.hasPassword(), account.getLastLoginAt(), account.getCreatedAt());
    }
}
