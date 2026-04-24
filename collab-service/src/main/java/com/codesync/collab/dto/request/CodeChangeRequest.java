package com.codesync.collab.dto.request;

import lombok.Data;

@Data
public class CodeChangeRequest {

    private String sessionId;
    private Long userId;
    private String content;

    // Operation type: "INSERT", "DELETE", "REPLACE"
    private String operation;

    // Position of change
    private Integer fromLine;
    private Integer fromCol;
    private Integer toLine;
    private Integer toCol;

    // The changed text
    private String text;
}