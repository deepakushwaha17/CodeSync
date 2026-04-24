package com.codesync.project.dto.request;

import com.codesync.project.enums.MemberRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddMemberRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Member role is required")
    private MemberRole role;
}