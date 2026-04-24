package com.codesync.notification.service;

import com.codesync.notification.dto.request.SendBulkNotificationRequest;
import com.codesync.notification.dto.request.SendNotificationRequest;
import com.codesync.notification.dto.response.NotificationResponse;

import java.util.List;

public interface NotificationService {

    // Send
    NotificationResponse send(SendNotificationRequest request);
    List<NotificationResponse> sendBulk(
            SendBulkNotificationRequest request);

    // Get
    List<NotificationResponse> getByRecipient(Long recipientId);
    List<NotificationResponse> getUnread(Long recipientId);
    NotificationResponse getById(Long notificationId);
    int getUnreadCount(Long recipientId);

    // Read state
    NotificationResponse markAsRead(Long notificationId);
    int markAllAsRead(Long recipientId);

    // Delete
    void deleteNotification(Long notificationId);
    int deleteAllRead(Long recipientId);
}