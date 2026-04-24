package com.codesync.execution.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubmitExecutionRequest {

    // Optional — links execution to a project
    private Long projectId;

    // Optional — links execution to a file
    private Long fileId;

    @NotBlank(message = "Language is required")
    @Size(max = 50)
    private String language;

    @NotBlank(message = "Source code is required")
    private String sourceCode;

    // Optional standard input
    private String stdin;
}