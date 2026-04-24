package com.codesync.version.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateBranchRequest {

    @NotNull(message = "Project ID is required")
    private Long projectId;

    @NotNull(message = "File ID is required")
    private Long fileId;

    @NotBlank(message = "Branch name is required")
    @Size(max = 100,
            message = "Branch name must be under 100 characters")
    private String branchName;

    // Create branch from this snapshot
    @NotNull(message = "Source snapshot ID is required")
    private Long fromSnapshotId;
}