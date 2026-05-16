package com.codesync.execution.controller;

import com.codesync.execution.dto.request.SubmitExecutionRequest;
import com.codesync.execution.dto.response.ExecutionResponse;
import com.codesync.execution.dto.response.ExecutionStatsResponse;
import com.codesync.execution.dto.response.LanguageResponse;
import com.codesync.execution.enums.ExecutionStatus;
import com.codesync.execution.exception.ExecutionNotFoundException;
import com.codesync.execution.service.ExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import com.codesync.execution.repository.SupportedLanguageRepository;

@WebMvcTest(
        controllers = ExecutionController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
                org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class
        }
)
@AutoConfigureMockMvc(addFilters = false)
class ExecutionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private ExecutionService executionService;
    @MockBean
    private SupportedLanguageRepository supportedLanguageRepository;

    // ── Fixtures ──────────────────────────────────────────────────────

    private ExecutionResponse buildResponse(String jobId,
                                            ExecutionStatus status) {
        return ExecutionResponse.builder()
                .jobId(jobId).projectId(1L).fileId(10L).userId(1L)
                .language("java").sourceCode("code").status(status)
                .createdAt(LocalDateTime.now()).build();
    }

    private LanguageResponse buildLangResponse(String name) {
        return LanguageResponse.builder()
                .id(1L).name(name).displayName(name.toUpperCase())
                .version("1.0").fileExtension("." + name)
                .isActive(true).build();
    }

    // ════════════════════════════════════════════════════════════════
    // POST /api/v1/executions
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST / - 202 Accepted on successful job submission")
    void submitJob_valid_returns202() throws Exception {
        SubmitExecutionRequest req = new SubmitExecutionRequest();
        req.setLanguage("java"); req.setSourceCode("code");

        when(executionService.submitJob(eq(1L), any()))
                .thenReturn(buildResponse("job-1", ExecutionStatus.QUEUED));

        mockMvc.perform(post("/api/v1/executions")
                        .header("X-Auth-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.jobId").value("job-1"))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.message").value(
                        "Job submitted. Poll /api/v1/executions/job-1 for result."));
    }

    @Test
    @DisplayName("POST / - 400 Bad Request for unsupported language")
    void submitJob_unsupportedLanguage_returns400() throws Exception {
        SubmitExecutionRequest req = new SubmitExecutionRequest();
        req.setLanguage("brainfuck"); req.setSourceCode("code");

        when(executionService.submitJob(eq(1L), any()))
                .thenThrow(new IllegalArgumentException(
                        "Language not supported: brainfuck."));

        mockMvc.perform(post("/api/v1/executions")
                        .header("X-Auth-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST / - 400 Bad Request for disabled language")
    void submitJob_disabledLanguage_returns400() throws Exception {
        SubmitExecutionRequest req = new SubmitExecutionRequest();
        req.setLanguage("cobol"); req.setSourceCode("code");

        when(executionService.submitJob(eq(1L), any()))
                .thenThrow(new IllegalArgumentException(
                        "Language is currently disabled: cobol"));

        mockMvc.perform(post("/api/v1/executions")
                        .header("X-Auth-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST / - 400 Bad Request when required fields are missing")
    void submitJob_missingFields_returns400() throws Exception {
        SubmitExecutionRequest req = new SubmitExecutionRequest();
        // language and sourceCode missing

        mockMvc.perform(post("/api/v1/executions")
                        .header("X-Auth-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/executions/{jobId}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /{jobId} - 200 OK returns job details")
    void getJob_existing_returns200() throws Exception {
        ExecutionResponse resp = buildResponse("job-1", ExecutionStatus.COMPLETED);
        resp.setStdout("Hello World"); resp.setExitCode(0);
        resp.setExecutionTimeMs(123L);

        when(executionService.getJobById("job-1")).thenReturn(resp);

        mockMvc.perform(get("/api/v1/executions/job-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value("job-1"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.stdout").value("Hello World"))
                .andExpect(jsonPath("$.data.exitCode").value(0))
                .andExpect(jsonPath("$.message").value("Job fetched."));
    }

    @Test
    @DisplayName("GET /{jobId} - 404 Not Found for unknown job")
    void getJob_notFound_returns404() throws Exception {
        when(executionService.getJobById("ghost"))
                .thenThrow(new ExecutionNotFoundException(
                        "Execution job not found: ghost"));

        mockMvc.perform(get("/api/v1/executions/ghost"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /{jobId} - 200 OK returns QUEUED job (polling case)")
    void getJob_queued_returns200() throws Exception {
        when(executionService.getJobById("job-1"))
                .thenReturn(buildResponse("job-1", ExecutionStatus.QUEUED));

        mockMvc.perform(get("/api/v1/executions/job-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"));
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/executions/user/{userId}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /user/{userId} - 200 OK returns user's job list")
    void getByUser_existing_returns200() throws Exception {
        when(executionService.getJobsByUser(1L)).thenReturn(List.of(
                buildResponse("j1", ExecutionStatus.COMPLETED),
                buildResponse("j2", ExecutionStatus.QUEUED)
        ));

        mockMvc.perform(get("/api/v1/executions/user/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.message").value("Jobs fetched."));
    }

    @Test
    @DisplayName("GET /user/{userId} - 200 OK returns empty list for new user")
    void getByUser_noJobs_returnsEmpty() throws Exception {
        when(executionService.getJobsByUser(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/executions/user/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/executions/project/{projectId}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /project/{projectId} - 200 OK returns project's job list")
    void getByProject_existing_returns200() throws Exception {
        when(executionService.getJobsByProject(10L)).thenReturn(List.of(
                buildResponse("j1", ExecutionStatus.COMPLETED)
        ));

        mockMvc.perform(get("/api/v1/executions/project/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // ════════════════════════════════════════════════════════════════
    // POST /api/v1/executions/{jobId}/cancel
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /{jobId}/cancel - 200 OK on successful cancel")
    void cancelJob_queued_returns200() throws Exception {
        doNothing().when(executionService).cancelJob("job-1", 1L);

        mockMvc.perform(post("/api/v1/executions/job-1/cancel")
                        .header("X-Auth-User-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Job cancelled."));
    }

    @Test
    @DisplayName("POST /{jobId}/cancel - 400 Bad Request for COMPLETED job")
    void cancelJob_completed_returns400() throws Exception {
        doThrow(new IllegalArgumentException(
                "Cannot cancel job with status: COMPLETED"))
                .when(executionService).cancelJob("job-1", 1L);

        mockMvc.perform(post("/api/v1/executions/job-1/cancel")
                        .header("X-Auth-User-Id", 1L))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /{jobId}/cancel - 400 Bad Request for another user's job")
    void cancelJob_wrongUser_returns400() throws Exception {
        doThrow(new IllegalArgumentException(
                "You can only cancel your own jobs."))
                .when(executionService).cancelJob("job-1", 99L);

        mockMvc.perform(post("/api/v1/executions/job-1/cancel")
                        .header("X-Auth-User-Id", 99L))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /{jobId}/cancel - 404 Not Found for unknown job")
    void cancelJob_notFound_returns404() throws Exception {
        doThrow(new ExecutionNotFoundException(
                "Execution job not found: ghost"))
                .when(executionService).cancelJob("ghost", 1L);

        mockMvc.perform(post("/api/v1/executions/ghost/cancel")
                        .header("X-Auth-User-Id", 1L))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/executions/languages
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /languages - 200 OK returns all active languages")
    void getLanguages_returns200() throws Exception {
        when(executionService.getSupportedLanguages()).thenReturn(List.of(
                buildLangResponse("java"),
                buildLangResponse("python"),
                buildLangResponse("go")
        ));

        mockMvc.perform(get("/api/v1/executions/languages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].name").value("java"))
                .andExpect(jsonPath("$.message").value("Languages fetched."));
    }

    @Test
    @DisplayName("GET /languages - 200 OK returns empty when none active")
    void getLanguages_noneActive_returnsEmpty() throws Exception {
        when(executionService.getSupportedLanguages()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/executions/languages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/executions/languages/{name}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /languages/{name} - 200 OK returns language details")
    void getLanguage_existing_returns200() throws Exception {
        when(executionService.getLanguageByName("java"))
                .thenReturn(buildLangResponse("java"));

        mockMvc.perform(get("/api/v1/executions/languages/java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("java"))
                .andExpect(jsonPath("$.data.displayName").value("JAVA"))
                .andExpect(jsonPath("$.message").value("Language fetched."));
    }

    @Test
    @DisplayName("GET /languages/{name} - 404 Not Found for unknown language")
    void getLanguage_unknown_returns404() throws Exception {
        when(executionService.getLanguageByName("haskell"))
                .thenThrow(new ExecutionNotFoundException(
                        "Language not found: haskell"));

        mockMvc.perform(get("/api/v1/executions/languages/haskell"))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/executions/stats
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /stats - 200 OK returns aggregated statistics")
    void getStats_returns200() throws Exception {
        ExecutionStatsResponse stats = ExecutionStatsResponse.builder()
                .totalJobs(10L).completedJobs(7L).failedJobs(2L)
                .cancelledJobs(1L).timedOutJobs(0L)
                .queuedJobs(0L).runningJobs(0L)
                .jobsByLanguage(Map.of("java", 6L, "python", 4L))
                .avgTimeByLanguage(Map.of("java", 150.0, "python", 250.0))
                .build();

        when(executionService.getStats()).thenReturn(stats);

        mockMvc.perform(get("/api/v1/executions/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalJobs").value(10))
                .andExpect(jsonPath("$.data.completedJobs").value(7))
                .andExpect(jsonPath("$.data.failedJobs").value(2))
                .andExpect(jsonPath("$.data.jobsByLanguage.java").value(6))
                .andExpect(jsonPath("$.message").value("Stats fetched."));
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/executions/stats/user/{userId}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /stats/user/{userId} - 200 OK returns user statistics")
    void getStatsByUser_returns200() throws Exception {
        ExecutionStatsResponse stats = ExecutionStatsResponse.builder()
                .totalJobs(4L).completedJobs(2L).failedJobs(1L)
                .cancelledJobs(1L)
                .jobsByLanguage(Map.of("java", 2L, "python", 1L, "go", 1L))
                .build();

        when(executionService.getStatsByUser(1L)).thenReturn(stats);

        mockMvc.perform(get("/api/v1/executions/stats/user/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalJobs").value(4))
                .andExpect(jsonPath("$.data.completedJobs").value(2))
                .andExpect(jsonPath("$.data.jobsByLanguage.java").value(2))
                .andExpect(jsonPath("$.message").value("User stats fetched."));
    }

    @Test
    @DisplayName("GET /stats/user/{userId} - 200 OK returns zeros for new user")
    void getStatsByUser_newUser_returnsZeros() throws Exception {
        ExecutionStatsResponse stats = ExecutionStatsResponse.builder()
                .totalJobs(0L).completedJobs(0L).failedJobs(0L)
                .cancelledJobs(0L).jobsByLanguage(Map.of())
                .build();

        when(executionService.getStatsByUser(99L)).thenReturn(stats);

        mockMvc.perform(get("/api/v1/executions/stats/user/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalJobs").value(0));
    }
}