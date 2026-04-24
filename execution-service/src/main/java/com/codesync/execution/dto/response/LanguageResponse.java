package com.codesync.execution.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LanguageResponse {

    private Long id;
    private String name;
    private String displayName;
    private String version;
    private String fileExtension;
    private String helloWorldCode;
    private Boolean isActive;
}