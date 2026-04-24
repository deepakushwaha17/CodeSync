package com.codesync.collab.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket configuration using STOMP protocol.
 *
 * Connection endpoint: ws://localhost:8087/ws
 * With SockJS fallback: http://localhost:8087/ws
 *
 * Subscribe to session events:
 * /topic/session/{sessionId}          ← code changes and cursor moves
 * /topic/session/{sessionId}/users    ← participant join/leave events
 *
 * Send messages to:
 * /app/session/{sessionId}/change     ← code change
 * /app/session/{sessionId}/cursor     ← cursor move
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig
        implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(
            MessageBrokerRegistry registry) {

        // Use simple in-memory broker for topics
        // /topic — for broadcasting to all subscribers
        // /queue — for sending to specific user
        registry.enableSimpleBroker("/topic", "/queue");

        // Prefix for messages sent from client to server
        registry.setApplicationDestinationPrefixes("/app");

        // Prefix for user-specific messages
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(
            StompEndpointRegistry registry) {

        // Main WebSocket endpoint with SockJS fallback
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        // Plain WebSocket endpoint (without SockJS)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }
}