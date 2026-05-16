package com.codesync.auth.repository;

import com.codesync.auth.entity.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface OtpVerificationRepository
        extends JpaRepository<OtpVerification, Long> {

    // Find latest unverified OTP for email + purpose
    Optional<OtpVerification> findTopByEmailAndPurposeAndIsVerifiedFalseOrderByCreatedAtDesc(
            String email, String purpose);

    // Delete all OTPs for an email
    @Modifying
    @Transactional
    @Query("DELETE FROM OtpVerification o " +
            "WHERE o.email = :email " +
            "AND o.purpose = :purpose")
    void deleteByEmailAndPurpose(
            String email, String purpose);

    // Cleanup expired OTPs
    @Modifying
    @Transactional
    @Query("DELETE FROM OtpVerification o " +
            "WHERE o.expiresAt < :now")
    void deleteExpiredOtps(LocalDateTime now);
}