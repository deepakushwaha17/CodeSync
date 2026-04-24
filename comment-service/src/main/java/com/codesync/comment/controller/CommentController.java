package com.codesync.comment.controller;

import com.codesync.comment.dto.request.AddCommentRequest;
import com.codesync.comment.dto.request.UpdateCommentRequest;
import com.codesync.comment.dto.response.ApiResponse;
import com.codesync.comment.dto.response.CommentResponse;
import com.codesync.comment.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/comments")
@RequiredArgsConstructor
@Tag(name = "Comment Service",
        description = "Inline Code Review Comment APIs")
public class CommentController {

    private final CommentService commentService;

    // ── POST /api/v1/comments ─────────────────────────────────────────
    @PostMapping
    @Operation(summary = "Add an inline comment to a file line")
    public ResponseEntity<ApiResponse<CommentResponse>> addComment(
            @RequestHeader("X-Auth-User-Id") Long userId,
            @Valid @RequestBody AddCommentRequest request) {

        CommentResponse response =
                commentService.addComment(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Comment added.", response));
    }

    // ── GET /api/v1/comments/{commentId} ──────────────────────────────
    @GetMapping("/{commentId}")
    @Operation(summary = "Get comment by ID")
    public ResponseEntity<ApiResponse<CommentResponse>>
    getById(@PathVariable Long commentId) {

        CommentResponse response =
                commentService.getCommentById(commentId);
        return ResponseEntity.ok(
                ApiResponse.success("Comment fetched.", response));
    }

    // ── GET /api/v1/comments/file/{fileId} ────────────────────────────
    @GetMapping("/file/{fileId}")
    @Operation(summary = "Get all top-level comments for a file")
    public ResponseEntity<ApiResponse<List<CommentResponse>>>
    getByFile(@PathVariable Long fileId) {

        List<CommentResponse> comments =
                commentService.getCommentsByFile(fileId);
        return ResponseEntity.ok(
                ApiResponse.success("Comments fetched.", comments));
    }

    // ── GET /api/v1/comments/project/{projectId} ──────────────────────
    @GetMapping("/project/{projectId}")
    @Operation(summary = "Get all comments for a project")
    public ResponseEntity<ApiResponse<List<CommentResponse>>>
    getByProject(@PathVariable Long projectId) {

        List<CommentResponse> comments =
                commentService.getCommentsByProject(projectId);
        return ResponseEntity.ok(
                ApiResponse.success("Comments fetched.", comments));
    }

    // ── GET /api/v1/comments/{commentId}/replies ──────────────────────
    @GetMapping("/{commentId}/replies")
    @Operation(summary = "Get all replies to a comment")
    public ResponseEntity<ApiResponse<List<CommentResponse>>>
    getReplies(@PathVariable Long commentId) {

        List<CommentResponse> replies =
                commentService.getReplies(commentId);
        return ResponseEntity.ok(
                ApiResponse.success("Replies fetched.", replies));
    }

    // ── GET /api/v1/comments/file/{fileId}/line/{lineNumber} ──────────
    @GetMapping("/file/{fileId}/line/{lineNumber}")
    @Operation(summary = "Get comments on a specific line")
    public ResponseEntity<ApiResponse<List<CommentResponse>>>
    getByLine(
            @PathVariable Long fileId,
            @PathVariable Integer lineNumber) {

        List<CommentResponse> comments =
                commentService.getCommentsByLine(fileId, lineNumber);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Line comments fetched.", comments));
    }

    // ── PUT /api/v1/comments/{commentId} ──────────────────────────────
    @PutMapping("/{commentId}")
    @Operation(summary = "Update a comment")
    public ResponseEntity<ApiResponse<CommentResponse>>
    updateComment(
            @PathVariable Long commentId,
            @RequestHeader("X-Auth-User-Id") Long userId,
            @Valid @RequestBody UpdateCommentRequest request) {

        CommentResponse response = commentService.updateComment(
                commentId, userId, request);
        return ResponseEntity.ok(
                ApiResponse.success("Comment updated.", response));
    }

    // ── DELETE /api/v1/comments/{commentId} ───────────────────────────
    @DeleteMapping("/{commentId}")
    @Operation(summary = "Delete a comment")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable Long commentId,
            @RequestHeader("X-Auth-User-Id") Long userId) {

        commentService.deleteComment(commentId, userId);
        return ResponseEntity.ok(
                ApiResponse.success("Comment deleted."));
    }

    // ── PUT /api/v1/comments/{commentId}/resolve ──────────────────────
    @PutMapping("/{commentId}/resolve")
    @Operation(summary = "Resolve a comment")
    public ResponseEntity<ApiResponse<CommentResponse>>
    resolveComment(
            @PathVariable Long commentId,
            @RequestHeader("X-Auth-User-Id") Long userId) {

        CommentResponse response =
                commentService.resolveComment(commentId, userId);
        return ResponseEntity.ok(
                ApiResponse.success("Comment resolved.", response));
    }

    // ── PUT /api/v1/comments/{commentId}/unresolve ────────────────────
    @PutMapping("/{commentId}/unresolve")
    @Operation(summary = "Unresolve a comment")
    public ResponseEntity<ApiResponse<CommentResponse>>
    unresolveComment(
            @PathVariable Long commentId,
            @RequestHeader("X-Auth-User-Id") Long userId) {

        CommentResponse response =
                commentService.unresolveComment(commentId, userId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Comment unresolved.", response));
    }

    // ── GET /api/v1/comments/file/{fileId}/count ──────────────────────
    @GetMapping("/file/{fileId}/count")
    @Operation(summary = "Get total comment count for a file")
    public ResponseEntity<ApiResponse<Integer>> getCommentCount(
            @PathVariable Long fileId) {

        int count = commentService.getCommentCount(fileId);
        return ResponseEntity.ok(
                ApiResponse.success("Comment count.", count));
    }

    // ── GET /api/v1/comments/file/{fileId}/unresolved/count ───────────
    @GetMapping("/file/{fileId}/unresolved/count")
    @Operation(summary = "Get unresolved comment count for a file")
    public ResponseEntity<ApiResponse<Integer>>
    getUnresolvedCount(@PathVariable Long fileId) {

        int count = commentService.getUnresolvedCount(fileId);
        return ResponseEntity.ok(
                ApiResponse.success("Unresolved count.", count));
    }
}