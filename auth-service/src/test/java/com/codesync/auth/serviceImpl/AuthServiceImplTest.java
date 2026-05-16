package com.codesync.auth.serviceImpl;

import com.codesync.auth.dto.request.ChangePasswordRequest;
import com.codesync.auth.dto.request.LoginRequest;
import com.codesync.auth.dto.request.RegisterRequest;
import com.codesync.auth.dto.request.UpdateProfileRequest;
import com.codesync.auth.dto.response.AuthResponse;
import com.codesync.auth.dto.response.UserResponse;
import com.codesync.auth.entity.User;
import com.codesync.auth.enums.Provider;
import com.codesync.auth.enums.Role;
import com.codesync.auth.exception.InvalidCredentialsException;
import com.codesync.auth.exception.UserAlreadyExistsException;
import com.codesync.auth.exception.UserNotFoundException;
import com.codesync.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthServiceImpl.
 * All dependencies are mocked with Mockito.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks private AuthServiceImpl authService;

    // ── Setup ─────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        // Inject @Value fields via ReflectionTestUtils
        // Using a 32-char+ secret to satisfy HS256 minimum key size
        ReflectionTestUtils.setField(authService, "jwtSecret",
                "MySuperSecretKeyForTestingPurposesThatIs32Chars!!");
        ReflectionTestUtils.setField(authService, "jwtExpiration", 3600000L);   // 1 hour
        ReflectionTestUtils.setField(authService, "refreshExpiration", 86400000L); // 24 hours
    }

    private User buildUser(Long id, String username, String email) {
        return User.builder()
                .userId(id)
                .username(username)
                .email(email)
                .passwordHash("$2a$10$hashedpw")
                .fullName("Full Name")
                .role(Role.DEVELOPER)
                .provider(Provider.LOCAL)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ════════════════════════════════════════════════════════════════════
    // register()
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("register - success: returns AuthResponse with tokens")
    void register_validRequest_returnsAuthResponse() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setEmail("new@example.com");
        request.setPassword("password123");
        request.setFullName("New User");

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");

        User saved = buildUser(1L, "newuser", "new@example.com");
        saved.setPasswordHash("hashed");
        when(userRepository.save(any(User.class))).thenReturn(saved);

        AuthResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getUser().getEmail()).isEqualTo("new@example.com");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("register - throws UserAlreadyExistsException when email taken")
    void register_emailAlreadyExists_throwsException() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("taken@example.com");
        request.setUsername("someone");
        request.setPassword("pass123");

        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("taken@example.com");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register - throws UserAlreadyExistsException when username taken")
    void register_usernameAlreadyExists_throwsException() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("unique@example.com");
        request.setUsername("takenname");
        request.setPassword("pass123");

        when(userRepository.existsByEmail("unique@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("takenname")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("takenname");

        verify(userRepository, never()).save(any());
    }

    // ════════════════════════════════════════════════════════════════════
    // login()
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("login - success: returns AuthResponse with tokens")
    void login_validCredentials_returnsAuthResponse() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("correctPassword");

        User user = buildUser(1L, "user", "user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctPassword", user.getPasswordHash())).thenReturn(true);

        AuthResponse response = authService.login(request);

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getUser().getEmail()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("login - throws InvalidCredentialsException when email not found")
    void login_emailNotFound_throwsException() {
        LoginRequest request = new LoginRequest();
        request.setEmail("ghost@example.com");
        request.setPassword("anypass");

        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("login - throws InvalidCredentialsException when account is deactivated")
    void login_inactiveAccount_throwsException() {
        LoginRequest request = new LoginRequest();
        request.setEmail("inactive@example.com");
        request.setPassword("pass");

        User user = buildUser(1L, "inactive", "inactive@example.com");
        user.setIsActive(false);
        when(userRepository.findByEmail("inactive@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("deactivated");
    }

    @Test
    @DisplayName("login - throws InvalidCredentialsException when password is wrong")
    void login_wrongPassword_throwsException() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("wrongPassword");

        User user = buildUser(1L, "user", "user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPassword", user.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // ════════════════════════════════════════════════════════════════════
    // refreshToken()
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("refreshToken - success: returns new AuthResponse")
    void refreshToken_validToken_returnsNewTokens() {
        // First create a valid refresh token by logging in
        User user = buildUser(1L, "user", "refresh@example.com");
        when(userRepository.findByEmail("refresh@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("refresh@example.com");
        loginRequest.setPassword("pass");
        AuthResponse loginResponse = authService.login(loginRequest);

        // Now use the refresh token
        String refreshToken = loginResponse.getRefreshToken();

        AuthResponse newResponse = authService.refreshToken(refreshToken);

        assertThat(newResponse.getAccessToken()).isNotBlank();
        assertThat(newResponse.getRefreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("refreshToken - throws InvalidCredentialsException for invalid token")
    void refreshToken_invalidToken_throwsException() {
        assertThatThrownBy(() -> authService.refreshToken("invalid.jwt.token"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("invalid or expired");
    }

    // ════════════════════════════════════════════════════════════════════
    // getUserById()
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getUserById - returns UserResponse for existing user")
    void getUserById_existingId_returnsUser() {
        User user = buildUser(5L, "found", "found@example.com");
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        UserResponse response = authService.getUserById(5L);

        assertThat(response.getUserId()).isEqualTo(5L);
        assertThat(response.getUsername()).isEqualTo("found");
    }

    @Test
    @DisplayName("getUserById - throws UserNotFoundException for non-existing ID")
    void getUserById_nonExistingId_throwsException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getUserById(99L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ════════════════════════════════════════════════════════════════════
    // getUserByEmail()
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getUserByEmail - returns UserResponse for existing email")
    void getUserByEmail_existingEmail_returnsUser() {
        User user = buildUser(1L, "emailUser", "email@example.com");
        when(userRepository.findByEmail("email@example.com")).thenReturn(Optional.of(user));

        UserResponse response = authService.getUserByEmail("email@example.com");

        assertThat(response.getEmail()).isEqualTo("email@example.com");
    }

    @Test
    @DisplayName("getUserByEmail - throws UserNotFoundException when email not found")
    void getUserByEmail_nonExistingEmail_throwsException() {
        when(userRepository.findByEmail("noone@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getUserByEmail("noone@example.com"))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ════════════════════════════════════════════════════════════════════
    // searchUsers()
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("searchUsers - returns list of matching UserResponses")
    void searchUsers_matchingKeyword_returnsList() {
        User u1 = buildUser(1L, "javadev", "j@example.com");
        User u2 = buildUser(2L, "javaguru", "g@example.com");
        when(userRepository.searchByUsername("java")).thenReturn(List.of(u1, u2));

        List<UserResponse> results = authService.searchUsers("java");

        assertThat(results).hasSize(2);
        assertThat(results).extracting(UserResponse::getUsername)
                .containsExactlyInAnyOrder("javadev", "javaguru");
    }

    @Test
    @DisplayName("searchUsers - returns empty list when no match")
    void searchUsers_noMatch_returnsEmptyList() {
        when(userRepository.searchByUsername("xyz")).thenReturn(List.of());

        List<UserResponse> results = authService.searchUsers("xyz");

        assertThat(results).isEmpty();
    }

    // ════════════════════════════════════════════════════════════════════
    // getAllUsers()
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getAllUsers - returns all users as UserResponse list")
    void getAllUsers_multipleUsers_returnsAll() {
        when(userRepository.findAll()).thenReturn(List.of(
                buildUser(1L, "user1", "u1@example.com"),
                buildUser(2L, "user2", "u2@example.com")
        ));

        List<UserResponse> result = authService.getAllUsers();

        assertThat(result).hasSize(2);
    }

    // ════════════════════════════════════════════════════════════════════
    // updateProfile()
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("updateProfile - updates username, fullName, bio, avatarUrl")
    void updateProfile_validRequest_updatesFields() {
        User user = buildUser(1L, "oldname", "u@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("newname")).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setUsername("newname");
        req.setFullName("New Full Name");
        req.setBio("My bio");

        UserResponse response = authService.updateProfile(1L, req);

        assertThat(response.getUsername()).isEqualTo("newname");
        assertThat(response.getFullName()).isEqualTo("New Full Name");
        assertThat(response.getBio()).isEqualTo("My bio");
    }

    @Test
    @DisplayName("updateProfile - throws UserAlreadyExistsException if new username is taken")
    void updateProfile_usernameTaken_throwsException() {
        User user = buildUser(1L, "oldname", "u@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("takenname")).thenReturn(true);

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setUsername("takenname");

        assertThatThrownBy(() -> authService.updateProfile(1L, req))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("takenname");
    }

    @Test
    @DisplayName("updateProfile - throws UserNotFoundException for invalid userId")
    void updateProfile_userNotFound_throwsException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.updateProfile(99L, new UpdateProfileRequest()))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ════════════════════════════════════════════════════════════════════
    // changePassword()
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("changePassword - success: updates password hash")
    void changePassword_correctCurrentPassword_updatesPassword() {
        User user = buildUser(1L, "user", "u@example.com");
        user.setPasswordHash("oldHash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldPass", "oldHash")).thenReturn(true);
        when(passwordEncoder.encode("newPass")).thenReturn("newHash");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("oldPass");
        req.setNewPassword("newPass");

        assertThatCode(() -> authService.changePassword(1L, req))
                .doesNotThrowAnyException();

        verify(passwordEncoder).encode("newPass");
        verify(userRepository).save(argThat(u -> u.getPasswordHash().equals("newHash")));
    }

    @Test
    @DisplayName("changePassword - throws InvalidCredentialsException for wrong current password")
    void changePassword_wrongCurrentPassword_throwsException() {
        User user = buildUser(1L, "user", "u@example.com");
        user.setPasswordHash("correctHash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPass", "correctHash")).thenReturn(false);

        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("wrongPass");
        req.setNewPassword("newPass123");

        assertThatThrownBy(() -> authService.changePassword(1L, req))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("incorrect");
    }

    // ════════════════════════════════════════════════════════════════════
    // deactivateAccount()
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("deactivateAccount - sets isActive to false")
    void deactivateAccount_existingUser_setsInactive() {
        User user = buildUser(1L, "user", "u@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService.deactivateAccount(1L);

        verify(userRepository).save(argThat(u -> !u.getIsActive()));
    }

    @Test
    @DisplayName("deactivateAccount - throws UserNotFoundException for invalid userId")
    void deactivateAccount_userNotFound_throwsException() {
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.deactivateAccount(42L))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ════════════════════════════════════════════════════════════════════
    // validateToken()
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("validateToken - returns true for a valid token")
    void validateToken_validToken_returnsTrue() {
        // Generate a real token by doing a register
        RegisterRequest req = new RegisterRequest();
        req.setUsername("tokenuser");
        req.setEmail("tok@example.com");
        req.setPassword("pass1234");

        when(userRepository.existsByEmail("tok@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("tokenuser")).thenReturn(false);
        when(passwordEncoder.encode("pass1234")).thenReturn("hashed");

        User saved = buildUser(1L, "tokenuser", "tok@example.com");
        when(userRepository.save(any())).thenReturn(saved);

        AuthResponse authResponse = authService.register(req);
        String token = authResponse.getAccessToken();

        boolean valid = authService.validateToken(token);
        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("validateToken - returns false for a garbage token")
    void validateToken_invalidToken_returnsFalse() {
        boolean valid = authService.validateToken("this.is.not.a.real.jwt");

        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("validateToken - returns false for an empty string")
    void validateToken_emptyString_returnsFalse() {
        boolean valid = authService.validateToken("");

        assertThat(valid).isFalse();
    }
}