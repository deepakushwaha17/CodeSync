package com.codesync.execution.worker;

import com.codesync.execution.entity.ExecutionJob;
import com.codesync.execution.enums.ExecutionStatus;
import com.codesync.execution.repository.ExecutionJobRepository;
import com.codesync.execution.service.ExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * RabbitMQ consumer that processes execution jobs.
 *
 * Listens to: execution.jobs queue
 * Receives:   job ID (String)
 * Processes:  fetches job from DB → simulates execution
 *             → updates result in DB
 *
 * In production: replace simulateExecution() with
 * actual Docker container execution using Docker Java SDK.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionWorker {

    private final ExecutionJobRepository jobRepository;
    private final ExecutionService executionService;

    @Value("${execution.sandbox.max-time-seconds:10}")
    private int maxTimeSeconds;

    @Value("${execution.sandbox.max-memory-mb:256}")
    private int maxMemoryMb;

    /**
     * Listen to RabbitMQ queue and process execution jobs.
     */
    @RabbitListener(queues = "${execution.sandbox.queue-name"
            + ":execution.jobs}")
    @Transactional
    public void processJob(String jobId) {
        log.info("Worker received job ID: {}", jobId);

        Optional<ExecutionJob> optJob =
                jobRepository.findById(jobId);

        if (optJob.isEmpty()) {
            log.error("Job not found in DB: {}", jobId);
            return;
        }

        ExecutionJob job = optJob.get();

        // Skip if already cancelled
        if (job.getStatus() == ExecutionStatus.CANCELLED) {
            log.info("Job {} was cancelled, skipping.", jobId);
            return;
        }

        // Mark as RUNNING
        job.setStatus(ExecutionStatus.RUNNING);
        jobRepository.save(job);

        log.info("Executing job: {} language: {}",
                jobId, job.getLanguage());

        try {
            // Execute the code
            ExecutionResult result = simulateExecution(
                    job.getLanguage(),
                    job.getSourceCode(),
                    job.getStdin()
            );

            // Save result
            executionService.updateJobResult(
                    jobId,
                    result.stdout,
                    result.stderr,
                    result.exitCode,
                    result.executionTimeMs,
                    result.memoryUsedKb
            );

            log.info("Job {} completed. Exit code: {}",
                    jobId, result.exitCode);

        } catch (Exception e) {
            log.error("Job {} failed: {}", jobId, e.getMessage());
            executionService.updateJobResult(
                    jobId,
                    null,
                    "Execution error: " + e.getMessage(),
                    1,
                    0L,
                    0L
            );
        }
    }

    /**
     * Simulates code execution with realistic output.
     *
     * TODO: Replace this with actual Docker container execution:
     *
     * DockerClient docker = DockerClientBuilder.getInstance().build();
     * CreateContainerResponse container = docker.createContainerCmd(image)
     *     .withCmd("sh", "-c", compileAndRunCommand)
     *     .withMemory(maxMemoryMb * 1024 * 1024L)
     *     .withNetworkDisabled(true)
     *     .exec();
     * docker.startContainerCmd(container.getId()).exec();
     * // wait for result with timeout
     * // collect stdout/stderr
     * // destroy container
     */
    private ExecutionResult simulateExecution(
            String language,
            String sourceCode,
            String stdin) throws InterruptedException {

        long startTime = System.currentTimeMillis();

        // Simulate execution time (500ms - 3000ms)
        long simulatedTime = 500 + (long)(Math.random() * 2500);
        Thread.sleep(Math.min(simulatedTime,
                maxTimeSeconds * 1000L));

        long executionTime =
                System.currentTimeMillis() - startTime;

        // Simulate memory usage (10MB - 150MB)
        long memoryUsed = 10240 + (long)(Math.random() * 143360);

        String stdout;
        String stderr = "";
        int exitCode;

        // Generate realistic output based on language
        switch (language.toLowerCase()) {

            case "java":
                if (sourceCode.contains(
                        "System.out.println")) {
                    stdout = extractPrintOutput(
                            sourceCode, "println");
                    exitCode = 0;
                } else if (sourceCode.contains("main")) {
                    stdout = "Program executed successfully.\n";
                    exitCode = 0;
                } else {
                    stderr = "error: class is public, " +
                            "should be in file named "
                            + extractClassName(sourceCode)
                            + ".java\n";
                    stdout = "";
                    exitCode = 1;
                }
                break;

            case "python":
                if (sourceCode.contains("print")) {
                    stdout = extractPrintOutput(
                            sourceCode, "print");
                    exitCode = 0;
                } else {
                    stdout = "";
                    exitCode = 0;
                }
                break;

            case "javascript":
            case "nodejs":
                if (sourceCode.contains("console.log")) {
                    stdout = extractPrintOutput(
                            sourceCode, "console.log");
                    exitCode = 0;
                } else {
                    stdout = "";
                    exitCode = 0;
                }
                break;

            case "c":
            case "cpp":
                if (sourceCode.contains("printf")
                        || sourceCode.contains("cout")) {
                    stdout = "Program output here\n";
                    exitCode = 0;
                } else {
                    stdout = "";
                    exitCode = 0;
                }
                break;

            default:
                stdout = "Program executed successfully "
                        + "in " + language + "\n";
                exitCode = 0;
        }

        // Simulate occasional timeout
        if (executionTime > maxTimeSeconds * 900L) {
            stderr = "Time Limit Exceeded: execution "
                    + "exceeded " + maxTimeSeconds + "s";
            exitCode = 124;
        }

        return new ExecutionResult(
                stdout, stderr, exitCode,
                executionTime, memoryUsed);
    }

    private String extractPrintOutput(
            String code, String printFunc) {
        // Very basic extraction for simulation
        StringBuilder output = new StringBuilder();
        String[] lines = code.split("\n");
        for (String line : lines) {
            if (line.trim().startsWith(printFunc)) {
                int start = line.indexOf("\"");
                int end = line.lastIndexOf("\"");
                if (start != -1 && end > start) {
                    output.append(
                                    line.substring(start + 1, end))
                            .append("\n");
                }
            }
        }
        return output.length() > 0
                ? output.toString()
                : "Hello, World!\n";
    }

    private String extractClassName(String code) {
        String[] parts = code.split("\\s+");
        for (int i = 0; i < parts.length - 1; i++) {
            if (parts[i].equals("class")) {
                return parts[i + 1].replace("{", "");
            }
        }
        return "Main";
    }

    /**
     * Result holder for execution output.
     */
    private record ExecutionResult(
            String stdout,
            String stderr,
            int exitCode,
            long executionTimeMs,
            long memoryUsedKb) {}
}