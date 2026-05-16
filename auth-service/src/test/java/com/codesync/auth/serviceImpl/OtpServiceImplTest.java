package com.codesync.auth.serviceImpl;

import com.codesync.auth.dto.response.OtpResponse;
import com.codesync.auth.entity.OtpVerification;
import com.codesync.auth.repository.OtpVerificationRepository;
import com.codesync.auth.repository.UserRepository;
import com.codesync.auth.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OtpServiceImpl.
 */
@ExtendWith(MockitoExtension.class)
class OtpServiceImplTest {

    @Mock private OtpVerificationRepository otpRepo;
    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;

    @InjectMocks private OtpServiceImpl otpService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(otpService, "expiryMinutes", 5);
        ReflectionTestUtils.setField(otpService, "otpLength", 6);
        ReflectionTestUtils.setField(otpService, "maxAttempts", 3);
    }

    // ── Helper ─────────────────────────────────────────────────────────────

    private OtpVerification buildOtpEntity(String email, String otp,
                                           String purpose,
                                           boolean verified,
                                           int attempts,
                                           LocalDateTime expiresAt) {
        return OtpVerification.builder()
                .id(1L)
                .email(email)
                .otp(otp)
                .purpose(purpose)
                .isVerified(verified)
                .attempts(attempts)
                .expiresAt(expiresAt)
                .build();
    }

    // ════════════════════════════════════════════════════════════════════
    // sendOtp()
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("sendOtp LOGIN - success when email exists")
    void sendOtp_login_emailExists_returnsSuccess() {
        when(userRepository.existsByEmail("user@test.com")).thenReturn(true);
        when(otpRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(emailService).sendOtpEmail(anyString(), anyString(), anyString());

        OtpResponse response = otpService.sendOtp("user@test.com", "LOGIN");

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getMessage()).contains("OTP sent");
        assertThat(response.getMaskedEmail()).isNotNull();
        assertThat(response.getExpiryMinutes()).isEqualTo(5);
        verify(emailService).sendOtpEmail(eq("user@test.com"), anyString(), eq("LOGIN"));
    }

    @Test
    @DisplayName("sendOtp LOGIN - returns failure when email not registered")
    void sendOtp_login_emailNotRegistered_returnsFailure() {
        when(userRepository.existsByEmail("nobody@test.com")).thenReturn(false);

        OtpResponse response = otpService.sendOtp("nobody@test.com", "LOGIN");

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getMessage()).contains("No account found");
        verify(emailService, never()).sendOtpEmail(any(), any(), any());
    }

    @Test
    @DisplayName("sendOtp SIGNUP - success when email not yet registered")
    void sendOtp_signup_emailAvailable_returnsSuccess() {
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(otpRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(emailService).sendOtpEmail(anyString(), anyString(), anyString());

        OtpResponse response = otpService.sendOtp("new@test.com", "SIGNUP");

        assertThat(response.getSuccess()).isTrue();
        verify(emailService).sendOtpEmail(eq("new@test.com"), anyString(), eq("SIGNUP"));
    }

    @Test
    @DisplayName("sendOtp SIGNUP - returns failure when email already registered")
    void sendOtp_signup_emailAlreadyRegistered_returnsFailure() {
        when(userRepository.existsByEmail("taken@test.com")).thenReturn(true);

        OtpResponse response = otpService.sendOtp("taken@test.com", "SIGNUP");

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getMessage()).contains("already registered");
        verify(emailService, never()).sendOtpEmail(any(), any(), any());
    }

    @Test
    @DisplayName("sendOtp - deletes existing OTP before creating new one")
    void sendOtp_existingOtp_deletedBeforeCreatingNew() {
        when(userRepository.existsByEmail("user@test.com")).thenReturn(true);
        when(otpRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(emailService).sendOtpEmail(any(), any(), any());

        otpService.sendOtp("user@test.com", "LOGIN");

        verify(otpRepo).deleteByEmailAndPurpose("user@test.com", "LOGIN");
    }


    // ════════════════════════════════════════════════════════════════════
    // verifyOtp()
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("verifyOtp - success: returns true for correct OTP")
    void verifyOtp_correctOtp_returnsTrue() {
        OtpVerification otp = buildOtpEntity("user@test.com", "123456",
                "LOGIN", false, 0, LocalDateTime.now().plusMinutes(5));
        when(otpRepo.findTopByEmailAndPurposeAndIsVerifiedFalseOrderByCreatedAtDesc(
                "user@test.com", "LOGIN")).thenReturn(Optional.of(otp));
        when(otpRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        boolean result = otpService.verifyOtp("user@test.com", "123456", "LOGIN");

        assertThat(result).isTrue();
        verify(otpRepo, times(2)).save(any()); // once for attempts, once for isVerified
    }

    @Test
    @DisplayName("verifyOtp - throws RuntimeException when no OTP record found")
    void verifyOtp_noOtpFound_throwsRuntimeException() {
        when(otpRepo.findTopByEmailAndPurposeAndIsVerifiedFalseOrderByCreatedAtDesc(
                "user@test.com", "LOGIN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> otpService.verifyOtp("user@test.com", "123456", "LOGIN"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("OTP not found");
    }

    @Test
    @DisplayName("verifyOtp - throws RuntimeException when OTP is expired")
    void verifyOtp_expiredOtp_throwsRuntimeException() {
        OtpVerification otp = buildOtpEntity("user@test.com", "123456",
                "LOGIN", false, 0,
                LocalDateTime.now().minusMinutes(1)); // expired
        when(otpRepo.findTopByEmailAndPurposeAndIsVerifiedFalseOrderByCreatedAtDesc(
                "user@test.com", "LOGIN")).thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> otpService.verifyOtp("user@test.com", "123456", "LOGIN"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("expired");

        verify(otpRepo).delete(otp);
    }

    @Test
    @DisplayName("verifyOtp - throws RuntimeException when max attempts exceeded")
    void verifyOtp_maxAttemptsExceeded_throwsRuntimeException() {
        OtpVerification otp = buildOtpEntity("user@test.com", "123456",
                "LOGIN", false, 3, // maxAttempts is 3
                LocalDateTime.now().plusMinutes(5));
        when(otpRepo.findTopByEmailAndPurposeAndIsVerifiedFalseOrderByCreatedAtDesc(
                "user@test.com", "LOGIN")).thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> otpService.verifyOtp("user@test.com", "wrong", "LOGIN"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Too many wrong attempts");

        verify(otpRepo).delete(otp);
    }

    @Test
    @DisplayName("verifyOtp - throws RuntimeException for wrong OTP, shows remaining attempts")
    void verifyOtp_wrongOtp_throwsWithRemainingAttempts() {
        OtpVerification otp = buildOtpEntity("user@test.com", "123456",
                "LOGIN", false, 1, // 1 attempt used
                LocalDateTime.now().plusMinutes(5));
        when(otpRepo.findTopByEmailAndPurposeAndIsVerifiedFalseOrderByCreatedAtDesc(
                "user@test.com", "LOGIN")).thenReturn(Optional.of(otp));
        when(otpRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> otpService.verifyOtp("user@test.com", "999999", "LOGIN"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Incorrect OTP")
                .hasMessageContaining("1 attempts remaining"); // 3 max - 2 attempts = 1
    }

    @Test
    @DisplayName("verifyOtp - marks OTP as verified on success")
    void verifyOtp_success_setsIsVerifiedTrue() {
        OtpVerification otp = buildOtpEntity("user@test.com", "654321",
                "SIGNUP", false, 0, LocalDateTime.now().plusMinutes(5));
        when(otpRepo.findTopByEmailAndPurposeAndIsVerifiedFalseOrderByCreatedAtDesc(
                "user@test.com", "SIGNUP")).thenReturn(Optional.of(otp));
        when(otpRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        otpService.verifyOtp("user@test.com", "654321", "SIGNUP");

        assertThat(otp.getIsVerified()).isTrue();
    }

    // ════════════════════════════════════════════════════════════════════
    // isEmailRegistered()
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("isEmailRegistered - returns true when email exists")
    void isEmailRegistered_existingEmail_returnsTrue() {
        when(userRepository.existsByEmail("exists@test.com")).thenReturn(true);

        assertThat(otpService.isEmailRegistered("exists@test.com")).isTrue();
    }

    @Test
    @DisplayName("isEmailRegistered - returns false when email does not exist")
    void isEmailRegistered_nonExistingEmail_returnsFalse() {
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);

        assertThat(otpService.isEmailRegistered("new@test.com")).isFalse();
    }
}