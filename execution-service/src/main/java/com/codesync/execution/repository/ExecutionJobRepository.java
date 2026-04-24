package com.codesync.execution.repository;

import com.codesync.execution.entity.ExecutionJob;
import com.codesync.execution.enums.ExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ExecutionJobRepository
        extends JpaRepository<ExecutionJob, String> {

    List<ExecutionJob> findByUserIdOrderByCreatedAtDesc(
            Long userId);

    List<ExecutionJob> findByProjectIdOrderByCreatedAtDesc(
            Long projectId);

    List<ExecutionJob> findByStatus(ExecutionStatus status);

    List<ExecutionJob> findByLanguageOrderByCreatedAtDesc(
            String language);

    List<ExecutionJob> findByCreatedAtBetween(
            LocalDateTime start, LocalDateTime end);

    int countByUserId(Long userId);

    int countByStatus(ExecutionStatus status);

    // Count by language for stats
    @Query("SELECT e.language, COUNT(e) FROM ExecutionJob e " +
            "GROUP BY e.language ORDER BY COUNT(e) DESC")
    List<Object[]> countByLanguage();

    // Get recent jobs for a user
    @Query("SELECT e FROM ExecutionJob e " +
            "WHERE e.userId = :userId " +
            "ORDER BY e.createdAt DESC LIMIT :limit")
    List<ExecutionJob> findRecentByUser(
            @Param("userId") Long userId,
            @Param("limit") int limit);

    // Average execution time by language
    @Query("SELECT e.language, AVG(e.executionTimeMs) " +
            "FROM ExecutionJob e " +
            "WHERE e.status = 'COMPLETED' " +
            "GROUP BY e.language")
    List<Object[]> avgExecutionTimeByLanguage();
}