package com.codesync.collab.repository;

import com.codesync.collab.entity.CollabSession;
import com.codesync.collab.enums.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CollabSessionRepository
        extends JpaRepository<CollabSession, String> {

    List<CollabSession> findByProjectId(Long projectId);

    List<CollabSession> findByFileId(Long fileId);

    List<CollabSession> findByOwnerId(Long ownerId);

    List<CollabSession> findByStatus(SessionStatus status);

    Optional<CollabSession> findByProjectIdAndFileIdAndStatus(
            Long projectId, Long fileId, SessionStatus status);

    List<CollabSession> findByProjectIdAndStatus(
            Long projectId, SessionStatus status);

    // Find inactive sessions for auto-cleanup
    @Query("SELECT s FROM CollabSession s " +
            "WHERE s.status = 'ACTIVE' " +
            "AND s.lastActivityAt < :cutoffTime")
    List<CollabSession> findInactiveSessions(
            @Param("cutoffTime") LocalDateTime cutoffTime);
}