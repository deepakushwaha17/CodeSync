package com.codesync.comment.repository;

import com.codesync.comment.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository
        extends JpaRepository<Comment, Long> {

    // All top-level comments for a file
    List<Comment> findByFileIdAndParentCommentIdIsNull(Long fileId);

    // All comments for a file including replies
    List<Comment> findByFileIdOrderByCreatedAtAsc(Long fileId);

    // All top-level comments for a project
    List<Comment> findByProjectIdAndParentCommentIdIsNull(
            Long projectId);

    // All comments by a specific author
    List<Comment> findByAuthorId(Long authorId);

    // Replies to a specific comment
    List<Comment> findByParentCommentId(Long parentCommentId);

    // Comments on a specific line
    List<Comment> findByFileIdAndLineNumber(
            Long fileId, Integer lineNumber);

    // Resolved or unresolved comments for a file
    List<Comment> findByFileIdAndResolved(
            Long fileId, Boolean resolved);

    // Count comments for a file
    int countByFileId(Long fileId);

    // Count unresolved comments for a file
    int countByFileIdAndResolved(Long fileId, Boolean resolved);

    // Comments linked to a specific snapshot
    List<Comment> findBySnapshotId(Long snapshotId);

    // Search comments by content keyword
    @Query("SELECT c FROM Comment c WHERE c.projectId = :projectId " +
            "AND LOWER(c.content) LIKE " +
            "LOWER(CONCAT('%', :keyword, '%'))")
    List<Comment> searchByContent(
            @Param("projectId") Long projectId,
            @Param("keyword") String keyword);

    // Get all @mentions in a project
    @Query("SELECT c FROM Comment c WHERE c.projectId = :projectId " +
            "AND c.content LIKE CONCAT('%@', :username, '%')")
    List<Comment> findMentions(
            @Param("projectId") Long projectId,
            @Param("username") String username);
}