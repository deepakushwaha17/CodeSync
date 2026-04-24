package com.codesync.file.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateFileContentRequest {

    @NotNull(message = "Content is required")
    private String content;
}