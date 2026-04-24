package com.codesync.file.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateFolderRequest {

    @NotNull(message = "Project ID is required")
    private Long projectId;

    @NotBlank(message = "Folder name is required")
    @Size(max = 255, message = "Folder name must be under 255 characters")
    private String name;

    // Parent path — empty string means root
    @NotNull(message = "Parent path is required (use empty string for root)")
    private String parentPath;
}