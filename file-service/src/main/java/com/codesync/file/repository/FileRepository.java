package com.codesync.file.repository;

import com.codesync.file.entity.CodeFile;
import com.codesync.file.enums.FileType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileRepository extends JpaRepository<CodeFile, Long> {

    // All non-deleted files in a project
    List<CodeFile> findByProjectIdAndIsDeleted(
            Long projectId, Boolean isDeleted);

    // Find by exact path in a project
    Optional<CodeFile> findByProjectIdAndPath(
            Long projectId, String path);

    // All files (not folders) by project
    List<CodeFile> findByProjectIdAndFileTypeAndIsDeleted(
            Long projectId, FileType fileType, Boolean isDeleted);

    // Files by language
    List<CodeFile> findByProjectIdAndLanguageAndIsDeleted(
            Long projectId, String language, Boolean isDeleted);

    // Files edited by a specific user
    List<CodeFile> findByLastEditedBy(Long userId);

    // Count files in project
    int countByProjectIdAndIsDeleted(
            Long projectId, Boolean isDeleted);

    // All deleted files (for restore)
    List<CodeFile> findByProjectIdAndIsDeletedTrue(Long projectId);

    // Search file content for a keyword
    @Query("SELECT f FROM CodeFile f WHERE f.projectId = :projectId " +
            "AND f.isDeleted = false " +
            "AND f.fileType = 'FILE' " +
            "AND (LOWER(f.content) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(f.name) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<CodeFile> searchInProject(
            @Param("projectId") Long projectId,
            @Param("keyword") String keyword);

    // Check if path exists in project
    boolean existsByProjectIdAndPath(Long projectId, String path);

    // Find all files under a folder path
    @Query("SELECT f FROM CodeFile f WHERE f.projectId = :projectId " +
            "AND f.path LIKE CONCAT(:folderPath, '%') " +
            "AND f.isDeleted = false")
    List<CodeFile> findByProjectIdAndPathStartingWith(
            @Param("projectId") Long projectId,
            @Param("folderPath") String folderPath);
}