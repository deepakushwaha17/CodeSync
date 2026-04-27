package com.codesync.collab.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationClientFallback
        implements NotificationClient {

    @Override
    public void sendNotification(
            NotificationRequest request) {
        log.warn("[collab-service] notification-service is " +
                        "down. Session notification not sent to: {}",
                request.getRecipientId());
    }
}