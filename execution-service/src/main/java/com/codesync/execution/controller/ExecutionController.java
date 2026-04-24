package com.codesync.execution.controller;

import com.codesync.execution.dto.request.SubmitExecutionRequest;
import com.codesync.execution.dto.response.ApiResponse;
import com.codesync.execution.dto.response.ExecutionResponse;
import com.codesync.execution.dto.response.ExecutionStatsResponse;
import com.codesync.execution.dto.response.LanguageResponse;
import com.codesync.execution.service.ExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/executions")
@RequiredArgsConstructor
@Tag(name = "Execution Service",
        description = "Code Execution APIs")
public class ExecutionController {

    private final ExecutionService executionService;

    // ── POST /api/v1/executions ───────────────────────────────────────
    @PostMapping
    @Operation(summary = "Submit code for execution")
    public ResponseEntity<ApiResponse<ExecutionResponse>>
    submitJob(
            @RequestHeader("X-Auth-User-Id") Long userId,
            @Valid @RequestBody SubmitExecutionRequest request) {

        ExecutionResponse response =
                executionService.submitJob(userId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(
                        "Job submitted. Poll /api/v1/executions/"
                                + response.getJobId()
                                + " for result.",
                        response));
    }

    // ── GET /api/v1/executions/{jobId} ────────────────────────────────
    @GetMapping("/{jobId}")
    @Operation(summary = "Get execution job by ID")
    public ResponseEntity<ApiResponse<ExecutionResponse>>
    getJob(@PathVariable String jobId) {

        ExecutionResponse response =
                executionService.getJobById(jobId);
        return ResponseEntity.ok(
                ApiResponse.success("Job fetched.", response));
    }

    // ── GET /api/v1/executions/user/{userId} ──────────────────────────
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get all jobs for a user")
    public ResponseEntity<ApiResponse<List<ExecutionResponse>>>
    getByUser(@PathVariable Long userId) {

        List<ExecutionResponse> jobs =
                executionService.getJobsByUser(userId);
        return ResponseEntity.ok(
                ApiResponse.success("Jobs fetched.", jobs));
    }

    // ── GET /api/v1/executions/project/{projectId} ────────────────────
    @GetMapping("/project/{projectId}")
    @Operation(summary = "Get all jobs for a project")
    public ResponseEntity<ApiResponse<List<ExecutionResponse>>>
    getByProject(@PathVariable Long projectId) {

        List<ExecutionResponse> jobs =
                executionService.getJobsByProject(projectId);
        return ResponseEntity.ok(
                ApiResponse.success("Jobs fetched.", jobs));
    }

    // ── POST /api/v1/executions/{jobId}/cancel ────────────────────────
    @PostMapping("/{jobId}/cancel")
    @Operation(summary = "Cancel a running or queued job")
    public ResponseEntity<ApiResponse<Void>> cancelJob(
            @PathVariable String jobId,
            @RequestHeader("X-Auth-User-Id") Long userId) {

        executionService.cancelJob(jobId, userId);
        return ResponseEntity.ok(
                ApiResponse.success("Job cancelled."));
    }

    // ── GET /api/v1/executions/languages ──────────────────────────────
    @GetMapping("/languages")
    @Operation(summary = "Get all supported languages")
    public ResponseEntity<ApiResponse<List<LanguageResponse>>>
    getLanguages() {

        List<LanguageResponse> languages =
                executionService.getSupportedLanguages();
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Languages fetched.", languages));
    }

    // ── GET /api/v1/executions/languages/{name} ───────────────────────
    @GetMapping("/languages/{name}")
    @Operation(summary = "Get language details by name")
    public ResponseEntity<ApiResponse<LanguageResponse>>
    getLanguage(@PathVariable String name) {

        LanguageResponse response =
                executionService.getLanguageByName(name);
        return ResponseEntity.ok(
                ApiResponse.success("Language fetched.", response));
    }

    // ── GET /api/v1/executions/stats ──────────────────────────────────
    @GetMapping("/stats")
    @Operation(summary = "Get overall execution statistics")
    public ResponseEntity<ApiResponse<ExecutionStatsResponse>>
    getStats() {

        ExecutionStatsResponse stats =
                executionService.getStats();
        return ResponseEntity.ok(
                ApiResponse.success("Stats fetched.", stats));
    }

    // ── GET /api/v1/executions/stats/user/{userId} ────────────────────
    @GetMapping("/stats/user/{userId}")
    @Operation(summary = "Get execution stats for a user")
    public ResponseEntity<ApiResponse<ExecutionStatsResponse>>
    getStatsByUser(@PathVariable Long userId) {

        ExecutionStatsResponse stats =
                executionService.getStatsByUser(userId);
        return ResponseEntity.ok(
                ApiResponse.success("User stats fetched.", stats));
    }
}