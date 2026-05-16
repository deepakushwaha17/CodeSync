package com.codesync.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VerifyOtpRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "OTP is required")
    @Size(min = 6, max = 6,
            message = "OTP must be 6 digits")
    private String otp;

    // "LOGIN" or "SIGNUP"
    @NotBlank(message = "Purpose is required")
    private String purpose;
}