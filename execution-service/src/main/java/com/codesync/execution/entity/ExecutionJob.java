package com.codesync.execution.entity;

import com.codesync.execution.enums.ExecutionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "execution_jobs",
        indexes = {
                @Index(name = "idx_user_id",
                        columnList = "user_id"),
                @Index(name = "idx_status",
                        columnList = "status"),
                @Index(name = "idx_project_id",
                        columnList = "project_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String jobId;

    // References project-service (optional)
    private Long projectId;

    // References file-service (optional)
    private Long fileId;

    // References auth-service
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String language;

    // Source code to execute
    @Column(nullable = false, columnDefinition = "TEXT")
    private String sourceCode;

    // Standard input provided by user
    @Column(columnDefinition = "TEXT")
    private String stdin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ExecutionStatus status = ExecutionStatus.QUEUED;

    // Execution output
    @Column(columnDefinition = "TEXT")
    private String stdout;

    // Error output
    @Column(columnDefinition = "TEXT")
    private String stderr;

    // Process exit code (0 = success)
    private Integer exitCode;

    // How long execution took in milliseconds
    private Long executionTimeMs;

    // Memory used in kilobytes
    private Long memoryUsedKb;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // When execution finished
    private LocalDateTime completedAt;
}