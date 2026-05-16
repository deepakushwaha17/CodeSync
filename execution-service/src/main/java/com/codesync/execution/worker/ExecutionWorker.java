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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionWorker {

    private static final String DOCKER = "docker";
    private static final String RUN = "run";
    private static final String REMOVE_AFTER_RUN = "--rm";
    private static final String INTERACTIVE = "-i";
    private static final String VOLUME_FLAG = "-v";
    private static final String WORKSPACE = "/workspace/";
    private static final String SHELL = "sh";
    private static final String SHELL_COMMAND = "-c";

    private final ExecutionJobRepository jobRepository;
    private final ExecutionService executionService;

    @Value("${execution.sandbox.max-time-seconds:10}")
    private int maxTimeSeconds;

    @Value("${execution.sandbox.max-memory-mb:256}")
    private int maxMemoryMb;

    @RabbitListener(queues = "execution.jobs")
    @Transactional
    public void processJob(String jobId) {
        log.info("Worker received job ID: {}", jobId);

        Optional<ExecutionJob> optJob = jobRepository.findById(jobId);

        if (optJob.isEmpty()) {
            log.error("Job not found in DB: {}", jobId);
            return;
        }

        ExecutionJob job = optJob.get();

        if (job.getStatus() == ExecutionStatus.CANCELLED) {
            log.info("Job {} was cancelled, skipping.", jobId);
            return;
        }

        job.setStatus(ExecutionStatus.RUNNING);
        jobRepository.save(job);

        log.info("Executing job: {} language: {}", jobId, job.getLanguage());

        try {
            ExecutionResult result = simulateExecution(
                    job.getLanguage(),
                    job.getSourceCode(),
                    job.getStdin()
            );

            executionService.updateJobResult(
                    jobId,
                    result.stdout,
                    result.stderr,
                    result.exitCode,
                    result.executionTimeMs,
                    result.memoryUsedKb
            );

            log.info("Job {} completed. Exit code: {}", jobId, result.exitCode);

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

    private ExecutionResult simulateExecution(
            String language,
            String sourceCode,
            String stdin) throws Exception {

        long startTime = System.currentTimeMillis();
        Path tempDir = Files.createTempDirectory("codesync-");
        String volume = tempDir.toAbsolutePath() + ":/workspace";

        ProcessBuilder pb = createProcessBuilder(
                language.toLowerCase(),
                sourceCode,
                tempDir,
                volume
        );

        if (pb == null) {
            return new ExecutionResult(
                    "",
                    "Unsupported language: " + language,
                    1,
                    0L,
                    0L
            );
        }

        pb.directory(tempDir.toFile());

        Process process = pb.start();

        if (stdin != null && !stdin.isBlank()) {
            process.getOutputStream()
                    .write(stdin.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
        }

        process.getOutputStream().close();

        boolean finished = process.waitFor(maxTimeSeconds, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            return new ExecutionResult(
                    "",
                    "Time Limit Exceeded",
                    124,
                    0L,
                    0L
            );
        }

        String stdout = new String(
                process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        );

        String stderr = new String(
                process.getErrorStream().readAllBytes(),
                StandardCharsets.UTF_8
        );

        return new ExecutionResult(
                stdout,
                stderr,
                process.exitValue(),
                System.currentTimeMillis() - startTime,
                0L
        );
    }

    private ProcessBuilder createProcessBuilder(
            String language,
            String sourceCode,
            Path tempDir,
            String volume) throws Exception {

        return switch (language) {
            case "python", "py" -> createDirectRunBuilder(
                    tempDir,
                    "main.py",
                    sourceCode,
                    volume,
                    "python:3.11-slim",
                    "python",
                    WORKSPACE + "main.py"
            );

            case "java" -> createJavaBuilder(sourceCode, tempDir, volume);

            case "javascript", "js", "nodejs" -> createDirectRunBuilder(
                    tempDir,
                    "main.js",
                    sourceCode,
                    volume,
                    "node:18-slim",
                    "node",
                    WORKSPACE + "main.js"
            );

            case "typescript", "ts" -> createShellRunBuilder(
                    tempDir,
                    "main.ts",
                    sourceCode,
                    volume,
                    "node:18",
                    "npm install -g typescript ts-node >/dev/null 2>&1 "
                            + "&& ts-node /workspace/main.ts"
            );

            case "c" -> createShellRunBuilder(
                    tempDir,
                    "main.c",
                    sourceCode,
                    volume,
                    "gcc:13",
                    "gcc /workspace/main.c -o /workspace/main "
                            + "&& /workspace/main"
            );

            case "cpp", "c++" -> createShellRunBuilder(
                    tempDir,
                    "main.cpp",
                    sourceCode,
                    volume,
                    "gcc:13",
                    "g++ /workspace/main.cpp -o /workspace/main "
                            + "&& /workspace/main"
            );

            case "go" -> createDirectRunBuilder(
                    tempDir,
                    "main.go",
                    sourceCode,
                    volume,
                    "golang:1.21-alpine",
                    "go",
                    "run",
                    WORKSPACE + "main.go"
            );

            case "rust", "rs" -> createShellRunBuilder(
                    tempDir,
                    "main.rs",
                    sourceCode,
                    volume,
                    "rust:1.75-slim",
                    "rustc /workspace/main.rs -o /workspace/main "
                            + "&& /workspace/main"
            );

            case "ruby", "rb" -> createDirectRunBuilder(
                    tempDir,
                    "main.rb",
                    sourceCode,
                    volume,
                    "ruby:3.2-slim",
                    "ruby",
                    WORKSPACE + "main.rb"
            );

            case "php" -> createDirectRunBuilder(
                    tempDir,
                    "main.php",
                    sourceCode,
                    volume,
                    "php:8.2-cli",
                    "php",
                    WORKSPACE + "main.php"
            );

            case "kotlin", "kt" -> createShellRunBuilder(
                    tempDir,
                    "Main.kt",
                    sourceCode,
                    volume,
                    "gradle:jdk21",
                    "kotlinc /workspace/Main.kt -include-runtime "
                            + "-d /workspace/main.jar && java -jar "
                            + "/workspace/main.jar"
            );

            case "swift" -> createDirectRunBuilder(
                    tempDir,
                    "main.swift",
                    sourceCode,
                    volume,
                    "swift:5.9-slim",
                    "swift",
                    WORKSPACE + "main.swift"
            );

            default -> null;
        };
    }

    private ProcessBuilder createDirectRunBuilder(
            Path tempDir,
            String fileName,
            String sourceCode,
            String volume,
            String image,
            String... command) throws Exception {

        Files.writeString(
                tempDir.resolve(fileName),
                sourceCode,
                StandardCharsets.UTF_8
        );

        String[] baseCommand = {
                DOCKER, RUN, REMOVE_AFTER_RUN, INTERACTIVE,
                VOLUME_FLAG, volume, image
        };

        String[] fullCommand =
                new String[baseCommand.length + command.length];

        System.arraycopy(baseCommand, 0, fullCommand, 0, baseCommand.length);
        System.arraycopy(
                command,
                0,
                fullCommand,
                baseCommand.length,
                command.length
        );

        return new ProcessBuilder(fullCommand);
    }

    private ProcessBuilder createShellRunBuilder(
            Path tempDir,
            String fileName,
            String sourceCode,
            String volume,
            String image,
            String command) throws Exception {

        return createDirectRunBuilder(
                tempDir,
                fileName,
                sourceCode,
                volume,
                image,
                SHELL,
                SHELL_COMMAND,
                command
        );
    }

    private ProcessBuilder createJavaBuilder(
            String sourceCode,
            Path tempDir,
            String volume) throws Exception {

        String className = extractJavaClassName(sourceCode);

        return createShellRunBuilder(
                tempDir,
                className + ".java",
                sourceCode,
                volume,
                "eclipse-temurin:21-jdk",
                "javac /workspace/" + className
                        + ".java && java -cp /workspace "
                        + className
        );
    }

    private String extractJavaClassName(String sourceCode) {
        Pattern publicClassPattern =
                Pattern.compile("public\\s+class\\s+(\\w+)");

        Matcher publicMatcher =
                publicClassPattern.matcher(sourceCode);

        if (publicMatcher.find()) {
            return publicMatcher.group(1);
        }

        Pattern classPattern =
                Pattern.compile("class\\s+(\\w+)");

        Matcher classMatcher =
                classPattern.matcher(sourceCode);

        if (classMatcher.find()) {
            return classMatcher.group(1);
        }

        return "Main";
    }

    private record ExecutionResult(
            String stdout,
            String stderr,
            int exitCode,
            long executionTimeMs,
            long memoryUsedKb) {
    }
}