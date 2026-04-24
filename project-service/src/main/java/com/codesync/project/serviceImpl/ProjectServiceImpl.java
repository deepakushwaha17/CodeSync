package com.codesync.project.serviceImpl;

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
import com.codesync.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;

    // ─────────────────────────────────────────────────────────────────
    // CORE CRUD
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ProjectResponse createProject(Long ownerId,
                                         CreateProjectRequest request) {
        log.info("Creating project '{}' for owner ID: {}",
                request.getName(), ownerId);

        Project project = Project.builder()
                .ownerId(ownerId)
                .name(request.getName())
                .description(request.getDescription())
                .language(request.getLanguage())
                .visibility(request.getVisibility())
                .templateId(request.getTemplateId())
                .isArchived(false)
                .starCount(0)
                .forkCount(0)
                .build();

        Project saved = projectRepository.save(project);

        // Auto-add owner as OWNER member
        ProjectMember ownerMember = ProjectMember.builder()
                .project(saved)
                .userId(ownerId)
                .role(MemberRole.OWNER)
                .build();
        memberRepository.save(ownerMember);

        log.info("Project created with ID: {}", saved.getProjectId());
        return mapToResponse(saved);
    }

    @Override
    public ProjectResponse getProjectById(Long projectId,
                                          Long requestingUserId) {
        Project project = findProjectOrThrow(projectId);

        // Private project — only members can view
        if (project.getVisibility() == Visibility.PRIVATE) {
            if (requestingUserId == null ||
                    !memberRepository.existsByProject_ProjectIdAndUserId(
                            projectId, requestingUserId)) {
                throw new UnauthorizedAccessException(
                        "You do not have access to this private project.");
            }
        }

        return mapToResponse(project);
    }

    @Override
    @Transactional
    public ProjectResponse updateProject(Long projectId, Long ownerId,
                                         UpdateProjectRequest request) {
        Project project = findProjectOrThrow(projectId);
        verifyOwner(project, ownerId);

        if (request.getName() != null)
            project.setName(request.getName());
        if (request.getDescription() != null)
            project.setDescription(request.getDescription());
        if (request.getLanguage() != null)
            project.setLanguage(request.getLanguage());
        if (request.getVisibility() != null)
            project.setVisibility(request.getVisibility());

        Project updated = projectRepository.save(project);
        log.info("Project ID {} updated.", projectId);
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public void archiveProject(Long projectId, Long ownerId) {
        Project project = findProjectOrThrow(projectId);
        verifyOwner(project, ownerId);
        project.setIsArchived(true);
        projectRepository.save(project);
        log.info("Project ID {} archived.", projectId);
    }

    @Override
    @Transactional
    public void deleteProject(Long projectId, Long ownerId) {
        Project project = findProjectOrThrow(projectId);
        verifyOwner(project, ownerId);
        memberRepository.deleteAll(
                memberRepository.findByProject_ProjectId(projectId));
        projectRepository.delete(project);
        log.info("Project ID {} deleted.", projectId);
    }

    // ─────────────────────────────────────────────────────────────────
    // QUERIES
    // ─────────────────────────────────────────────────────────────────

    @Override
    public List<ProjectResponse> getProjectsByOwner(Long ownerId) {
        return projectRepository.findByOwnerId(ownerId)
                .stream().map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProjectResponse> getPublicProjects() {
        return projectRepository.findByVisibility(Visibility.PUBLIC)
                .stream()
                .filter(p -> !p.getIsArchived())
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProjectResponse> searchProjects(String keyword) {
        return projectRepository.searchPublicByName(keyword)
                .stream().map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProjectResponse> getProjectsByMember(Long userId) {
        List<Long> projectIds =
                memberRepository.findProjectIdsByUserId(userId);
        return projectIds.stream()
                .map(this::findProjectOrThrow)
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProjectResponse> getProjectsByLanguage(String language) {
        return projectRepository
                .findByVisibilityAndLanguage(Visibility.PUBLIC, language)
                .stream().map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────
    // FORK AND STAR
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ProjectResponse forkProject(Long projectId,
                                       Long requestingUserId) {
        Project original = findProjectOrThrow(projectId);

        if (original.getVisibility() == Visibility.PRIVATE) {
            throw new UnauthorizedAccessException(
                    "Cannot fork a private project.");
        }

        // Create a new project as a copy
        Project forked = Project.builder()
                .ownerId(requestingUserId)
                .name(original.getName() + "-fork")
                .description("Forked from: " + original.getName())
                .language(original.getLanguage())
                .visibility(Visibility.PRIVATE)
                .forkedFromId(original.getProjectId())
                .isArchived(false)
                .starCount(0)
                .forkCount(0)
                .build();

        Project savedFork = projectRepository.save(forked);

        // Add forking user as OWNER of the fork
        ProjectMember ownerMember = ProjectMember.builder()
                .project(savedFork)
                .userId(requestingUserId)
                .role(MemberRole.OWNER)
                .build();
        memberRepository.save(ownerMember);

        // Increment forkCount on original
        original.setForkCount(original.getForkCount() + 1);
        projectRepository.save(original);

        log.info("Project {} forked by user {} → new project {}",
                projectId, requestingUserId, savedFork.getProjectId());

        return mapToResponse(savedFork);
    }

    @Override
    @Transactional
    public void starProject(Long projectId, Long userId) {
        Project project = findProjectOrThrow(projectId);
        project.setStarCount(project.getStarCount() + 1);
        projectRepository.save(project);
        log.info("User {} starred project {}", userId, projectId);
    }

    @Override
    @Transactional
    public void unstarProject(Long projectId, Long userId) {
        Project project = findProjectOrThrow(projectId);
        int current = project.getStarCount();
        project.setStarCount(Math.max(0, current - 1));
        projectRepository.save(project);
        log.info("User {} unstarred project {}", userId, projectId);
    }

    // ─────────────────────────────────────────────────────────────────
    // MEMBER MANAGEMENT
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ProjectMemberResponse addMember(Long projectId, Long ownerId,
                                           AddMemberRequest request) {
        Project project = findProjectOrThrow(projectId);
        verifyOwner(project, ownerId);

        // Check if user is already a member
        if (memberRepository.existsByProject_ProjectIdAndUserId(
                projectId, request.getUserId())) {
            throw new IllegalStateException(
                    "User is already a member of this project.");
        }

        ProjectMember member = ProjectMember.builder()
                .project(project)
                .userId(request.getUserId())
                .role(request.getRole())
                .build();

        ProjectMember saved = memberRepository.save(member);
        log.info("User {} added to project {} as {}",
                request.getUserId(), projectId, request.getRole());

        return mapToMemberResponse(saved);
    }

    @Override
    @Transactional
    public void removeMember(Long projectId, Long ownerId, Long userId) {
        Project project = findProjectOrThrow(projectId);
        verifyOwner(project, ownerId);

        // Cannot remove the owner
        if (userId.equals(ownerId)) {
            throw new UnauthorizedAccessException(
                    "Cannot remove the project owner.");
        }

        memberRepository.deleteByProject_ProjectIdAndUserId(
                projectId, userId);
        log.info("User {} removed from project {}", userId, projectId);
    }

    @Override
    public List<ProjectMemberResponse> getMembers(Long projectId) {
        findProjectOrThrow(projectId); // verify project exists
        return memberRepository.findByProject_ProjectId(projectId)
                .stream()
                .map(this::mapToMemberResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────

    private Project findProjectOrThrow(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(
                        "Project not found with ID: " + projectId));
    }

    private void verifyOwner(Project project, Long userId) {
        if (!project.getOwnerId().equals(userId)) {
            throw new UnauthorizedAccessException(
                    "Only the project owner can perform this action.");
        }
    }

    private ProjectResponse mapToResponse(Project p) {
        int memberCount = memberRepository
                .countByProject_ProjectId(p.getProjectId());
        return ProjectResponse.builder()
                .projectId(p.getProjectId())
                .ownerId(p.getOwnerId())
                .name(p.getName())
                .description(p.getDescription())
                .language(p.getLanguage())
                .visibility(p.getVisibility())
                .templateId(p.getTemplateId())
                .isArchived(p.getIsArchived())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .starCount(p.getStarCount())
                .forkCount(p.getForkCount())
                .forkedFromId(p.getForkedFromId())
                .memberCount(memberCount)
                .build();
    }

    private ProjectMemberResponse mapToMemberResponse(ProjectMember m) {
        return ProjectMemberResponse.builder()
                .id(m.getId())
                .projectId(m.getProject().getProjectId())
                .userId(m.getUserId())
                .role(m.getRole())
                .joinedAt(m.getJoinedAt())
                .build();
    }
}