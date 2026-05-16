package com.codesync.collab.serviceImpl;

import com.codesync.collab.client.NotificationClient;
import com.codesync.collab.dto.request.CreateSessionRequest;
import com.codesync.collab.dto.request.JoinSessionRequest;
import com.codesync.collab.dto.response.ParticipantResponse;
import com.codesync.collab.dto.response.SessionResponse;
import com.codesync.collab.entity.CollabSession;
import com.codesync.collab.entity.Participant;
import com.codesync.collab.enums.ParticipantRole;
import com.codesync.collab.enums.SessionStatus;
import com.codesync.collab.exception.SessionAccessException;
import com.codesync.collab.exception.SessionNotFoundException;
import com.codesync.collab.repository.CollabSessionRepository;
import com.codesync.collab.repository.ParticipantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CollabServiceImplTest {

    @Mock private CollabSessionRepository sessionRepository;
    @Mock private ParticipantRepository participantRepository;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;
    @Mock private NotificationClient notificationClient;

    @InjectMocks private CollabServiceImpl collabService;

    private CollabSession buildSession(String id, Long projectId, Long fileId,
                                       Long ownerId, SessionStatus status,
                                       boolean passwordProtected,
                                       String hashedPassword) {
        return CollabSession.builder()
                .sessionId(id)
                .projectId(projectId)
                .fileId(fileId)
                .ownerId(ownerId)
                .status(status)
                .language("java")
                .maxParticipants(10)
                .isPasswordProtected(passwordProtected)
                .sessionPassword(hashedPassword)
                .lastActivityAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Participant buildParticipant(Long id, String sessionId, Long userId,
                                         ParticipantRole role, LocalDateTime leftAt) {
        return Participant.builder()
                .participantId(id)
                .sessionId(sessionId)
                .userId(userId)
                .role(role)
                .color("#FF6B6B")
                .cursorLine(0)
                .cursorCol(0)
                .joinedAt(LocalDateTime.now())
                .leftAt(leftAt)
                .build();
    }

    private void stubRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        doNothing().when(valueOps)
                .set(anyString(), any(), anyLong(), any(TimeUnit.class));
    }

    private void stubActivityUpdate(String sessionId) {
        stubRedis();

        CollabSession session = buildSession(sessionId, 1L, 10L, 1L,
                SessionStatus.ACTIVE, false, null);

        when(sessionRepository.findById(anyString()))
                .thenReturn(Optional.of(session));
    }

    @Test
    @DisplayName("createSession - saves session, auto-joins owner as HOST")
    void createSession_valid_savesSessionAndHostParticipant() {
        CreateSessionRequest req = new CreateSessionRequest();
        req.setProjectId(1L);
        req.setFileId(10L);
        req.setLanguage("java");
        req.setMaxParticipants(10);
        req.setIsPasswordProtected(false);

        when(sessionRepository.findByProjectIdAndFileIdAndStatus(
                1L, 10L, SessionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        doAnswer(inv -> {
            CollabSession session = inv.getArgument(0);
            session.setSessionId("sess-1");
            return session;
        }).when(sessionRepository).save(any(CollabSession.class));

        Participant ownerParticipant =
                buildParticipant(1L, "sess-1", 5L, ParticipantRole.HOST, null);

        when(participantRepository.save(any(Participant.class)))
                .thenReturn(ownerParticipant);

        when(participantRepository.countActiveParticipants(anyString()))
                .thenReturn(1);

        stubActivityUpdate("sess-1");

        SessionResponse response = collabService.createSession(5L, req);

        assertThat(response.getSessionId()).isEqualTo("sess-1");
        assertThat(response.getOwnerId()).isEqualTo(5L);
        assertThat(response.getStatus()).isEqualTo(SessionStatus.ACTIVE);

        verify(participantRepository).save(argThat(p ->
                p.getUserId().equals(5L)
                        && p.getRole() == ParticipantRole.HOST
        ));
    }

    @Test
    @DisplayName("createSession - throws SessionAccessException when active session exists for file")
    void createSession_activeSessionExists_throwsException() {
        CreateSessionRequest req = new CreateSessionRequest();
        req.setProjectId(1L);
        req.setFileId(10L);

        CollabSession existing = buildSession("existing-id", 1L, 10L, 1L,
                SessionStatus.ACTIVE, false, null);

        when(sessionRepository.findByProjectIdAndFileIdAndStatus(
                1L, 10L, SessionStatus.ACTIVE))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> collabService.createSession(5L, req))
                .isInstanceOf(SessionAccessException.class)
                .hasMessageContaining("existing-id");

        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("createSession - password protected session is created successfully")
    void createSession_passwordProtected_hashesPassword() {
        CreateSessionRequest req = new CreateSessionRequest();
        req.setProjectId(1L);
        req.setFileId(10L);
        req.setIsPasswordProtected(true);
        req.setSessionPassword("secret123");

        when(sessionRepository.findByProjectIdAndFileIdAndStatus(
                1L, 10L, SessionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        final CollabSession[] captured = new CollabSession[1];

        doAnswer(inv -> {
            CollabSession session = inv.getArgument(0);
            session.setSessionId("sess-pw");
            captured[0] = session;
            return session;
        }).when(sessionRepository).save(any(CollabSession.class));

        Participant p = buildParticipant(1L, "sess-pw", 1L,
                ParticipantRole.HOST, null);

        when(participantRepository.save(any(Participant.class)))
                .thenReturn(p);

        when(participantRepository.countActiveParticipants(anyString()))
                .thenReturn(1);

        stubActivityUpdate("sess-pw");

        SessionResponse response = collabService.createSession(1L, req);

        assertThat(response.getSessionId()).isEqualTo("sess-pw");
        assertThat(captured[0]).isNotNull();
    }

    @Test
    @DisplayName("createSession - does not hash password when not password-protected")
    void createSession_notPasswordProtected_noPasswordSet() {
        CreateSessionRequest req = new CreateSessionRequest();
        req.setProjectId(1L);
        req.setFileId(10L);
        req.setIsPasswordProtected(false);
        req.setSessionPassword("ignored");

        when(sessionRepository.findByProjectIdAndFileIdAndStatus(
                1L, 10L, SessionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        final CollabSession[] captured = new CollabSession[1];

        doAnswer(inv -> {
            CollabSession session = inv.getArgument(0);
            session.setSessionId("sess-1");
            captured[0] = session;
            return session;
        }).when(sessionRepository).save(any(CollabSession.class));

        Participant p = buildParticipant(1L, "sess-1", 1L,
                ParticipantRole.HOST, null);

        when(participantRepository.save(any(Participant.class)))
                .thenReturn(p);

        when(participantRepository.countActiveParticipants(anyString()))
                .thenReturn(1);

        stubActivityUpdate("sess-1");

        collabService.createSession(1L, req);

        assertThat(captured[0]).isNotNull();
        assertThat(captured[0].getSessionPassword()).isNull();
    }

    @Test
    @DisplayName("getSessionById - returns SessionResponse for existing session")
    void getSessionById_existing_returnsResponse() {
        CollabSession session = buildSession("sess-1", 1L, 10L, 1L,
                SessionStatus.ACTIVE, false, null);

        when(sessionRepository.findById("sess-1"))
                .thenReturn(Optional.of(session));

        when(participantRepository.countActiveParticipants("sess-1"))
                .thenReturn(3);

        SessionResponse response = collabService.getSessionById("sess-1");

        assertThat(response.getSessionId()).isEqualTo("sess-1");
        assertThat(response.getActiveParticipantCount()).isEqualTo(3);
        assertThat(response.getWebSocketTopic()).isEqualTo("/topic/session/sess-1");
    }

    @Test
    @DisplayName("getSessionById - throws SessionNotFoundException for unknown session")
    void getSessionById_notFound_throwsException() {
        when(sessionRepository.findById("ghost"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> collabService.getSessionById("ghost"))
                .isInstanceOf(SessionNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    @DisplayName("getSessionsByProject - returns all sessions for project")
    void getSessionsByProject_existing_returnsList() {
        CollabSession s1 = buildSession("s1", 1L, 10L, 1L,
                SessionStatus.ACTIVE, false, null);

        CollabSession s2 = buildSession("s2", 1L, 11L, 1L,
                SessionStatus.ENDED, false, null);

        when(sessionRepository.findByProjectId(1L))
                .thenReturn(List.of(s1, s2));

        when(participantRepository.countActiveParticipants(anyString()))
                .thenReturn(0);

        List<SessionResponse> results = collabService.getSessionsByProject(1L);

        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("getActiveSession - returns active session for file")
    void getActiveSession_existing_returnsSession() {
        CollabSession session = buildSession("sess-1", 1L, 10L, 1L,
                SessionStatus.ACTIVE, false, null);

        when(sessionRepository.findByProjectIdAndFileIdAndStatus(
                1L, 10L, SessionStatus.ACTIVE))
                .thenReturn(Optional.of(session));

        when(participantRepository.countActiveParticipants("sess-1"))
                .thenReturn(1);

        SessionResponse response = collabService.getActiveSession(1L, 10L);

        assertThat(response.getStatus()).isEqualTo(SessionStatus.ACTIVE);
    }

    @Test
    @DisplayName("getActiveSession - throws SessionNotFoundException when no active session")
    void getActiveSession_noActiveSession_throwsException() {
        when(sessionRepository.findByProjectIdAndFileIdAndStatus(
                1L, 10L, SessionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> collabService.getActiveSession(1L, 10L))
                .isInstanceOf(SessionNotFoundException.class)
                .hasMessageContaining("No active session");
    }

    @Test
    @DisplayName("endSession - owner ends session, marks all participants left")
    void endSession_owner_endsSessionAndClearsParticipants() {
        CollabSession session = buildSession("sess-1", 1L, 10L, 1L,
                SessionStatus.ACTIVE, false, null);

        when(sessionRepository.findById("sess-1"))
                .thenReturn(Optional.of(session));

        when(sessionRepository.save(any(CollabSession.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Participant p1 = buildParticipant(1L, "sess-1", 1L,
                ParticipantRole.HOST, null);

        Participant p2 = buildParticipant(2L, "sess-1", 2L,
                ParticipantRole.EDITOR, null);

        when(participantRepository.findBySessionIdAndLeftAtIsNull("sess-1"))
                .thenReturn(List.of(p1, p2));

        when(participantRepository.saveAll(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        when(redisTemplate.delete(anyString()))
                .thenReturn(true);

        collabService.endSession("sess-1", 1L);

        verify(sessionRepository).save(argThat(s ->
                s.getStatus() == SessionStatus.ENDED
                        && s.getEndedAt() != null
        ));

        assertThat(p1.getLeftAt()).isNotNull();
        assertThat(p2.getLeftAt()).isNotNull();

        verify(redisTemplate, times(2)).delete(anyString());
    }

    @Test
    @DisplayName("endSession - throws SessionAccessException for non-owner")
    void endSession_nonOwner_throwsException() {
        CollabSession session = buildSession("sess-1", 1L, 10L, 1L,
                SessionStatus.ACTIVE, false, null);

        when(sessionRepository.findById("sess-1"))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> collabService.endSession("sess-1", 99L))
                .isInstanceOf(SessionAccessException.class)
                .hasMessageContaining("Only the session owner");
    }

    @Test
    @DisplayName("endSession - throws SessionNotFoundException for unknown session")
    void endSession_notFound_throwsException() {
        when(sessionRepository.findById("ghost"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> collabService.endSession("ghost", 1L))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    @DisplayName("joinSession - new participant joins successfully")
    void joinSession_newUser_joinsSuccessfully() {
        CollabSession session = buildSession("sess-1", 1L, 10L, 1L,
                SessionStatus.ACTIVE, false, null);

        when(sessionRepository.findById("sess-1"))
                .thenReturn(Optional.of(session));

        when(participantRepository.existsBySessionIdAndUserId("sess-1", 5L))
                .thenReturn(false);

        when(participantRepository.countActiveParticipants("sess-1"))
                .thenReturn(2);

        Participant saved = buildParticipant(3L, "sess-1", 5L,
                ParticipantRole.EDITOR, null);

        when(participantRepository.save(any(Participant.class)))
                .thenReturn(saved);

        doNothing().when(notificationClient).sendNotification(any());

        stubActivityUpdate("sess-1");

        ParticipantResponse response =
                collabService.joinSession("sess-1", 5L, new JoinSessionRequest());

        assertThat(response.getUserId()).isEqualTo(5L);
        assertThat(response.getRole()).isEqualTo(ParticipantRole.EDITOR);
        assertThat(response.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("joinSession - re-joining user clears leftAt")
    void joinSession_reJoin_clearsLeftAt() {
        CollabSession session = buildSession("sess-1", 1L, 10L, 1L,
                SessionStatus.ACTIVE, false, null);

        when(sessionRepository.findById("sess-1"))
                .thenReturn(Optional.of(session));

        when(participantRepository.existsBySessionIdAndUserId("sess-1", 5L))
                .thenReturn(true);

        Participant existing = buildParticipant(2L, "sess-1", 5L,
                ParticipantRole.EDITOR, LocalDateTime.now().minusMinutes(10));

        when(participantRepository.findBySessionIdAndUserId("sess-1", 5L))
                .thenReturn(Optional.of(existing));

        when(participantRepository.save(any(Participant.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ParticipantResponse response =
                collabService.joinSession("sess-1", 5L, new JoinSessionRequest());

        assertThat(existing.getLeftAt()).isNull();
        assertThat(response.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("joinSession - throws SessionAccessException when session has ended")
    void joinSession_endedSession_throwsException() {
        CollabSession session = buildSession("sess-1", 1L, 10L, 1L,
                SessionStatus.ENDED, false, null);

        when(sessionRepository.findById("sess-1"))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() ->
                collabService.joinSession("sess-1", 5L, new JoinSessionRequest()))
                .isInstanceOf(SessionAccessException.class)
                .hasMessageContaining("session has ended");
    }

    @Test
    @DisplayName("joinSession - throws SessionAccessException when session is full")
    void joinSession_sessionFull_throwsException() {
        CollabSession session = buildSession("sess-1", 1L, 10L, 1L,
                SessionStatus.ACTIVE, false, null);

        session.setMaxParticipants(3);

        when(sessionRepository.findById("sess-1"))
                .thenReturn(Optional.of(session));

        when(participantRepository.existsBySessionIdAndUserId("sess-1", 5L))
                .thenReturn(false);

        when(participantRepository.countActiveParticipants("sess-1"))
                .thenReturn(3);

        assertThatThrownBy(() ->
                collabService.joinSession("sess-1", 5L, new JoinSessionRequest()))
                .isInstanceOf(SessionAccessException.class)
                .hasMessageContaining("Session is full");
    }

    @Test
    @DisplayName("joinSession - throws SessionAccessException for wrong password")
    void joinSession_wrongPassword_throwsException() {
        String hashedPassword =
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                        .encode("correctPass");

        CollabSession session = buildSession("sess-1", 1L, 10L, 1L,
                SessionStatus.ACTIVE, true, hashedPassword);

        when(sessionRepository.findById("sess-1"))
                .thenReturn(Optional.of(session));

        when(participantRepository.existsBySessionIdAndUserId("sess-1", 5L))
                .thenReturn(false);

        when(participantRepository.countActiveParticipants("sess-1"))
                .thenReturn(0);

        JoinSessionRequest req = new JoinSessionRequest();
        req.setSessionPassword("wrongPass");

        assertThatThrownBy(() -> collabService.joinSession("sess-1", 5L, req))
                .isInstanceOf(SessionAccessException.class)
                .hasMessageContaining("Incorrect session password");
    }

    @Test
    @DisplayName("joinSession - allows join with correct password")
    void joinSession_correctPassword_joinsSuccessfully() {
        String hashedPassword =
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                        .encode("secret123");

        CollabSession session = buildSession("sess-1", 1L, 10L, 1L,
                SessionStatus.ACTIVE, true, hashedPassword);

        when(sessionRepository.findById("sess-1"))
                .thenReturn(Optional.of(session));

        when(participantRepository.existsBySessionIdAndUserId("sess-1", 5L))
                .thenReturn(false);

        when(participantRepository.countActiveParticipants("sess-1"))
                .thenReturn(0);

        Participant saved = buildParticipant(1L, "sess-1", 5L,
                ParticipantRole.EDITOR, null);

        when(participantRepository.save(any(Participant.class)))
                .thenReturn(saved);

        doNothing().when(notificationClient).sendNotification(any());

        stubActivityUpdate("sess-1");

        JoinSessionRequest req = new JoinSessionRequest();
        req.setSessionPassword("secret123");

        assertThatCode(() -> collabService.joinSession("sess-1", 5L, req))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("joinSession - does not notify owner when owner joins own session")
    void joinSession_ownerJoins_noNotificationSent() {
        CollabSession session = buildSession("sess-1", 1L, 10L, 1L,
                SessionStatus.ACTIVE, false, null);

        when(sessionRepository.findById("sess-1"))
                .thenReturn(Optional.of(session));

        when(participantRepository.existsBySessionIdAndUserId("sess-1", 1L))
                .thenReturn(false);

        when(participantRepository.countActiveParticipants("sess-1"))
                .thenReturn(0);

        Participant saved = buildParticipant(1L, "sess-1", 1L,
                ParticipantRole.EDITOR, null);

        when(participantRepository.save(any(Participant.class)))
                .thenReturn(saved);

        stubActivityUpdate("sess-1");

        collabService.joinSession("sess-1", 1L, new JoinSessionRequest());

        verify(notificationClient, never()).sendNotification(any());
    }

    @Test
    @DisplayName("joinSession - gracefully handles notification failure")
    void joinSession_notificationFails_doesNotPropagate() {
        CollabSession session = buildSession("sess-1", 1L, 10L, 1L,
                SessionStatus.ACTIVE, false, null);

        when(sessionRepository.findById("sess-1"))
                .thenReturn(Optional.of(session));

        when(participantRepository.existsBySessionIdAndUserId("sess-1", 5L))
                .thenReturn(false);

        when(participantRepository.countActiveParticipants("sess-1"))
                .thenReturn(0);

        Participant saved = buildParticipant(2L, "sess-1", 5L,
                ParticipantRole.EDITOR, null);

        when(participantRepository.save(any(Participant.class)))
                .thenReturn(saved);

        doThrow(new RuntimeException("notification down"))
                .when(notificationClient).sendNotification(any());

        stubActivityUpdate("sess-1");

        assertThatCode(() ->
                collabService.joinSession("sess-1", 5L, new JoinSessionRequest()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("leaveSession - sets leftAt on participant")
    void leaveSession_activeParticipant_setsLeftAt() {
        Participant p = buildParticipant(1L, "sess-1", 5L,
                ParticipantRole.EDITOR, null);

        when(participantRepository.findBySessionIdAndUserId("sess-1", 5L))
                .thenReturn(Optional.of(p));

        when(participantRepository.save(any(Participant.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        stubActivityUpdate("sess-1");

        collabService.leaveSession("sess-1", 5L);

        assertThat(p.getLeftAt()).isNotNull();

        verify(participantRepository).save(argThat(part ->
                part.getLeftAt() != null
        ));
    }

    @Test
    @DisplayName("leaveSession - throws SessionNotFoundException when not in session")
    void leaveSession_notInSession_throwsException() {
        when(participantRepository.findBySessionIdAndUserId("sess-1", 99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> collabService.leaveSession("sess-1", 99L))
                .isInstanceOf(SessionNotFoundException.class)
                .hasMessageContaining("Participant not found");
    }

    @Test
    @DisplayName("kickParticipant - owner kicks a participant and sends notification")
    void kickParticipant_owner_kicksParticipant() {
        CollabSession session = buildSession("sess-1", 1L, 10L, 1L,
                SessionStatus.ACTIVE, false, null);

        when(sessionRepository.findById("sess-1"))
                .thenReturn(Optional.of(session));

        Participant target = buildParticipant(2L, "sess-1", 5L,
                ParticipantRole.EDITOR, null);

        when(participantRepository.findBySessionIdAndUserId("sess-1", 5L))
                .thenReturn(Optional.of(target));

        when(participantRepository.save(any(Participant.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        doNothing().when(notificationClient).sendNotification(any());

        collabService.kickParticipant("sess-1", 1L, 5L);

        assertThat(target.getLeftAt()).isNotNull();

        verify(notificationClient).sendNotification(
                argThat(n -> n.getRecipientId().equals(5L))
        );
    }

    @Test
    @DisplayName("kickParticipant - throws SessionAccessException for non-owner")
    void kickParticipant_nonOwner_throwsException() {
        CollabSession session = buildSession("sess-1", 1L, 10L, 1L,
                SessionStatus.ACTIVE, false, null);

        when(sessionRepository.findById("sess-1"))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() ->
                collabService.kickParticipant("sess-1", 99L, 5L))
                .isInstanceOf(SessionAccessException.class)
                .hasMessageContaining("Only the session owner");
    }

    @Test
    @DisplayName("kickParticipant - throws SessionAccessException when trying to kick owner")
    void kickParticipant_kickingOwner_throwsException() {
        CollabSession session = buildSession("sess-1", 1L, 10L, 1L,
                SessionStatus.ACTIVE, false, null);

        when(sessionRepository.findById("sess-1"))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() ->
                collabService.kickParticipant("sess-1", 1L, 1L))
                .isInstanceOf(SessionAccessException.class)
                .hasMessageContaining("Cannot kick the session owner");
    }

    @Test
    @DisplayName("kickParticipant - throws SessionNotFoundException when target not in session")
    void kickParticipant_targetNotFound_throwsException() {
        CollabSession session = buildSession("sess-1", 1L, 10L, 1L,
                SessionStatus.ACTIVE, false, null);

        when(sessionRepository.findById("sess-1"))
                .thenReturn(Optional.of(session));

        when(participantRepository.findBySessionIdAndUserId("sess-1", 99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                collabService.kickParticipant("sess-1", 1L, 99L))
                .isInstanceOf(SessionNotFoundException.class)
                .hasMessageContaining("Participant not found");
    }

    @Test
    @DisplayName("getParticipants - returns active participants in session")
    void getParticipants_existing_returnsActiveList() {
        CollabSession session = buildSession("sess-1", 1L, 10L, 1L,
                SessionStatus.ACTIVE, false, null);

        when(sessionRepository.findById("sess-1"))
                .thenReturn(Optional.of(session));

        Participant p1 = buildParticipant(1L, "sess-1", 1L,
                ParticipantRole.HOST, null);

        Participant p2 = buildParticipant(2L, "sess-1", 2L,
                ParticipantRole.EDITOR, null);

        when(participantRepository.findBySessionIdAndLeftAtIsNull("sess-1"))
                .thenReturn(List.of(p1, p2));

        List<ParticipantResponse> participants =
                collabService.getParticipants("sess-1");

        assertThat(participants).hasSize(2);
        assertThat(participants).allMatch(ParticipantResponse::getIsActive);
    }

    @Test
    @DisplayName("getParticipants - throws SessionNotFoundException for unknown session")
    void getParticipants_notFound_throwsException() {
        when(sessionRepository.findById("ghost"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> collabService.getParticipants("ghost"))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    @DisplayName("updateCursor - updates cursor line and col for participant")
    void updateCursor_existing_updatesCursorPosition() {
        Participant p = buildParticipant(1L, "sess-1", 5L,
                ParticipantRole.EDITOR, null);

        when(participantRepository.findBySessionIdAndUserId("sess-1", 5L))
                .thenReturn(Optional.of(p));

        when(participantRepository.save(any(Participant.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        stubActivityUpdate("sess-1");

        ParticipantResponse response =
                collabService.updateCursor("sess-1", 5L, 42, 15);

        assertThat(p.getCursorLine()).isEqualTo(42);
        assertThat(p.getCursorCol()).isEqualTo(15);
        assertThat(response.getCursorLine()).isEqualTo(42);
        assertThat(response.getCursorCol()).isEqualTo(15);
    }

    @Test
    @DisplayName("updateCursor - throws SessionNotFoundException when participant not found")
    void updateCursor_notFound_throwsException() {
        when(participantRepository.findBySessionIdAndUserId("sess-1", 99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                collabService.updateCursor("sess-1", 99L, 1, 1))
                .isInstanceOf(SessionNotFoundException.class)
                .hasMessageContaining("Participant not found");
    }

    @Test
    @DisplayName("saveContentToRedis - stores content with 2-hour TTL")
    void saveContentToRedis_storesWithTTL() {
        stubRedis();

        CollabSession session = buildSession("sess-1", 1L, 10L, 1L,
                SessionStatus.ACTIVE, false, null);

        when(sessionRepository.findById("sess-1"))
                .thenReturn(Optional.of(session));

        when(sessionRepository.save(any(CollabSession.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        collabService.saveContentToRedis("sess-1", "class App {}");

        verify(valueOps).set(
                eq("session:sess-1:content"),
                eq("class App {}"),
                eq(2L),
                eq(TimeUnit.HOURS)
        );
    }

    @Test
    @DisplayName("getContentFromRedis - returns stored content string")
    void getContentFromRedis_existing_returnsContent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("session:sess-1:content"))
                .thenReturn("class App {}");

        String content = collabService.getContentFromRedis("sess-1");

        assertThat(content).isEqualTo("class App {}");
    }

    @Test
    @DisplayName("getContentFromRedis - returns null when no content in Redis")
    void getContentFromRedis_missing_returnsNull() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("session:sess-1:content"))
                .thenReturn(null);

        String content = collabService.getContentFromRedis("sess-1");

        assertThat(content).isNull();
    }
}