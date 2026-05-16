package com.codesync.auth.repository;

import com.codesync.auth.entity.OtpVerification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository-layer tests for OtpVerificationRepository.
 */
@DataJpaTest
@ActiveProfiles("test")
class OtpVerificationRepositoryTest {

    @Autowired
    private OtpVerificationRepository otpRepository;

    @BeforeEach
    void setUp() {
        otpRepository.deleteAll();
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private OtpVerification buildOtp(String email, String otp,
                                     String purpose, boolean verified,
                                     LocalDateTime expiresAt) {
        return OtpVerification.builder()
                .email(email)
                .otp(otp)
                .purpose(purpose)
                .isVerified(verified)
                .attempts(0)
                .expiresAt(expiresAt)
                .build();
    }

    // ── findTopByEmailAndPurposeAndIsVerifiedFalseOrderByCreatedAtDesc ─────

    @Test
    @DisplayName("findTopByEmailAndPurpose - returns latest unverified OTP")
    void findTopByEmailAndPurpose_latestUnverified_returnsCorrectOtp() {
        // Save two OTPs for same email — latest should be returned
        OtpVerification older = buildOtp("user@test.com", "111111", "LOGIN",
                false, LocalDateTime.now().plusMinutes(5));
        otpRepository.save(older);

        // small delay to ensure different createdAt ordering
        OtpVerification newer = buildOtp("user@test.com", "222222", "LOGIN",
                false, LocalDateTime.now().plusMinutes(5));
        otpRepository.save(newer);

        Optional<OtpVerification> result =
                otpRepository.findTopByEmailAndPurposeAndIsVerifiedFalseOrderByCreatedAtDesc(
                        "user@test.com", "LOGIN");

        assertThat(result).isPresent();
        // Should be the newest OTP (222222)
        assertThat(result.get().getOtp()).isEqualTo("222222");
    }

    @Test
    @DisplayName("findTopByEmailAndPurpose - returns empty for wrong purpose")
    void findTopByEmailAndPurpose_wrongPurpose_returnsEmpty() {
        OtpVerification otp = buildOtp("user@test.com", "123456",
                "SIGNUP", false, LocalDateTime.now().plusMinutes(5));
        otpRepository.save(otp);

        Optional<OtpVerification> result =
                otpRepository.findTopByEmailAndPurposeAndIsVerifiedFalseOrderByCreatedAtDesc(
                        "user@test.com", "LOGIN"); // wrong purpose

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findTopByEmailAndPurpose - returns empty for different email")
    void findTopByEmailAndPurpose_differentEmail_returnsEmpty() {
        OtpVerification otp = buildOtp("user@test.com", "123456",
                "LOGIN", false, LocalDateTime.now().plusMinutes(5));
        otpRepository.save(otp);

        Optional<OtpVerification> result =
                otpRepository.findTopByEmailAndPurposeAndIsVerifiedFalseOrderByCreatedAtDesc(
                        "other@test.com", "LOGIN");

        assertThat(result).isEmpty();
    }

    // ── deleteByEmailAndPurpose ────────────────────────────────────────────

    @Test
    @DisplayName("deleteByEmailAndPurpose - deletes matching records only")
    void deleteByEmailAndPurpose_matchingRecords_deletedSuccessfully() {
        otpRepository.save(buildOtp("del@test.com", "111111",
                "LOGIN", false, LocalDateTime.now().plusMinutes(5)));
        otpRepository.save(buildOtp("del@test.com", "222222",
                "SIGNUP", false, LocalDateTime.now().plusMinutes(5)));

        otpRepository.deleteByEmailAndPurpose("del@test.com", "LOGIN");

        // LOGIN OTP should be gone
        Optional<OtpVerification> loginOtp =
                otpRepository.findTopByEmailAndPurposeAndIsVerifiedFalseOrderByCreatedAtDesc(
                        "del@test.com", "LOGIN");
        assertThat(loginOtp).isEmpty();

        // SIGNUP OTP should still exist
        Optional<OtpVerification> signupOtp =
                otpRepository.findTopByEmailAndPurposeAndIsVerifiedFalseOrderByCreatedAtDesc(
                        "del@test.com", "SIGNUP");
        assertThat(signupOtp).isPresent();
    }

    @Test
    @DisplayName("deleteByEmailAndPurpose - no-op when no records match")
    void deleteByEmailAndPurpose_noMatch_doesNotThrow() {
        // Should not throw
        otpRepository.deleteByEmailAndPurpose("nobody@test.com", "LOGIN");
        assertThat(otpRepository.count()).isZero();
    }

    // ── deleteExpiredOtps ──────────────────────────────────────────────────

    @Test
    @DisplayName("deleteExpiredOtps - removes only expired OTPs")
    void deleteExpiredOtps_expiredRecords_removedSuccessfully() {
        // Expired OTP
        OtpVerification expired = buildOtp("exp@test.com", "000000",
                "LOGIN", false, LocalDateTime.now().minusMinutes(10));
        otpRepository.save(expired);

        // Valid OTP
        OtpVerification valid = buildOtp("valid@test.com", "123456",
                "LOGIN", false, LocalDateTime.now().plusMinutes(5));
        otpRepository.save(valid);

        otpRepository.deleteExpiredOtps(LocalDateTime.now());

        assertThat(otpRepository.count()).isEqualTo(1);
        assertThat(otpRepository.findAll().get(0).getEmail())
                .isEqualTo("valid@test.com");
    }

    @Test
    @DisplayName("deleteExpiredOtps - no-op when no OTPs have expired")
    void deleteExpiredOtps_noneExpired_keepsAll() {
        otpRepository.save(buildOtp("a@test.com", "111111",
                "LOGIN", false, LocalDateTime.now().plusMinutes(5)));
        otpRepository.save(buildOtp("b@test.com", "222222",
                "SIGNUP", false, LocalDateTime.now().plusMinutes(10)));

        otpRepository.deleteExpiredOtps(LocalDateTime.now());

        assertThat(otpRepository.count()).isEqualTo(2);
    }

    // ── save & prePersist ──────────────────────────────────────────────────

    @Test
    @DisplayName("save - @PrePersist sets createdAt, isVerified=false, attempts=0")
    void save_newOtp_prePersistFieldsSetCorrectly() {
        OtpVerification otp = OtpVerification.builder()
                .email("pre@test.com")
                .otp("654321")
                .purpose("LOGIN")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        OtpVerification saved = otpRepository.save(otp);

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getIsVerified()).isFalse();
        assertThat(saved.getAttempts()).isZero();
    }
}