package com.codesync.auth.oauth2;

import com.codesync.auth.entity.User;
import com.codesync.auth.enums.Provider;
import com.codesync.auth.enums.Role;
import com.codesync.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2UserServiceImpl extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration()
                .getRegistrationId().toLowerCase();

        OAuth2User effectiveUser = oAuth2User;
        if ("github".equals(registrationId)) {
            Map<String, Object> attributes = new HashMap<>(oAuth2User.getAttributes());
            String email = attributes.get("email") != null
                    ? attributes.get("email").toString() : null;

            if (email == null || email.isBlank()) {
                email = fetchGithubPrimaryEmail(
                        userRequest.getAccessToken().getTokenValue());
                attributes.put("email", email);
                log.info("Fetched GitHub primary email via API: {}", email);
            }

            effectiveUser = new DefaultOAuth2User(
                    oAuth2User.getAuthorities(), attributes, "id");
        }

        OAuth2UserInfo userInfo = OAuth2UserInfoFactory.extract(
                registrationId, effectiveUser.getAttributes());

        if (userInfo.getEmail() == null || userInfo.getEmail().isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("email_not_found"),
                    "Email not found from OAuth2 provider: " + registrationId);
        }

        log.info("OAuth2 user loaded: provider={}, email={}", registrationId, userInfo.getEmail());

        Optional<User> existingUser = userRepository.findByProviderAndProviderId(
                Provider.valueOf(registrationId.toUpperCase()),
                userInfo.getProviderId()
        );

        if (existingUser.isPresent()) {
            User user = existingUser.get();
            if (userInfo.getAvatarUrl() != null) {
                user.setAvatarUrl(userInfo.getAvatarUrl());
                userRepository.save(user);
            }
            log.info("Existing OAuth2 user logged in: {}", user.getEmail());
        } else {
            registerNewUser(userInfo, registrationId);
        }

        return effectiveUser;
    }

    private void registerNewUser(OAuth2UserInfo userInfo, String registrationId) {
        String baseUsername = userInfo.getName() != null
                ? userInfo.getName().toLowerCase()
                  .replaceAll("\\s+", "")
                  .replaceAll("[^a-z0-9]", "")
                : registrationId + userInfo.getProviderId();

        if (baseUsername.isBlank()) {
            baseUsername = registrationId + userInfo.getProviderId();
        }

        String username = baseUsername;
        int counter = 1;
        while (userRepository.existsByUsername(username)) {
            username = baseUsername + counter++;
        }

        User newUser = User.builder()
                .username(username)
                .email(userInfo.getEmail())
                .passwordHash(UUID.randomUUID().toString())
                .fullName(userInfo.getName())
                .role(Role.DEVELOPER)
                .provider(Provider.valueOf(registrationId.toUpperCase()))
                .providerId(userInfo.getProviderId())   // FIX: store providerId
                .avatarUrl(userInfo.getAvatarUrl())
                .isActive(true)
                .build();

        userRepository.save(newUser);
        log.info("New OAuth2 user registered: {} via {}", newUser.getEmail(), registrationId);
    }

    private String fetchGithubPrimaryEmail(String accessToken) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("X-GitHub-Api-Version", "2022-11-28");

        ResponseEntity<List> response = restTemplate.exchange(
                "https://api.github.com/user/emails",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                List.class
        );

        List<Map<String, Object>> emails = response.getBody();
        if (emails != null) {
            for (Map<String, Object> e : emails) {
                if (Boolean.TRUE.equals(e.get("primary"))
                        && Boolean.TRUE.equals(e.get("verified"))
                        && e.get("email") != null) {
                    return e.get("email").toString();
                }
            }
            for (Map<String, Object> e : emails) {
                if (Boolean.TRUE.equals(e.get("verified")) && e.get("email") != null) {
                    return e.get("email").toString();
                }
            }
        }

        throw new OAuth2AuthenticationException(
                new OAuth2Error("email_not_found"),
                "Could not retrieve a verified email from GitHub");
    }
}