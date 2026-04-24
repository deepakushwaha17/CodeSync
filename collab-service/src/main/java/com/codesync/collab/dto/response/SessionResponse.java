package com.codesync.collab.dto.response;

import com.codesync.collab.enums.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponse {

    private String sessionId;
    private Long projectId;
    private Long fileId;
    private Long ownerId;
    private SessionStatus status;
    private String language;
    private LocalDateTime createdAt;
    private LocalDateTime endedAt;
    private Integer maxParticipants;
    private Boolean isPasswordProtected;
    private Integer activeParticipantCount;

    // WebSocket topic for this session
    // Frontend subscribes to this
    private String webSocketTopic;
}