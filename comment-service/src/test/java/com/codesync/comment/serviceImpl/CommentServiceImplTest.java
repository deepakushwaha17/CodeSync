package com.codesync.comment.serviceImpl;

import com.codesync.comment.client.NotificationClient;
import com.codesync.comment.dto.request.AddCommentRequest;
import com.codesync.comment.dto.request.UpdateCommentRequest;
import com.codesync.comment.dto.response.CommentResponse;
import com.codesync.comment.entity.Comment;
import com.codesync.comment.exception.CommentNotFoundException;
import com.codesync.comment.exception.UnauthorizedAccessException;
import com.codesync.comment.repository.CommentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    @Mock private CommentRepository commentRepository;
    @Mock private NotificationClient notificationClient;

    @InjectMocks private CommentServiceImpl commentService;

    // ── Fixture ───────────────────────────────────────────────────────

    private Comment build(Long id, Long projectId, Long fileId,
                          Long authorId, String content, Integer line,
                          Long parentId, boolean resolved) {
        return Comment.builder()
                .commentId(id).projectId(projectId).fileId(fileId)
                .authorId(authorId).content(content).lineNumber(line)
                .parentCommentId(parentId).resolved(resolved)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    // stub replyCount for top-level comment
    private void stubNoReplies(Long commentId) {
        when(commentRepository.findByParentCommentId(commentId))
                .thenReturn(List.of());
    }

    // ════════════════════════════════════════════════════════════════
    // addComment()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("addComment - saves top-level comment and returns response")
    void addComment_topLevel_savesAndReturns() {
        AddCommentRequest req = new AddCommentRequest();
        req.setProjectId(1L); req.setFileId(10L);
        req.setContent("Fix this NPE"); req.setLineNumber(42);
        req.setParentCommentId(null);

        Comment saved = build(1L, 1L, 10L, 5L, "Fix this NPE", 42, null, false);
        when(commentRepository.save(any())).thenReturn(saved);
        stubNoReplies(1L);

        CommentResponse response = commentService.addComment(5L, req);

        assertThat(response.getCommentId()).isEqualTo(1L);
        assertThat(response.getContent()).isEqualTo("Fix this NPE");
        assertThat(response.getLineNumber()).isEqualTo(42);
        assertThat(response.getResolved()).isFalse();
        verify(commentRepository).save(any(Comment.class));
    }

    @Test
    @DisplayName("addComment - saves reply when valid parentCommentId provided")
    void addComment_reply_savesReply() {
        Comment parent = build(10L, 1L, 10L, 1L, "parent", 5, null, false);

        AddCommentRequest req = new AddCommentRequest();
        req.setProjectId(1L); req.setFileId(10L);
        req.setContent("Good point"); req.setLineNumber(5);
        req.setParentCommentId(10L);

        when(commentRepository.findById(10L)).thenReturn(Optional.of(parent));

        Comment savedReply = build(11L, 1L, 10L, 2L, "Good point", 5, 10L, false);
        when(commentRepository.save(any())).thenReturn(savedReply);
        doNothing().when(notificationClient).sendNotification(any());

        CommentResponse response = commentService.addComment(2L, req);

        assertThat(response.getParentCommentId()).isEqualTo(10L);
        verify(commentRepository, times(2)).findById(10L); // once for validate, once for notify
    }

    @Test
    @DisplayName("addComment - throws CommentNotFoundException when parent does not exist")
    void addComment_invalidParentId_throwsException() {
        AddCommentRequest req = new AddCommentRequest();
        req.setProjectId(1L); req.setFileId(10L);
        req.setContent("reply"); req.setLineNumber(5);
        req.setParentCommentId(999L);

        when(commentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.addComment(1L, req))
                .isInstanceOf(CommentNotFoundException.class)
                .hasMessageContaining("999");

        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("addComment - does not notify when replying to own comment")
    void addComment_replyToOwnComment_noNotificationSent() {
        Long authorId = 5L;
        Comment parent = build(10L, 1L, 10L, authorId, "parent", 5, null, false);

        AddCommentRequest req = new AddCommentRequest();
        req.setProjectId(1L); req.setFileId(10L);
        req.setContent("my reply"); req.setLineNumber(5);
        req.setParentCommentId(10L);

        when(commentRepository.findById(10L)).thenReturn(Optional.of(parent));
        Comment saved = build(11L, 1L, 10L, authorId, "my reply", 5, 10L, false);
        when(commentRepository.save(any())).thenReturn(saved);

        commentService.addComment(authorId, req); // same user as parent author

        verify(notificationClient, never()).sendNotification(any());
    }

    @Test
    @DisplayName("addComment - gracefully handles notification failure")
    void addComment_notificationFails_doesNotPropagate() {
        Comment parent = build(10L, 1L, 10L, 1L, "parent", 5, null, false);

        AddCommentRequest req = new AddCommentRequest();
        req.setProjectId(1L); req.setFileId(10L);
        req.setContent("reply"); req.setLineNumber(5);
        req.setParentCommentId(10L);

        when(commentRepository.findById(10L)).thenReturn(Optional.of(parent));
        Comment saved = build(11L, 1L, 10L, 2L, "reply", 5, 10L, false);
        when(commentRepository.save(any())).thenReturn(saved);
        doThrow(new RuntimeException("notification down"))
                .when(notificationClient).sendNotification(any());

        assertThatCode(() -> commentService.addComment(2L, req))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("addComment - extracts @mentions in response")
    void addComment_withMentions_returnsMentionsList() {
        AddCommentRequest req = new AddCommentRequest();
        req.setProjectId(1L); req.setFileId(10L);
        req.setContent("@johndoe and @janedoe please review"); req.setLineNumber(1);

        Comment saved = build(1L, 1L, 10L, 5L,
                "@johndoe and @janedoe please review", 1, null, false);
        when(commentRepository.save(any())).thenReturn(saved);
        stubNoReplies(1L);

        CommentResponse response = commentService.addComment(5L, req);

        assertThat(response.getMentions())
                .containsExactlyInAnyOrder("johndoe", "janedoe");
    }

    // ════════════════════════════════════════════════════════════════
    // getCommentById()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getCommentById - returns CommentResponse for existing comment")
    void getCommentById_existing_returnsResponse() {
        Comment c = build(1L, 1L, 10L, 5L, "content", 5, null, false);
        when(commentRepository.findById(1L)).thenReturn(Optional.of(c));
        stubNoReplies(1L);

        CommentResponse response = commentService.getCommentById(1L);

        assertThat(response.getCommentId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getCommentById - throws CommentNotFoundException for unknown ID")
    void getCommentById_notFound_throwsException() {
        when(commentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.getCommentById(99L))
                .isInstanceOf(CommentNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ════════════════════════════════════════════════════════════════
    // getCommentsByFile()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getCommentsByFile - returns top-level comments for file")
    void getCommentsByFile_existing_returnsList() {
        Comment c1 = build(1L, 1L, 10L, 1L, "A", 1, null, false);
        Comment c2 = build(2L, 1L, 10L, 2L, "B", 2, null, false);
        when(commentRepository.findByFileIdAndParentCommentIdIsNull(10L))
                .thenReturn(List.of(c1, c2));
        stubNoReplies(1L);
        stubNoReplies(2L);

        List<CommentResponse> result = commentService.getCommentsByFile(10L);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("getCommentsByFile - returns empty when file has no comments")
    void getCommentsByFile_noComments_returnsEmpty() {
        when(commentRepository.findByFileIdAndParentCommentIdIsNull(10L))
                .thenReturn(List.of());

        assertThat(commentService.getCommentsByFile(10L)).isEmpty();
    }

    // ════════════════════════════════════════════════════════════════
    // getCommentsByProject()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getCommentsByProject - returns top-level comments for project")
    void getCommentsByProject_existing_returnsList() {
        Comment c = build(1L, 1L, 10L, 1L, "proj comment", 3, null, false);
        when(commentRepository.findByProjectIdAndParentCommentIdIsNull(1L))
                .thenReturn(List.of(c));
        stubNoReplies(1L);

        List<CommentResponse> result = commentService.getCommentsByProject(1L);

        assertThat(result).hasSize(1);
    }

    // ════════════════════════════════════════════════════════════════
    // getReplies()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getReplies - returns all replies to a valid parent comment")
    void getReplies_existing_returnsReplies() {
        Comment parent = build(1L, 1L, 10L, 1L, "parent", 5, null, false);
        Comment r1     = build(2L, 1L, 10L, 2L, "reply1", 5, 1L, false);
        Comment r2     = build(3L, 1L, 10L, 3L, "reply2", 5, 1L, false);

        when(commentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(commentRepository.findByParentCommentId(1L)).thenReturn(List.of(r1, r2));

        List<CommentResponse> replies = commentService.getReplies(1L);

        assertThat(replies).hasSize(2);
    }

    @Test
    @DisplayName("getReplies - throws CommentNotFoundException for unknown parent")
    void getReplies_unknownParent_throwsException() {
        when(commentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.getReplies(99L))
                .isInstanceOf(CommentNotFoundException.class);
    }

    @Test
    @DisplayName("getReplies - returns empty list when comment has no replies")
    void getReplies_noReplies_returnsEmpty() {
        Comment parent = build(1L, 1L, 10L, 1L, "parent", 5, null, false);
        when(commentRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(commentRepository.findByParentCommentId(1L)).thenReturn(List.of());

        assertThat(commentService.getReplies(1L)).isEmpty();
    }

    // ════════════════════════════════════════════════════════════════
    // getCommentsByLine()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getCommentsByLine - returns comments on specific line")
    void getCommentsByLine_existing_returnsComments() {
        Comment c = build(1L, 1L, 10L, 1L, "line 5 issue", 5, null, false);
        when(commentRepository.findByFileIdAndLineNumber(10L, 5))
                .thenReturn(List.of(c));
        stubNoReplies(1L);

        List<CommentResponse> result = commentService.getCommentsByLine(10L, 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLineNumber()).isEqualTo(5);
    }

    @Test
    @DisplayName("getCommentsByLine - returns empty for line with no comments")
    void getCommentsByLine_noComments_returnsEmpty() {
        when(commentRepository.findByFileIdAndLineNumber(10L, 999))
                .thenReturn(List.of());

        assertThat(commentService.getCommentsByLine(10L, 999)).isEmpty();
    }

    // ════════════════════════════════════════════════════════════════
    // updateComment()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("updateComment - author updates content successfully")
    void updateComment_author_updatesContent() {
        Comment c = build(1L, 1L, 10L, 5L, "old content", 5, null, false);
        when(commentRepository.findById(1L)).thenReturn(Optional.of(c));
        when(commentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubNoReplies(1L);

        UpdateCommentRequest req = new UpdateCommentRequest();
        req.setContent("updated content");

        CommentResponse response = commentService.updateComment(1L, 5L, req);

        assertThat(response.getContent()).isEqualTo("updated content");
        verify(commentRepository).save(any(Comment.class));
    }

    @Test
    @DisplayName("updateComment - throws UnauthorizedAccessException for non-author")
    void updateComment_nonAuthor_throwsException() {
        Comment c = build(1L, 1L, 10L, 5L, "content", 5, null, false);
        when(commentRepository.findById(1L)).thenReturn(Optional.of(c));

        UpdateCommentRequest req = new UpdateCommentRequest();
        req.setContent("hacked");

        assertThatThrownBy(() -> commentService.updateComment(1L, 99L, req))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("own comments");

        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateComment - throws CommentNotFoundException for unknown comment")
    void updateComment_notFound_throwsException() {
        when(commentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                commentService.updateComment(99L, 1L, new UpdateCommentRequest()))
                .isInstanceOf(CommentNotFoundException.class);
    }

    // ════════════════════════════════════════════════════════════════
    // deleteComment()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("deleteComment - author deletes comment and all its replies")
    void deleteComment_author_deletesCommentAndReplies() {
        Comment c  = build(1L, 1L, 10L, 5L, "to delete", 5, null, false);
        Comment r1 = build(2L, 1L, 10L, 2L, "reply1",    5, 1L, false);
        Comment r2 = build(3L, 1L, 10L, 3L, "reply2",    5, 1L, false);

        when(commentRepository.findById(1L)).thenReturn(Optional.of(c));
        when(commentRepository.findByParentCommentId(1L)).thenReturn(List.of(r1, r2));

        commentService.deleteComment(1L, 5L);

        verify(commentRepository).deleteAll(List.of(r1, r2));
        verify(commentRepository).delete(c);
    }

    @Test
    @DisplayName("deleteComment - deletes comment with no replies")
    void deleteComment_noReplies_deletesOnlyComment() {
        Comment c = build(1L, 1L, 10L, 5L, "alone", 5, null, false);
        when(commentRepository.findById(1L)).thenReturn(Optional.of(c));
        when(commentRepository.findByParentCommentId(1L)).thenReturn(List.of());

        commentService.deleteComment(1L, 5L);

        verify(commentRepository).deleteAll(List.of());
        verify(commentRepository).delete(c);
    }

    @Test
    @DisplayName("deleteComment - throws UnauthorizedAccessException for non-author")
    void deleteComment_nonAuthor_throwsException() {
        Comment c = build(1L, 1L, 10L, 5L, "content", 5, null, false);
        when(commentRepository.findById(1L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> commentService.deleteComment(1L, 99L))
                .isInstanceOf(UnauthorizedAccessException.class);

        verify(commentRepository, never()).delete(any(Comment.class));
    }

    @Test
    @DisplayName("deleteComment - throws CommentNotFoundException for unknown comment")
    void deleteComment_notFound_throwsException() {
        when(commentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.deleteComment(99L, 1L))
                .isInstanceOf(CommentNotFoundException.class);
    }

    // ════════════════════════════════════════════════════════════════
    // resolveComment()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("resolveComment - sets resolved=true")
    void resolveComment_existing_setsResolvedTrue() {
        Comment c = build(1L, 1L, 10L, 5L, "bug here", 5, null, false);
        when(commentRepository.findById(1L)).thenReturn(Optional.of(c));
        when(commentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubNoReplies(1L);

        CommentResponse response = commentService.resolveComment(1L, 5L);

        assertThat(response.getResolved()).isTrue();
        verify(commentRepository).save(argThat(Comment::getResolved));
    }

    @Test
    @DisplayName("resolveComment - throws CommentNotFoundException for unknown comment")
    void resolveComment_notFound_throwsException() {
        when(commentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.resolveComment(99L, 1L))
                .isInstanceOf(CommentNotFoundException.class);
    }

    // ════════════════════════════════════════════════════════════════
    // unresolveComment()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("unresolveComment - sets resolved=false")
    void unresolveComment_existing_setsResolvedFalse() {
        Comment c = build(1L, 1L, 10L, 5L, "bug", 5, null, true); // already resolved
        when(commentRepository.findById(1L)).thenReturn(Optional.of(c));
        when(commentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubNoReplies(1L);

        CommentResponse response = commentService.unresolveComment(1L, 5L);

        assertThat(response.getResolved()).isFalse();
        verify(commentRepository).save(argThat(cm -> !cm.getResolved()));
    }

    @Test
    @DisplayName("unresolveComment - throws CommentNotFoundException for unknown comment")
    void unresolveComment_notFound_throwsException() {
        when(commentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.unresolveComment(99L, 1L))
                .isInstanceOf(CommentNotFoundException.class);
    }

    // ════════════════════════════════════════════════════════════════
    // getCommentCount()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getCommentCount - returns total comment count for file")
    void getCommentCount_returnsCount() {
        when(commentRepository.countByFileId(10L)).thenReturn(7);

        assertThat(commentService.getCommentCount(10L)).isEqualTo(7);
    }

    @Test
    @DisplayName("getCommentCount - returns 0 for file with no comments")
    void getCommentCount_noComments_returnsZero() {
        when(commentRepository.countByFileId(10L)).thenReturn(0);

        assertThat(commentService.getCommentCount(10L)).isZero();
    }

    // ════════════════════════════════════════════════════════════════
    // getUnresolvedCount()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getUnresolvedCount - returns count of unresolved comments")
    void getUnresolvedCount_returnsCount() {
        when(commentRepository.countByFileIdAndResolved(10L, false)).thenReturn(3);

        assertThat(commentService.getUnresolvedCount(10L)).isEqualTo(3);
    }

    @Test
    @DisplayName("getUnresolvedCount - returns 0 when all comments are resolved")
    void getUnresolvedCount_allResolved_returnsZero() {
        when(commentRepository.countByFileIdAndResolved(10L, false)).thenReturn(0);

        assertThat(commentService.getUnresolvedCount(10L)).isZero();
    }
}