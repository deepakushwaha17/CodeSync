package com.codesync.execution.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionStatsResponse {

    private long totalJobs;
    private long completedJobs;
    private long failedJobs;
    private long cancelledJobs;
    private long timedOutJobs;
    private long queuedJobs;
    private long runningJobs;

    // Job count per language
    private Map<String, Long> jobsByLanguage;

    // Average execution time per language (ms)
    private Map<String, Double> avgTimeByLanguage;
}