package com.codesync.version.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateSnapshotRequest {

    @NotNull(message = "Project ID is required")
    private Long projectId;

    @NotNull(message = "File ID is required")
    private Long fileId;

    @NotBlank(message = "Commit message is required")
    @Size(max = 500,
            message = "Commit message must be under 500 characters")
    private String message;

    @NotBlank(message = "Content is required")
    private String content;

    // Optional — defaults to "main"
    private String branch;

    // Optional parent snapshot ID
    private Long parentSnapshotId;
}