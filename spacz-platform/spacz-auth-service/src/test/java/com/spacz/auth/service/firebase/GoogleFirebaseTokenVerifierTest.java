package com.spacz.auth.service.firebase;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.spacz.auth.config.FirebaseProperties;
import com.spacz.auth.exception.SpaczException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleFirebaseTokenVerifierTest {

    private static final String PROJECT = "spacz-test";
    private static KeyPair keys;
    private static KeyPair otherKeys;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        otherKeys = generator.generateKeyPair();
    }

    private final GoogleFirebaseTokenVerifier verifier = new GoogleFirebaseTokenVerifier(new FirebaseProperties(PROJECT),
            NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build());

    @Test
    void validTokenReturnsThePhoneNumber() throws Exception {
        assertThat(verifier.verifyPhone(token(keys, b -> { }))).isEqualTo("+919876543210");
    }

    @Test
    void rejectsWrongProjectExpiredUnsignedAndPhonelessTokens() throws Exception {
        assertUnauthorized(token(keys, b -> b.audience("another-project")));
        assertUnauthorized(token(keys, b -> b.issuer("https://securetoken.google.com/another-project")));
        assertUnauthorized(token(keys, b -> b.expirationTime(Date.from(Instant.now().minusSeconds(600)))));
        assertUnauthorized(token(keys, b -> b.claim("phone_number", null)));
        assertUnauthorized(token(otherKeys, b -> { }));
        assertUnauthorized("not-a-jwt");
    }

    @Test
    void unconfiguredProjectAnswers503() {
        GoogleFirebaseTokenVerifier unconfigured = new GoogleFirebaseTokenVerifier(new FirebaseProperties(""));
        assertThatThrownBy(() -> unconfigured.verifyPhone("anything"))
                .isInstanceOfSatisfying(SpaczException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private void assertUnauthorized(String token) {
        assertThatThrownBy(() -> verifier.verifyPhone(token))
                .isInstanceOfSatisfying(SpaczException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    private static String token(KeyPair signingKeys, Consumer<JWTClaimsSet.Builder> customize) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer("https://securetoken.google.com/" + PROJECT)
                .audience(PROJECT)
                .subject("firebase-uid-1")
                .issueTime(Date.from(now.minusSeconds(10)))
                .expirationTime(Date.from(now.plusSeconds(3600)))
                .claim("auth_time", now.minusSeconds(10).getEpochSecond())
                .claim("phone_number", "+919876543210");
        customize.accept(claims);
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k1").build(), claims.build());
        jwt.sign(new RSASSASigner(signingKeys.getPrivate()));
        return jwt.serialize();
    }
}
