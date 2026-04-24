package com.codesync.version.repository;

import com.codesync.version.entity.Snapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SnapshotRepository
        extends JpaRepository<Snapshot, Long> {

    List<Snapshot> findByProjectIdOrderByCreatedAtDesc(
            Long projectId);

    List<Snapshot> findByFileIdOrderByCreatedAtDesc(Long fileId);

    List<Snapshot> findByAuthorIdOrderByCreatedAtDesc(Long authorId);

    List<Snapshot> findByBranchOrderByCreatedAtDesc(String branch);

    List<Snapshot> findByFileIdAndBranchOrderByCreatedAtDesc(
            Long fileId, String branch);

    Optional<Snapshot> findByHash(String hash);

    Optional<Snapshot> findByTag(String tag);

    // Get latest snapshot for a file on a specific branch
    @Query("SELECT s FROM Snapshot s WHERE s.fileId = :fileId " +
            "AND s.branch = :branch " +
            "ORDER BY s.createdAt DESC LIMIT 1")
    Optional<Snapshot> findLatestByFileIdAndBranch(
            @Param("fileId") Long fileId,
            @Param("branch") String branch);

    // Get latest snapshot for a file on any branch
    @Query("SELECT s FROM Snapshot s WHERE s.fileId = :fileId " +
            "ORDER BY s.createdAt DESC LIMIT 1")
    Optional<Snapshot> findLatestByFileId(
            @Param("fileId") Long fileId);

    // Get all distinct branches for a project
    @Query("SELECT DISTINCT s.branch FROM Snapshot s " +
            "WHERE s.projectId = :projectId")
    List<String> findDistinctBranchesByProjectId(
            @Param("projectId") Long projectId);

    // Check if branch exists in project
    boolean existsByProjectIdAndBranch(
            Long projectId, String branch);

    // Get snapshot chain — all ancestors of a snapshot
    @Query("SELECT s FROM Snapshot s WHERE s.fileId = :fileId " +
            "ORDER BY s.createdAt ASC")
    List<Snapshot> findFileHistory(@Param("fileId") Long fileId);
}