package com.codesync.collab.repository;

import com.codesync.collab.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParticipantRepository
        extends JpaRepository<Participant, Long> {

    List<Participant> findBySessionId(String sessionId);

    // Only active participants (not left)
    List<Participant> findBySessionIdAndLeftAtIsNull(
            String sessionId);

    Optional<Participant> findBySessionIdAndUserId(
            String sessionId, Long userId);

    boolean existsBySessionIdAndUserId(
            String sessionId, Long userId);

    // Count active participants in a session
    @Query("SELECT COUNT(p) FROM Participant p " +
            "WHERE p.sessionId = :sessionId " +
            "AND p.leftAt IS NULL")
    int countActiveParticipants(
            @Param("sessionId") String sessionId);

    List<Participant> findByUserId(Long userId);
}