package com.codesync.version.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationClientFallback
        implements NotificationClient {

    @Override
    public void sendNotification(
            NotificationRequest request) {
        log.warn("[version-service] notification-service is " +
                "down. Snapshot notification not sent.");
    }
}