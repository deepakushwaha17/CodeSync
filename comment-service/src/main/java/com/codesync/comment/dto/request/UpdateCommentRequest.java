package com.codesync.comment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateCommentRequest {

    @NotBlank(message = "Content is required")
    @Size(max = 2000,
            message = "Comment must be under 2000 characters")
    private String content;
}