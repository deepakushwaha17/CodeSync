package com.codesync.execution.service;

import com.codesync.execution.dto.request.SubmitExecutionRequest;
import com.codesync.execution.dto.response.ExecutionResponse;
import com.codesync.execution.dto.response.ExecutionStatsResponse;
import com.codesync.execution.dto.response.LanguageResponse;

import java.util.List;

public interface ExecutionService {

    // Submit
    ExecutionResponse submitJob(Long userId,
                                SubmitExecutionRequest request);

    // Get
    ExecutionResponse getJobById(String jobId);
    List<ExecutionResponse> getJobsByUser(Long userId);
    List<ExecutionResponse> getJobsByProject(Long projectId);

    // Cancel
    void cancelJob(String jobId, Long userId);

    // Languages
    List<LanguageResponse> getSupportedLanguages();
    LanguageResponse getLanguageByName(String name);

    // Stats
    ExecutionStatsResponse getStats();
    ExecutionStatsResponse getStatsByUser(Long userId);

    // Internal — called by worker after execution
    void updateJobResult(String jobId, String stdout,
                         String stderr, Integer exitCode,
                         Long executionTimeMs,
                         Long memoryUsedKb);
}