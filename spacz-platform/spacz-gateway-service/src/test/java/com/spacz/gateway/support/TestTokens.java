package com.spacz.gateway.support;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * Mints real HS256 tokens with the test secret, so tests exercise the actual JWT validation.
 */
public final class TestTokens {

    public static final String SECRET = "test-only-jwt-secret-for-automated-tests-0123456789";
    public static final String ISSUER = "spacz-auth";
    public static final String INTERNAL_KEY = "test-internal-api-key-0123456789";

    private TestTokens() {
    }

    public static String token(long userId, String role) {
        return token(userId, role, SECRET, ISSUER, Duration.ofMinutes(15));
    }

    public static String bearer(long userId, String role) {
        return "Bearer " + token(userId, role);
    }

    public static String token(long userId, String role, String secret, String issuer, Duration ttl) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        Instant issuedAt = ttl.isNegative() ? expiresAt.minusSeconds(60) : now.minusSeconds(1);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("userId", userId)
                .claim("email", "user" + userId + "@test.local")
                .claim("role", role)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
