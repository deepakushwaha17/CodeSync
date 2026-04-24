package com.codesync.project.service;

import com.codesync.project.dto.request.AddMemberRequest;
import com.codesync.project.dto.request.CreateProjectRequest;
import com.codesync.project.dto.request.UpdateProjectRequest;
import com.codesync.project.dto.response.ProjectMemberResponse;
import com.codesync.project.dto.response.ProjectResponse;

import java.util.List;

public interface ProjectService {

    // Core CRUD
    ProjectResponse createProject(Long ownerId, CreateProjectRequest request);
    ProjectResponse getProjectById(Long projectId, Long requestingUserId);
    ProjectResponse updateProject(Long projectId, Long ownerId,
                                  UpdateProjectRequest request);
    void archiveProject(Long projectId, Long ownerId);
    void deleteProject(Long projectId, Long ownerId);

    // Queries
    List<ProjectResponse> getProjectsByOwner(Long ownerId);
    List<ProjectResponse> getPublicProjects();
    List<ProjectResponse> searchProjects(String keyword);
    List<ProjectResponse> getProjectsByMember(Long userId);
    List<ProjectResponse> getProjectsByLanguage(String language);

    // Fork and Star
    ProjectResponse forkProject(Long projectId, Long requestingUserId);
    void starProject(Long projectId, Long userId);
    void unstarProject(Long projectId, Long userId);

    // Member management
    ProjectMemberResponse addMember(Long projectId, Long ownerId,
                                    AddMemberRequest request);
    void removeMember(Long projectId, Long ownerId, Long userId);
    List<ProjectMemberResponse> getMembers(Long projectId);
}