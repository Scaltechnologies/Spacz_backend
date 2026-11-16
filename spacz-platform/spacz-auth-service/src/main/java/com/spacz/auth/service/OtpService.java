package com.spacz.auth.service;

import com.spacz.auth.dto.AuthResponse;
import com.spacz.auth.dto.OtpRequestResponse;
import com.spacz.auth.dto.OtpVerifyRequest;

/**
 * Phone + one-time-code login and registration.
 */
public interface OtpService {

    OtpRequestResponse requestCode(String phone, String requesterIp);

    AuthResponse verify(OtpVerifyRequest request);
}
