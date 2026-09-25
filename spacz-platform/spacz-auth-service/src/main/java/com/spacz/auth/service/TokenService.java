package com.spacz.auth.service;

import com.spacz.auth.dto.AuthResponse;
import com.spacz.auth.entity.UserAccount;

public interface TokenService {

    /** Issues a new access token and a new refresh token (persisted as a hash). */
    AuthResponse issueTokens(UserAccount account);

    String hash(String rawRefreshToken);
}
