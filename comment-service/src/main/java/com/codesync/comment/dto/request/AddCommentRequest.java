package com.codesync.comment.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddCommentRequest {

    @NotNull(message = "Project ID is required")
    private Long projectId;

    @NotNull(message = "File ID is required")
    private Long fileId;

    @NotBlank(message = "Comment content is required")
    @Size(max = 2000,
            message = "Comment must be under 2000 characters")
    private String content;

    @NotNull(message = "Line number is required")
    @Min(value = 1, message = "Line number must be at least 1")
    private Integer lineNumber;

    // Optional
    private Integer columnNumber;

    // Null = top-level comment, set = reply to another comment
    private Long parentCommentId;

    // Optional snapshot reference
    private Long snapshotId;
}