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
import com.codesync.auth.service.AuthService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private Long jwtExpiration;

    @Value("${jwt.refresh-expiration}")
    private Long refreshExpiration;

    // ─────────────────────────────────────────────────────────────────
    // AUTHENTICATION
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Registering new user with email: {}", request.getEmail());

        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException(
                    "Email '" + request.getEmail() + "' is already registered.");
        }

        // Check if username already exists
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException(
                    "Username '" + request.getUsername() + "' is already taken.");
        }

        // Build and save user entity
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(Role.DEVELOPER)
                .provider(Provider.LOCAL)
                .isActive(true)
                .build();

        User savedUser = userRepository.save(user);
        log.info("User registered successfully with ID: {}", savedUser.getUserId());

        // Generate tokens
        String accessToken  = generateAccessToken(savedUser);
        String refreshToken = generateRefreshToken(savedUser);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtExpiration)
                .user(mapToUserResponse(savedUser))
                .build();
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        // Find user by email
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException(
                        "Invalid email or password."));

        // Check if account is active
        if (!user.getIsActive()) {
            throw new InvalidCredentialsException(
                    "Your account has been deactivated. Please contact support.");
        }

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password.");
        }

        log.info("User logged in successfully: {}", user.getEmail());

        String accessToken  = generateAccessToken(user);
        String refreshToken = generateRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtExpiration)
                .user(mapToUserResponse(user))
                .build();
    }

    @Override
    public AuthResponse refreshToken(String refreshToken) {
        log.info("Refreshing token");

        // Validate the refresh token
        if (!validateToken(refreshToken)) {
            throw new InvalidCredentialsException(
                    "Refresh token is invalid or expired. Please login again.");
        }

        // Extract user info from refresh token
        Claims claims = extractAllClaims(refreshToken);
        String email  = claims.getSubject();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found for token refresh."));

        if (!user.getIsActive()) {
            throw new InvalidCredentialsException("Account is deactivated.");
        }

        // Issue new access token
        String newAccessToken  = generateAccessToken(user);
        String newRefreshToken = generateRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(jwtExpiration)
                .user(mapToUserResponse(user))
                .build();
    }

    // ─────────────────────────────────────────────────────────────────
    // USER RETRIEVAL
    // ─────────────────────────────────────────────────────────────────

    @Override
    public UserResponse getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found with ID: " + userId));
        return mapToUserResponse(user);
    }

    @Override
    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found with email: " + email));
        return mapToUserResponse(user);
    }

    @Override
    public List<UserResponse> searchUsers(String keyword) {
        return userRepository.searchByUsername(keyword)
                .stream()
                .map(this::mapToUserResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::mapToUserResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────
    // PROFILE MANAGEMENT
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found with ID: " + userId));

        // Only update fields that are provided (non-null)
        if (request.getUsername() != null) {
            // Check username is not taken by someone else
            if (!request.getUsername().equals(user.getUsername())
                    && userRepository.existsByUsername(request.getUsername())) {
                throw new UserAlreadyExistsException(
                        "Username '" + request.getUsername() + "' is already taken.");
            }
            user.setUsername(request.getUsername());
        }

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }

        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }

        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        User updatedUser = userRepository.save(user);
        log.info("Profile updated for user ID: {}", userId);

        return mapToUserResponse(updatedUser);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found with ID: " + userId));

        // Verify current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException(
                    "Current password is incorrect.");
        }

        // Encode and save new password
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        log.info("Password changed for user ID: {}", userId);
    }

    @Override
    @Transactional
    public void deactivateAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found with ID: " + userId));

        user.setIsActive(false);
        userRepository.save(user);

        log.info("Account deactivated for user ID: {}", userId);
    }

    // ─────────────────────────────────────────────────────────────────
    // JWT HELPERS
    // ─────────────────────────────────────────────────────────────────

    @Override
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            log.warn("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    private String generateAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getUserId());
        claims.put("role",   user.getRole().name());
        claims.put("email",  user.getEmail());

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getEmail())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private String generateRefreshToken(User user) {
        return Jwts.builder()
                .setSubject(user.getEmail())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // ─────────────────────────────────────────────────────────────────
    // MAPPER
    // ─────────────────────────────────────────────────────────────────

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .avatarUrl(user.getAvatarUrl())
                .provider(user.getProvider())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .bio(user.getBio())
                .build();
    }
}