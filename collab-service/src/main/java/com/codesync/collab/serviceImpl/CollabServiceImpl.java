package com.codesync.collab.serviceImpl;

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
import com.codesync.collab.service.CollabService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollabServiceImpl implements CollabService {

    private final CollabSessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    // Redis key patterns
    private static final String CONTENT_KEY =
            "session:%s:content";
    private static final String ACTIVITY_KEY =
            "session:%s:activity";

    // Cursor colors for participants
    private static final String[] COLORS = {
            "#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4",
            "#FFEAA7", "#DDA0DD", "#98D8C8", "#F7DC6F",
            "#BB8FCE", "#85C1E9"
    };

    // ─────────────────────────────────────────────────────────────────
    // SESSION LIFECYCLE
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public SessionResponse createSession(
            Long ownerId, CreateSessionRequest request) {

        log.info("Creating session for file: {} by owner: {}",
                request.getFileId(), ownerId);

        // Check if active session already exists for this file
        sessionRepository.findByProjectIdAndFileIdAndStatus(
                        request.getProjectId(),
                        request.getFileId(),
                        SessionStatus.ACTIVE)
                .ifPresent(s -> {
                    throw new SessionAccessException(
                            "An active session already exists for "
                                    + "this file. Session ID: "
                                    + s.getSessionId());
                });

        String hashedPassword = null;
        if (Boolean.TRUE.equals(request.getIsPasswordProtected())
                && request.getSessionPassword() != null) {
            hashedPassword = passwordEncoder
                    .encode(request.getSessionPassword());
        }

        CollabSession session = CollabSession.builder()
                .projectId(request.getProjectId())
                .fileId(request.getFileId())
                .ownerId(ownerId)
                .status(SessionStatus.ACTIVE)
                .language(request.getLanguage())
                .maxParticipants(request.getMaxParticipants())
                .isPasswordProtected(
                        Boolean.TRUE.equals(
                                request.getIsPasswordProtected()))
                .sessionPassword(hashedPassword)
                .lastActivityAt(LocalDateTime.now())
                .build();

        CollabSession saved = sessionRepository.save(session);

        // Auto-join owner as HOST
        Participant owner = Participant.builder()
                .sessionId(saved.getSessionId())
                .userId(ownerId)
                .role(ParticipantRole.HOST)
                .color(COLORS[0])
                .cursorLine(0)
                .cursorCol(0)
                .build();
        participantRepository.save(owner);

        // Set initial activity in Redis
        updateLastActivity(saved.getSessionId());

        log.info("Session created: {}", saved.getSessionId());
        return mapToSessionResponse(saved);
    }

    @Override
    public SessionResponse getSessionById(String sessionId) {
        return mapToSessionResponse(findSessionOrThrow(sessionId));
    }

    @Override
    public List<SessionResponse> getSessionsByProject(
            Long projectId) {
        return sessionRepository.findByProjectId(projectId)
                .stream()
                .map(this::mapToSessionResponse)
                .collect(Collectors.toList());
    }

    @Override
    public SessionResponse getActiveSession(
            Long projectId, Long fileId) {
        CollabSession session = sessionRepository
                .findByProjectIdAndFileIdAndStatus(
                        projectId, fileId, SessionStatus.ACTIVE)
                .orElseThrow(() -> new SessionNotFoundException(
                        "No active session found for this file."));
        return mapToSessionResponse(session);
    }

    @Override
    @Transactional
    public void endSession(String sessionId, Long ownerId) {
        CollabSession session = findSessionOrThrow(sessionId);

        if (!session.getOwnerId().equals(ownerId)) {
            throw new SessionAccessException(
                    "Only the session owner can end the session.");
        }

        session.setStatus(SessionStatus.ENDED);
        session.setEndedAt(LocalDateTime.now());
        sessionRepository.save(session);

        // Mark all active participants as left
        List<Participant> active = participantRepository
                .findBySessionIdAndLeftAtIsNull(sessionId);
        active.forEach(p -> p.setLeftAt(LocalDateTime.now()));
        participantRepository.saveAll(active);

        // Clean up Redis
        redisTemplate.delete(
                String.format(CONTENT_KEY, sessionId));
        redisTemplate.delete(
                String.format(ACTIVITY_KEY, sessionId));

        log.info("Session {} ended by owner {}", sessionId, ownerId);
    }

    // ─────────────────────────────────────────────────────────────────
    // PARTICIPANT MANAGEMENT
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ParticipantResponse joinSession(
            String sessionId, Long userId,
            JoinSessionRequest request) {

        CollabSession session = findSessionOrThrow(sessionId);

        // Check session is still active
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new SessionAccessException(
                    "This session has ended.");
        }

        // Check if user already in session
        if (participantRepository.existsBySessionIdAndUserId(
                sessionId, userId)) {

            // Re-joining — clear leftAt
            Participant existing = participantRepository
                    .findBySessionIdAndUserId(sessionId, userId)
                    .get();
            existing.setLeftAt(null);
            Participant updated =
                    participantRepository.save(existing);
            log.info("User {} re-joined session {}", userId,
                    sessionId);
            return mapToParticipantResponse(updated);
        }

        // Check max participants
        int activeCount = participantRepository
                .countActiveParticipants(sessionId);
        if (activeCount >= session.getMaxParticipants()) {
            throw new SessionAccessException(
                    "Session is full. Max participants: "
                            + session.getMaxParticipants());
        }

        // Check password if protected
        if (Boolean.TRUE.equals(session.getIsPasswordProtected())) {
            if (request.getSessionPassword() == null
                    || !passwordEncoder.matches(
                    request.getSessionPassword(),
                    session.getSessionPassword())) {
                throw new SessionAccessException(
                        "Incorrect session password.");
            }
        }

        // Assign unique color
        String color = COLORS[activeCount % COLORS.length];

        Participant participant = Participant.builder()
                .sessionId(sessionId)
                .userId(userId)
                .role(ParticipantRole.EDITOR)
                .color(color)
                .cursorLine(0)
                .cursorCol(0)
                .build();

        Participant saved = participantRepository.save(participant);
        updateLastActivity(sessionId);

        log.info("User {} joined session {}", userId, sessionId);
        return mapToParticipantResponse(saved);
    }

    @Override
    @Transactional
    public void leaveSession(String sessionId, Long userId) {
        Participant participant = participantRepository
                .findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new SessionNotFoundException(
                        "Participant not found in session."));

        participant.setLeftAt(LocalDateTime.now());
        participantRepository.save(participant);
        updateLastActivity(sessionId);

        log.info("User {} left session {}", userId, sessionId);
    }

    @Override
    @Transactional
    public void kickParticipant(
            String sessionId, Long ownerId, Long userId) {

        CollabSession session = findSessionOrThrow(sessionId);

        if (!session.getOwnerId().equals(ownerId)) {
            throw new SessionAccessException(
                    "Only the session owner can kick participants.");
        }

        if (userId.equals(ownerId)) {
            throw new SessionAccessException(
                    "Cannot kick the session owner.");
        }

        Participant participant = participantRepository
                .findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new SessionNotFoundException(
                        "Participant not found in session."));

        participant.setLeftAt(LocalDateTime.now());
        participantRepository.save(participant);

        log.info("User {} kicked from session {} by owner {}",
                userId, sessionId, ownerId);
    }

    @Override
    public List<ParticipantResponse> getParticipants(
            String sessionId) {
        findSessionOrThrow(sessionId);
        return participantRepository
                .findBySessionIdAndLeftAtIsNull(sessionId)
                .stream()
                .map(this::mapToParticipantResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ParticipantResponse updateCursor(
            String sessionId, Long userId,
            Integer line, Integer col) {

        Participant participant = participantRepository
                .findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new SessionNotFoundException(
                        "Participant not found in session."));

        participant.setCursorLine(line);
        participant.setCursorCol(col);
        Participant updated = participantRepository.save(participant);

        updateLastActivity(sessionId);
        return mapToParticipantResponse(updated);
    }

    // ─────────────────────────────────────────────────────────────────
    // REDIS OPERATIONS
    // ─────────────────────────────────────────────────────────────────

    @Override
    public void saveContentToRedis(
            String sessionId, String content) {
        String key = String.format(CONTENT_KEY, sessionId);
        // Store with 2 hour TTL
        redisTemplate.opsForValue().set(key, content, 2,
                TimeUnit.HOURS);
        updateLastActivity(sessionId);
    }

    @Override
    public String getContentFromRedis(String sessionId) {
        String key = String.format(CONTENT_KEY, sessionId);
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? value.toString() : null;
    }

    @Override
    public void updateLastActivity(String sessionId) {
        // Update in Redis
        String key = String.format(ACTIVITY_KEY, sessionId);
        redisTemplate.opsForValue().set(
                key,
                LocalDateTime.now().toString(),
                2, TimeUnit.HOURS);

        // Update in DB
        sessionRepository.findById(sessionId).ifPresent(s -> {
            s.setLastActivityAt(LocalDateTime.now());
            sessionRepository.save(s);
        });
    }

    // ─────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────

    private CollabSession findSessionOrThrow(String sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(
                        "Session not found: " + sessionId));
    }

    private SessionResponse mapToSessionResponse(
            CollabSession s) {
        int activeCount = participantRepository
                .countActiveParticipants(s.getSessionId());
        return SessionResponse.builder()
                .sessionId(s.getSessionId())
                .projectId(s.getProjectId())
                .fileId(s.getFileId())
                .ownerId(s.getOwnerId())
                .status(s.getStatus())
                .language(s.getLanguage())
                .createdAt(s.getCreatedAt())
                .endedAt(s.getEndedAt())
                .maxParticipants(s.getMaxParticipants())
                .isPasswordProtected(s.getIsPasswordProtected())
                .activeParticipantCount(activeCount)
                .webSocketTopic("/topic/session/"
                        + s.getSessionId())
                .build();
    }

    private ParticipantResponse mapToParticipantResponse(
            Participant p) {
        return ParticipantResponse.builder()
                .participantId(p.getParticipantId())
                .sessionId(p.getSessionId())
                .userId(p.getUserId())
                .role(p.getRole())
                .joinedAt(p.getJoinedAt())
                .leftAt(p.getLeftAt())
                .cursorLine(p.getCursorLine())
                .cursorCol(p.getCursorCol())
                .color(p.getColor())
                .isActive(p.getLeftAt() == null)
                .build();
    }
}