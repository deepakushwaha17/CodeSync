package com.codesync.file.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateFileRequest {

    @NotNull(message = "Project ID is required")
    private Long projectId;

    @NotBlank(message = "File name is required")
    @Size(max = 255, message = "File name must be under 255 characters")
    private String name;

    // Parent folder path — empty string means root
    // e.g. "src/main" means create file inside src/main/
    @NotNull(message = "Parent path is required (use empty string for root)")
    private String parentPath;

    @Size(max = 50, message = "Language must be under 50 characters")
    private String language;

    // Optional initial content
    private String content;
}