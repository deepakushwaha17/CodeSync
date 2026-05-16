package com.codesync.auth.controller;

import com.codesync.auth.dto.request.*;
import com.codesync.auth.dto.response.ApiResponse;
import com.codesync.auth.dto.response.AuthResponse;
import com.codesync.auth.dto.response.OtpResponse;
import com.codesync.auth.dto.response.UserResponse;
import com.codesync.auth.service.AuthService;
import com.codesync.auth.service.OtpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import java.io.IOException;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth Service", description = "Authentication and User Management APIs")
public class AuthController {

    private final AuthService authService;
    private final OtpService otpService;

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

    @PostMapping("/users/{userId}/avatar")
    @Operation(summary = "Upload user avatar")
    public ResponseEntity<ApiResponse<UserResponse>> uploadAvatar(
            @PathVariable Long userId,
            @RequestParam("file") MultipartFile file) {

        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.<UserResponse>builder()
                                .success(false)
                                .message("File is empty.")
                                .build());
            }

            String contentType = file.getContentType();

            if (contentType == null ||
                    !(contentType.equals("image/jpeg") ||
                            contentType.equals("image/png") ||
                            contentType.equals("image/gif") ||
                            contentType.equals("image/webp"))) {

                return ResponseEntity.badRequest()
                        .body(ApiResponse.<UserResponse>builder()
                                .success(false)
                                .message("Only JPG, PNG, GIF, WEBP images are allowed.")
                                .build());
            }

            if (file.getSize() > 2 * 1024 * 1024) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.<UserResponse>builder()
                                .success(false)
                                .message("Image size must be less than 2MB.")
                                .build());
            }

            String uploadDir = "uploads/avatars/";
            Files.createDirectories(Paths.get(uploadDir));

            String originalName = file.getOriginalFilename();
            String extension = "";

            if (originalName != null && originalName.contains(".")) {
                extension = originalName.substring(originalName.lastIndexOf("."));
            }

            String fileName = UUID.randomUUID() + extension;
            Path filePath = Paths.get(uploadDir + fileName);

            Files.write(filePath, file.getBytes());

            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setAvatarUrl(
                    "http://localhost:8081/uploads/avatars/" + fileName
            );

            UserResponse updated =
                    authService.updateProfile(userId, request);

            return ResponseEntity.ok(
                    ApiResponse.success("Avatar uploaded successfully.", updated)
            );

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<UserResponse>builder()
                            .success(false)
                            .message("Avatar upload failed: " + e.getMessage())
                            .build());
        }
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
    @GetMapping("/oauth2/authorize/github")
    @Operation(summary = "Initiate GitHub OAuth2 login")
    public void githubLogin(HttpServletResponse response) throws IOException {
        response.sendRedirect("/oauth2/authorization/github");
    }

    // ── GET /api/v1/auth/oauth2/authorize/google ──────────────────────
    @GetMapping("/oauth2/authorize/google")
    @Operation(summary = "Initiate Google OAuth2 login")
    public void googleLogin(HttpServletResponse response) throws IOException {
        response.sendRedirect("/oauth2/authorization/google");
    }

    /**
     * login/signup — send OTP to email.
     * POST /api/v1/auth/otp/send
     */
    @PostMapping("/otp/send")
    public ResponseEntity<ApiResponse<OtpResponse>>
    sendOtp(
            @Valid @RequestBody
            SendOtpRequest request) {

        log.info("OTP send request for: {}",
                request.getEmail());

        OtpResponse response = otpService.sendOtp(
                request.getEmail(),
                request.getPurpose()
        );

        if (!response.getSuccess()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<OtpResponse>builder()
                            .success(false)
                            .message(response.getMessage())
                            .build());
        }

        return ResponseEntity.ok(
                ApiResponse.<OtpResponse>builder()
                        .success(true)
                        .message(response.getMessage())
                        .data(response)
                        .build()
        );
    }

    /**
     * login/signup — verify OTP.
     * POST /api/v1/auth/otp/verify
     */
    @PostMapping("/otp/verify")
    public ResponseEntity<ApiResponse<String>>
    verifyOtp(
            @Valid @RequestBody
            VerifyOtpRequest request) {

        log.info("OTP verify request for: {}",
                request.getEmail());

        try {
            boolean valid = otpService.verifyOtp(
                    request.getEmail(),
                    request.getOtp(),
                    request.getPurpose()
            );

            if (valid) {
                return ResponseEntity.ok(
                        ApiResponse.<String>builder()
                                .success(true)
                                .message("OTP verified " +
                                        "successfully.")
                                .data("VERIFIED")
                                .build()
                );
            }

            return ResponseEntity.badRequest()
                    .body(ApiResponse.<String>builder()
                            .success(false)
                            .message("Invalid OTP.")
                            .build());

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<String>builder()
                            .success(false)
                            .message(e.getMessage())
                            .build());
        }
    }

    /**
     * Check if email exists (for signup validation).
     * GET /api/v1/auth/check-email?email=...
     */
    @GetMapping("/check-email")
    public ResponseEntity<ApiResponse<Boolean>>
    checkEmail(
            @RequestParam String email) {

        boolean exists =
                otpService.isEmailRegistered(email);

        return ResponseEntity.ok(
                ApiResponse.<Boolean>builder()
                        .success(true)
                        .message(exists
                                ? "Email already registered."
                                : "Email available.")
                        .data(exists)
                        .build()
        );
    }
}