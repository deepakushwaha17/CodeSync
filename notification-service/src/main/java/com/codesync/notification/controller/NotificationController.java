package com.codesync.notification.controller;

import com.codesync.notification.dto.request.SendBulkNotificationRequest;
import com.codesync.notification.dto.request.SendNotificationRequest;
import com.codesync.notification.dto.response.ApiResponse;
import com.codesync.notification.dto.response.NotificationResponse;
import com.codesync.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification Service",
        description = "Notification Management APIs")
public class NotificationController {

    private final NotificationService notificationService;

    // ── POST /api/v1/notifications ────────────────────────────────────
    @PostMapping
    @Operation(summary = "Send a notification to a user")
    public ResponseEntity<ApiResponse<NotificationResponse>>
    send(@Valid @RequestBody SendNotificationRequest request) {

        NotificationResponse response =
                notificationService.send(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Notification sent.", response));
    }

    // ── POST /api/v1/notifications/bulk ───────────────────────────────
    @PostMapping("/bulk")
    @Operation(summary = "Send notification to multiple users")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>>
    sendBulk(
            @Valid @RequestBody
            SendBulkNotificationRequest request) {

        List<NotificationResponse> responses =
                notificationService.sendBulk(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Bulk notification sent. Count: "
                                + responses.size(), responses));
    }

    // ── GET /api/v1/notifications/recipient/{recipientId} ─────────────
    @GetMapping("/recipient/{recipientId}")
    @Operation(summary = "Get all notifications for a user")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>>
    getByRecipient(@PathVariable Long recipientId) {

        List<NotificationResponse> notifications =
                notificationService.getByRecipient(recipientId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Notifications fetched.", notifications));
    }

    // ── GET /api/v1/notifications/recipient/{recipientId}/unread ──────
    @GetMapping("/recipient/{recipientId}/unread")
    @Operation(summary = "Get unread notifications for a user")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>>
    getUnread(@PathVariable Long recipientId) {

        List<NotificationResponse> notifications =
                notificationService.getUnread(recipientId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Unread notifications fetched.",
                        notifications));
    }

    // ── GET /api/v1/notifications/recipient/{recipientId}/count ───────
    @GetMapping("/recipient/{recipientId}/count")
    @Operation(summary = "Get unread notification count")
    public ResponseEntity<ApiResponse<Integer>> getUnreadCount(
            @PathVariable Long recipientId) {

        int count = notificationService.getUnreadCount(recipientId);
        return ResponseEntity.ok(
                ApiResponse.success("Unread count.", count));
    }

    // ── GET /api/v1/notifications/{notificationId} ────────────────────
    @GetMapping("/{notificationId}")
    @Operation(summary = "Get notification by ID")
    public ResponseEntity<ApiResponse<NotificationResponse>>
    getById(@PathVariable Long notificationId) {

        NotificationResponse response =
                notificationService.getById(notificationId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Notification fetched.", response));
    }

    // ── PUT /api/v1/notifications/{notificationId}/read ───────────────
    @PutMapping("/{notificationId}/read")
    @Operation(summary = "Mark a notification as read")
    public ResponseEntity<ApiResponse<NotificationResponse>>
    markAsRead(@PathVariable Long notificationId) {

        NotificationResponse response =
                notificationService.markAsRead(notificationId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Notification marked as read.", response));
    }

    // ── PUT /api/v1/notifications/recipient/{recipientId}/read-all ────
    @PutMapping("/recipient/{recipientId}/read-all")
    @Operation(summary = "Mark all notifications as read")
    public ResponseEntity<ApiResponse<Integer>> markAllAsRead(
            @PathVariable Long recipientId) {

        int count = notificationService.markAllAsRead(recipientId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Marked " + count
                                + " notifications as read.", count));
    }

    // ── DELETE /api/v1/notifications/{notificationId} ─────────────────
    @DeleteMapping("/{notificationId}")
    @Operation(summary = "Delete a notification")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(
            @PathVariable Long notificationId) {

        notificationService.deleteNotification(notificationId);
        return ResponseEntity.ok(
                ApiResponse.success("Notification deleted."));
    }

    // ── DELETE /api/v1/notifications/recipient/{recipientId}/read ─────
    @DeleteMapping("/recipient/{recipientId}/read")
    @Operation(summary = "Delete all read notifications for a user")
    public ResponseEntity<ApiResponse<Integer>> deleteAllRead(
            @PathVariable Long recipientId) {

        int count = notificationService.deleteAllRead(recipientId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Deleted " + count
                                + " read notifications.", count));
    }
}