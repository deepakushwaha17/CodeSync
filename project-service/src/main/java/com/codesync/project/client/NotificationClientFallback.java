package com.codesync.project.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationClientFallback
        implements NotificationClient {

    @Override
    public void sendNotification(
            NotificationRequest request) {
        log.warn("[project-service] notification-service is " +
                        "down. Notification not sent to user: {}",
                request.getRecipientId());
    }

    @Override
    public void sendBulkNotification(
            BulkNotificationRequest request) {
        log.warn("[project-service] notification-service is " +
                "down. Bulk notification not sent.");
    }
}