package com.codesync.comment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentResponse {

    private Long commentId;
    private Long projectId;
    private Long fileId;
    private Long authorId;
    private String content;
    private Integer lineNumber;
    private Integer columnNumber;
    private Long parentCommentId;
    private Boolean resolved;
    private Long snapshotId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Parsed @mentions from content
    private List<String> mentions;

    // Reply count for top-level comments
    private Integer replyCount;
}