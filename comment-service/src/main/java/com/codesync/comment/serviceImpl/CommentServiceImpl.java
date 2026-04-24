package com.codesync.comment.serviceImpl;

import com.codesync.comment.dto.request.AddCommentRequest;
import com.codesync.comment.dto.request.UpdateCommentRequest;
import com.codesync.comment.dto.response.CommentResponse;
import com.codesync.comment.entity.Comment;
import com.codesync.comment.exception.CommentNotFoundException;
import com.codesync.comment.exception.UnauthorizedAccessException;
import com.codesync.comment.repository.CommentRepository;
import com.codesync.comment.service.CommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;

    // Regex to extract @mentions from comment content
    private static final Pattern MENTION_PATTERN =
            Pattern.compile("@([a-zA-Z0-9_]+)");

    // ─────────────────────────────────────────────────────────────────
    // ADD
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public CommentResponse addComment(
            Long authorId, AddCommentRequest request) {

        log.info("Adding comment on file: {} line: {} by user: {}",
                request.getFileId(),
                request.getLineNumber(),
                authorId);

        // If this is a reply validate parent exists
        if (request.getParentCommentId() != null) {
            commentRepository
                    .findById(request.getParentCommentId())
                    .orElseThrow(() -> new CommentNotFoundException(
                            "Parent comment not found with ID: "
                                    + request.getParentCommentId()));
        }

        Comment comment = Comment.builder()
                .projectId(request.getProjectId())
                .fileId(request.getFileId())
                .authorId(authorId)
                .content(request.getContent())
                .lineNumber(request.getLineNumber())
                .columnNumber(request.getColumnNumber())
                .parentCommentId(request.getParentCommentId())
                .snapshotId(request.getSnapshotId())
                .resolved(false)
                .build();

        Comment saved = commentRepository.save(comment);
        log.info("Comment saved with ID: {}", saved.getCommentId());

        return mapToResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────────
    // GET
    // ─────────────────────────────────────────────────────────────────

    @Override
    public CommentResponse getCommentById(Long commentId) {
        return mapToResponse(findCommentOrThrow(commentId));
    }

    @Override
    public List<CommentResponse> getCommentsByFile(Long fileId) {
        return commentRepository
                .findByFileIdAndParentCommentIdIsNull(fileId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<CommentResponse> getCommentsByProject(
            Long projectId) {
        return commentRepository
                .findByProjectIdAndParentCommentIdIsNull(projectId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<CommentResponse> getReplies(Long parentCommentId) {
        // Verify parent exists
        findCommentOrThrow(parentCommentId);

        return commentRepository
                .findByParentCommentId(parentCommentId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<CommentResponse> getCommentsByLine(
            Long fileId, Integer lineNumber) {
        return commentRepository
                .findByFileIdAndLineNumber(fileId, lineNumber)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────
    // UPDATE
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public CommentResponse updateComment(
            Long commentId, Long userId,
            UpdateCommentRequest request) {

        Comment comment = findCommentOrThrow(commentId);

        // Only author can update their own comment
        verifyAuthor(comment, userId);

        comment.setContent(request.getContent());
        Comment updated = commentRepository.save(comment);

        log.info("Comment {} updated by user {}", commentId, userId);
        return mapToResponse(updated);
    }

    // ─────────────────────────────────────────────────────────────────
    // DELETE
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        Comment comment = findCommentOrThrow(commentId);

        // Only author can delete their own comment
        verifyAuthor(comment, userId);

        // Delete all replies first
        List<Comment> replies = commentRepository
                .findByParentCommentId(commentId);
        commentRepository.deleteAll(replies);

        commentRepository.delete(comment);
        log.info("Comment {} deleted by user {}", commentId, userId);
    }

    // ─────────────────────────────────────────────────────────────────
    // RESOLVE WORKFLOW
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public CommentResponse resolveComment(
            Long commentId, Long userId) {

        Comment comment = findCommentOrThrow(commentId);
        comment.setResolved(true);
        Comment updated = commentRepository.save(comment);

        log.info("Comment {} resolved by user {}",
                commentId, userId);
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public CommentResponse unresolveComment(
            Long commentId, Long userId) {

        Comment comment = findCommentOrThrow(commentId);
        comment.setResolved(false);
        Comment updated = commentRepository.save(comment);

        log.info("Comment {} unresolved by user {}",
                commentId, userId);
        return mapToResponse(updated);
    }

    // ─────────────────────────────────────────────────────────────────
    // STATS
    // ─────────────────────────────────────────────────────────────────

    @Override
    public int getCommentCount(Long fileId) {
        return commentRepository.countByFileId(fileId);
    }

    @Override
    public int getUnresolvedCount(Long fileId) {
        return commentRepository.countByFileIdAndResolved(
                fileId, false);
    }

    // ─────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────

    private Comment findCommentOrThrow(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(
                        "Comment not found with ID: " + commentId));
    }

    private void verifyAuthor(Comment comment, Long userId) {
        if (!comment.getAuthorId().equals(userId)) {
            throw new UnauthorizedAccessException(
                    "You can only modify your own comments.");
        }
    }

    /**
     * Extract @mentions from comment content.
     * Example: "Fix this @johndoe" → ["johndoe"]
     */
    private List<String> extractMentions(String content) {
        List<String> mentions = new ArrayList<>();
        if (content == null) return mentions;

        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            mentions.add(matcher.group(1));
        }
        return mentions;
    }

    private CommentResponse mapToResponse(Comment c) {
        int replyCount = (c.getParentCommentId() == null)
                ? commentRepository.findByParentCommentId(
                c.getCommentId()).size()
                : 0;

        return CommentResponse.builder()
                .commentId(c.getCommentId())
                .projectId(c.getProjectId())
                .fileId(c.getFileId())
                .authorId(c.getAuthorId())
                .content(c.getContent())
                .lineNumber(c.getLineNumber())
                .columnNumber(c.getColumnNumber())
                .parentCommentId(c.getParentCommentId())
                .resolved(c.getResolved())
                .snapshotId(c.getSnapshotId())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .mentions(extractMentions(c.getContent()))
                .replyCount(replyCount)
                .build();
    }
}