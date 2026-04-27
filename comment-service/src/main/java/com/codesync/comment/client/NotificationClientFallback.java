package com.codesync.comment.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationClientFallback
        implements NotificationClient {

    @Override
    public void sendNotification(
            NotificationRequest request) {
        log.warn("[comment-service] notification-service is " +
                        "down. Comment notification not sent to: {}",
                request.getRecipientId());
    }
}