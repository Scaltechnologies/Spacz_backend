package com.spacz.studyhall.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public class AuthenticatedUserToken extends AbstractAuthenticationToken {

    private final AuthenticatedUser principal;
    private final String token;

    public AuthenticatedUserToken(AuthenticatedUser principal, String token) {
        super(List.of(new SimpleGrantedAuthority(principal.role().authority())));
        this.principal = principal;
        this.token = token;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public AuthenticatedUser getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return String.valueOf(principal.userId());
    }
}
