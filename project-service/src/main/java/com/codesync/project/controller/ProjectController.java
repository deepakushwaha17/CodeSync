package com.codesync.project.controller;

import com.codesync.project.dto.request.AddMemberRequest;
import com.codesync.project.dto.request.CreateProjectRequest;
import com.codesync.project.dto.request.UpdateProjectRequest;
import com.codesync.project.dto.response.ApiResponse;
import com.codesync.project.dto.response.ProjectMemberResponse;
import com.codesync.project.dto.response.ProjectResponse;
import com.codesync.project.service.ProjectService;
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
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Project Service", description = "Project Management APIs")
public class ProjectController {

    private final ProjectService projectService;

    // ── POST /api/v1/projects ─────────────────────────────────────────
    @PostMapping
    @Operation(summary = "Create a new project")
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @RequestHeader("X-Auth-User-Id") Long ownerId,
            @Valid @RequestBody CreateProjectRequest request) {

        ProjectResponse response = projectService.createProject(
                ownerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Project created successfully.", response));
    }

    // ── GET /api/v1/projects/{projectId} ──────────────────────────────
    @GetMapping("/{projectId}")
    @Operation(summary = "Get project by ID")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProjectById(
            @PathVariable Long projectId,
            @RequestHeader(value = "X-Auth-User-Id",
                    required = false) Long requestingUserId) {

        ProjectResponse response = projectService.getProjectById(
                projectId, requestingUserId);
        return ResponseEntity.ok(
                ApiResponse.success("Project fetched.", response));
    }

    // ── GET /api/v1/projects/public ───────────────────────────────────
    @GetMapping("/public")
    @Operation(summary = "Get all public projects (guest accessible)")
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> getPublicProjects() {

        List<ProjectResponse> projects = projectService.getPublicProjects();
        return ResponseEntity.ok(
                ApiResponse.success("Public projects fetched.", projects));
    }

    // ── GET /api/v1/projects/search?keyword= ──────────────────────────
    @GetMapping("/search")
    @Operation(summary = "Search public projects by name")
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> searchProjects(
            @RequestParam String keyword) {

        List<ProjectResponse> projects =
                projectService.searchProjects(keyword);
        return ResponseEntity.ok(
                ApiResponse.success("Search results.", projects));
    }

    // ── GET /api/v1/projects/owner/{ownerId} ──────────────────────────
    @GetMapping("/owner/{ownerId}")
    @Operation(summary = "Get all projects by owner")
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> getByOwner(
            @PathVariable Long ownerId) {

        List<ProjectResponse> projects =
                projectService.getProjectsByOwner(ownerId);
        return ResponseEntity.ok(
                ApiResponse.success("Owner projects fetched.", projects));
    }

    // ── GET /api/v1/projects/member ───────────────────────────────────
    @GetMapping("/member")
    @Operation(summary = "Get all projects where requesting user is a member")
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> getByMember(
            @RequestHeader("X-Auth-User-Id") Long userId) {

        List<ProjectResponse> projects =
                projectService.getProjectsByMember(userId);
        return ResponseEntity.ok(
                ApiResponse.success("Member projects fetched.", projects));
    }

    // ── GET /api/v1/projects/language/{language} ──────────────────────
    @GetMapping("/language/{language}")
    @Operation(summary = "Get public projects by language")
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> getByLanguage(
            @PathVariable String language) {

        List<ProjectResponse> projects =
                projectService.getProjectsByLanguage(language);
        return ResponseEntity.ok(
                ApiResponse.success("Language projects fetched.", projects));
    }

    // ── PUT /api/v1/projects/{projectId} ──────────────────────────────
    @PutMapping("/{projectId}")
    @Operation(summary = "Update a project")
    public ResponseEntity<ApiResponse<ProjectResponse>> updateProject(
            @PathVariable Long projectId,
            @RequestHeader("X-Auth-User-Id") Long ownerId,
            @Valid @RequestBody UpdateProjectRequest request) {

        ProjectResponse response = projectService.updateProject(
                projectId, ownerId, request);
        return ResponseEntity.ok(
                ApiResponse.success("Project updated.", response));
    }

    // ── PUT /api/v1/projects/{projectId}/archive ──────────────────────
    @PutMapping("/{projectId}/archive")
    @Operation(summary = "Archive a project")
    public ResponseEntity<ApiResponse<Void>> archiveProject(
            @PathVariable Long projectId,
            @RequestHeader("X-Auth-User-Id") Long ownerId) {

        projectService.archiveProject(projectId, ownerId);
        return ResponseEntity.ok(
                ApiResponse.success("Project archived."));
    }

    // ── DELETE /api/v1/projects/{projectId} ───────────────────────────
    @DeleteMapping("/{projectId}")
    @Operation(summary = "Delete a project")
    public ResponseEntity<ApiResponse<Void>> deleteProject(
            @PathVariable Long projectId,
            @RequestHeader("X-Auth-User-Id") Long ownerId) {

        projectService.deleteProject(projectId, ownerId);
        return ResponseEntity.ok(
                ApiResponse.success("Project deleted."));
    }

    // ── POST /api/v1/projects/{projectId}/fork ────────────────────────
    @PostMapping("/{projectId}/fork")
    @Operation(summary = "Fork a public project")
    public ResponseEntity<ApiResponse<ProjectResponse>> forkProject(
            @PathVariable Long projectId,
            @RequestHeader("X-Auth-User-Id") Long userId) {

        ProjectResponse response =
                projectService.forkProject(projectId, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Project forked.", response));
    }

    // ── POST /api/v1/projects/{projectId}/star ────────────────────────
    @PostMapping("/{projectId}/star")
    @Operation(summary = "Star a project")
    public ResponseEntity<ApiResponse<Void>> starProject(
            @PathVariable Long projectId,
            @RequestHeader("X-Auth-User-Id") Long userId) {

        projectService.starProject(projectId, userId);
        return ResponseEntity.ok(ApiResponse.success("Project starred."));
    }

    // ── DELETE /api/v1/projects/{projectId}/star ──────────────────────
    @DeleteMapping("/{projectId}/star")
    @Operation(summary = "Unstar a project")
    public ResponseEntity<ApiResponse<Void>> unstarProject(
            @PathVariable Long projectId,
            @RequestHeader("X-Auth-User-Id") Long userId) {

        projectService.unstarProject(projectId, userId);
        return ResponseEntity.ok(ApiResponse.success("Project unstarred."));
    }

    // ── GET /api/v1/projects/{projectId}/members ──────────────────────
    @GetMapping("/{projectId}/members")
    @Operation(summary = "Get all members of a project")
    public ResponseEntity<ApiResponse<List<ProjectMemberResponse>>> getMembers(
            @PathVariable Long projectId) {

        List<ProjectMemberResponse> members =
                projectService.getMembers(projectId);
        return ResponseEntity.ok(
                ApiResponse.success("Members fetched.", members));
    }

    // ── POST /api/v1/projects/{projectId}/members ─────────────────────
    @PostMapping("/{projectId}/members")
    @Operation(summary = "Add a member to a project")
    public ResponseEntity<ApiResponse<ProjectMemberResponse>> addMember(
            @PathVariable Long projectId,
            @RequestHeader("X-Auth-User-Id") Long ownerId,
            @Valid @RequestBody AddMemberRequest request) {

        ProjectMemberResponse response =
                projectService.addMember(projectId, ownerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Member added.", response));
    }

    // ── DELETE /api/v1/projects/{projectId}/members/{userId} ──────────
    @DeleteMapping("/{projectId}/members/{userId}")
    @Operation(summary = "Remove a member from a project")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable Long projectId,
            @PathVariable Long userId,
            @RequestHeader("X-Auth-User-Id") Long ownerId) {

        projectService.removeMember(projectId, ownerId, userId);
        return ResponseEntity.ok(ApiResponse.success("Member removed."));
    }
}