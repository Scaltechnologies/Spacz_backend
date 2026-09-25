package com.spacz.auth.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/**
 * Turns a verified JWT into an {@link AuthenticatedUserToken}. Tokens without the SPACZ claims
 * ({@code userId}, {@code role}) are rejected with 401.
 */
public class JwtClaimsConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_EMAIL = "email";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        try {
            Object rawUserId = jwt.getClaims().get(CLAIM_USER_ID);
            Long userId = rawUserId instanceof Number number ? number.longValue() : Long.valueOf(jwt.getSubject());
            Role role = Role.valueOf(jwt.getClaimAsString(CLAIM_ROLE));
            AuthenticatedUser principal = new AuthenticatedUser(userId, jwt.getClaimAsString(CLAIM_EMAIL), role);
            return new AuthenticatedUserToken(principal, jwt.getTokenValue());
        } catch (RuntimeException ex) {
            throw new InvalidBearerTokenException("Access token is missing required claims");
        }
    }
}
