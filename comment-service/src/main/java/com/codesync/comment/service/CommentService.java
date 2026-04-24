package com.codesync.comment.service;

import com.codesync.comment.dto.request.AddCommentRequest;
import com.codesync.comment.dto.request.UpdateCommentRequest;
import com.codesync.comment.dto.response.CommentResponse;

import java.util.List;

public interface CommentService {

    // Add
    CommentResponse addComment(Long authorId,
                               AddCommentRequest request);

    // Get
    CommentResponse getCommentById(Long commentId);
    List<CommentResponse> getCommentsByFile(Long fileId);
    List<CommentResponse> getCommentsByProject(Long projectId);
    List<CommentResponse> getReplies(Long parentCommentId);
    List<CommentResponse> getCommentsByLine(
            Long fileId, Integer lineNumber);

    // Update
    CommentResponse updateComment(Long commentId, Long userId,
                                  UpdateCommentRequest request);

    // Delete
    void deleteComment(Long commentId, Long userId);

    // Resolve workflow
    CommentResponse resolveComment(Long commentId, Long userId);
    CommentResponse unresolveComment(Long commentId, Long userId);

    // Stats
    int getCommentCount(Long fileId);
    int getUnresolvedCount(Long fileId);
}