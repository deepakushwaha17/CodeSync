package com.codesync.auth.service;

import com.codesync.auth.dto.request.ChangePasswordRequest;
import com.codesync.auth.dto.request.LoginRequest;
import com.codesync.auth.dto.request.RegisterRequest;
import com.codesync.auth.dto.request.UpdateProfileRequest;
import com.codesync.auth.dto.response.AuthResponse;
import com.codesync.auth.dto.response.UserResponse;

import java.util.List;

public interface AuthService {

    // Authentication
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    AuthResponse refreshToken(String refreshToken);

    // User retrieval
    UserResponse getUserById(Long userId);
    UserResponse getUserByEmail(String email);
    List<UserResponse> searchUsers(String keyword);
    List<UserResponse> getAllUsers();

    // Profile management
    UserResponse updateProfile(Long userId, UpdateProfileRequest request);
    void changePassword(Long userId, ChangePasswordRequest request);
    void deactivateAccount(Long userId);

    // Token validation (used by gateway and other services)
    boolean validateToken(String token);
}