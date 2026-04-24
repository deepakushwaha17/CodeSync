package com.codesync.execution.dto.response;

import com.codesync.execution.enums.ExecutionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionResponse {

    private String jobId;
    private Long projectId;
    private Long fileId;
    private Long userId;
    private String language;
    private String sourceCode;
    private String stdin;
    private ExecutionStatus status;
    private String stdout;
    private String stderr;
    private Integer exitCode;
    private Long executionTimeMs;
    private Long memoryUsedKb;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}