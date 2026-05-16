package com.codesync.auth.controller;

import com.codesync.auth.security.JwtAuthenticationFilter;
import com.codesync.auth.security.UserDetailsServiceImpl;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import com.codesync.auth.dto.request.*;
import com.codesync.auth.dto.response.AuthResponse;
import com.codesync.auth.dto.response.OtpResponse;
import com.codesync.auth.dto.response.UserResponse;
import com.codesync.auth.enums.Provider;
import com.codesync.auth.enums.Role;
import com.codesync.auth.exception.InvalidCredentialsException;
import com.codesync.auth.exception.UserAlreadyExistsException;
import com.codesync.auth.exception.UserNotFoundException;
import com.codesync.auth.service.AuthService;
import com.codesync.auth.service.OtpService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = AuthController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class
        }
)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AuthService authService;
    @MockBean private OtpService otpService;

    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean private UserDetailsServiceImpl userDetailsService;

    // ── Fixtures ──────────────────────────────────────────────────────────

    private UserResponse buildUserResponse() {
        return UserResponse.builder()
                .userId(1L)
                .username("testuser")
                .email("test@example.com")
                .fullName("Test User")
                .role(Role.DEVELOPER)
                .provider(Provider.LOCAL)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private AuthResponse buildAuthResponse() {
        return AuthResponse.builder()
                .accessToken("access.jwt.token")
                .refreshToken("refresh.jwt.token")
                .expiresIn(3600000L)
                .user(buildUserResponse())
                .build();
    }

    // ════════════════════════════════════════════════════════════════════
    // POST /api/v1/auth/register
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /register - 201 Created on successful registration")
    void register_validRequest_returns201() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("newuser");
        req.setEmail("new@example.com");
        req.setPassword("password123");
        req.setFullName("New User");

        when(authService.register(any(RegisterRequest.class))).thenReturn(buildAuthResponse());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access.jwt.token"))
                .andExpect(jsonPath("$.data.user.email").value("test@example.com"));
    }

    @Test
    @DisplayName("POST /register - 400 Bad Request when email is blank")
    void register_blankEmail_returns400() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("newuser");
        req.setEmail("");           // invalid
        req.setPassword("pass123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /register - 409 Conflict when email already exists")
    void register_emailConflict_returns409() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("user");
        req.setEmail("taken@example.com");
        req.setPassword("pass123");

        when(authService.register(any())).thenThrow(
                new UserAlreadyExistsException("Email already registered."));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    // ════════════════════════════════════════════════════════════════════
    // POST /api/v1/auth/login
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /login - 200 OK on successful login")
    void login_validCredentials_returns200() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("user@example.com");
        req.setPassword("password");

        when(authService.login(any(LoginRequest.class))).thenReturn(buildAuthResponse());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists());
    }

    @Test
    @DisplayName("POST /login - 401 Unauthorized for wrong credentials")
    void login_wrongCredentials_returns401() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("user@example.com");
        req.setPassword("wrongpass");

        when(authService.login(any())).thenThrow(
                new InvalidCredentialsException("Invalid email or password."));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /login - 400 Bad Request when password is blank")
    void login_blankPassword_returns400() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("user@example.com");
        req.setPassword("");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ════════════════════════════════════════════════════════════════════
    // POST /api/v1/auth/refresh
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /refresh - 200 OK with valid refresh token")
    void refresh_validToken_returns200() throws Exception {
        when(authService.refreshToken("valid.refresh.token")).thenReturn(buildAuthResponse());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("Refresh-Token", "valid.refresh.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists());
    }

    @Test
    @DisplayName("POST /refresh - 401 Unauthorized for invalid refresh token")
    void refresh_invalidToken_returns401() throws Exception {
        when(authService.refreshToken("bad.token"))
                .thenThrow(new InvalidCredentialsException("Token invalid or expired."));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("Refresh-Token", "bad.token"))
                .andExpect(status().isUnauthorized());
    }

    // ════════════════════════════════════════════════════════════════════
    // GET /api/v1/auth/users/{userId}
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /users/{userId} - 200 OK for existing user")
    void getUserById_existingUser_returns200() throws Exception {
        when(authService.getUserById(1L)).thenReturn(buildUserResponse());

        mockMvc.perform(get("/api/v1/auth/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.username").value("testuser"));
    }

    @Test
    @DisplayName("GET /users/{userId} - 404 Not Found for missing user")
    void getUserById_missingUser_returns404() throws Exception {
        when(authService.getUserById(99L))
                .thenThrow(new UserNotFoundException("User not found with ID: 99"));

        mockMvc.perform(get("/api/v1/auth/users/99"))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════════
    // GET /api/v1/auth/users/email/{email}
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /users/email/{email} - 200 OK for existing email")
    void getUserByEmail_existingEmail_returns200() throws Exception {
        when(authService.getUserByEmail("test@example.com")).thenReturn(buildUserResponse());

        mockMvc.perform(get("/api/v1/auth/users/email/test@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("test@example.com"));
    }

    @Test
    @DisplayName("GET /users/email/{email} - 404 Not Found for missing email")
    void getUserByEmail_missingEmail_returns404() throws Exception {
        when(authService.getUserByEmail("ghost@example.com"))
                .thenThrow(new UserNotFoundException("User not found"));

        mockMvc.perform(get("/api/v1/auth/users/email/ghost@example.com"))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════════
    // GET /api/v1/auth/users/search?keyword=
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /users/search - 200 OK with results")
    void searchUsers_keyword_returns200WithList() throws Exception {
        when(authService.searchUsers("java")).thenReturn(List.of(buildUserResponse()));

        mockMvc.perform(get("/api/v1/auth/users/search").param("keyword", "java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName("GET /users/search - 200 OK with empty list when no match")
    void searchUsers_noMatch_returns200WithEmptyList() throws Exception {
        when(authService.searchUsers("zzz")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/auth/users/search").param("keyword", "zzz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ════════════════════════════════════════════════════════════════════
    // GET /api/v1/auth/users
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /users - 200 OK returns all users")
    void getAllUsers_returns200WithAllUsers() throws Exception {
        when(authService.getAllUsers()).thenReturn(
                List.of(buildUserResponse(), buildUserResponse()));

        mockMvc.perform(get("/api/v1/auth/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    // ════════════════════════════════════════════════════════════════════
    // PUT /api/v1/auth/users/{userId}/profile
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PUT /users/{userId}/profile - 200 OK on successful update")
    void updateProfile_validRequest_returns200() throws Exception {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setUsername("newname");
        req.setFullName("New Name");

        UserResponse updated = buildUserResponse();
        updated.setUsername("newname");
        when(authService.updateProfile(eq(1L), any(UpdateProfileRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/auth/users/1/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("newname"));
    }

    @Test
    @DisplayName("PUT /users/{userId}/profile - 404 when user not found")
    void updateProfile_userNotFound_returns404() throws Exception {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setUsername("name");

        when(authService.updateProfile(eq(99L), any()))
                .thenThrow(new UserNotFoundException("User not found"));

        mockMvc.perform(put("/api/v1/auth/users/99/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════════
    // PUT /api/v1/auth/users/{userId}/password
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PUT /users/{userId}/password - 200 OK on successful password change")
    void changePassword_validRequest_returns200() throws Exception {
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("oldPass1");
        req.setNewPassword("newPass123");

        doNothing().when(authService).changePassword(eq(1L), any(ChangePasswordRequest.class));

        mockMvc.perform(put("/api/v1/auth/users/1/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PUT /users/{userId}/password - 401 when current password is wrong")
    void changePassword_wrongCurrentPassword_returns401() throws Exception {
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("wrongPass");
        req.setNewPassword("newPass123");

        doThrow(new InvalidCredentialsException("Current password is incorrect."))
                .when(authService).changePassword(eq(1L), any());

        mockMvc.perform(put("/api/v1/auth/users/1/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // ════════════════════════════════════════════════════════════════════
    // PUT /api/v1/auth/users/{userId}/deactivate
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PUT /users/{userId}/deactivate - 200 OK on successful deactivation")
    void deactivateAccount_existingUser_returns200() throws Exception {
        doNothing().when(authService).deactivateAccount(1L);

        mockMvc.perform(put("/api/v1/auth/users/1/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Account deactivated successfully."));
    }

    @Test
    @DisplayName("PUT /users/{userId}/deactivate - 404 when user not found")
    void deactivateAccount_userNotFound_returns404() throws Exception {
        doThrow(new UserNotFoundException("User not found."))
                .when(authService).deactivateAccount(99L);

        mockMvc.perform(put("/api/v1/auth/users/99/deactivate"))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════════
    // GET /api/v1/auth/validate?token=
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /validate - 200 OK returns true for valid token")
    void validateToken_validToken_returnsTrue() throws Exception {
        when(authService.validateToken("good.token")).thenReturn(true);

        mockMvc.perform(get("/api/v1/auth/validate").param("token", "good.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    @DisplayName("GET /validate - 200 OK returns false for invalid token")
    void validateToken_invalidToken_returnsFalse() throws Exception {
        when(authService.validateToken("bad.token")).thenReturn(false);

        mockMvc.perform(get("/api/v1/auth/validate").param("token", "bad.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));
    }

    // ════════════════════════════════════════════════════════════════════
    // POST /api/v1/auth/otp/send
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /otp/send - 200 OK when OTP sent successfully")
    void sendOtp_success_returns200() throws Exception {
        SendOtpRequest req = new SendOtpRequest();
        req.setEmail("user@test.com");
        req.setPurpose("LOGIN");

        OtpResponse otpResponse = OtpResponse.builder()
                .success(true)
                .message("OTP sent to your email.")
                .maskedEmail("us***@test.com")
                .expiryMinutes(5)
                .build();

        when(otpService.sendOtp("user@test.com", "LOGIN")).thenReturn(otpResponse);

        mockMvc.perform(post("/api/v1/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.maskedEmail").value("us***@test.com"));
    }

    @Test
    @DisplayName("POST /otp/send - 400 Bad Request when OTP sending fails")
    void sendOtp_failure_returns400() throws Exception {
        SendOtpRequest req = new SendOtpRequest();
        req.setEmail("nobody@test.com");
        req.setPurpose("LOGIN");

        OtpResponse failResponse = OtpResponse.builder()
                .success(false)
                .message("No account found with this email.")
                .build();

        when(otpService.sendOtp("nobody@test.com", "LOGIN")).thenReturn(failResponse);

        mockMvc.perform(post("/api/v1/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ════════════════════════════════════════════════════════════════════
    // POST /api/v1/auth/otp/verify
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /otp/verify - 200 OK for correct OTP")
    void verifyOtp_correctOtp_returns200() throws Exception {
        VerifyOtpRequest req = new VerifyOtpRequest();
        req.setEmail("user@test.com");
        req.setOtp("123456");
        req.setPurpose("LOGIN");

        when(otpService.verifyOtp("user@test.com", "123456", "LOGIN")).thenReturn(true);

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("VERIFIED"));
    }

    @Test
    @DisplayName("POST /otp/verify - 400 Bad Request for incorrect OTP")
    void verifyOtp_incorrectOtp_returns400() throws Exception {
        VerifyOtpRequest req = new VerifyOtpRequest();
        req.setEmail("user@test.com");
        req.setOtp("999999");
        req.setPurpose("LOGIN");

        when(otpService.verifyOtp("user@test.com", "999999", "LOGIN"))
                .thenThrow(new RuntimeException("Incorrect OTP. 2 attempts remaining."));

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ════════════════════════════════════════════════════════════════════
    // GET /api/v1/auth/check-email?email=
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /check-email - 200 OK returns true when email is registered")
    void checkEmail_existingEmail_returnsTrue() throws Exception {
        when(otpService.isEmailRegistered("taken@test.com")).thenReturn(true);

        mockMvc.perform(get("/api/v1/auth/check-email").param("email", "taken@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true))
                .andExpect(jsonPath("$.message").value("Email already registered."));
    }

    @Test
    @DisplayName("GET /check-email - 200 OK returns false when email is available")
    void checkEmail_newEmail_returnsFalse() throws Exception {
        when(otpService.isEmailRegistered("new@test.com")).thenReturn(false);

        mockMvc.perform(get("/api/v1/auth/check-email").param("email", "new@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false))
                .andExpect(jsonPath("$.message").value("Email available."));
    }
}
