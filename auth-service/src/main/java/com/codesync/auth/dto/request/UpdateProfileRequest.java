package com.codesync.auth.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    @Size(max = 100, message = "Full name must be under 100 characters")
    private String fullName;

    @Size(max = 500, message = "Bio must be under 500 characters")
    private String bio;

    @Size(max = 500, message = "Avatar URL must be under 500 characters")
    private String avatarUrl;
}