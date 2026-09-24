package com.spacz.auth.service.impl;

import com.spacz.auth.config.AuthProperties;
import com.spacz.auth.config.SecurityProperties;
import com.spacz.auth.dto.AuthResponse;
import com.spacz.auth.entity.RefreshToken;
import com.spacz.auth.entity.UserAccount;
import com.spacz.auth.mapper.AccountMapper;
import com.spacz.auth.repository.RefreshTokenRepository;
import com.spacz.auth.security.JwtClaimsConverter;
import com.spacz.auth.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthProperties authProperties;
    private final SecurityProperties securityProperties;
    private final AccountMapper accountMapper;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AuthResponse issueTokens(UserAccount account) {
        Instant now = clock.instant();
        Instant accessExpiry = now.plus(authProperties.accessTokenTtl());

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(securityProperties.jwt().issuer())
                .subject(String.valueOf(account.getId()))
                .issuedAt(now)
                .expiresAt(accessExpiry)
                .id(UUID.randomUUID().toString())
                .claim(JwtClaimsConverter.CLAIM_USER_ID, account.getId())
                .claim(JwtClaimsConverter.CLAIM_ROLE, account.getRole().name());
        if (account.getEmail() != null) {
            claims.claim(JwtClaimsConverter.CLAIM_EMAIL, account.getEmail());
        }
        if (account.getPhone() != null) {
            claims.claim("phone", account.getPhone());
        }
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();

        byte[] randomBytes = new byte[32];
        RANDOM.nextBytes(randomBytes);
        String rawRefreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        refreshTokenRepository.save(RefreshToken.issue(account, hash(rawRefreshToken),
                now.plus(authProperties.refreshTokenTtl())));

        return new AuthResponse(accessToken, rawRefreshToken, "Bearer",
                authProperties.accessTokenTtl().toSeconds(), accountMapper.toResponse(account));
    }

    @Override
    public String hash(String rawRefreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawRefreshToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
