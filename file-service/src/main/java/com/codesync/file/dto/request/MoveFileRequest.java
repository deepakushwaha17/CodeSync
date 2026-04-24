package com.codesync.file.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MoveFileRequest {

    // New parent folder path
    // empty string means move to root
    @NotNull(message = "New parent path is required")
    private String newParentPath;
}