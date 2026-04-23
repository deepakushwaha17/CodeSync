package com.codesync.auth.oauth2;

import com.codesync.auth.entity.User;
import com.codesync.auth.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler
        extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private Long jwtExpiration;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {

        OAuth2AuthenticationToken oauthToken =
                (OAuth2AuthenticationToken) authentication;

        String providerName = oauthToken
                .getAuthorizedClientRegistrationId()
                .toUpperCase();

        OAuth2User oAuth2User = oauthToken.getPrincipal();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        log.info("OAuth2 success handler triggered for: {}",
                providerName);

        // Extract email
        String email = extractEmail(providerName, attributes);
        log.info("Looking up user with email: {}", email);

        // Find user
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException(
                        "User not found: " + email));

        // Generate JWT
        String token = generateToken(user);

        // Return JSON response directly in the browser
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(String.format(
                "{" +
                        "\"success\": true," +
                        "\"message\": \"OAuth2 login successful\"," +
                        "\"data\": {" +
                        "\"accessToken\": \"%s\"," +
                        "\"tokenType\": \"Bearer\"," +
                        "\"expiresIn\": %d," +
                        "\"user\": {" +
                        "\"userId\": %d," +
                        "\"username\": \"%s\"," +
                        "\"email\": \"%s\"," +
                        "\"role\": \"%s\"," +
                        "\"provider\": \"%s\"," +
                        "\"isActive\": %b" +
                        "}" +
                        "}" +
                        "}",
                token,
                jwtExpiration,
                user.getUserId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getProvider(),
                user.getIsActive()
        ));
    }

    private String extractEmail(
            String provider,
            Map<String, Object> attributes) {

        if ("GOOGLE".equals(provider)) {
            return (String) attributes.get("email");
        }

        if ("GITHUB".equals(provider)) {
            Object email = attributes.get("email");
            if (email != null && !email.toString().isBlank()) {
                return email.toString();
            }
            // Fallback for private GitHub email
            String login = (String) attributes.get("login");
            return userRepository.findByUsername(login)
                    .map(User::getEmail)
                    .orElse("github_"
                            + attributes.get("id")
                            + "@codesync.placeholder");
        }

        throw new RuntimeException(
                "Cannot extract email for provider: " + provider);
    }

    private String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getUserId());
        claims.put("role",   user.getRole().name());
        claims.put("email",  user.getEmail());

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getEmail())
                .setIssuedAt(new Date())
                .setExpiration(new Date(
                        System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(
                jwtSecret.getBytes(StandardCharsets.UTF_8));
    }
}