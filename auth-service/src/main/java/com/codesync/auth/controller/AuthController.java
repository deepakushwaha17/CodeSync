package com.codesync.auth.controller;

import com.codesync.auth.dto.request.ChangePasswordRequest;
import com.codesync.auth.dto.request.LoginRequest;
import com.codesync.auth.dto.request.RegisterRequest;
import com.codesync.auth.dto.request.UpdateProfileRequest;
import com.codesync.auth.dto.response.ApiResponse;
import com.codesync.auth.dto.response.AuthResponse;
import com.codesync.auth.dto.response.UserResponse;
import com.codesync.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth Service", description = "Authentication and User Management APIs")
public class AuthController {

    private final AuthService authService;

    // ── POST /api/v1/auth/register ────────────────────────────────────
    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {

        AuthResponse response = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("User registered successfully.", response));
    }

    // ── POST /api/v1/auth/login ───────────────────────────────────────
    @PostMapping("/login")
    @Operation(summary = "Login and receive JWT tokens")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(
                ApiResponse.success("Login successful.", response));
    }

    // ── POST /api/v1/auth/refresh ─────────────────────────────────────
    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @RequestHeader("Refresh-Token") String refreshToken) {

        AuthResponse response = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(
                ApiResponse.success("Token refreshed successfully.", response));
    }

    // ── GET /api/v1/auth/users/{userId} ───────────────────────────────
    @GetMapping("/users/{userId}")
    @Operation(summary = "Get user by ID")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(
            @PathVariable Long userId) {

        UserResponse user = authService.getUserById(userId);
        return ResponseEntity.ok(
                ApiResponse.success("User fetched successfully.", user));
    }

    // ── GET /api/v1/auth/users/email/{email} ──────────────────────────
    @GetMapping("/users/email/{email}")
    @Operation(summary = "Get user by email")
    public ResponseEntity<ApiResponse<UserResponse>> getUserByEmail(
            @PathVariable String email) {

        UserResponse user = authService.getUserByEmail(email);
        return ResponseEntity.ok(
                ApiResponse.success("User fetched successfully.", user));
    }

    // ── GET /api/v1/auth/users/search?keyword= ────────────────────────
    @GetMapping("/users/search")
    @Operation(summary = "Search users by username")
    public ResponseEntity<ApiResponse<List<UserResponse>>> searchUsers(
            @RequestParam String keyword) {

        List<UserResponse> users = authService.searchUsers(keyword);
        return ResponseEntity.ok(
                ApiResponse.success("Search results fetched.", users));
    }

    // ── GET /api/v1/auth/users ────────────────────────────────────────
    @GetMapping("/users")
    @Operation(summary = "Get all users (Admin only)")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {

        List<UserResponse> users = authService.getAllUsers();
        return ResponseEntity.ok(
                ApiResponse.success("All users fetched.", users));
    }

    // ── PUT /api/v1/auth/users/{userId}/profile ───────────────────────
    @PutMapping("/users/{userId}/profile")
    @Operation(summary = "Update user profile")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateProfileRequest request) {

        UserResponse updated = authService.updateProfile(userId, request);
        return ResponseEntity.ok(
                ApiResponse.success("Profile updated successfully.", updated));
    }

    // ── PUT /api/v1/auth/users/{userId}/password ──────────────────────
    @PutMapping("/users/{userId}/password")
    @Operation(summary = "Change user password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @PathVariable Long userId,
            @Valid @RequestBody ChangePasswordRequest request) {

        authService.changePassword(userId, request);
        return ResponseEntity.ok(
                ApiResponse.success("Password changed successfully."));
    }

    // ── PUT /api/v1/auth/users/{userId}/deactivate ────────────────────
    @PutMapping("/users/{userId}/deactivate")
    @Operation(summary = "Deactivate a user account")
    public ResponseEntity<ApiResponse<Void>> deactivateAccount(
            @PathVariable Long userId) {

        authService.deactivateAccount(userId);
        return ResponseEntity.ok(
                ApiResponse.success("Account deactivated successfully."));
    }

    // ── GET /api/v1/auth/validate?token= ─────────────────────────────
    @GetMapping("/validate")
    @Operation(summary = "Validate a JWT token")
    public ResponseEntity<ApiResponse<Boolean>> validateToken(
            @RequestParam String token) {

        boolean isValid = authService.validateToken(token);
        return ResponseEntity.ok(
                ApiResponse.success("Token validation result.", isValid));
    }

    // ── GET /api/v1/auth/oauth2/authorize/github ──────────────────────
// Frontend calls this URL to start GitHub OAuth2 flow
    @GetMapping("/oauth2/authorize/github")
    @Operation(summary = "Initiate GitHub OAuth2 login")
    public ResponseEntity<ApiResponse<String>> githubLogin(
            HttpServletResponse response) throws IOException {

        String githubAuthUrl = "/api/v1/auth/oauth2/authorize/github";
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Redirect to GitHub for authentication.",
                        githubAuthUrl));
    }

    // ── GET /api/v1/auth/oauth2/authorize/google ──────────────────────
// Frontend calls this URL to start Google OAuth2 flow
    @GetMapping("/oauth2/authorize/google")
    @Operation(summary = "Initiate Google OAuth2 login")
    public ResponseEntity<ApiResponse<String>> googleLogin() {
        String googleAuthUrl = "/api/v1/auth/oauth2/authorize/google";
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Redirect to Google for authentication.",
                        googleAuthUrl));
    }
}