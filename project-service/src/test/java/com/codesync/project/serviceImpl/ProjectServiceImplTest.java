package com.codesync.project.serviceImpl;

import com.codesync.project.client.NotificationClient;
import com.codesync.project.dto.request.AddMemberRequest;
import com.codesync.project.dto.request.CreateProjectRequest;
import com.codesync.project.dto.request.UpdateProjectRequest;
import com.codesync.project.dto.response.ProjectMemberResponse;
import com.codesync.project.dto.response.ProjectResponse;
import com.codesync.project.entity.Project;
import com.codesync.project.entity.ProjectMember;
import com.codesync.project.enums.MemberRole;
import com.codesync.project.enums.Visibility;
import com.codesync.project.exception.ProjectNotFoundException;
import com.codesync.project.exception.UnauthorizedAccessException;
import com.codesync.project.repository.ProjectMemberRepository;
import com.codesync.project.repository.ProjectRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceImplTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository memberRepository;
    @Mock private NotificationClient notificationClient;

    @InjectMocks private ProjectServiceImpl projectService;

    // ── Fixtures ──────────────────────────────────────────────────────

    private Project buildProject(Long id, Long ownerId, String name, Visibility v) {
        return Project.builder()
                .projectId(id)
                .ownerId(ownerId)
                .name(name)
                .description("desc")
                .language("Java")
                .visibility(v)
                .isArchived(false)
                .starCount(0).forkCount(0)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private ProjectMember buildMember(Long id, Project project,
                                      Long userId, MemberRole role) {
        return ProjectMember.builder()
                .id(id).project(project)
                .userId(userId).role(role)
                .joinedAt(LocalDateTime.now())
                .build();
    }

    private void stubMemberCount(Long projectId, int count) {
        when(memberRepository.countByProject_ProjectId(projectId)).thenReturn(count);
    }

    // ════════════════════════════════════════════════════════════════
    // createProject()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("createProject - saves project and auto-adds owner as OWNER member")
    void createProject_validRequest_savesProjectAndOwnerMember() {
        CreateProjectRequest req = new CreateProjectRequest();
        req.setName("NewProject");
        req.setDescription("desc");
        req.setLanguage("Java");
        req.setVisibility(Visibility.PUBLIC);

        Project saved = buildProject(1L, 10L, "NewProject", Visibility.PUBLIC);
        when(projectRepository.save(any(Project.class))).thenReturn(saved);
        when(memberRepository.save(any(ProjectMember.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        stubMemberCount(1L, 1);

        ProjectResponse response = projectService.createProject(10L, req);

        assertThat(response.getName()).isEqualTo("NewProject");
        assertThat(response.getOwnerId()).isEqualTo(10L);

        // Owner member must be saved
        verify(memberRepository).save(argThat(m ->
                m.getUserId().equals(10L) && m.getRole() == MemberRole.OWNER));
    }

    @Test
    @DisplayName("createProject - sets isArchived=false, starCount=0, forkCount=0 by default")
    void createProject_defaults_areSetCorrectly() {
        CreateProjectRequest req = new CreateProjectRequest();
        req.setName("P"); req.setVisibility(Visibility.PRIVATE);

        Project saved = buildProject(1L, 5L, "P", Visibility.PRIVATE);
        when(projectRepository.save(any())).thenReturn(saved);
        when(memberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubMemberCount(1L, 1);

        ProjectResponse response = projectService.createProject(5L, req);

        assertThat(response.getIsArchived()).isFalse();
        assertThat(response.getStarCount()).isZero();
        assertThat(response.getForkCount()).isZero();
    }

    // ════════════════════════════════════════════════════════════════
    // getProjectById()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getProjectById - PUBLIC project accessible by any user")
    void getProjectById_public_returnableByAnyone() {
        Project project = buildProject(1L, 10L, "Pub", Visibility.PUBLIC);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        stubMemberCount(1L, 1);

        ProjectResponse response = projectService.getProjectById(1L, 99L);

        assertThat(response.getProjectId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getProjectById - PRIVATE project accessible by member")
    void getProjectById_private_accessibleByMember() {
        Project project = buildProject(1L, 10L, "Priv", Visibility.PRIVATE);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(memberRepository.existsByProject_ProjectIdAndUserId(1L, 10L)).thenReturn(true);
        stubMemberCount(1L, 1);

        ProjectResponse response = projectService.getProjectById(1L, 10L);

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("getProjectById - PRIVATE project throws UnauthorizedAccessException for non-member")
    void getProjectById_private_nonMember_throwsException() {
        Project project = buildProject(1L, 10L, "Priv", Visibility.PRIVATE);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(memberRepository.existsByProject_ProjectIdAndUserId(1L, 99L)).thenReturn(false);

        assertThatThrownBy(() -> projectService.getProjectById(1L, 99L))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    @DisplayName("getProjectById - throws ProjectNotFoundException for unknown project")
    void getProjectById_notFound_throwsException() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getProjectById(99L, 1L))
                .isInstanceOf(ProjectNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ════════════════════════════════════════════════════════════════
    // updateProject()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("updateProject - owner updates name and language successfully")
    void updateProject_owner_updatesFields() {
        Project project = buildProject(1L, 10L, "OldName", Visibility.PUBLIC);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubMemberCount(1L, 1);

        UpdateProjectRequest req = new UpdateProjectRequest();
        req.setName("NewName");
        req.setLanguage("Go");

        ProjectResponse response = projectService.updateProject(1L, 10L, req);

        assertThat(response.getName()).isEqualTo("NewName");
        assertThat(response.getLanguage()).isEqualTo("Go");
    }

    @Test
    @DisplayName("updateProject - throws UnauthorizedAccessException for non-owner")
    void updateProject_nonOwner_throwsException() {
        Project project = buildProject(1L, 10L, "P", Visibility.PUBLIC);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> projectService.updateProject(1L, 99L, new UpdateProjectRequest()))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    @DisplayName("updateProject - throws ProjectNotFoundException when project missing")
    void updateProject_notFound_throwsException() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.updateProject(99L, 1L, new UpdateProjectRequest()))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    // ════════════════════════════════════════════════════════════════
    // archiveProject()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("archiveProject - sets isArchived=true for owner")
    void archiveProject_owner_setsArchived() {
        Project project = buildProject(1L, 10L, "P", Visibility.PUBLIC);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        projectService.archiveProject(1L, 10L);

        verify(projectRepository).save(argThat(p -> p.getIsArchived()));
    }

    @Test
    @DisplayName("archiveProject - throws UnauthorizedAccessException for non-owner")
    void archiveProject_nonOwner_throwsException() {
        Project project = buildProject(1L, 10L, "P", Visibility.PUBLIC);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> projectService.archiveProject(1L, 99L))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    // ════════════════════════════════════════════════════════════════
    // deleteProject()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("deleteProject - owner deletes project and all its members")
    void deleteProject_owner_deletesProjectAndMembers() {
        Project project = buildProject(1L, 10L, "P", Visibility.PUBLIC);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(memberRepository.findByProject_ProjectId(1L)).thenReturn(List.of());

        projectService.deleteProject(1L, 10L);

        verify(memberRepository).deleteAll(any());
        verify(projectRepository).delete(project);
    }

    @Test
    @DisplayName("deleteProject - throws UnauthorizedAccessException for non-owner")
    void deleteProject_nonOwner_throwsException() {
        Project project = buildProject(1L, 10L, "P", Visibility.PUBLIC);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> projectService.deleteProject(1L, 99L))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    // ════════════════════════════════════════════════════════════════
    // getPublicProjects()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getPublicProjects - returns only non-archived public projects")
    void getPublicProjects_filtersArchived() {
        Project active   = buildProject(1L, 1L, "Active",   Visibility.PUBLIC);
        Project archived = buildProject(2L, 1L, "Archived", Visibility.PUBLIC);
        archived.setIsArchived(true);

        when(projectRepository.findByVisibility(Visibility.PUBLIC))
                .thenReturn(List.of(active, archived));
        when(memberRepository.countByProject_ProjectId(1L)).thenReturn(1);

        List<ProjectResponse> results = projectService.getPublicProjects();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Active");
    }

    // ════════════════════════════════════════════════════════════════
    // searchProjects()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("searchProjects - returns public projects matching keyword")
    void searchProjects_keyword_returnsList() {
        Project p = buildProject(1L, 1L, "JavaApp", Visibility.PUBLIC);
        when(projectRepository.searchPublicByName("java")).thenReturn(List.of(p));
        stubMemberCount(1L, 1);

        List<ProjectResponse> results = projectService.searchProjects("java");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("JavaApp");
    }

    @Test
    @DisplayName("searchProjects - returns empty list when no match")
    void searchProjects_noMatch_returnsEmpty() {
        when(projectRepository.searchPublicByName("xyz")).thenReturn(List.of());

        assertThat(projectService.searchProjects("xyz")).isEmpty();
    }

    // ════════════════════════════════════════════════════════════════
    // getProjectsByMember()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getProjectsByMember - returns all projects where user is member")
    void getProjectsByMember_existing_returnsProjects() {
        Project p = buildProject(1L, 5L, "P", Visibility.PUBLIC);
        when(memberRepository.findProjectIdsByUserId(10L)).thenReturn(List.of(1L));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(p));
        stubMemberCount(1L, 1);

        List<ProjectResponse> result = projectService.getProjectsByMember(10L);

        assertThat(result).hasSize(1);
    }

    // ════════════════════════════════════════════════════════════════
    // forkProject()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("forkProject - creates fork, increments forkCount, saves owner member")
    void forkProject_publicProject_createsSuccessfully() {
        Project original = buildProject(1L, 10L, "Original", Visibility.PUBLIC);
        original.setForkCount(0);

        Project fork = buildProject(2L, 20L, "Original-fork", Visibility.PRIVATE);
        fork.setForkedFromId(1L);

        when(projectRepository.findById(1L)).thenReturn(Optional.of(original));
        when(projectRepository.save(any(Project.class)))
                .thenReturn(fork)   // first call: save fork
                .thenReturn(original); // second call: update forkCount
        when(memberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubMemberCount(2L, 1);

        ProjectResponse response = projectService.forkProject(1L, 20L);

        assertThat(response.getForkedFromId()).isEqualTo(1L);
        verify(projectRepository, times(2)).save(any()); // fork + forkCount update
        verify(memberRepository).save(argThat(m ->
                m.getUserId().equals(20L) && m.getRole() == MemberRole.OWNER));
    }

    @Test
    @DisplayName("forkProject - throws UnauthorizedAccessException for private project")
    void forkProject_privateProject_throwsException() {
        Project project = buildProject(1L, 10L, "Private", Visibility.PRIVATE);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> projectService.forkProject(1L, 20L))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("Cannot fork a private project");
    }

    // ════════════════════════════════════════════════════════════════
    // starProject() / unstarProject()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("starProject - increments starCount by 1")
    void starProject_existing_incrementsStarCount() {
        Project project = buildProject(1L, 10L, "P", Visibility.PUBLIC);
        project.setStarCount(5);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        projectService.starProject(1L, 99L);

        verify(projectRepository).save(argThat(p -> p.getStarCount() == 6));
    }

    @Test
    @DisplayName("unstarProject - decrements starCount by 1")
    void unstarProject_existing_decrementsStarCount() {
        Project project = buildProject(1L, 10L, "P", Visibility.PUBLIC);
        project.setStarCount(3);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        projectService.unstarProject(1L, 99L);

        verify(projectRepository).save(argThat(p -> p.getStarCount() == 2));
    }

    @Test
    @DisplayName("unstarProject - does not go below 0 (floors at 0)")
    void unstarProject_atZero_staysAtZero() {
        Project project = buildProject(1L, 10L, "P", Visibility.PUBLIC);
        project.setStarCount(0);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        projectService.unstarProject(1L, 99L);

        verify(projectRepository).save(argThat(p -> p.getStarCount() == 0));
    }

    // ════════════════════════════════════════════════════════════════
    // addMember()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("addMember - owner adds new member successfully")
    void addMember_owner_addsNewMember() {
        Project project = buildProject(1L, 10L, "P", Visibility.PUBLIC);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(memberRepository.existsByProject_ProjectIdAndUserId(1L, 20L)).thenReturn(false);

        ProjectMember saved = buildMember(1L, project, 20L, MemberRole.EDITOR);
        when(memberRepository.save(any())).thenReturn(saved);

        AddMemberRequest req = new AddMemberRequest();
        req.setUserId(20L);
        req.setRole(MemberRole.EDITOR);

        ProjectMemberResponse response = projectService.addMember(1L, 10L, req);

        assertThat(response.getUserId()).isEqualTo(20L);
        assertThat(response.getRole()).isEqualTo(MemberRole.EDITOR);
    }

    @Test
    @DisplayName("addMember - throws IllegalStateException when user already a member")
    void addMember_alreadyMember_throwsException() {
        Project project = buildProject(1L, 10L, "P", Visibility.PUBLIC);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(memberRepository.existsByProject_ProjectIdAndUserId(1L, 20L)).thenReturn(true);

        AddMemberRequest req = new AddMemberRequest();
        req.setUserId(20L);
        req.setRole(MemberRole.VIEWER);

        assertThatThrownBy(() -> projectService.addMember(1L, 10L, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already a member");
    }

    @Test
    @DisplayName("addMember - throws UnauthorizedAccessException for non-owner")
    void addMember_nonOwner_throwsException() {
        Project project = buildProject(1L, 10L, "P", Visibility.PUBLIC);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> projectService.addMember(1L, 99L, new AddMemberRequest()))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    // ════════════════════════════════════════════════════════════════
    // removeMember()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("removeMember - owner removes a non-owner member")
    void removeMember_owner_removesMember() {
        Project project = buildProject(1L, 10L, "P", Visibility.PUBLIC);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        projectService.removeMember(1L, 10L, 20L);

        verify(memberRepository).deleteByProject_ProjectIdAndUserId(1L, 20L);
    }

    @Test
    @DisplayName("removeMember - throws UnauthorizedAccessException when removing owner")
    void removeMember_removingOwner_throwsException() {
        Project project = buildProject(1L, 10L, "P", Visibility.PUBLIC);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> projectService.removeMember(1L, 10L, 10L))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("Cannot remove the project owner");
    }

    @Test
    @DisplayName("removeMember - throws UnauthorizedAccessException for non-owner caller")
    void removeMember_nonOwnerCaller_throwsException() {
        Project project = buildProject(1L, 10L, "P", Visibility.PUBLIC);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> projectService.removeMember(1L, 99L, 20L))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    // ════════════════════════════════════════════════════════════════
    // getMembers()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getMembers - returns all members for a valid project")
    void getMembers_validProject_returnsList() {
        Project project = buildProject(1L, 10L, "P", Visibility.PUBLIC);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(memberRepository.findByProject_ProjectId(1L)).thenReturn(List.of(
                buildMember(1L, project, 10L, MemberRole.OWNER),
                buildMember(2L, project, 20L, MemberRole.EDITOR)
        ));

        List<ProjectMemberResponse> members = projectService.getMembers(1L);

        assertThat(members).hasSize(2);
    }

    @Test
    @DisplayName("getMembers - throws ProjectNotFoundException for unknown project")
    void getMembers_unknownProject_throwsException() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getMembers(99L))
                .isInstanceOf(ProjectNotFoundException.class);
    }
}