package com.spacz.auth.service.firebase;

/**
 * Verifies a Firebase ID token and returns the phone number Firebase confirmed.
 */
public interface FirebaseTokenVerifier {

    /**
     * @return the verified phone number in E.164 form (e.g. +919876543210)
     * @throws com.spacz.auth.exception.SpaczException 401 for an invalid token or one without a phone
     *         number, 503 when Firebase is not configured or its keys cannot be fetched
     */
    String verifyPhone(String idToken);
}
