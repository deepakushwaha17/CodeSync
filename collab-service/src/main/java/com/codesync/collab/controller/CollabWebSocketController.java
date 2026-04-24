package com.codesync.collab.controller;

import com.codesync.collab.dto.request.CodeChangeRequest;
import com.codesync.collab.dto.request.CursorUpdateRequest;
import com.codesync.collab.service.CollabService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket STOMP message handler.
 *
 * Clients connect to: ws://localhost:8087/ws
 *
 * Client subscribes to:
 * /topic/session/{sessionId}          ← receives code changes
 * /topic/session/{sessionId}/cursors  ← receives cursor updates
 * /topic/session/{sessionId}/users    ← receives join/leave events
 *
 * Client sends to:
 * /app/session/{sessionId}/change     ← sends code change
 * /app/session/{sessionId}/cursor     ← sends cursor move
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class CollabWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final CollabService collabService;

    /**
     * Receives code change from a participant and
     * broadcasts it to ALL participants in the session.
     *
     * Client sends to:
     * /app/session/{sessionId}/change
     */
    @MessageMapping("/session/{sessionId}/change")
    public void handleCodeChange(
            @DestinationVariable String sessionId,
            @Payload CodeChangeRequest request) {

        log.debug("Code change in session: {} by user: {}",
                sessionId, request.getUserId());

        // Save latest content to Redis
        if (request.getContent() != null) {
            collabService.saveContentToRedis(
                    sessionId, request.getContent());
        }

        // Update session activity
        collabService.updateLastActivity(sessionId);

        // Broadcast change to all session subscribers
        // Frontend listens on /topic/session/{sessionId}
        messagingTemplate.convertAndSend(
                "/topic/session/" + sessionId,
                request
        );
    }

    /**
     * Receives cursor position update from a participant
     * and broadcasts it to all other participants.
     *
     * Client sends to:
     * /app/session/{sessionId}/cursor
     */
    @MessageMapping("/session/{sessionId}/cursor")
    public void handleCursorUpdate(
            @DestinationVariable String sessionId,
            @Payload CursorUpdateRequest request) {

        log.debug("Cursor update in session: {} user: {} → {}:{}",
                sessionId, request.getUserId(),
                request.getLine(), request.getCol());

        // Persist cursor position to DB
        collabService.updateCursor(
                sessionId,
                request.getUserId(),
                request.getLine(),
                request.getCol()
        );

        // Broadcast cursor to all session subscribers
        // Frontend listens on /topic/session/{sessionId}/cursors
        messagingTemplate.convertAndSend(
                "/topic/session/" + sessionId + "/cursors",
                request
        );
    }

    /**
     * Notify all participants when someone joins.
     * Called internally after joinSession REST call.
     */
    public void broadcastUserJoined(
            String sessionId, Long userId, String color) {

        Map<String, Object> event = new HashMap<>();
        event.put("type", "USER_JOINED");
        event.put("userId", userId);
        event.put("color", color);

        messagingTemplate.convertAndSend(
                "/topic/session/" + sessionId + "/users",
                event
        );
    }

    /**
     * Notify all participants when someone leaves.
     */
    public void broadcastUserLeft(
            String sessionId, Long userId) {

        Map<String, Object> event = new HashMap<>();
        event.put("type", "USER_LEFT");
        event.put("userId", userId);

        messagingTemplate.convertAndSend(
                "/topic/session/" + sessionId + "/users",
                event
        );
    }

    /**
     * Notify a specific user they were kicked.
     */
    public void broadcastKicked(
            String sessionId, Long userId) {

        Map<String, Object> event = new HashMap<>();
        event.put("type", "KICKED");
        event.put("sessionId", sessionId);

        // Send to specific user queue
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/kicked",
                event
        );
    }
}