package com.codesync.version.controller;

import com.codesync.version.dto.request.CreateBranchRequest;
import com.codesync.version.dto.request.CreateSnapshotRequest;
import com.codesync.version.dto.request.TagSnapshotRequest;
import com.codesync.version.dto.response.ApiResponse;
import com.codesync.version.dto.response.DiffResponse;
import com.codesync.version.dto.response.SnapshotResponse;
import com.codesync.version.service.VersionService;
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
@RequestMapping("/api/v1/versions")
@RequiredArgsConstructor
@Tag(name = "Version Service",
        description = "Snapshot and Version Control APIs")
public class VersionController {

    private final VersionService versionService;

    // ── POST /api/v1/versions/snapshots ───────────────────────────────
    @PostMapping("/snapshots")
    @Operation(summary = "Create a new snapshot (commit)")
    public ResponseEntity<ApiResponse<SnapshotResponse>>
    createSnapshot(
            @RequestHeader("X-Auth-User-Id") Long userId,
            @Valid @RequestBody CreateSnapshotRequest request) {

        SnapshotResponse response =
                versionService.createSnapshot(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Snapshot created.", response));
    }

    // ── GET /api/v1/versions/snapshots/{snapshotId} ───────────────────
    @GetMapping("/snapshots/{snapshotId}")
    @Operation(summary = "Get snapshot by ID")
    public ResponseEntity<ApiResponse<SnapshotResponse>>
    getSnapshotById(@PathVariable Long snapshotId) {

        SnapshotResponse response =
                versionService.getSnapshotById(snapshotId);
        return ResponseEntity.ok(
                ApiResponse.success("Snapshot fetched.", response));
    }

    // ── GET /api/v1/versions/snapshots/file/{fileId} ──────────────────
    @GetMapping("/snapshots/file/{fileId}")
    @Operation(summary = "Get all snapshots for a file")
    public ResponseEntity<ApiResponse<List<SnapshotResponse>>>
    getByFile(@PathVariable Long fileId) {

        List<SnapshotResponse> list =
                versionService.getSnapshotsByFile(fileId);
        return ResponseEntity.ok(
                ApiResponse.success("Snapshots fetched.", list));
    }

    // ── GET /api/v1/versions/snapshots/project/{projectId} ────────────
    @GetMapping("/snapshots/project/{projectId}")
    @Operation(summary = "Get all snapshots for a project")
    public ResponseEntity<ApiResponse<List<SnapshotResponse>>>
    getByProject(@PathVariable Long projectId) {

        List<SnapshotResponse> list =
                versionService.getSnapshotsByProject(projectId);
        return ResponseEntity.ok(
                ApiResponse.success("Snapshots fetched.", list));
    }

    // ── GET /api/v1/versions/snapshots/file/{fileId}/branch/{branch} ──
    @GetMapping("/snapshots/file/{fileId}/branch/{branch}")
    @Operation(summary = "Get snapshots for a file on a branch")
    public ResponseEntity<ApiResponse<List<SnapshotResponse>>>
    getByBranch(
            @PathVariable Long fileId,
            @PathVariable String branch) {

        List<SnapshotResponse> list =
                versionService.getSnapshotsByBranch(fileId, branch);
        return ResponseEntity.ok(
                ApiResponse.success("Branch snapshots fetched.", list));
    }

    // ── GET /api/v1/versions/snapshots/file/{fileId}/latest ───────────
    @GetMapping("/snapshots/file/{fileId}/latest")
    @Operation(summary = "Get latest snapshot for a file")
    public ResponseEntity<ApiResponse<SnapshotResponse>>
    getLatest(@PathVariable Long fileId) {

        SnapshotResponse response =
                versionService.getLatestSnapshot(fileId);
        return ResponseEntity.ok(
                ApiResponse.success("Latest snapshot fetched.",
                        response));
    }

    // ── GET /api/v1/versions/snapshots/file/{fileId}/history ──────────
    @GetMapping("/snapshots/file/{fileId}/history")
    @Operation(summary = "Get full file version history")
    public ResponseEntity<ApiResponse<List<SnapshotResponse>>>
    getHistory(@PathVariable Long fileId) {

        List<SnapshotResponse> list =
                versionService.getFileHistory(fileId);
        return ResponseEntity.ok(
                ApiResponse.success("File history fetched.", list));
    }

    // ── POST /api/v1/versions/snapshots/{snapshotId}/restore ──────────
    @PostMapping("/snapshots/{snapshotId}/restore")
    @Operation(summary = "Restore file to a previous snapshot")
    public ResponseEntity<ApiResponse<SnapshotResponse>>
    restoreSnapshot(
            @PathVariable Long snapshotId,
            @RequestHeader("X-Auth-User-Id") Long userId) {

        SnapshotResponse response =
                versionService.restoreSnapshot(snapshotId, userId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Snapshot restored successfully.", response));
    }

    // ── GET /api/v1/versions/snapshots/diff?a={id}&b={id} ─────────────
    @GetMapping("/snapshots/diff")
    @Operation(summary = "Diff two snapshots")
    public ResponseEntity<ApiResponse<DiffResponse>> diffSnapshots(
            @RequestParam Long a,
            @RequestParam Long b) {

        DiffResponse diff = versionService.diffSnapshots(a, b);
        return ResponseEntity.ok(
                ApiResponse.success("Diff computed.", diff));
    }

    // ── POST /api/v1/versions/branches ────────────────────────────────
    @PostMapping("/branches")
    @Operation(summary = "Create a new branch")
    public ResponseEntity<ApiResponse<SnapshotResponse>>
    createBranch(
            @RequestHeader("X-Auth-User-Id") Long userId,
            @Valid @RequestBody CreateBranchRequest request) {

        SnapshotResponse response =
                versionService.createBranch(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Branch created.", response));
    }

    // ── GET /api/v1/versions/branches/project/{projectId} ─────────────
    @GetMapping("/branches/project/{projectId}")
    @Operation(summary = "Get all branches for a project")
    public ResponseEntity<ApiResponse<List<String>>> getBranches(
            @PathVariable Long projectId) {

        List<String> branches =
                versionService.getBranchesByProject(projectId);
        return ResponseEntity.ok(
                ApiResponse.success("Branches fetched.", branches));
    }

    // ── PUT /api/v1/versions/snapshots/{snapshotId}/tag ───────────────
    @PutMapping("/snapshots/{snapshotId}/tag")
    @Operation(summary = "Tag a snapshot with a release label")
    public ResponseEntity<ApiResponse<SnapshotResponse>> tagSnapshot(
            @PathVariable Long snapshotId,
            @Valid @RequestBody TagSnapshotRequest request) {

        SnapshotResponse response =
                versionService.tagSnapshot(snapshotId, request);
        return ResponseEntity.ok(
                ApiResponse.success("Snapshot tagged.", response));
    }
}