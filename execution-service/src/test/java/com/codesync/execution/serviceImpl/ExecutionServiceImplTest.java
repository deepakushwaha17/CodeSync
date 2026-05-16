package com.codesync.execution.serviceImpl;

import com.codesync.execution.dto.request.SubmitExecutionRequest;
import com.codesync.execution.dto.response.ExecutionResponse;
import com.codesync.execution.dto.response.ExecutionStatsResponse;
import com.codesync.execution.dto.response.LanguageResponse;
import com.codesync.execution.entity.ExecutionJob;
import com.codesync.execution.entity.SupportedLanguage;
import com.codesync.execution.enums.ExecutionStatus;
import com.codesync.execution.exception.ExecutionNotFoundException;
import com.codesync.execution.repository.ExecutionJobRepository;
import com.codesync.execution.repository.SupportedLanguageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExecutionServiceImplTest {

    @Mock private ExecutionJobRepository jobRepository;
    @Mock private SupportedLanguageRepository languageRepository;
    @Mock private RabbitTemplate rabbitTemplate;

    @InjectMocks private ExecutionServiceImpl executionService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(executionService,
                "exchangeName", "execution.exchange");
        ReflectionTestUtils.setField(executionService,
                "routingKey", "execution.route");
    }

    // ── Fixtures ──────────────────────────────────────────────────────

    private SupportedLanguage buildLang(String name, boolean active) {
        return SupportedLanguage.builder()
                .id(1L).name(name)
                .displayName(name.toUpperCase())
                .version("1.0").dockerImage("docker/" + name)
                .fileExtension("." + name).isActive(active)
                .build();
    }

    private ExecutionJob buildJob(String jobId, Long userId,
                                  String language,
                                  ExecutionStatus status) {
        return ExecutionJob.builder()
                .jobId(jobId).userId(userId)
                .projectId(1L).fileId(10L)
                .language(language).sourceCode("code")
                .status(status).createdAt(LocalDateTime.now())
                .build();
    }

    private SubmitExecutionRequest buildRequest(String language) {
        SubmitExecutionRequest req = new SubmitExecutionRequest();
        req.setProjectId(1L); req.setFileId(10L);
        req.setLanguage(language); req.setSourceCode("code");
        req.setStdin("");
        return req;
    }

    // ════════════════════════════════════════════════════════════════
    // submitJob()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("submitJob - saves QUEUED job and publishes to RabbitMQ")
    void submitJob_valid_savesAndPublishes() {
        when(languageRepository.findByName("java"))
                .thenReturn(Optional.of(buildLang("java", true)));

        ExecutionJob saved = buildJob("job-1", 1L, "java",
                ExecutionStatus.QUEUED);
        when(jobRepository.save(any())).thenReturn(saved);
        doNothing().when(rabbitTemplate)
                .convertAndSend(anyString(), (Object) any());

        ExecutionResponse response =
                executionService.submitJob(1L, buildRequest("java"));

        assertThat(response.getJobId()).isEqualTo("job-1");
        assertThat(response.getStatus()).isEqualTo(ExecutionStatus.QUEUED);
        assertThat(response.getLanguage()).isEqualTo("java");

        // Verify saved with QUEUED status
        verify(jobRepository).save(argThat(j ->
                j.getStatus() == ExecutionStatus.QUEUED &&
                        j.getUserId().equals(1L)));

        // Verify published to RabbitMQ
        verify(rabbitTemplate).convertAndSend(
                eq("execution.jobs"), eq("job-1"));
    }

    @Test
    @DisplayName("submitJob - lowercases language name before saving")
    void submitJob_uppercaseLanguage_lowercasedBeforeSave() {
        when(languageRepository.findByName("java"))
                .thenReturn(Optional.of(buildLang("java", true)));

        ExecutionJob saved = buildJob("job-1", 1L, "java",
                ExecutionStatus.QUEUED);
        when(jobRepository.save(any())).thenReturn(saved);
        doNothing().when(rabbitTemplate)
                .convertAndSend(anyString(), (Object) any());

        // Submit with uppercase "JAVA"
        executionService.submitJob(1L, buildRequest("JAVA"));

        // languageRepository must be called with lowercase
        verify(languageRepository).findByName("java");
        verify(jobRepository).save(argThat(j ->
                j.getLanguage().equals("java")));
    }

    @Test
    @DisplayName("submitJob - throws IllegalArgumentException for unsupported language")
    void submitJob_unsupportedLanguage_throwsException() {
        when(languageRepository.findByName("brainfuck"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                executionService.submitJob(1L, buildRequest("brainfuck")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Language not supported: brainfuck")
                .hasMessageContaining("/api/v1/executions/languages");

        verify(jobRepository, never()).save(any());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), (Object) any());
    }

    @Test
    @DisplayName("submitJob - throws IllegalArgumentException for disabled language")
    void submitJob_disabledLanguage_throwsException() {
        when(languageRepository.findByName("cobol"))
                .thenReturn(Optional.of(buildLang("cobol", false)));

        assertThatThrownBy(() ->
                executionService.submitJob(1L, buildRequest("cobol")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Language is currently disabled: cobol");

        verify(jobRepository, never()).save(any());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), (Object) any());
    }

    @Test
    @DisplayName("submitJob - saves job with null projectId and fileId (optional fields)")
    void submitJob_noProjectOrFile_savesSuccessfully() {
        when(languageRepository.findByName("python"))
                .thenReturn(Optional.of(buildLang("python", true)));

        SubmitExecutionRequest req = buildRequest("python");
        req.setProjectId(null); req.setFileId(null);

        ExecutionJob saved = ExecutionJob.builder()
                .jobId("job-2").userId(1L).language("python")
                .sourceCode("code").status(ExecutionStatus.QUEUED)
                .createdAt(LocalDateTime.now()).build();
        when(jobRepository.save(any())).thenReturn(saved);
        doNothing().when(rabbitTemplate)
                .convertAndSend(anyString(), (Object) any());

        ExecutionResponse response =
                executionService.submitJob(1L, req);

        assertThat(response.getJobId()).isEqualTo("job-2");
        verify(jobRepository).save(argThat(j ->
                j.getProjectId() == null && j.getFileId() == null));
    }

    // ════════════════════════════════════════════════════════════════
    // getJobById()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getJobById - returns ExecutionResponse for existing job")
    void getJobById_existing_returnsResponse() {
        ExecutionJob job = buildJob("job-1", 1L, "java",
                ExecutionStatus.COMPLETED);
        job.setStdout("Hello World");
        job.setExitCode(0);
        job.setExecutionTimeMs(123L);
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));

        ExecutionResponse response = executionService.getJobById("job-1");

        assertThat(response.getJobId()).isEqualTo("job-1");
        assertThat(response.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(response.getStdout()).isEqualTo("Hello World");
        assertThat(response.getExitCode()).isZero();
    }

    @Test
    @DisplayName("getJobById - throws ExecutionNotFoundException for unknown job")
    void getJobById_notFound_throwsException() {
        when(jobRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> executionService.getJobById("ghost"))
                .isInstanceOf(ExecutionNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    // ════════════════════════════════════════════════════════════════
    // getJobsByUser()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getJobsByUser - returns list of jobs for user")
    void getJobsByUser_existing_returnsList() {
        when(jobRepository.findByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(
                        buildJob("j1", 1L, "java",   ExecutionStatus.COMPLETED),
                        buildJob("j2", 1L, "python", ExecutionStatus.QUEUED)
                ));

        List<ExecutionResponse> results =
                executionService.getJobsByUser(1L);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(ExecutionResponse::getUserId)
                .allMatch(id -> id.equals(1L));
    }

    @Test
    @DisplayName("getJobsByUser - returns empty list when user has no jobs")
    void getJobsByUser_noJobs_returnsEmpty() {
        when(jobRepository.findByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of());

        assertThat(executionService.getJobsByUser(1L)).isEmpty();
    }

    // ════════════════════════════════════════════════════════════════
    // getJobsByProject()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getJobsByProject - returns list of jobs for project")
    void getJobsByProject_existing_returnsList() {
        when(jobRepository.findByProjectIdOrderByCreatedAtDesc(10L))
                .thenReturn(List.of(
                        buildJob("j1", 1L, "java",   ExecutionStatus.COMPLETED),
                        buildJob("j2", 2L, "python", ExecutionStatus.FAILED)
                ));

        List<ExecutionResponse> results =
                executionService.getJobsByProject(10L);

        assertThat(results).hasSize(2);
    }

    // ════════════════════════════════════════════════════════════════
    // cancelJob()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("cancelJob - QUEUED job cancelled successfully")
    void cancelJob_queued_cancelsJob() {
        ExecutionJob job = buildJob("job-1", 1L, "java",
                ExecutionStatus.QUEUED);
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        executionService.cancelJob("job-1", 1L);

        assertThat(job.getStatus()).isEqualTo(ExecutionStatus.CANCELLED);
        assertThat(job.getCompletedAt()).isNotNull();
        assertThat(job.getStderr()).isEqualTo("Job cancelled by user.");
        verify(jobRepository).save(job);
    }

    @Test
    @DisplayName("cancelJob - RUNNING job cancelled successfully")
    void cancelJob_running_cancelsJob() {
        ExecutionJob job = buildJob("job-1", 1L, "java",
                ExecutionStatus.RUNNING);
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        executionService.cancelJob("job-1", 1L);

        assertThat(job.getStatus()).isEqualTo(ExecutionStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancelJob - throws IllegalArgumentException for another user's job")
    void cancelJob_wrongUser_throwsException() {
        ExecutionJob job = buildJob("job-1", 1L, "java",
                ExecutionStatus.QUEUED);
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> executionService.cancelJob("job-1", 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("You can only cancel your own jobs.");

        verify(jobRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelJob - throws IllegalArgumentException for COMPLETED job")
    void cancelJob_completed_throwsException() {
        ExecutionJob job = buildJob("job-1", 1L, "java",
                ExecutionStatus.COMPLETED);
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> executionService.cancelJob("job-1", 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot cancel job with status: COMPLETED");
    }

    @Test
    @DisplayName("cancelJob - throws IllegalArgumentException for FAILED job")
    void cancelJob_failed_throwsException() {
        ExecutionJob job = buildJob("job-1", 1L, "java",
                ExecutionStatus.FAILED);
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> executionService.cancelJob("job-1", 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot cancel job with status: FAILED");
    }

    @Test
    @DisplayName("cancelJob - throws IllegalArgumentException for already CANCELLED job")
    void cancelJob_alreadyCancelled_throwsException() {
        ExecutionJob job = buildJob("job-1", 1L, "java",
                ExecutionStatus.CANCELLED);
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> executionService.cancelJob("job-1", 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot cancel job with status: CANCELLED");
    }

    @Test
    @DisplayName("cancelJob - throws ExecutionNotFoundException for unknown job")
    void cancelJob_notFound_throwsException() {
        when(jobRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> executionService.cancelJob("ghost", 1L))
                .isInstanceOf(ExecutionNotFoundException.class);
    }

    // ════════════════════════════════════════════════════════════════
    // getSupportedLanguages()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getSupportedLanguages - returns only active languages")
    void getSupportedLanguages_returnsActiveOnly() {
        when(languageRepository.findByIsActiveTrue())
                .thenReturn(List.of(
                        buildLang("java",   true),
                        buildLang("python", true)
                ));

        List<LanguageResponse> results =
                executionService.getSupportedLanguages();

        assertThat(results).hasSize(2).allMatch(LanguageResponse::getIsActive);
    }

    @Test
    @DisplayName("getSupportedLanguages - returns empty when no active languages")
    void getSupportedLanguages_noneActive_returnsEmpty() {
        when(languageRepository.findByIsActiveTrue()).thenReturn(List.of());

        assertThat(executionService.getSupportedLanguages()).isEmpty();
    }

    // ════════════════════════════════════════════════════════════════
    // getLanguageByName()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getLanguageByName - returns LanguageResponse for existing name")
    void getLanguageByName_existing_returnsResponse() {
        when(languageRepository.findByName("java"))
                .thenReturn(Optional.of(buildLang("java", true)));

        LanguageResponse response =
                executionService.getLanguageByName("java");

        assertThat(response.getName()).isEqualTo("java");
        assertThat(response.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("getLanguageByName - lowercases name before lookup")
    void getLanguageByName_uppercase_lowercasedBeforeLookup() {
        when(languageRepository.findByName("java"))
                .thenReturn(Optional.of(buildLang("java", true)));

        executionService.getLanguageByName("JAVA");

        verify(languageRepository).findByName("java");
    }

    @Test
    @DisplayName("getLanguageByName - throws ExecutionNotFoundException for unknown name")
    void getLanguageByName_unknown_throwsException() {
        when(languageRepository.findByName("haskell"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                executionService.getLanguageByName("haskell"))
                .isInstanceOf(ExecutionNotFoundException.class)
                .hasMessageContaining("haskell");
    }

    // ════════════════════════════════════════════════════════════════
    // updateJobResult()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("updateJobResult - exitCode=0 sets status to COMPLETED")
    void updateJobResult_exitCode0_setsCompleted() {
        ExecutionJob job = buildJob("job-1", 1L, "java",
                ExecutionStatus.RUNNING);
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        executionService.updateJobResult("job-1",
                "Hello World", "", 0, 150L, 1024L);

        assertThat(job.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(job.getStdout()).isEqualTo("Hello World");
        assertThat(job.getExitCode()).isZero();
        assertThat(job.getExecutionTimeMs()).isEqualTo(150L);
        assertThat(job.getMemoryUsedKb()).isEqualTo(1024L);
        assertThat(job.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("updateJobResult - exitCode != 0 sets status to FAILED")
    void updateJobResult_nonZeroExitCode_setsFailed() {
        ExecutionJob job = buildJob("job-1", 1L, "java",
                ExecutionStatus.RUNNING);
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        executionService.updateJobResult("job-1",
                "", "Error: NullPointerException", 1, 80L, 512L);

        assertThat(job.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(job.getStderr()).isEqualTo("Error: NullPointerException");
        assertThat(job.getExitCode()).isEqualTo(1);
    }

    @Test
    @DisplayName("updateJobResult - null exitCode sets status to FAILED")
    void updateJobResult_nullExitCode_setsFailed() {
        ExecutionJob job = buildJob("job-1", 1L, "java",
                ExecutionStatus.RUNNING);
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        executionService.updateJobResult("job-1",
                null, "Timeout", null, null, null);

        assertThat(job.getStatus()).isEqualTo(ExecutionStatus.FAILED);
    }

    @Test
    @DisplayName("updateJobResult - throws ExecutionNotFoundException for unknown job")
    void updateJobResult_notFound_throwsException() {
        when(jobRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                executionService.updateJobResult("ghost",
                        "out", "err", 0, 100L, 512L))
                .isInstanceOf(ExecutionNotFoundException.class);
    }

    // ════════════════════════════════════════════════════════════════
    // getStats()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getStats - returns correct aggregated statistics")
    void getStats_returnsCorrectStats() {
        when(jobRepository.count()).thenReturn(10L);
        when(jobRepository.countByStatus(ExecutionStatus.COMPLETED)).thenReturn(6);
        when(jobRepository.countByStatus(ExecutionStatus.FAILED)).thenReturn(2);
        when(jobRepository.countByStatus(ExecutionStatus.CANCELLED)).thenReturn(1);
        when(jobRepository.countByStatus(ExecutionStatus.TIMED_OUT)).thenReturn(0);
        when(jobRepository.countByStatus(ExecutionStatus.QUEUED)).thenReturn(1);
        when(jobRepository.countByStatus(ExecutionStatus.RUNNING)).thenReturn(0);

        Object[] javaRow   = new Object[]{"java",   4L};
        Object[] pythonRow = new Object[]{"python",  2L};
        when(jobRepository.countByLanguage())
                .thenReturn(List.of(javaRow, pythonRow));

        Object[] javaAvg   = new Object[]{"java",   150.0};
        Object[] pythonAvg = new Object[]{"python",  250.0};
        when(jobRepository.avgExecutionTimeByLanguage())
                .thenReturn(List.of(javaAvg, pythonAvg));

        ExecutionStatsResponse stats = executionService.getStats();

        assertThat(stats.getTotalJobs()).isEqualTo(10L);
        assertThat(stats.getCompletedJobs()).isEqualTo(6L);
        assertThat(stats.getFailedJobs()).isEqualTo(2L);
        assertThat(stats.getCancelledJobs()).isEqualTo(1L);
        assertThat(stats.getTimedOutJobs()).isZero();
        assertThat(stats.getQueuedJobs()).isEqualTo(1L);
        assertThat(stats.getRunningJobs()).isZero();
        assertThat(stats.getJobsByLanguage()).containsEntry("java", 4L);
        assertThat(stats.getJobsByLanguage()).containsEntry("python", 2L);
        assertThat(stats.getAvgTimeByLanguage()).containsEntry("java", 150.0);
    }

    // ════════════════════════════════════════════════════════════════
    // getStatsByUser()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getStatsByUser - returns correct per-user statistics")
    void getStatsByUser_returnsCorrectStats() {
        ExecutionJob j1 = buildJob("j1", 1L, "java",   ExecutionStatus.COMPLETED);
        ExecutionJob j2 = buildJob("j2", 1L, "java",   ExecutionStatus.COMPLETED);
        ExecutionJob j3 = buildJob("j3", 1L, "python", ExecutionStatus.FAILED);
        ExecutionJob j4 = buildJob("j4", 1L, "go",     ExecutionStatus.CANCELLED);

        when(jobRepository.findByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(j1, j2, j3, j4));

        ExecutionStatsResponse stats =
                executionService.getStatsByUser(1L);

        assertThat(stats.getTotalJobs()).isEqualTo(4L);
        assertThat(stats.getCompletedJobs()).isEqualTo(2L);
        assertThat(stats.getFailedJobs()).isEqualTo(1L);
        assertThat(stats.getCancelledJobs()).isEqualTo(1L);
        assertThat(stats.getJobsByLanguage()).containsEntry("java", 2L);
        assertThat(stats.getJobsByLanguage()).containsEntry("python", 1L);
        assertThat(stats.getJobsByLanguage()).containsEntry("go", 1L);
    }

    @Test
    @DisplayName("getStatsByUser - returns zero counts for user with no jobs")
    void getStatsByUser_noJobs_returnsZeroCounts() {
        when(jobRepository.findByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of());

        ExecutionStatsResponse stats =
                executionService.getStatsByUser(1L);

        assertThat(stats.getTotalJobs()).isZero();
        assertThat(stats.getCompletedJobs()).isZero();
        assertThat(stats.getJobsByLanguage()).isEmpty();
    }
}