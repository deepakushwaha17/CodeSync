package com.codesync.collab.scheduler;

import com.codesync.collab.entity.CollabSession;
import com.codesync.collab.entity.Participant;
import com.codesync.collab.enums.SessionStatus;
import com.codesync.collab.repository.CollabSessionRepository;
import com.codesync.collab.repository.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled job that automatically ends collaboration sessions
 * that have had no participant activity for 30 minutes.
 *
 * Runs every 5 minutes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InactiveSessionScheduler {

    private final CollabSessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${collab.session.inactive-timeout-minutes:30}")
    private int inactiveTimeoutMinutes;

    @Scheduled(fixedDelay = 300000) // every 5 minutes
    @Transactional
    public void endInactiveSessions() {
        LocalDateTime cutoffTime = LocalDateTime.now()
                .minusMinutes(inactiveTimeoutMinutes);

        List<CollabSession> inactiveSessions =
                sessionRepository.findInactiveSessions(cutoffTime);

        if (inactiveSessions.isEmpty()) {
            log.debug("No inactive sessions found.");
            return;
        }

        log.info("Found {} inactive sessions to end.",
                inactiveSessions.size());

        for (CollabSession session : inactiveSessions) {
            log.info("Auto-ending inactive session: {}",
                    session.getSessionId());

            // End the session
            session.setStatus(SessionStatus.ENDED);
            session.setEndedAt(LocalDateTime.now());
            sessionRepository.save(session);

            // Mark all active participants as left
            List<Participant> active = participantRepository
                    .findBySessionIdAndLeftAtIsNull(
                            session.getSessionId());
            active.forEach(p ->
                    p.setLeftAt(LocalDateTime.now()));
            participantRepository.saveAll(active);

            // Clean Redis
            redisTemplate.delete(
                    "session:" + session.getSessionId()
                            + ":content");
            redisTemplate.delete(
                    "session:" + session.getSessionId()
                            + ":activity");
        }

        log.info("Auto-ended {} inactive sessions.",
                inactiveSessions.size());
    }
}