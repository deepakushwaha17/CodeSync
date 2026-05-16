package com.codesync.collab.repository;

import com.codesync.collab.entity.CollabSession;
import com.codesync.collab.enums.SessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class CollabSessionRepositoryTest {

    @Autowired
    private CollabSessionRepository sessionRepository;

    @BeforeEach
    void setUp() { sessionRepository.deleteAll(); }

    // ── Helper ────────────────────────────────────────────────────────

    private CollabSession build(Long projectId, Long fileId,
                                Long ownerId, SessionStatus status,
                                LocalDateTime lastActivity) {
        return CollabSession.builder()
                .projectId(projectId).fileId(fileId).ownerId(ownerId)
                .status(status).language("java").maxParticipants(10)
                .isPasswordProtected(false).lastActivityAt(lastActivity)
                .build();
    }

    // ── findByProjectId ────────────────────────────────────────────────

    @Test
    @DisplayName("findByProjectId - returns all sessions for project")
    void findByProjectId_existing_returnsSessions() {
        sessionRepository.save(build(1L, 10L, 1L, SessionStatus.ACTIVE,
                LocalDateTime.now()));
        sessionRepository.save(build(1L, 11L, 1L, SessionStatus.ENDED,
                LocalDateTime.now()));
        sessionRepository.save(build(2L, 20L, 2L, SessionStatus.ACTIVE,
                LocalDateTime.now()));

        List<CollabSession> results = sessionRepository.findByProjectId(1L);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(s -> s.getProjectId().equals(1L));
    }

    @Test
    @DisplayName("findByProjectId - returns empty for unknown project")
    void findByProjectId_unknown_returnsEmpty() {
        assertThat(sessionRepository.findByProjectId(99L)).isEmpty();
    }

    // ── findByFileId ───────────────────────────────────────────────────

    @Test
    @DisplayName("findByFileId - returns all sessions for a file")
    void findByFileId_existing_returnsSessions() {
        sessionRepository.save(build(1L, 10L, 1L, SessionStatus.ACTIVE,
                LocalDateTime.now()));
        sessionRepository.save(build(1L, 10L, 2L, SessionStatus.ENDED,
                LocalDateTime.now()));
        sessionRepository.save(build(1L, 20L, 1L, SessionStatus.ACTIVE,
                LocalDateTime.now()));

        List<CollabSession> results = sessionRepository.findByFileId(10L);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(s -> s.getFileId().equals(10L));
    }

    // ── findByOwnerId ──────────────────────────────────────────────────

    @Test
    @DisplayName("findByOwnerId - returns sessions owned by user")
    void findByOwnerId_existing_returnsSessions() {
        sessionRepository.save(build(1L, 10L, 5L, SessionStatus.ACTIVE,
                LocalDateTime.now()));
        sessionRepository.save(build(1L, 11L, 5L, SessionStatus.ENDED,
                LocalDateTime.now()));
        sessionRepository.save(build(1L, 12L, 9L, SessionStatus.ACTIVE,
                LocalDateTime.now()));

        List<CollabSession> results = sessionRepository.findByOwnerId(5L);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(s -> s.getOwnerId().equals(5L));
    }

    // ── findByStatus ───────────────────────────────────────────────────

    @Test
    @DisplayName("findByStatus(ACTIVE) - returns only active sessions")
    void findByStatus_active_returnsActiveSessions() {
        sessionRepository.save(build(1L, 10L, 1L, SessionStatus.ACTIVE,
                LocalDateTime.now()));
        sessionRepository.save(build(1L, 11L, 1L, SessionStatus.ACTIVE,
                LocalDateTime.now()));
        sessionRepository.save(build(1L, 12L, 1L, SessionStatus.ENDED,
                LocalDateTime.now()));

        List<CollabSession> results =
                sessionRepository.findByStatus(SessionStatus.ACTIVE);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(s -> s.getStatus() == SessionStatus.ACTIVE);
    }

    @Test
    @DisplayName("findByStatus(ENDED) - returns only ended sessions")
    void findByStatus_ended_returnsEndedSessions() {
        sessionRepository.save(build(1L, 10L, 1L, SessionStatus.ACTIVE,
                LocalDateTime.now()));
        sessionRepository.save(build(1L, 11L, 1L, SessionStatus.ENDED,
                LocalDateTime.now()));

        List<CollabSession> results =
                sessionRepository.findByStatus(SessionStatus.ENDED);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getStatus()).isEqualTo(SessionStatus.ENDED);
    }

    // ── findByProjectIdAndFileIdAndStatus ──────────────────────────────

    @Test
    @DisplayName("findByProjectIdAndFileIdAndStatus - returns active session for file")
    void findByProjectIdAndFileIdAndStatus_active_returnsSession() {
        sessionRepository.save(build(1L, 10L, 1L, SessionStatus.ACTIVE,
                LocalDateTime.now()));
        sessionRepository.save(build(1L, 10L, 1L, SessionStatus.ENDED,
                LocalDateTime.now()));

        Optional<CollabSession> result =
                sessionRepository.findByProjectIdAndFileIdAndStatus(
                        1L, 10L, SessionStatus.ACTIVE);

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(SessionStatus.ACTIVE);
    }

    @Test
    @DisplayName("findByProjectIdAndFileIdAndStatus - returns empty when no active session")
    void findByProjectIdAndFileIdAndStatus_noActive_returnsEmpty() {
        sessionRepository.save(build(1L, 10L, 1L, SessionStatus.ENDED,
                LocalDateTime.now()));

        Optional<CollabSession> result =
                sessionRepository.findByProjectIdAndFileIdAndStatus(
                        1L, 10L, SessionStatus.ACTIVE);

        assertThat(result).isEmpty();
    }

    // ── findByProjectIdAndStatus ───────────────────────────────────────

    @Test
    @DisplayName("findByProjectIdAndStatus - returns only active sessions for project")
    void findByProjectIdAndStatus_active_returnsFiltered() {
        sessionRepository.save(build(1L, 10L, 1L, SessionStatus.ACTIVE,
                LocalDateTime.now()));
        sessionRepository.save(build(1L, 11L, 1L, SessionStatus.ACTIVE,
                LocalDateTime.now()));
        sessionRepository.save(build(1L, 12L, 1L, SessionStatus.ENDED,
                LocalDateTime.now()));

        List<CollabSession> results =
                sessionRepository.findByProjectIdAndStatus(
                        1L, SessionStatus.ACTIVE);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(s -> s.getStatus() == SessionStatus.ACTIVE);
    }

    // ── findInactiveSessions ───────────────────────────────────────────

    @Test
    @DisplayName("findInactiveSessions - returns ACTIVE sessions with old lastActivityAt")
    void findInactiveSessions_stale_returnsInactive() {
        // Active but stale (last activity 3 hours ago)
        sessionRepository.save(build(1L, 10L, 1L, SessionStatus.ACTIVE,
                LocalDateTime.now().minusHours(3)));
        // Active and recent
        sessionRepository.save(build(1L, 11L, 1L, SessionStatus.ACTIVE,
                LocalDateTime.now().minusMinutes(10)));
        // Ended and stale — should NOT be returned
        sessionRepository.save(build(1L, 12L, 1L, SessionStatus.ENDED,
                LocalDateTime.now().minusHours(5)));

        LocalDateTime cutoff = LocalDateTime.now().minusHours(2);
        List<CollabSession> results =
                sessionRepository.findInactiveSessions(cutoff);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getStatus()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(results.get(0).getLastActivityAt()).isBefore(cutoff);
    }

    @Test
    @DisplayName("findInactiveSessions - returns empty when no stale sessions")
    void findInactiveSessions_noStale_returnsEmpty() {
        sessionRepository.save(build(1L, 10L, 1L, SessionStatus.ACTIVE,
                LocalDateTime.now().minusMinutes(5)));

        LocalDateTime cutoff = LocalDateTime.now().minusHours(2);
        assertThat(sessionRepository.findInactiveSessions(cutoff)).isEmpty();
    }
}