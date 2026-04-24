package com.codesync.project.dto.request;

import com.codesync.project.enums.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateProjectRequest {

    @NotBlank(message = "Project name is required")
    @Size(min = 1, max = 100, message = "Project name must be between 1 and 100 characters")
    private String name;

    @Size(max = 500, message = "Description must be under 500 characters")
    private String description;

    @Size(max = 50, message = "Language must be under 50 characters")
    private String language;

    @NotNull(message = "Visibility is required")
    private Visibility visibility;

    // Optional: create from a template project
    private Long templateId;
}