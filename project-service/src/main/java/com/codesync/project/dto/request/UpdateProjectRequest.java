package com.codesync.project.dto.request;

import com.codesync.project.enums.Visibility;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProjectRequest {

    @Size(min = 1, max = 100, message = "Project name must be between 1 and 100 characters")
    private String name;

    @Size(max = 500, message = "Description must be under 500 characters")
    private String description;

    @Size(max = 50, message = "Language must be under 50 characters")
    private String language;

    private Visibility visibility;
}