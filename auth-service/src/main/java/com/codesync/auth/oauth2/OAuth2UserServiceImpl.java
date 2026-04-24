package com.codesync.auth.oauth2;

import com.codesync.auth.entity.User;
import com.codesync.auth.enums.Provider;
import com.codesync.auth.enums.Role;
import com.codesync.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2UserServiceImpl extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest)
            throws OAuth2AuthenticationException {

        OAuth2User oAuth2User = super.loadUser(userRequest);

        String providerName = userRequest
                .getClientRegistration()
                .getRegistrationId()
                .toUpperCase();

        Map<String, Object> attributes = oAuth2User.getAttributes();

        log.info("OAuth2 user loaded from provider: {}", providerName);
        log.debug("Attributes received: {}", attributes);

        OAuth2UserInfo userInfo = OAuth2UserInfoFactory
                .extract(providerName, attributes);

        // Handle missing email (GitHub private email setting)
        if (userInfo.getEmail() == null
                || userInfo.getEmail().isBlank()) {
            userInfo.setEmail(
                    "github_"
                            + userInfo.getProviderId()
                            + "@codesync.placeholder"
            );
            log.warn("GitHub email was private. " +
                    "Using placeholder: {}", userInfo.getEmail());
        }

        Optional<User> existingUser =
                userRepository.findByEmail(userInfo.getEmail());

        if (existingUser.isPresent()) {
            User user = existingUser.get();
            if (userInfo.getAvatarUrl() != null) {
                user.setAvatarUrl(userInfo.getAvatarUrl());
                userRepository.save(user);
            }
            log.info("Existing user logged in via OAuth2: {}",
                    user.getEmail());
        } else {
            registerNewUser(userInfo);
        }

        return oAuth2User;
    }

    private void registerNewUser(OAuth2UserInfo userInfo) {
        String baseUsername = userInfo.getName() != null
                ? userInfo.getName()
                  .toLowerCase()
                  .replaceAll("\\s+", "")
                  .replaceAll("[^a-z0-9]", "")
                : userInfo.getProvider().toLowerCase()
                  + userInfo.getProviderId();

        // Make sure username is not empty
        if (baseUsername.isBlank()) {
            baseUsername = userInfo.getProvider().toLowerCase()
                    + userInfo.getProviderId();
        }

        // Ensure unique username
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
                .provider(Provider.valueOf(userInfo.getProvider()))
                .avatarUrl(userInfo.getAvatarUrl())
                .isActive(true)
                .build();

        userRepository.save(newUser);
        log.info("New OAuth2 user registered: {} via {}",
                newUser.getEmail(), userInfo.getProvider());
    }
}