package com.codesync.file.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RenameFileRequest {

    @NotBlank(message = "New name is required")
    @Size(max = 255, message = "Name must be under 255 characters")
    private String newName;
}