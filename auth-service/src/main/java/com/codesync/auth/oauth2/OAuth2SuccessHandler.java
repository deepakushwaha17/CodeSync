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
import org.springframework.security.oauth2.client.authentication
        .OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication
        .SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.*;

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
            Authentication authentication)
            throws IOException {

        OAuth2AuthenticationToken oauthToken =
                (OAuth2AuthenticationToken) authentication;

        String registrationId = oauthToken
                .getAuthorizedClientRegistrationId()
                .toLowerCase();

        OAuth2User oAuth2User =
                oauthToken.getPrincipal();

        Map<String, Object> attributes =
                oAuth2User.getAttributes();

        // ── Extract email based on provider ──────────
        // Google: email is directly in attributes
        // GitHub: OAuth2UserServiceImpl stores the
        //         verified email in DB, read from there
        String email = null;

        if ("google".equals(registrationId)) {
            email = (String) attributes.get("email");

        } else if ("github".equals(registrationId)) {
            // GitHub email may be null in attributes
            // if user has private email setting.
            // But OAuth2UserServiceImpl already saved
            // the user with correct email via API call.
            // Look up by providerId instead.
            Object idObj = attributes.get("id");
            if (idObj != null) {
                String providerId =
                        String.valueOf(idObj);
                email = userRepository
                        .findByProviderAndProviderId(
                                com.codesync.auth
                                        .enums.Provider.GITHUB,
                                providerId)
                        .map(User::getEmail)
                        .orElse(null);
            }
            // Fallback: try direct attribute
            if (email == null) {
                Object attrEmail =
                        attributes.get("email");
                if (attrEmail != null
                        && !attrEmail.toString()
                        .isBlank()) {
                    email = attrEmail.toString();
                }
            }
        }

        if (email == null || email.isBlank()) {
            log.error(
                    "Could not resolve email for " +
                            "provider: {}", registrationId);
            response.sendRedirect(
                    "http://localhost:3000/auth"
                            + "?error=email_not_found");
            return;
        }

        // ── Find user in DB ───────────────────────────
        Optional<User> userOpt =
                userRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            log.error(
                    "User not found in DB after OAuth2: {}",
                    email);
            response.sendRedirect(
                    "http://localhost:3000/auth"
                            + "?error=user_not_found");
            return;
        }

        User user = userOpt.get();
        String token = generateToken(user);

        // ── Redirect to frontend /oauth2/success ──────
        String redirectUrl =
                "http://localhost:3000/oauth2/success"
                        + "?token="    + token
                        + "&userId="   + user.getUserId()
                        + "&username=" + encode(user.getUsername())
                        + "&email="    + encode(user.getEmail())
                        + "&role="     + user.getRole()
                        + "&provider=" + user.getProvider()
                        + "&fullName=" + encode(
                        user.getFullName() != null
                                ? user.getFullName()
                                : user.getUsername());

        log.info("OAuth2 success for: {} via {}",
                email, registrationId);

        getRedirectStrategy()
                .sendRedirect(request, response,
                        redirectUrl);
    }

    private String encode(String value) {
        if (value == null) return "";
        try {
            return java.net.URLEncoder.encode(
                    value,
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
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
                        System.currentTimeMillis()
                                + jwtExpiration))
                .signWith(getSigningKey(),
                        SignatureAlgorithm.HS256)
                .compact();
    }

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(
                jwtSecret.getBytes(
                        StandardCharsets.UTF_8));
    }
}