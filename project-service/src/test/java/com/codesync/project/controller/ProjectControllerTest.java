package com.codesync.project.controller;

import com.codesync.project.dto.request.AddMemberRequest;
import com.codesync.project.dto.request.CreateProjectRequest;
import com.codesync.project.dto.request.UpdateProjectRequest;
import com.codesync.project.dto.response.ProjectMemberResponse;
import com.codesync.project.dto.response.ProjectResponse;
import com.codesync.project.enums.MemberRole;
import com.codesync.project.enums.Visibility;
import com.codesync.project.exception.ProjectNotFoundException;
import com.codesync.project.exception.UnauthorizedAccessException;
import com.codesync.project.service.ProjectService;
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
        value = ProjectController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class
        }
)
@AutoConfigureMockMvc(addFilters = false)
class ProjectControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private ProjectService projectService;

    // ── Fixtures ──────────────────────────────────────────────────────

    private ProjectResponse buildProjectResponse(Long id, String name) {
        return ProjectResponse.builder()
                .projectId(id).ownerId(10L).name(name)
                .visibility(Visibility.PUBLIC).language("Java")
                .isArchived(false).starCount(0).forkCount(0)
                .memberCount(1).createdAt(LocalDateTime.now())
                .build();
    }

    private ProjectMemberResponse buildMemberResponse(Long userId, MemberRole role) {
        return ProjectMemberResponse.builder()
                .id(1L).projectId(1L).userId(userId)
                .role(role).joinedAt(LocalDateTime.now())
                .build();
    }

    // ════════════════════════════════════════════════════════════════
    // POST /api/v1/projects
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST / - 201 Created on successful project creation")
    void createProject_valid_returns201() throws Exception {
        CreateProjectRequest req = new CreateProjectRequest();
        req.setName("MyProject");
        req.setVisibility(Visibility.PUBLIC);
        req.setLanguage("Java");

        when(projectService.createProject(eq(10L), any())).thenReturn(
                buildProjectResponse(1L, "MyProject"));

        mockMvc.perform(post("/api/v1/projects")
                        .header("X-Auth-User-Id", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("MyProject"))
                .andExpect(jsonPath("$.data.projectId").value(1));
    }

    @Test
    @DisplayName("POST / - 400 Bad Request when name is missing")
    void createProject_missingName_returns400() throws Exception {
        CreateProjectRequest req = new CreateProjectRequest();
        req.setVisibility(Visibility.PUBLIC); // no name

        mockMvc.perform(post("/api/v1/projects")
                        .header("X-Auth-User-Id", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/projects/{projectId}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /{id} - 200 OK for existing public project")
    void getProjectById_existing_returns200() throws Exception {
        when(projectService.getProjectById(1L, 10L)).thenReturn(
                buildProjectResponse(1L, "Pub"));

        mockMvc.perform(get("/api/v1/projects/1")
                        .header("X-Auth-User-Id", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(1));
    }

    @Test
    @DisplayName("GET /{id} - 404 Not Found for missing project")
    void getProjectById_missing_returns404() throws Exception {
        when(projectService.getProjectById(99L, 10L))
                .thenThrow(new ProjectNotFoundException("Project not found with ID: 99"));

        mockMvc.perform(get("/api/v1/projects/99")
                        .header("X-Auth-User-Id", 10L))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /{id} - 403 Forbidden for private project non-member")
    void getProjectById_privateNonMember_returns403() throws Exception {
        when(projectService.getProjectById(1L, 99L))
                .thenThrow(new UnauthorizedAccessException("No access to private project."));

        mockMvc.perform(get("/api/v1/projects/1")
                        .header("X-Auth-User-Id", 99L))
                .andExpect(status().isForbidden());
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/projects/public
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /public - 200 OK returns all public projects")
    void getPublicProjects_returns200() throws Exception {
        when(projectService.getPublicProjects()).thenReturn(
                List.of(buildProjectResponse(1L, "Pub1"),
                        buildProjectResponse(2L, "Pub2")));

        mockMvc.perform(get("/api/v1/projects/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/projects/search
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /search - 200 OK with keyword returns matching projects")
    void searchProjects_keyword_returns200() throws Exception {
        when(projectService.searchProjects("java")).thenReturn(
                List.of(buildProjectResponse(1L, "JavaApp")));

        mockMvc.perform(get("/api/v1/projects/search").param("keyword", "java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("JavaApp"));
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/projects/owner/{ownerId}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /owner/{ownerId} - 200 OK returns owner's projects")
    void getByOwner_returns200() throws Exception {
        when(projectService.getProjectsByOwner(10L)).thenReturn(
                List.of(buildProjectResponse(1L, "P1")));

        mockMvc.perform(get("/api/v1/projects/owner/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/projects/member
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /member - 200 OK returns projects user is member of")
    void getByMember_returns200() throws Exception {
        when(projectService.getProjectsByMember(10L)).thenReturn(
                List.of(buildProjectResponse(1L, "P")));

        mockMvc.perform(get("/api/v1/projects/member")
                        .header("X-Auth-User-Id", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/projects/language/{language}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /language/{language} - 200 OK returns projects by language")
    void getByLanguage_returns200() throws Exception {
        when(projectService.getProjectsByLanguage("Java")).thenReturn(
                List.of(buildProjectResponse(1L, "JavaApp")));

        mockMvc.perform(get("/api/v1/projects/language/Java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("JavaApp"));
    }

    // ════════════════════════════════════════════════════════════════
    // PUT /api/v1/projects/{projectId}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PUT /{id} - 200 OK on successful update")
    void updateProject_owner_returns200() throws Exception {
        UpdateProjectRequest req = new UpdateProjectRequest();
        req.setName("Updated");

        when(projectService.updateProject(eq(1L), eq(10L), any()))
                .thenReturn(buildProjectResponse(1L, "Updated"));

        mockMvc.perform(put("/api/v1/projects/1")
                        .header("X-Auth-User-Id", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated"));
    }

    @Test
    @DisplayName("PUT /{id} - 403 Forbidden for non-owner")
    void updateProject_nonOwner_returns403() throws Exception {
        UpdateProjectRequest req = new UpdateProjectRequest();
        req.setName("x");

        when(projectService.updateProject(eq(1L), eq(99L), any()))
                .thenThrow(new UnauthorizedAccessException("Only the project owner can do this."));

        mockMvc.perform(put("/api/v1/projects/1")
                        .header("X-Auth-User-Id", 99L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // ════════════════════════════════════════════════════════════════
    // PUT /api/v1/projects/{projectId}/archive
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PUT /{id}/archive - 200 OK on successful archive")
    void archiveProject_owner_returns200() throws Exception {
        doNothing().when(projectService).archiveProject(1L, 10L);

        mockMvc.perform(put("/api/v1/projects/1/archive")
                        .header("X-Auth-User-Id", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PUT /{id}/archive - 403 Forbidden for non-owner")
    void archiveProject_nonOwner_returns403() throws Exception {
        doThrow(new UnauthorizedAccessException("Only owner can archive."))
                .when(projectService).archiveProject(1L, 99L);

        mockMvc.perform(put("/api/v1/projects/1/archive")
                        .header("X-Auth-User-Id", 99L))
                .andExpect(status().isForbidden());
    }

    // ════════════════════════════════════════════════════════════════
    // DELETE /api/v1/projects/{projectId}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("DELETE /{id} - 200 OK on successful deletion")
    void deleteProject_owner_returns200() throws Exception {
        doNothing().when(projectService).deleteProject(1L, 10L);

        mockMvc.perform(delete("/api/v1/projects/1")
                        .header("X-Auth-User-Id", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /{id} - 404 Not Found for missing project")
    void deleteProject_notFound_returns404() throws Exception {
        doThrow(new ProjectNotFoundException("Not found"))
                .when(projectService).deleteProject(99L, 10L);

        mockMvc.perform(delete("/api/v1/projects/99")
                        .header("X-Auth-User-Id", 10L))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════
    // POST /api/v1/projects/{projectId}/fork
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /{id}/fork - 201 Created on successful fork")
    void forkProject_public_returns201() throws Exception {
        ProjectResponse forked = buildProjectResponse(2L, "Original-fork");
        forked.setForkedFromId(1L);
        when(projectService.forkProject(1L, 20L)).thenReturn(forked);

        mockMvc.perform(post("/api/v1/projects/1/fork")
                        .header("X-Auth-User-Id", 20L))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.forkedFromId").value(1));
    }

    @Test
    @DisplayName("POST /{id}/fork - 403 Forbidden for private project")
    void forkProject_private_returns403() throws Exception {
        when(projectService.forkProject(1L, 20L))
                .thenThrow(new UnauthorizedAccessException("Cannot fork a private project."));

        mockMvc.perform(post("/api/v1/projects/1/fork")
                        .header("X-Auth-User-Id", 20L))
                .andExpect(status().isForbidden());
    }

    // ════════════════════════════════════════════════════════════════
    // POST /api/v1/projects/{projectId}/star
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /{id}/star - 200 OK on successful star")
    void starProject_returns200() throws Exception {
        doNothing().when(projectService).starProject(1L, 10L);

        mockMvc.perform(post("/api/v1/projects/1/star")
                        .header("X-Auth-User-Id", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Project starred."));
    }

    // ════════════════════════════════════════════════════════════════
    // DELETE /api/v1/projects/{projectId}/star
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("DELETE /{id}/star - 200 OK on successful unstar")
    void unstarProject_returns200() throws Exception {
        doNothing().when(projectService).unstarProject(1L, 10L);

        mockMvc.perform(delete("/api/v1/projects/1/star")
                        .header("X-Auth-User-Id", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Project unstarred."));
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/projects/{projectId}/members
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /{id}/members - 200 OK returns member list")
    void getMembers_returns200() throws Exception {
        when(projectService.getMembers(1L)).thenReturn(List.of(
                buildMemberResponse(10L, MemberRole.OWNER),
                buildMemberResponse(20L, MemberRole.EDITOR)));

        mockMvc.perform(get("/api/v1/projects/1/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    // ════════════════════════════════════════════════════════════════
    // POST /api/v1/projects/{projectId}/members
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /{id}/members - 201 Created on adding a new member")
    void addMember_owner_returns201() throws Exception {
        AddMemberRequest req = new AddMemberRequest();
        req.setUserId(20L);
        req.setRole(MemberRole.EDITOR);

        when(projectService.addMember(eq(1L), eq(10L), any()))
                .thenReturn(buildMemberResponse(20L, MemberRole.EDITOR));

        mockMvc.perform(post("/api/v1/projects/1/members")
                        .header("X-Auth-User-Id", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userId").value(20))
                .andExpect(jsonPath("$.data.role").value("EDITOR"));
    }

    // ════════════════════════════════════════════════════════════════
    // DELETE /api/v1/projects/{projectId}/members/{userId}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("DELETE /{id}/members/{userId} - 200 OK on successful removal")
    void removeMember_owner_returns200() throws Exception {
        doNothing().when(projectService).removeMember(1L, 10L, 20L);

        mockMvc.perform(delete("/api/v1/projects/1/members/20")
                        .header("X-Auth-User-Id", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Member removed."));
    }

    @Test
    @DisplayName("DELETE /{id}/members/{userId} - 403 when trying to remove owner")
    void removeMember_removingOwner_returns403() throws Exception {
        doThrow(new UnauthorizedAccessException("Cannot remove the project owner."))
                .when(projectService).removeMember(1L, 10L, 10L);

        mockMvc.perform(delete("/api/v1/projects/1/members/10")
                        .header("X-Auth-User-Id", 10L))
                .andExpect(status().isForbidden());
    }
}