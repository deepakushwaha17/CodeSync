package com.codesync.auth.service;

import com.codesync.auth.dto.response.OtpResponse;

public interface OtpService {

    // Generate and send OTP
    OtpResponse sendOtp(
            String email, String purpose
    );

    // Verify OTP — returns true if valid
    boolean verifyOtp(
            String email,
            String otp,
            String purpose
    );

    // Check if email already registered
    boolean isEmailRegistered(String email);
}