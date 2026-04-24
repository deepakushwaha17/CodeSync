package com.codesync.project.repository;

import com.codesync.project.entity.ProjectMember;
import com.codesync.project.enums.MemberRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    List<ProjectMember> findByProject_ProjectId(Long projectId);

    List<ProjectMember> findByUserId(Long userId);

    Optional<ProjectMember> findByProject_ProjectIdAndUserId(
            Long projectId, Long userId);

    boolean existsByProject_ProjectIdAndUserId(Long projectId, Long userId);

    void deleteByProject_ProjectIdAndUserId(Long projectId, Long userId);

    int countByProject_ProjectId(Long projectId);

    // Get all project IDs where a user is a member
    @Query("SELECT pm.project.projectId FROM ProjectMember pm " +
            "WHERE pm.userId = :userId")
    List<Long> findProjectIdsByUserId(@Param("userId") Long userId);

    List<ProjectMember> findByProject_ProjectIdAndRole(
            Long projectId, MemberRole role);
}