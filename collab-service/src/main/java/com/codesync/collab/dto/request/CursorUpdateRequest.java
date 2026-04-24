package com.codesync.collab.dto.request;

import lombok.Data;

@Data
public class CursorUpdateRequest {

    private String sessionId;
    private Long userId;
    private Integer line;
    private Integer col;
    private String color;
}