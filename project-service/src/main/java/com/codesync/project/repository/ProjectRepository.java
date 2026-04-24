package com.codesync.project.repository;

import com.codesync.project.entity.Project;
import com.codesync.project.enums.Visibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByOwnerId(Long ownerId);

    List<Project> findByVisibility(Visibility visibility);

    List<Project> findByLanguage(String language);

    List<Project> findByIsArchived(Boolean isArchived);

    List<Project> findByOwnerIdAndIsArchived(Long ownerId, Boolean isArchived);

    // Search projects by name keyword (case-insensitive)
    @Query("SELECT p FROM Project p WHERE LOWER(p.name) " +
            "LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Project> searchByName(@Param("keyword") String keyword);

    // Search public projects by name
    @Query("SELECT p FROM Project p WHERE p.visibility = 'PUBLIC' " +
            "AND LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Project> searchPublicByName(@Param("keyword") String keyword);

    // Get public projects by language
    List<Project> findByVisibilityAndLanguage(Visibility visibility, String language);

    // Count projects by owner
    int countByOwnerId(Long ownerId);

    // Find projects forked from a specific project
    List<Project> findByForkedFromId(Long forkedFromId);
}