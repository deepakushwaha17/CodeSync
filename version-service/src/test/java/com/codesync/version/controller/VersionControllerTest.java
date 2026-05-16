package com.codesync.version.controller;

import com.codesync.version.dto.request.CreateBranchRequest;
import com.codesync.version.dto.request.CreateSnapshotRequest;
import com.codesync.version.dto.request.TagSnapshotRequest;
import com.codesync.version.dto.response.DiffResponse;
import com.codesync.version.dto.response.SnapshotResponse;
import com.codesync.version.exception.SnapshotNotFoundException;
import com.codesync.version.service.VersionService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@WebMvcTest(
        value = VersionController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class
        }
)
@AutoConfigureMockMvc(addFilters = false)
class VersionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private VersionService versionService;

    // ── Fixture ───────────────────────────────────────────────────────

    private SnapshotResponse buildResponse(Long id, String message,
                                           String branch, String tag) {
        return SnapshotResponse.builder()
                .snapshotId(id).projectId(1L).fileId(10L).authorId(1L)
                .message(message).content("class App {}")
                .hash("sha256abc").branch(branch).tag(tag)
                .parentSnapshotId(null).createdAt(LocalDateTime.now())
                .build();
    }

    // ════════════════════════════════════════════════════════════════
    // POST /api/v1/versions/snapshots
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /snapshots - 201 Created on successful snapshot creation")
    void createSnapshot_valid_returns201() throws Exception {
        CreateSnapshotRequest req = new CreateSnapshotRequest();
        req.setProjectId(1L); req.setFileId(10L);
        req.setMessage("Initial commit");
        req.setContent("class App {}");
        req.setBranch("main");

        when(versionService.createSnapshot(eq(1L), any()))
                .thenReturn(buildResponse(1L, "Initial commit", "main", null));

        mockMvc.perform(post("/api/v1/versions/snapshots")
                        .header("X-Auth-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.snapshotId").value(1))
                .andExpect(jsonPath("$.data.message").value("Initial commit"))
                .andExpect(jsonPath("$.data.branch").value("main"));
    }

    @Test
    @DisplayName("POST /snapshots - 400 Bad Request when required fields are missing")
    void createSnapshot_missingFields_returns400() throws Exception {
        CreateSnapshotRequest req = new CreateSnapshotRequest();
        // projectId, fileId, message, content missing

        mockMvc.perform(post("/api/v1/versions/snapshots")
                        .header("X-Auth-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/versions/snapshots/{snapshotId}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /snapshots/{id} - 200 OK for existing snapshot")
    void getSnapshotById_existing_returns200() throws Exception {
        when(versionService.getSnapshotById(1L))
                .thenReturn(buildResponse(1L, "msg", "main", null));

        mockMvc.perform(get("/api/v1/versions/snapshots/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.snapshotId").value(1))
                .andExpect(jsonPath("$.data.branch").value("main"));
    }

    @Test
    @DisplayName("GET /snapshots/{id} - 404 Not Found for unknown snapshot")
    void getSnapshotById_notFound_returns404() throws Exception {
        when(versionService.getSnapshotById(99L))
                .thenThrow(new SnapshotNotFoundException("Snapshot not found with ID: 99"));

        mockMvc.perform(get("/api/v1/versions/snapshots/99"))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/versions/snapshots/file/{fileId}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /snapshots/file/{fileId} - 200 OK returns file snapshots")
    void getByFile_existing_returns200() throws Exception {
        when(versionService.getSnapshotsByFile(10L))
                .thenReturn(List.of(
                        buildResponse(1L, "A", "main", null),
                        buildResponse(2L, "B", "main", null)
                ));

        mockMvc.perform(get("/api/v1/versions/snapshots/file/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("GET /snapshots/file/{fileId} - 200 OK returns empty list")
    void getByFile_noSnapshots_returnsEmpty() throws Exception {
        when(versionService.getSnapshotsByFile(10L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/versions/snapshots/file/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/versions/snapshots/project/{projectId}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /snapshots/project/{projectId} - 200 OK returns all project snapshots")
    void getByProject_existing_returns200() throws Exception {
        when(versionService.getSnapshotsByProject(1L))
                .thenReturn(List.of(buildResponse(1L, "A", "main", null)));

        mockMvc.perform(get("/api/v1/versions/snapshots/project/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/versions/snapshots/file/{fileId}/branch/{branch}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /snapshots/file/{fileId}/branch/{branch} - 200 OK returns branch snapshots")
    void getByBranch_existing_returns200() throws Exception {
        when(versionService.getSnapshotsByBranch(10L, "feature"))
                .thenReturn(List.of(buildResponse(1L, "feat", "feature", null)));

        mockMvc.perform(get("/api/v1/versions/snapshots/file/10/branch/feature"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].branch").value("feature"));
    }

    @Test
    @DisplayName("GET /snapshots/file/{fileId}/branch/{branch} - 200 OK empty for missing branch")
    void getByBranch_noSnapshots_returnsEmpty() throws Exception {
        when(versionService.getSnapshotsByBranch(10L, "ghost")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/versions/snapshots/file/10/branch/ghost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/versions/snapshots/file/{fileId}/latest
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /snapshots/file/{fileId}/latest - 200 OK returns latest snapshot")
    void getLatest_existing_returns200() throws Exception {
        when(versionService.getLatestSnapshot(10L))
                .thenReturn(buildResponse(5L, "latest", "main", null));

        mockMvc.perform(get("/api/v1/versions/snapshots/file/10/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.snapshotId").value(5))
                .andExpect(jsonPath("$.data.message").value("latest"));
    }

    @Test
    @DisplayName("GET /snapshots/file/{fileId}/latest - 404 Not Found when no snapshots")
    void getLatest_noSnapshots_returns404() throws Exception {
        when(versionService.getLatestSnapshot(99L))
                .thenThrow(new SnapshotNotFoundException("No snapshots found for file ID: 99"));

        mockMvc.perform(get("/api/v1/versions/snapshots/file/99/latest"))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/versions/snapshots/file/{fileId}/history
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /snapshots/file/{fileId}/history - 200 OK returns full history")
    void getHistory_existing_returns200() throws Exception {
        when(versionService.getFileHistory(10L))
                .thenReturn(List.of(
                        buildResponse(1L, "first",  "main", null),
                        buildResponse(2L, "second", "main", null),
                        buildResponse(3L, "third",  "main", null)
                ));

        mockMvc.perform(get("/api/v1/versions/snapshots/file/10/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].message").value("first"));
    }

    // ════════════════════════════════════════════════════════════════
    // POST /api/v1/versions/snapshots/{snapshotId}/restore
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /snapshots/{id}/restore - 200 OK on successful restore")
    void restoreSnapshot_existing_returns200() throws Exception {
        when(versionService.restoreSnapshot(3L, 1L))
                .thenReturn(buildResponse(8L,
                        "Restored from snapshot #3: old msg", "main", null));

        mockMvc.perform(post("/api/v1/versions/snapshots/3/restore")
                        .header("X-Auth-User-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("Restored from snapshot #3: old msg"));
    }

    @Test
    @DisplayName("POST /snapshots/{id}/restore - 404 Not Found for unknown snapshot")
    void restoreSnapshot_notFound_returns404() throws Exception {
        when(versionService.restoreSnapshot(99L, 1L))
                .thenThrow(new SnapshotNotFoundException("Snapshot not found."));

        mockMvc.perform(post("/api/v1/versions/snapshots/99/restore")
                        .header("X-Auth-User-Id", 1L))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/versions/snapshots/diff?a={id}&b={id}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /snapshots/diff - 200 OK returns diff result")
    void diffSnapshots_valid_returns200() throws Exception {
        DiffResponse diff = DiffResponse.builder()
                .snapshotIdA(1L).snapshotIdB(2L)
                .branchA("main").branchB("main")
                .addedLines(2).removedLines(1).unchangedLines(3)
                .lines(List.of(
                        DiffResponse.DiffLine.builder().type("UNCHANGED")
                                .lineNumber(1).content("line1").build(),
                        DiffResponse.DiffLine.builder().type("REMOVED")
                                .lineNumber(2).content("old").build(),
                        DiffResponse.DiffLine.builder().type("ADDED")
                                .lineNumber(2).content("new").build()
                ))
                .build();

        when(versionService.diffSnapshots(1L, 2L)).thenReturn(diff);

        mockMvc.perform(get("/api/v1/versions/snapshots/diff")
                        .param("a", "1")
                        .param("b", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.snapshotIdA").value(1))
                .andExpect(jsonPath("$.data.snapshotIdB").value(2))
                .andExpect(jsonPath("$.data.addedLines").value(2))
                .andExpect(jsonPath("$.data.removedLines").value(1))
                .andExpect(jsonPath("$.data.lines.length()").value(3));
    }

    @Test
    @DisplayName("GET /snapshots/diff - 404 Not Found when either snapshot is missing")
    void diffSnapshots_notFound_returns404() throws Exception {
        when(versionService.diffSnapshots(1L, 99L))
                .thenThrow(new SnapshotNotFoundException("Snapshot not found."));

        mockMvc.perform(get("/api/v1/versions/snapshots/diff")
                        .param("a", "1")
                        .param("b", "99"))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════
    // POST /api/v1/versions/branches
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /branches - 201 Created on successful branch creation")
    void createBranch_valid_returns201() throws Exception {
        CreateBranchRequest req = new CreateBranchRequest();
        req.setProjectId(1L); req.setFileId(10L);
        req.setBranchName("feature-login");
        req.setFromSnapshotId(5L);

        when(versionService.createBranch(eq(1L), any()))
                .thenReturn(buildResponse(6L,
                        "Branch 'feature-login' created from snapshot #5",
                        "feature-login", null));

        mockMvc.perform(post("/api/v1/versions/branches")
                        .header("X-Auth-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.branch").value("feature-login"));
    }

    @Test
    @DisplayName("POST /branches - 400 Bad Request when branch already exists")
    void createBranch_alreadyExists_returns400() throws Exception {
        CreateBranchRequest req = new CreateBranchRequest();
        req.setProjectId(1L); req.setFileId(10L);
        req.setBranchName("main");
        req.setFromSnapshotId(1L);

        when(versionService.createBranch(eq(1L), any()))
                .thenThrow(new IllegalArgumentException("Branch 'main' already exists."));

        mockMvc.perform(post("/api/v1/versions/branches")
                        .header("X-Auth-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/versions/branches/project/{projectId}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /branches/project/{projectId} - 200 OK returns branch list")
    void getBranches_existing_returns200() throws Exception {
        when(versionService.getBranchesByProject(1L))
                .thenReturn(List.of("main", "feature", "hotfix"));

        mockMvc.perform(get("/api/v1/versions/branches/project/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0]").value("main"));
    }

    @Test
    @DisplayName("GET /branches/project/{projectId} - 200 OK returns empty list for new project")
    void getBranches_empty_returns200() throws Exception {
        when(versionService.getBranchesByProject(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/versions/branches/project/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ════════════════════════════════════════════════════════════════
    // PUT /api/v1/versions/snapshots/{snapshotId}/tag
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PUT /snapshots/{id}/tag - 200 OK on successful tagging")
    void tagSnapshot_valid_returns200() throws Exception {
        TagSnapshotRequest req = new TagSnapshotRequest();
        req.setTag("v1.0.0");

        when(versionService.tagSnapshot(eq(1L), any()))
                .thenReturn(buildResponse(1L, "release", "main", "v1.0.0"));

        mockMvc.perform(put("/api/v1/versions/snapshots/1/tag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tag").value("v1.0.0"));
    }

    @Test
    @DisplayName("PUT /snapshots/{id}/tag - 400 Bad Request when tag is already used")
    void tagSnapshot_duplicateTag_returns400() throws Exception {
        TagSnapshotRequest req = new TagSnapshotRequest();
        req.setTag("v1.0.0");

        when(versionService.tagSnapshot(eq(1L), any()))
                .thenThrow(new IllegalArgumentException("Tag 'v1.0.0' is already used."));

        mockMvc.perform(put("/api/v1/versions/snapshots/1/tag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /snapshots/{id}/tag - 404 Not Found for unknown snapshot")
    void tagSnapshot_notFound_returns404() throws Exception {
        TagSnapshotRequest req = new TagSnapshotRequest();
        req.setTag("v2.0.0");

        when(versionService.tagSnapshot(eq(99L), any()))
                .thenThrow(new SnapshotNotFoundException("Snapshot not found."));

        mockMvc.perform(put("/api/v1/versions/snapshots/99/tag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }
}