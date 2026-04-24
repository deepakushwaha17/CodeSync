package com.codesync.collab.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateSessionRequest {

    @NotNull(message = "Project ID is required")
    private Long projectId;

    @NotNull(message = "File ID is required")
    private Long fileId;

    @Size(max = 50)
    private String language;

    @Min(2) @Max(50)
    private Integer maxParticipants = 10;

    private Boolean isPasswordProtected = false;

    @Size(min = 4, max = 20,
            message = "Password must be 4-20 characters")
    private String sessionPassword;
}