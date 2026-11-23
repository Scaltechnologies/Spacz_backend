package com.spacz.admin.paymentgateway.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM encryption for secrets admin-service stores at rest (payment gateway key secrets,
 * webhook secrets, and any future admin-entered credential). These must be recoverable in plaintext
 * for studyhall-service to use them against Razorpay, so they're encrypted, never hashed. Each call
 * generates a fresh random IV and prepends it to the ciphertext before base64-encoding, so no two
 * encryptions of the same plaintext ever produce the same stored value.
 *
 * Deliberately keyed by its own dedicated ADMIN_SECRETS_ENCRYPTION_KEY rather than reusing
 * JWT_SECRET: mixing an HMAC-signing key with an at-rest encryption key is a key-reuse anti-pattern.
 */
@Component
public class SecretEncryptionService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int KEY_LENGTH_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final String base64Key;

    public SecretEncryptionService(@Value("${spacz.security.secrets-encryption-key:}") String base64Key) {
        this.base64Key = base64Key;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return null;
        SecretKeySpec key = key();
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt secret", e);
        }
    }

    public String decrypt(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        SecretKeySpec key = key();
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(combined, IV_LENGTH_BYTES, combined.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt secret", e);
        }
    }

    /** Last-4-chars mask for admin-facing display; never returns enough to reconstruct the secret. */
    public String mask(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return null;
        int visible = Math.min(4, plaintext.length());
        return "•".repeat(Math.max(0, plaintext.length() - visible))
                + plaintext.substring(plaintext.length() - visible);
    }

    private SecretKeySpec key() {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("ADMIN_SECRETS_ENCRYPTION_KEY is not configured on this environment");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "ADMIN_SECRETS_ENCRYPTION_KEY is not valid base64 (generate one with: openssl rand -base64 32)");
        }
        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException("ADMIN_SECRETS_ENCRYPTION_KEY must decode to " + KEY_LENGTH_BYTES
                    + " bytes for AES-256 (got " + keyBytes.length + "); generate one with: openssl rand -base64 32");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}
