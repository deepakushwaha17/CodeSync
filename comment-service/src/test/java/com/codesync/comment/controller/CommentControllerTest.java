package com.codesync.comment.controller;

import com.codesync.comment.dto.request.AddCommentRequest;
import com.codesync.comment.dto.request.UpdateCommentRequest;
import com.codesync.comment.dto.response.CommentResponse;
import com.codesync.comment.exception.CommentNotFoundException;
import com.codesync.comment.exception.UnauthorizedAccessException;
import com.codesync.comment.service.CommentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@WebMvcTest(
        value = CommentController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class
        }
)
@AutoConfigureMockMvc(addFilters = false)
class CommentControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private CommentService commentService;

    // ── Fixture ───────────────────────────────────────────────────────

    private CommentResponse buildResponse(Long id, String content,
                                          Integer line, Long parentId,
                                          boolean resolved) {
        return CommentResponse.builder()
                .commentId(id).projectId(1L).fileId(10L).authorId(5L)
                .content(content).lineNumber(line).parentCommentId(parentId)
                .resolved(resolved).replyCount(0).mentions(List.of())
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    // ════════════════════════════════════════════════════════════════
    // POST /api/v1/comments
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST / - 201 Created on successful comment addition")
    void addComment_valid_returns201() throws Exception {
        AddCommentRequest req = new AddCommentRequest();
        req.setProjectId(1L); req.setFileId(10L);
        req.setContent("Fix this NPE"); req.setLineNumber(42);

        when(commentService.addComment(eq(5L), any()))
                .thenReturn(buildResponse(1L, "Fix this NPE", 42, null, false));

        mockMvc.perform(post("/api/v1/comments")
                        .header("X-Auth-User-Id", 5L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.commentId").value(1))
                .andExpect(jsonPath("$.data.content").value("Fix this NPE"))
                .andExpect(jsonPath("$.data.lineNumber").value(42))
                .andExpect(jsonPath("$.data.resolved").value(false));
    }

    @Test
    @DisplayName("POST / - 400 Bad Request when required fields are missing")
    void addComment_missingFields_returns400() throws Exception {
        AddCommentRequest req = new AddCommentRequest();
        // content and lineNumber are missing

        mockMvc.perform(post("/api/v1/comments")
                        .header("X-Auth-User-Id", 5L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST / - 404 Not Found when parent comment does not exist")
    void addComment_invalidParent_returns404() throws Exception {
        AddCommentRequest req = new AddCommentRequest();
        req.setProjectId(1L); req.setFileId(10L);
        req.setContent("reply"); req.setLineNumber(5);
        req.setParentCommentId(999L);

        when(commentService.addComment(eq(5L), any()))
                .thenThrow(new CommentNotFoundException("Parent comment not found with ID: 999"));

        mockMvc.perform(post("/api/v1/comments")
                        .header("X-Auth-User-Id", 5L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/comments/{commentId}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /{commentId} - 200 OK for existing comment")
    void getById_existing_returns200() throws Exception {
        when(commentService.getCommentById(1L))
                .thenReturn(buildResponse(1L, "Fix this", 5, null, false));

        mockMvc.perform(get("/api/v1/comments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commentId").value(1))
                .andExpect(jsonPath("$.data.content").value("Fix this"));
    }

    @Test
    @DisplayName("GET /{commentId} - 404 Not Found for unknown comment")
    void getById_notFound_returns404() throws Exception {
        when(commentService.getCommentById(99L))
                .thenThrow(new CommentNotFoundException("Comment not found with ID: 99"));

        mockMvc.perform(get("/api/v1/comments/99"))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/comments/file/{fileId}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /file/{fileId} - 200 OK returns file comments")
    void getByFile_existing_returns200() throws Exception {
        when(commentService.getCommentsByFile(10L)).thenReturn(List.of(
                buildResponse(1L, "A", 1, null, false),
                buildResponse(2L, "B", 2, null, false)
        ));

        mockMvc.perform(get("/api/v1/comments/file/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("GET /file/{fileId} - 200 OK returns empty list when no comments")
    void getByFile_noComments_returnsEmpty() throws Exception {
        when(commentService.getCommentsByFile(10L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/comments/file/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/comments/project/{projectId}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /project/{projectId} - 200 OK returns project comments")
    void getByProject_existing_returns200() throws Exception {
        when(commentService.getCommentsByProject(1L)).thenReturn(
                List.of(buildResponse(1L, "proj comment", 3, null, false)));

        mockMvc.perform(get("/api/v1/comments/project/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/comments/{commentId}/replies
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /{commentId}/replies - 200 OK returns all replies")
    void getReplies_existing_returns200() throws Exception {
        when(commentService.getReplies(1L)).thenReturn(List.of(
                buildResponse(2L, "reply1", 5, 1L, false),
                buildResponse(3L, "reply2", 5, 1L, false)
        ));

        mockMvc.perform(get("/api/v1/comments/1/replies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].parentCommentId").value(1));
    }

    @Test
    @DisplayName("GET /{commentId}/replies - 404 Not Found for unknown parent comment")
    void getReplies_unknownParent_returns404() throws Exception {
        when(commentService.getReplies(99L))
                .thenThrow(new CommentNotFoundException("Comment not found."));

        mockMvc.perform(get("/api/v1/comments/99/replies"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /{commentId}/replies - 200 OK returns empty when no replies")
    void getReplies_noReplies_returnsEmpty() throws Exception {
        when(commentService.getReplies(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/comments/1/replies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/comments/file/{fileId}/line/{lineNumber}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /file/{fileId}/line/{lineNumber} - 200 OK returns line comments")
    void getByLine_existing_returns200() throws Exception {
        when(commentService.getCommentsByLine(10L, 5)).thenReturn(
                List.of(buildResponse(1L, "line 5 issue", 5, null, false)));

        mockMvc.perform(get("/api/v1/comments/file/10/line/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].lineNumber").value(5));
    }

    @Test
    @DisplayName("GET /file/{fileId}/line/{lineNumber} - 200 OK empty for line with no comments")
    void getByLine_noComments_returnsEmpty() throws Exception {
        when(commentService.getCommentsByLine(10L, 999)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/comments/file/10/line/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ════════════════════════════════════════════════════════════════
    // PUT /api/v1/comments/{commentId}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PUT /{commentId} - 200 OK on successful update")
    void updateComment_author_returns200() throws Exception {
        UpdateCommentRequest req = new UpdateCommentRequest();
        req.setContent("updated content");

        when(commentService.updateComment(eq(1L), eq(5L), any()))
                .thenReturn(buildResponse(1L, "updated content", 5, null, false));

        mockMvc.perform(put("/api/v1/comments/1")
                        .header("X-Auth-User-Id", 5L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("updated content"));
    }

    @Test
    @DisplayName("PUT /{commentId} - 403 Forbidden for non-author")
    void updateComment_nonAuthor_returns403() throws Exception {
        UpdateCommentRequest req = new UpdateCommentRequest();
        req.setContent("hacked");

        when(commentService.updateComment(eq(1L), eq(99L), any()))
                .thenThrow(new UnauthorizedAccessException("You can only modify your own comments."));

        mockMvc.perform(put("/api/v1/comments/1")
                        .header("X-Auth-User-Id", 99L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /{commentId} - 404 Not Found for unknown comment")
    void updateComment_notFound_returns404() throws Exception {
        UpdateCommentRequest req = new UpdateCommentRequest();
        req.setContent("content");

        when(commentService.updateComment(eq(99L), eq(5L), any()))
                .thenThrow(new CommentNotFoundException("Comment not found."));

        mockMvc.perform(put("/api/v1/comments/99")
                        .header("X-Auth-User-Id", 5L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════
    // DELETE /api/v1/comments/{commentId}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("DELETE /{commentId} - 200 OK on successful deletion")
    void deleteComment_author_returns200() throws Exception {
        doNothing().when(commentService).deleteComment(1L, 5L);

        mockMvc.perform(delete("/api/v1/comments/1")
                        .header("X-Auth-User-Id", 5L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Comment deleted."));
    }

    @Test
    @DisplayName("DELETE /{commentId} - 403 Forbidden for non-author")
    void deleteComment_nonAuthor_returns403() throws Exception {
        doThrow(new UnauthorizedAccessException("You can only modify your own comments."))
                .when(commentService).deleteComment(1L, 99L);

        mockMvc.perform(delete("/api/v1/comments/1")
                        .header("X-Auth-User-Id", 99L))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /{commentId} - 404 Not Found for unknown comment")
    void deleteComment_notFound_returns404() throws Exception {
        doThrow(new CommentNotFoundException("Comment not found."))
                .when(commentService).deleteComment(99L, 5L);

        mockMvc.perform(delete("/api/v1/comments/99")
                        .header("X-Auth-User-Id", 5L))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════
    // PUT /api/v1/comments/{commentId}/resolve
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PUT /{commentId}/resolve - 200 OK on successful resolve")
    void resolveComment_returns200() throws Exception {
        when(commentService.resolveComment(1L, 5L))
                .thenReturn(buildResponse(1L, "bug", 5, null, true));

        mockMvc.perform(put("/api/v1/comments/1/resolve")
                        .header("X-Auth-User-Id", 5L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resolved").value(true))
                .andExpect(jsonPath("$.message").value("Comment resolved."));
    }

    @Test
    @DisplayName("PUT /{commentId}/resolve - 404 Not Found for unknown comment")
    void resolveComment_notFound_returns404() throws Exception {
        when(commentService.resolveComment(99L, 5L))
                .thenThrow(new CommentNotFoundException("Comment not found."));

        mockMvc.perform(put("/api/v1/comments/99/resolve")
                        .header("X-Auth-User-Id", 5L))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════
    // PUT /api/v1/comments/{commentId}/unresolve
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PUT /{commentId}/unresolve - 200 OK on successful unresolve")
    void unresolveComment_returns200() throws Exception {
        when(commentService.unresolveComment(1L, 5L))
                .thenReturn(buildResponse(1L, "bug", 5, null, false));

        mockMvc.perform(put("/api/v1/comments/1/unresolve")
                        .header("X-Auth-User-Id", 5L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resolved").value(false))
                .andExpect(jsonPath("$.message").value("Comment unresolved."));
    }

    @Test
    @DisplayName("PUT /{commentId}/unresolve - 404 Not Found for unknown comment")
    void unresolveComment_notFound_returns404() throws Exception {
        when(commentService.unresolveComment(99L, 5L))
                .thenThrow(new CommentNotFoundException("Comment not found."));

        mockMvc.perform(put("/api/v1/comments/99/unresolve")
                        .header("X-Auth-User-Id", 5L))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/comments/file/{fileId}/count
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /file/{fileId}/count - 200 OK returns total comment count")
    void getCommentCount_returns200() throws Exception {
        when(commentService.getCommentCount(10L)).thenReturn(9);

        mockMvc.perform(get("/api/v1/comments/file/10/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(9));
    }

    @Test
    @DisplayName("GET /file/{fileId}/count - 200 OK returns 0 for file with no comments")
    void getCommentCount_noComments_returnsZero() throws Exception {
        when(commentService.getCommentCount(10L)).thenReturn(0);

        mockMvc.perform(get("/api/v1/comments/file/10/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(0));
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/comments/file/{fileId}/unresolved/count
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /file/{fileId}/unresolved/count - 200 OK returns unresolved count")
    void getUnresolvedCount_returns200() throws Exception {
        when(commentService.getUnresolvedCount(10L)).thenReturn(4);

        mockMvc.perform(get("/api/v1/comments/file/10/unresolved/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(4));
    }

    @Test
    @DisplayName("GET /file/{fileId}/unresolved/count - 200 OK returns 0 when all resolved")
    void getUnresolvedCount_allResolved_returnsZero() throws Exception {
        when(commentService.getUnresolvedCount(10L)).thenReturn(0);

        mockMvc.perform(get("/api/v1/comments/file/10/unresolved/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(0));
    }
}