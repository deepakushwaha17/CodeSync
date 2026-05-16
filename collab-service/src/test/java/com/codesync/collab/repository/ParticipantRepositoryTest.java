package com.codesync.collab.repository;

import com.codesync.collab.entity.CollabSession;
import com.codesync.collab.entity.Participant;
import com.codesync.collab.enums.ParticipantRole;
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
class ParticipantRepositoryTest {

    @Autowired private ParticipantRepository participantRepository;
    @Autowired private CollabSessionRepository sessionRepository;

    private String sessionId;

    @BeforeEach
    void setUp() {
        participantRepository.deleteAll();
        sessionRepository.deleteAll();

        CollabSession session = sessionRepository.save(
                CollabSession.builder()
                        .projectId(1L).fileId(10L).ownerId(1L)
                        .status(SessionStatus.ACTIVE).maxParticipants(10)
                        .isPasswordProtected(false)
                        .lastActivityAt(LocalDateTime.now())
                        .build());
        sessionId = session.getSessionId();
    }

    private Participant build(Long userId, ParticipantRole role,
                              LocalDateTime leftAt) {
        return Participant.builder()
                .sessionId(sessionId).userId(userId).role(role)
                .color("#FF6B6B").cursorLine(0).cursorCol(0)
                .leftAt(leftAt)
                .build();
    }

    // ── findBySessionId ────────────────────────────────────────────────

    @Test
    @DisplayName("findBySessionId - returns all participants (active and left)")
    void findBySessionId_returnsAll() {
        participantRepository.save(build(1L, ParticipantRole.HOST, null));
        participantRepository.save(build(2L, ParticipantRole.EDITOR,
                LocalDateTime.now()));

        List<Participant> results =
                participantRepository.findBySessionId(sessionId);

        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("findBySessionId - returns empty for unknown session")
    void findBySessionId_unknown_returnsEmpty() {
        assertThat(participantRepository.findBySessionId("ghost-session")).isEmpty();
    }

    // ── findBySessionIdAndLeftAtIsNull ────────────────────────────────

    @Test
    @DisplayName("findBySessionIdAndLeftAtIsNull - returns only active participants")
    void findBySessionIdAndLeftAtIsNull_returnsActive() {
        participantRepository.save(build(1L, ParticipantRole.HOST, null));      // active
        participantRepository.save(build(2L, ParticipantRole.EDITOR,
                LocalDateTime.now())); // left

        List<Participant> active =
                participantRepository.findBySessionIdAndLeftAtIsNull(sessionId);

        assertThat(active).hasSize(1);
        assertThat(active.get(0).getUserId()).isEqualTo(1L);
        assertThat(active.get(0).getLeftAt()).isNull();
    }

    @Test
    @DisplayName("findBySessionIdAndLeftAtIsNull - returns empty when all participants left")
    void findBySessionIdAndLeftAtIsNull_allLeft_returnsEmpty() {
        participantRepository.save(build(1L, ParticipantRole.HOST,
                LocalDateTime.now()));
        participantRepository.save(build(2L, ParticipantRole.EDITOR,
                LocalDateTime.now()));

        assertThat(participantRepository
                .findBySessionIdAndLeftAtIsNull(sessionId)).isEmpty();
    }

    // ── findBySessionIdAndUserId ───────────────────────────────────────

    @Test
    @DisplayName("findBySessionIdAndUserId - returns participant when found")
    void findBySessionIdAndUserId_existing_returnsParticipant() {
        participantRepository.save(build(5L, ParticipantRole.EDITOR, null));

        Optional<Participant> result =
                participantRepository.findBySessionIdAndUserId(sessionId, 5L);

        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("findBySessionIdAndUserId - returns empty when not in session")
    void findBySessionIdAndUserId_notInSession_returnsEmpty() {
        Optional<Participant> result =
                participantRepository.findBySessionIdAndUserId(sessionId, 99L);

        assertThat(result).isEmpty();
    }

    // ── existsBySessionIdAndUserId ────────────────────────────────────

    @Test
    @DisplayName("existsBySessionIdAndUserId - returns true when participant exists")
    void exists_existingParticipant_returnsTrue() {
        participantRepository.save(build(5L, ParticipantRole.EDITOR, null));

        assertThat(participantRepository
                .existsBySessionIdAndUserId(sessionId, 5L)).isTrue();
    }

    @Test
    @DisplayName("existsBySessionIdAndUserId - returns false when not a participant")
    void exists_notParticipant_returnsFalse() {
        assertThat(participantRepository
                .existsBySessionIdAndUserId(sessionId, 99L)).isFalse();
    }

    // ── countActiveParticipants ───────────────────────────────────────

    @Test
    @DisplayName("countActiveParticipants - returns correct count of active participants")
    void countActiveParticipants_returnsCorrectCount() {
        participantRepository.save(build(1L, ParticipantRole.HOST, null));
        participantRepository.save(build(2L, ParticipantRole.EDITOR, null));
        participantRepository.save(build(3L, ParticipantRole.EDITOR,
                LocalDateTime.now())); // left

        int count = participantRepository.countActiveParticipants(sessionId);

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("countActiveParticipants - returns 0 when no active participants")
    void countActiveParticipants_allLeft_returnsZero() {
        participantRepository.save(build(1L, ParticipantRole.HOST,
                LocalDateTime.now()));

        assertThat(participantRepository
                .countActiveParticipants(sessionId)).isZero();
    }

    @Test
    @DisplayName("countActiveParticipants - returns 0 for unknown session")
    void countActiveParticipants_unknownSession_returnsZero() {
        assertThat(participantRepository
                .countActiveParticipants("ghost-session")).isZero();
    }

    // ── findByUserId ──────────────────────────────────────────────────

    @Test
    @DisplayName("findByUserId - returns all sessions a user participated in")
    void findByUserId_existing_returnsParticipations() {
        participantRepository.save(build(10L, ParticipantRole.EDITOR, null));
        participantRepository.save(build(20L, ParticipantRole.EDITOR, null));

        List<Participant> results = participantRepository.findByUserId(10L);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getUserId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("findByUserId - returns empty for user not in any session")
    void findByUserId_notInAny_returnsEmpty() {
        assertThat(participantRepository.findByUserId(99L)).isEmpty();
    }
}