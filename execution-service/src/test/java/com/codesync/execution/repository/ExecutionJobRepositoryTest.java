package com.codesync.execution.repository;

import com.codesync.execution.entity.ExecutionJob;
import com.codesync.execution.enums.ExecutionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ExecutionJobRepositoryTest {

    @Autowired
    private ExecutionJobRepository jobRepository;

    @BeforeEach
    void setUp() { jobRepository.deleteAll(); }

    // ── Helper ────────────────────────────────────────────────────────

    private ExecutionJob build(Long userId, Long projectId,
                               String language, String code,
                               ExecutionStatus status,
                               Long execTimeMs) {
        ExecutionJob job = ExecutionJob.builder()
                .userId(userId).projectId(projectId)
                .language(language).sourceCode(code)
                .status(status)
                .build();
        if (execTimeMs != null) job.setExecutionTimeMs(execTimeMs);
        return job;
    }

    // ── findByUserIdOrderByCreatedAtDesc ──────────────────────────────

    @Test
    @DisplayName("findByUserIdOrderByCreatedAtDesc - returns jobs newest first")
    void findByUserId_returnsNewestFirst() {
        jobRepository.save(build(1L, 10L, "java", "code1",
                ExecutionStatus.COMPLETED, 100L));
        jobRepository.save(build(1L, 10L, "python", "code2",
                ExecutionStatus.QUEUED, null));
        jobRepository.save(build(2L, 10L, "go", "code3",
                ExecutionStatus.QUEUED, null));

        List<ExecutionJob> results =
                jobRepository.findByUserIdOrderByCreatedAtDesc(1L);

        assertThat(results).hasSize(2).allMatch(j -> j.getUserId().equals(1L));
        // newest saved last
        assertThat(results.get(0).getLanguage()).isEqualTo("python");
    }

    @Test
    @DisplayName("findByUserIdOrderByCreatedAtDesc - returns empty for unknown user")
    void findByUserId_unknown_returnsEmpty() {
        assertThat(jobRepository.findByUserIdOrderByCreatedAtDesc(99L)).isEmpty();
    }

    // ── findByProjectIdOrderByCreatedAtDesc ───────────────────────────

    @Test
    @DisplayName("findByProjectIdOrderByCreatedAtDesc - returns jobs for project")
    void findByProjectId_returnsProjectJobs() {
        jobRepository.save(build(1L, 10L, "java", "c",
                ExecutionStatus.COMPLETED, 200L));
        jobRepository.save(build(2L, 10L, "python", "c",
                ExecutionStatus.FAILED, null));
        jobRepository.save(build(1L, 20L, "go", "c",
                ExecutionStatus.QUEUED, null));

        List<ExecutionJob> results =
                jobRepository.findByProjectIdOrderByCreatedAtDesc(10L);

        assertThat(results).hasSize(2).allMatch(j -> j.getProjectId().equals(10L));
    }

    @Test
    @DisplayName("findByProjectIdOrderByCreatedAtDesc - returns empty for unknown project")
    void findByProjectId_unknown_returnsEmpty() {
        assertThat(jobRepository.findByProjectIdOrderByCreatedAtDesc(99L)).isEmpty();
    }

    // ── findByStatus ──────────────────────────────────────────────────

    @Test
    @DisplayName("findByStatus(QUEUED) - returns only queued jobs")
    void findByStatus_queued_returnsQueuedOnly() {
        jobRepository.save(build(1L, 10L, "java", "c",
                ExecutionStatus.QUEUED, null));
        jobRepository.save(build(1L, 10L, "python", "c",
                ExecutionStatus.QUEUED, null));
        jobRepository.save(build(2L, 10L, "go", "c",
                ExecutionStatus.COMPLETED, 50L));

        List<ExecutionJob> queued =
                jobRepository.findByStatus(ExecutionStatus.QUEUED);

        assertThat(queued).hasSize(2).allMatch(j ->
                j.getStatus() == ExecutionStatus.QUEUED);
    }

    @Test
    @DisplayName("findByStatus(RUNNING) - returns only running jobs")
    void findByStatus_running_returnsRunningOnly() {
        jobRepository.save(build(1L, 10L, "java", "c",
                ExecutionStatus.RUNNING, null));
        jobRepository.save(build(2L, 10L, "python", "c",
                ExecutionStatus.QUEUED, null));

        List<ExecutionJob> running =
                jobRepository.findByStatus(ExecutionStatus.RUNNING);

        assertThat(running).hasSize(1);
        assertThat(running.get(0).getStatus()).isEqualTo(ExecutionStatus.RUNNING);
    }

    // ── findByLanguageOrderByCreatedAtDesc ────────────────────────────

    @Test
    @DisplayName("findByLanguageOrderByCreatedAtDesc - returns jobs by language")
    void findByLanguage_returnsCorrectLanguageJobs() {
        jobRepository.save(build(1L, 10L, "java", "c",
                ExecutionStatus.COMPLETED, 100L));
        jobRepository.save(build(2L, 10L, "java", "c",
                ExecutionStatus.FAILED, null));
        jobRepository.save(build(1L, 10L, "python", "c",
                ExecutionStatus.QUEUED, null));

        List<ExecutionJob> javaJobs =
                jobRepository.findByLanguageOrderByCreatedAtDesc("java");

        assertThat(javaJobs).hasSize(2).allMatch(j ->
                j.getLanguage().equals("java"));
    }

    // ── findByCreatedAtBetween ────────────────────────────────────────

    @Test
    @DisplayName("findByCreatedAtBetween - returns jobs within time range")
    void findByCreatedAtBetween_inRange_returnsJobs() {
        ExecutionJob job = jobRepository.save(
                build(1L, 10L, "java", "code",
                        ExecutionStatus.COMPLETED, 100L));

        LocalDateTime start = LocalDateTime.now().minusMinutes(5);
        LocalDateTime end   = LocalDateTime.now().plusMinutes(5);

        List<ExecutionJob> results =
                jobRepository.findByCreatedAtBetween(start, end);

        assertThat(results).isNotEmpty().anyMatch(j ->
                j.getJobId().equals(job.getJobId()));
    }

    @Test
    @DisplayName("findByCreatedAtBetween - returns empty when no jobs in range")
    void findByCreatedAtBetween_outOfRange_returnsEmpty() {
        jobRepository.save(build(1L, 10L, "java", "code",
                ExecutionStatus.COMPLETED, 100L));

        // Range is in the future
        LocalDateTime start = LocalDateTime.now().plusHours(1);
        LocalDateTime end   = LocalDateTime.now().plusHours(2);

        assertThat(jobRepository.findByCreatedAtBetween(start, end)).isEmpty();
    }

    // ── countByUserId ─────────────────────────────────────────────────

    @Test
    @DisplayName("countByUserId - returns correct count for user")
    void countByUserId_returnsCount() {
        jobRepository.save(build(1L, 10L, "java",   "c",
                ExecutionStatus.COMPLETED, 100L));
        jobRepository.save(build(1L, 10L, "python", "c",
                ExecutionStatus.FAILED, null));
        jobRepository.save(build(2L, 10L, "go",     "c",
                ExecutionStatus.QUEUED, null));

        assertThat(jobRepository.countByUserId(1L)).isEqualTo(2);
    }

    @Test
    @DisplayName("countByUserId - returns 0 for unknown user")
    void countByUserId_unknown_returnsZero() {
        assertThat(jobRepository.countByUserId(99L)).isZero();
    }

    // ── countByStatus ─────────────────────────────────────────────────

    @Test
    @DisplayName("countByStatus - returns correct count for each status")
    void countByStatus_returnsCorrectCount() {
        jobRepository.save(build(1L, 10L, "java",   "c",
                ExecutionStatus.COMPLETED, 100L));
        jobRepository.save(build(1L, 10L, "python", "c",
                ExecutionStatus.COMPLETED, 200L));
        jobRepository.save(build(2L, 10L, "go",     "c",
                ExecutionStatus.FAILED, null));
        jobRepository.save(build(3L, 10L, "rust",   "c",
                ExecutionStatus.QUEUED, null));

        assertThat(jobRepository.countByStatus(ExecutionStatus.COMPLETED))
                .isEqualTo(2);
        assertThat(jobRepository.countByStatus(ExecutionStatus.FAILED))
                .isEqualTo(1);
        assertThat(jobRepository.countByStatus(ExecutionStatus.QUEUED))
                .isEqualTo(1);
        assertThat(jobRepository.countByStatus(ExecutionStatus.RUNNING))
                .isZero();
    }

    // ── countByLanguage (JPQL aggregate) ──────────────────────────────

    @Test
    @DisplayName("countByLanguage - returns language counts ordered by most used")
    void countByLanguage_returnsGroupedCounts() {
        jobRepository.save(build(1L, 10L, "java", "c",
                ExecutionStatus.COMPLETED, 100L));
        jobRepository.save(build(1L, 10L, "java", "c",
                ExecutionStatus.COMPLETED, 150L));
        jobRepository.save(build(2L, 10L, "python", "c",
                ExecutionStatus.QUEUED, null));

        List<Object[]> results = jobRepository.countByLanguage();

        assertThat(results).isNotEmpty();
        // java has 2 — should be first
        assertThat((String) results.get(0)[0]).isEqualTo("java");
        assertThat((Long)   results.get(0)[1]).isEqualTo(2L);
    }

    // ── findRecentByUser (JPQL LIMIT) ─────────────────────────────────

    @Test
    @DisplayName("findRecentByUser - returns at most `limit` most recent jobs")
    void findRecentByUser_respectsLimit() {
        for (int i = 0; i < 5; i++) {
            jobRepository.save(build(1L, 10L, "java",
                    "code " + i, ExecutionStatus.COMPLETED, 100L));
        }

        List<ExecutionJob> recent =
                jobRepository.findRecentByUser(1L, 3);

        assertThat(recent).hasSize(3);
    }

    @Test
    @DisplayName("findRecentByUser - returns all jobs when total < limit")
    void findRecentByUser_fewerThanLimit_returnsAll() {
        jobRepository.save(build(1L, 10L, "java", "c",
                ExecutionStatus.COMPLETED, 100L));
        jobRepository.save(build(1L, 10L, "python", "c",
                ExecutionStatus.QUEUED, null));

        List<ExecutionJob> recent = jobRepository.findRecentByUser(1L, 10);

        assertThat(recent).hasSize(2);
    }

    // ── avgExecutionTimeByLanguage (JPQL aggregate) ───────────────────

    @Test
    @DisplayName("avgExecutionTimeByLanguage - returns avg time for COMPLETED jobs")
    void avgExecutionTimeByLanguage_completedJobs_returnsAvg() {
        ExecutionJob j1 = build(1L, 10L, "java", "c",
                ExecutionStatus.COMPLETED, 100L);
        ExecutionJob j2 = build(2L, 10L, "java", "c",
                ExecutionStatus.COMPLETED, 200L);
        ExecutionJob j3 = build(3L, 10L, "python", "c",
                ExecutionStatus.COMPLETED, 300L);
        // FAILED job should NOT be counted
        ExecutionJob j4 = build(4L, 10L, "java", "c",
                ExecutionStatus.FAILED, 999L);
        jobRepository.saveAll(List.of(j1, j2, j3, j4));

        List<Object[]> results =
                jobRepository.avgExecutionTimeByLanguage();

        assertThat(results).isNotEmpty();

        Object[] javaRow = results.stream()
                .filter(r -> "java".equals(r[0]))
                .findFirst().orElseThrow();
        // avg of 100 and 200 = 150.0
        assertThat((Double) javaRow[1]).isEqualTo(150.0);

        Object[] pythonRow = results.stream()
                .filter(r -> "python".equals(r[0]))
                .findFirst().orElseThrow();
        assertThat((Double) pythonRow[1]).isEqualTo(300.0);
    }

    @Test
    @DisplayName("avgExecutionTimeByLanguage - returns empty when no COMPLETED jobs")
    void avgExecutionTimeByLanguage_noCompleted_returnsEmpty() {
        jobRepository.save(build(1L, 10L, "java", "c",
                ExecutionStatus.QUEUED, null));

        assertThat(jobRepository.avgExecutionTimeByLanguage()).isEmpty();
    }
}