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
import com.codesync.execution.service.ExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionServiceImpl implements ExecutionService {

    private final ExecutionJobRepository jobRepository;
    private final SupportedLanguageRepository languageRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${execution.sandbox.exchange-name:execution.exchange}")
    private String exchangeName;

    @Value("${execution.sandbox.routing-key:execution.route}")
    private String routingKey;

    // ─────────────────────────────────────────────────────────────────
    // SUBMIT
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ExecutionResponse submitJob(
            Long userId, SubmitExecutionRequest request) {

        log.info("Submitting {} job for user: {}",
                request.getLanguage(), userId);

        // Validate language is supported
        SupportedLanguage lang = languageRepository
                .findByName(request.getLanguage().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Language not supported: "
                                + request.getLanguage()
                                + ". Call /api/v1/executions/languages "
                                + "for supported list."));

        if (!lang.getIsActive()) {
            throw new IllegalArgumentException(
                    "Language is currently disabled: "
                            + request.getLanguage());
        }

        // Save job to DB with QUEUED status
        ExecutionJob job = ExecutionJob.builder()
                .projectId(request.getProjectId())
                .fileId(request.getFileId())
                .userId(userId)
                .language(request.getLanguage().toLowerCase())
                .sourceCode(request.getSourceCode())
                .stdin(request.getStdin())
                .status(ExecutionStatus.QUEUED)
                .build();

        ExecutionJob saved = jobRepository.save(job);
        log.info("Job saved with ID: {}", saved.getJobId());

        // Publish job ID to RabbitMQ queue
        // Worker will pick it up and execute
        rabbitTemplate.convertAndSend(
                exchangeName, routingKey, saved.getJobId());

        log.info("Job {} published to RabbitMQ queue",
                saved.getJobId());

        return mapToResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────────
    // GET
    // ─────────────────────────────────────────────────────────────────

    @Override
    public ExecutionResponse getJobById(String jobId) {
        return mapToResponse(findJobOrThrow(jobId));
    }

    @Override
    public List<ExecutionResponse> getJobsByUser(Long userId) {
        return jobRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ExecutionResponse> getJobsByProject(
            Long projectId) {
        return jobRepository
                .findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────
    // CANCEL
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void cancelJob(String jobId, Long userId) {
        ExecutionJob job = findJobOrThrow(jobId);

        if (!job.getUserId().equals(userId)) {
            throw new IllegalArgumentException(
                    "You can only cancel your own jobs.");
        }

        if (job.getStatus() == ExecutionStatus.COMPLETED
                || job.getStatus() == ExecutionStatus.FAILED
                || job.getStatus() == ExecutionStatus.CANCELLED) {
            throw new IllegalArgumentException(
                    "Cannot cancel job with status: "
                            + job.getStatus());
        }

        job.setStatus(ExecutionStatus.CANCELLED);
        job.setCompletedAt(LocalDateTime.now());
        job.setStderr("Job cancelled by user.");
        jobRepository.save(job);

        log.info("Job {} cancelled by user {}", jobId, userId);
    }

    // ─────────────────────────────────────────────────────────────────
    // LANGUAGES
    // ─────────────────────────────────────────────────────────────────

    @Override
    public List<LanguageResponse> getSupportedLanguages() {
        return languageRepository.findByIsActiveTrue()
                .stream()
                .map(this::mapToLanguageResponse)
                .collect(Collectors.toList());
    }

    @Override
    public LanguageResponse getLanguageByName(String name) {
        SupportedLanguage lang = languageRepository
                .findByName(name.toLowerCase())
                .orElseThrow(() -> new ExecutionNotFoundException(
                        "Language not found: " + name));
        return mapToLanguageResponse(lang);
    }

    // ─────────────────────────────────────────────────────────────────
    // STATS
    // ─────────────────────────────────────────────────────────────────

    @Override
    public ExecutionStatsResponse getStats() {
        long total = jobRepository.count();
        long completed = jobRepository.countByStatus(
                ExecutionStatus.COMPLETED);
        long failed = jobRepository.countByStatus(
                ExecutionStatus.FAILED);
        long cancelled = jobRepository.countByStatus(
                ExecutionStatus.CANCELLED);
        long timedOut = jobRepository.countByStatus(
                ExecutionStatus.TIMED_OUT);
        long queued = jobRepository.countByStatus(
                ExecutionStatus.QUEUED);
        long running = jobRepository.countByStatus(
                ExecutionStatus.RUNNING);

        // Build language counts map
        Map<String, Long> byLanguage = new HashMap<>();
        jobRepository.countByLanguage().forEach(row ->
                byLanguage.put(
                        (String) row[0],
                        (Long) row[1]));

        // Build avg time map
        Map<String, Double> avgTime = new HashMap<>();
        jobRepository.avgExecutionTimeByLanguage().forEach(row ->
                avgTime.put(
                        (String) row[0],
                        (Double) row[1]));

        return ExecutionStatsResponse.builder()
                .totalJobs(total)
                .completedJobs(completed)
                .failedJobs(failed)
                .cancelledJobs(cancelled)
                .timedOutJobs(timedOut)
                .queuedJobs(queued)
                .runningJobs(running)
                .jobsByLanguage(byLanguage)
                .avgTimeByLanguage(avgTime)
                .build();
    }

    @Override
    public ExecutionStatsResponse getStatsByUser(Long userId) {
        List<ExecutionJob> userJobs =
                jobRepository.findByUserIdOrderByCreatedAtDesc(
                        userId);

        long total = userJobs.size();
        long completed = userJobs.stream().filter(j ->
                        j.getStatus() == ExecutionStatus.COMPLETED)
                .count();
        long failed = userJobs.stream().filter(j ->
                        j.getStatus() == ExecutionStatus.FAILED)
                .count();
        long cancelled = userJobs.stream().filter(j ->
                        j.getStatus() == ExecutionStatus.CANCELLED)
                .count();

        Map<String, Long> byLanguage = userJobs.stream()
                .collect(Collectors.groupingBy(
                        ExecutionJob::getLanguage,
                        Collectors.counting()));

        return ExecutionStatsResponse.builder()
                .totalJobs(total)
                .completedJobs(completed)
                .failedJobs(failed)
                .cancelledJobs(cancelled)
                .jobsByLanguage(byLanguage)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────
    // INTERNAL — called by worker
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void updateJobResult(
            String jobId, String stdout, String stderr,
            Integer exitCode, Long executionTimeMs,
            Long memoryUsedKb) {

        ExecutionJob job = findJobOrThrow(jobId);

        job.setStdout(stdout);
        job.setStderr(stderr);
        job.setExitCode(exitCode);
        job.setExecutionTimeMs(executionTimeMs);
        job.setMemoryUsedKb(memoryUsedKb);
        job.setCompletedAt(LocalDateTime.now());

        if (exitCode != null && exitCode == 0) {
            job.setStatus(ExecutionStatus.COMPLETED);
        } else {
            job.setStatus(ExecutionStatus.FAILED);
        }

        jobRepository.save(job);
        log.info("Job {} result saved. Status: {}",
                jobId, job.getStatus());
    }

    // ─────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────

    private ExecutionJob findJobOrThrow(String jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ExecutionNotFoundException(
                        "Execution job not found: " + jobId));
    }

    private ExecutionResponse mapToResponse(ExecutionJob j) {
        return ExecutionResponse.builder()
                .jobId(j.getJobId())
                .projectId(j.getProjectId())
                .fileId(j.getFileId())
                .userId(j.getUserId())
                .language(j.getLanguage())
                .sourceCode(j.getSourceCode())
                .stdin(j.getStdin())
                .status(j.getStatus())
                .stdout(j.getStdout())
                .stderr(j.getStderr())
                .exitCode(j.getExitCode())
                .executionTimeMs(j.getExecutionTimeMs())
                .memoryUsedKb(j.getMemoryUsedKb())
                .createdAt(j.getCreatedAt())
                .completedAt(j.getCompletedAt())
                .build();
    }

    private LanguageResponse mapToLanguageResponse(
            SupportedLanguage l) {
        return LanguageResponse.builder()
                .id(l.getId())
                .name(l.getName())
                .displayName(l.getDisplayName())
                .version(l.getVersion())
                .fileExtension(l.getFileExtension())
                .helloWorldCode(l.getHelloWorldCode())
                .isActive(l.getIsActive())
                .build();
    }
}