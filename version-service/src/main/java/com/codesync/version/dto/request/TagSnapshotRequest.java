package com.codesync.version.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TagSnapshotRequest {

    @NotBlank(message = "Tag name is required")
    @Size(max = 100,
            message = "Tag must be under 100 characters")
    private String tag;
}