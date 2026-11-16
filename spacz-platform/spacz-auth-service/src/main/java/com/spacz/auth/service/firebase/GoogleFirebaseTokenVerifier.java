package com.spacz.auth.service.firebase;

import com.spacz.auth.config.FirebaseProperties;
import com.spacz.auth.exception.ServiceUnavailableException;
import com.spacz.auth.exception.SpaczException;
import com.spacz.auth.service.impl.InvalidCredentialsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;

/**
 * Checks Firebase ID tokens the way the Firebase Admin SDK does, without needing a service account:
 * RS256 signature against Google's published keys, issuer {@code https://securetoken.google.com/<project>},
 * audience = project ID, not expired, non-empty subject, and auth_time in the past.
 */
@Slf4j
@Component
public class GoogleFirebaseTokenVerifier implements FirebaseTokenVerifier {

    static final String JWK_SET_URI =
            "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

    private final FirebaseProperties properties;
    private volatile JwtDecoder decoder;

    @Autowired
    public GoogleFirebaseTokenVerifier(FirebaseProperties properties) {
        this.properties = properties;
    }

    /** For tests: a decoder with a local key instead of Google's key set. */
    GoogleFirebaseTokenVerifier(FirebaseProperties properties, NimbusJwtDecoder decoder) {
        this.properties = properties;
        decoder.setJwtValidator(validator(properties.projectId()));
        this.decoder = decoder;
    }

    @Override
    public String verifyPhone(String idToken) {
        if (!properties.enabled()) {
            throw new SpaczException(HttpStatus.SERVICE_UNAVAILABLE, "FIREBASE_NOT_CONFIGURED",
                    "Firebase phone login is not configured on the server");
        }
        Jwt jwt;
        try {
            jwt = decoder().decode(idToken);
        } catch (BadJwtException ex) {
            log.info("Rejected Firebase ID token: {}", ex.getMessage());
            throw new InvalidCredentialsException("The Firebase sign-in is invalid or has expired. Verify the phone number again.");
        } catch (JwtException ex) {
            log.error("Could not verify Firebase ID token", ex);
            throw new ServiceUnavailableException("Firebase token verification", ex);
        }
        String phone = jwt.getClaimAsString("phone_number");
        if (phone == null || phone.isBlank()) {
            throw new InvalidCredentialsException("The Firebase sign-in has no verified phone number");
        }
        return phone;
    }

    private JwtDecoder decoder() {
        JwtDecoder local = decoder;
        if (local == null) {
            synchronized (this) {
                if (decoder == null) {
                    SimpleClientHttpRequestFactory http = new SimpleClientHttpRequestFactory();
                    http.setConnectTimeout(Duration.ofSeconds(3));
                    http.setReadTimeout(Duration.ofSeconds(5));
                    NimbusJwtDecoder built = NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI)
                            .jwsAlgorithm(SignatureAlgorithm.RS256)
                            .restOperations(new RestTemplate(http))
                            .build();
                    built.setJwtValidator(validator(properties.projectId()));
                    decoder = built;
                }
                local = decoder;
            }
        }
        return local;
    }

    private static OAuth2TokenValidator<Jwt> validator(String projectId) {
        OAuth2TokenValidator<Jwt> firebaseClaims = jwt -> {
            boolean audienceOk = jwt.getAudience() != null && jwt.getAudience().contains(projectId);
            boolean subjectOk = jwt.getSubject() != null && !jwt.getSubject().isBlank();
            Instant authTime = jwt.getClaimAsInstant("auth_time");
            boolean authTimeOk = authTime == null || !authTime.isAfter(Instant.now().plusSeconds(60));
            if (audienceOk && subjectOk && authTimeOk) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                    "Firebase token has the wrong audience, no subject, or a future auth_time", null));
        };
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer("https://securetoken.google.com/" + projectId), firebaseClaims);
    }
}
