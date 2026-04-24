package com.codesync.auth.oauth2;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2UserInfo {
    private String providerId;
    private String email;
    private String name;
    private String avatarUrl;
    private String provider;
}