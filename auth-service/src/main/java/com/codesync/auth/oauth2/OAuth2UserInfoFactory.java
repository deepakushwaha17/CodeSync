package com.codesync.auth.oauth2;

import java.util.Map;

public class OAuth2UserInfoFactory {

    public static OAuth2UserInfo extract(String registrationId, Map<String, Object> attributes) {

        switch (registrationId.toLowerCase()) {

            case "github":
                return OAuth2UserInfo.builder()
                        .provider("GITHUB")
                        .providerId(String.valueOf(attributes.get("id")))
                        .email((String) attributes.get("email"))
                        .name(attributes.get("name") != null
                                ? (String) attributes.get("name")
                                : (String) attributes.get("login"))
                        .avatarUrl((String) attributes.get("avatar_url"))
                        .build();

            case "google":
                return OAuth2UserInfo.builder()
                        .provider("GOOGLE")
                        .providerId((String) attributes.get("sub"))
                        .email((String) attributes.get("email"))
                        .name((String) attributes.get("name"))
                        .avatarUrl((String) attributes.get("picture"))
                        .build();

            default:
                throw new IllegalArgumentException(
                        "Unsupported OAuth2 provider: " + registrationId);
        }
    }
}