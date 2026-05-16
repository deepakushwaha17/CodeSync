package com.codesync.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpResponse {

    private Boolean success;
    private String  message;

    // Masked email like de***@gmail.com
    private String  maskedEmail;

    // Expiry in minutes
    private Integer expiryMinutes;
}