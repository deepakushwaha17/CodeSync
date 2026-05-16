package com.codesync.auth.serviceImpl;

import com.codesync.auth.dto.response.OtpResponse;
import com.codesync.auth.entity.OtpVerification;
import com.codesync.auth.repository.OtpVerificationRepository;
import com.codesync.auth.repository.UserRepository;
import com.codesync.auth.service.EmailService;
import com.codesync.auth.service.OtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private final OtpVerificationRepository otpRepo;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${otp.expiry-minutes:5}")
    private int expiryMinutes;

    @Value("${otp.length:6}")
    private int otpLength;

    @Value("${otp.max-attempts:3}")
    private int maxAttempts;

    private final SecureRandom random =
            new SecureRandom();

    @Override
    @Transactional
    public OtpResponse sendOtp(
            String email, String purpose) {

        log.info("Sending OTP to: {} for: {}",
                email, purpose);

        // For LOGIN — check email exists
        if ("LOGIN".equals(purpose)) {
            boolean exists =
                    userRepository
                            .existsByEmail(email);
            if (!exists) {
                return OtpResponse.builder()
                        .success(false)
                        .message("No account found " +
                                "with this email.")
                        .build();
            }
        }

        // For SIGNUP — check email not taken
        if ("SIGNUP".equals(purpose)) {
            boolean exists =
                    userRepository
                            .existsByEmail(email);
            if (exists) {
                return OtpResponse.builder()
                        .success(false)
                        .message("Email already registered."
                                + " Please sign in.")
                        .build();
            }
        }

        // Delete any existing OTP for this email
        otpRepo.deleteByEmailAndPurpose(
                email, purpose);

        // Generate new OTP
        String otp = generateOtp();

        // Save to DB
        OtpVerification otpEntity =
                OtpVerification.builder()
                        .email(email)
                        .otp(otp)
                        .purpose(purpose)
                        .expiresAt(LocalDateTime.now()
                                .plusMinutes(expiryMinutes))
                        .isVerified(false)
                        .attempts(0)
                        .build();

        otpRepo.save(otpEntity);

        // Send email
        emailService.sendOtpEmail(
                email, otp, purpose);

        return OtpResponse.builder()
                .success(true)
                .message("OTP sent to your email.")
                .maskedEmail(maskEmail(email))
                .expiryMinutes(expiryMinutes)
                .build();
    }

    @Override
    @Transactional
    public boolean verifyOtp(
            String email,
            String otp,
            String purpose) {

        Optional<OtpVerification> optOtp =
                otpRepo
                        .findTopByEmailAndPurposeAndIsVerifiedFalseOrderByCreatedAtDesc(
                                email, purpose);

        if (optOtp.isEmpty()) {
            log.warn("No OTP found for: {}", email);
            throw new RuntimeException(
                    "OTP not found. " +
                            "Please request a new one.");
        }

        OtpVerification otpEntity = optOtp.get();

        // Check if expired
        if (LocalDateTime.now()
                .isAfter(otpEntity.getExpiresAt())) {
            otpRepo.delete(otpEntity);
            log.warn("OTP expired for: {}", email);
            throw new RuntimeException(
                    "OTP has expired. " +
                            "Please request a new one.");
        }

        // Check max attempts
        if (otpEntity.getAttempts() >= maxAttempts) {
            otpRepo.delete(otpEntity);
            log.warn("Max OTP attempts for: {}",
                    email);
            throw new RuntimeException(
                    "Too many wrong attempts. " +
                            "Please request a new OTP.");
        }

        // Increment attempts
        otpEntity.setAttempts(
                otpEntity.getAttempts() + 1);
        otpRepo.save(otpEntity);

        // Check if OTP matches
        if (!otpEntity.getOtp().equals(otp)) {
            int remaining =
                    maxAttempts - otpEntity.getAttempts();
            log.warn("Wrong OTP for: {}", email);
            throw new RuntimeException(
                    "Incorrect OTP. " + remaining
                            + " attempts remaining.");
        }

        // Mark as verified
        otpEntity.setIsVerified(true);
        otpRepo.save(otpEntity);

        log.info("OTP verified for: {}", email);
        return true;
    }

    @Override
    public boolean isEmailRegistered(String email) {
        return userRepository.existsByEmail(email);
    }

    // ── Helpers ──────────────────────────────────────

    private String generateOtp() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < otpLength; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 2) return email;
        return email.substring(0, 2)
                + "***"
                + email.substring(atIndex);
    }
}