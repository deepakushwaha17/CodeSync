package com.codesync.auth.dto.response;

import com.codesync.auth.enums.Provider;
import com.codesync.auth.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long userId;
    private String username;
    private String email;
    private String fullName;
    private Role role;
    private String avatarUrl;
    private Provider provider;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private String bio;
}