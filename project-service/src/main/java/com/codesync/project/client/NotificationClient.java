package com.codesync.project.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "notification-service",
        fallback = NotificationClientFallback.class
)
public interface NotificationClient {

    @PostMapping("/api/v1/notifications")
    void sendNotification(
            @RequestBody NotificationRequest request);

    @PostMapping("/api/v1/notifications/bulk")
    void sendBulkNotification(
            @RequestBody BulkNotificationRequest request);
}