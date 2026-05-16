package com.codesync.notification.controller;

import com.codesync.notification.dto.request.SendBulkNotificationRequest;
import com.codesync.notification.dto.request.SendNotificationRequest;
import com.codesync.notification.dto.response.NotificationResponse;
import com.codesync.notification.enums.NotificationType;
import com.codesync.notification.exception.NotificationNotFoundException;
import com.codesync.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private NotificationService notificationService;

    // ── Fixture ───────────────────────────────────────────────────────

    private NotificationResponse buildResponse(Long id, Long recipientId,
                                               NotificationType type,
                                               boolean isRead) {
        return NotificationResponse.builder()
                .notificationId(id).recipientId(recipientId).actorId(2L)
                .type(type).title("Test Title").message("Test message")
                .relatedId(10L).relatedType("COMMENT").isRead(isRead)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ════════════════════════════════════════════════════════════════
    // POST /api/v1/notifications
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST / - 201 Created on successful notification send")
    void send_valid_returns201() throws Exception {
        SendNotificationRequest req = new SendNotificationRequest();
        req.setRecipientId(1L); req.setActorId(2L);
        req.setType(NotificationType.COMMENT_REPLY);
        req.setTitle("Reply"); req.setMessage("Someone replied");

        when(notificationService.send(any()))
                .thenReturn(buildResponse(1L, 1L,
                        NotificationType.COMMENT_REPLY, false));

        mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.notificationId").value(1))
                .andExpect(jsonPath("$.data.recipientId").value(1))
                .andExpect(jsonPath("$.data.isRead").value(false))
                .andExpect(jsonPath("$.message").value("Notification sent."));
    }

    @Test
    @DisplayName("POST / - 400 Bad Request when required fields are missing")
    void send_missingFields_returns400() throws Exception {
        SendNotificationRequest req = new SendNotificationRequest();
        // recipientId, type, title, message missing

        mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ════════════════════════════════════════════════════════════════
    // POST /api/v1/notifications/bulk
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /bulk - 201 Created on successful bulk send")
    void sendBulk_valid_returns201() throws Exception {
        SendBulkNotificationRequest req = new SendBulkNotificationRequest();
        req.setRecipientIds(List.of(1L, 2L, 3L));
        req.setActorId(5L);
        req.setType(NotificationType.PARTICIPANT_JOINED);
        req.setTitle("Joined"); req.setMessage("Someone joined");

        when(notificationService.sendBulk(any())).thenReturn(List.of(
                buildResponse(1L, 1L, NotificationType.PARTICIPANT_JOINED, false),
                buildResponse(2L, 2L, NotificationType.PARTICIPANT_JOINED, false),
                buildResponse(3L, 3L, NotificationType.PARTICIPANT_JOINED, false)
        ));

        mockMvc.perform(post("/api/v1/notifications/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.message").value("Bulk notification sent. Count: 3"));
    }

    @Test
    @DisplayName("POST /bulk - 400 Bad Request when recipientIds is empty")
    void sendBulk_emptyRecipients_returns400() throws Exception {
        SendBulkNotificationRequest req = new SendBulkNotificationRequest();
        req.setRecipientIds(List.of()); // empty
        req.setType(NotificationType.COMMENT_REPLY);
        req.setTitle("T"); req.setMessage("m");

        mockMvc.perform(post("/api/v1/notifications/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/notifications/recipient/{recipientId}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /recipient/{recipientId} - 200 OK returns all notifications")
    void getByRecipient_existing_returns200() throws Exception {
        when(notificationService.getByRecipient(1L)).thenReturn(List.of(
                buildResponse(2L, 1L, NotificationType.COMMENT_REPLY, false),
                buildResponse(1L, 1L, NotificationType.PARTICIPANT_JOINED, true)
        ));

        mockMvc.perform(get("/api/v1/notifications/recipient/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.message").value("Notifications fetched."));
    }

    @Test
    @DisplayName("GET /recipient/{recipientId} - 200 OK returns empty list")
    void getByRecipient_noNotifications_returnsEmpty() throws Exception {
        when(notificationService.getByRecipient(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/notifications/recipient/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/notifications/recipient/{recipientId}/unread
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /recipient/{recipientId}/unread - 200 OK returns unread notifications")
    void getUnread_existing_returns200() throws Exception {
        when(notificationService.getUnread(1L)).thenReturn(List.of(
                buildResponse(1L, 1L, NotificationType.COMMENT_REPLY, false)
        ));

        mockMvc.perform(get("/api/v1/notifications/recipient/1/unread"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].isRead").value(false))
                .andExpect(jsonPath("$.message").value("Unread notifications fetched."));
    }

    @Test
    @DisplayName("GET /recipient/{recipientId}/unread - 200 OK returns empty when all read")
    void getUnread_allRead_returnsEmpty() throws Exception {
        when(notificationService.getUnread(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/notifications/recipient/1/unread"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/notifications/recipient/{recipientId}/count
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /recipient/{recipientId}/count - 200 OK returns unread count")
    void getUnreadCount_returns200() throws Exception {
        when(notificationService.getUnreadCount(1L)).thenReturn(7);

        mockMvc.perform(get("/api/v1/notifications/recipient/1/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(7))
                .andExpect(jsonPath("$.message").value("Unread count."));
    }

    @Test
    @DisplayName("GET /recipient/{recipientId}/count - 200 OK returns 0 when none unread")
    void getUnreadCount_noneUnread_returnsZero() throws Exception {
        when(notificationService.getUnreadCount(1L)).thenReturn(0);

        mockMvc.perform(get("/api/v1/notifications/recipient/1/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(0));
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/notifications/{notificationId}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /{notificationId} - 200 OK for existing notification")
    void getById_existing_returns200() throws Exception {
        when(notificationService.getById(1L))
                .thenReturn(buildResponse(1L, 1L,
                        NotificationType.COMMENT_REPLY, false));

        mockMvc.perform(get("/api/v1/notifications/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notificationId").value(1))
                .andExpect(jsonPath("$.data.type").value("COMMENT_REPLY"))
                .andExpect(jsonPath("$.message").value("Notification fetched."));
    }

    @Test
    @DisplayName("GET /{notificationId} - 404 Not Found for unknown notification")
    void getById_notFound_returns404() throws Exception {
        when(notificationService.getById(99L))
                .thenThrow(new NotificationNotFoundException(
                        "Notification not found: 99"));

        mockMvc.perform(get("/api/v1/notifications/99"))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════
    // PUT /api/v1/notifications/{notificationId}/read
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PUT /{notificationId}/read - 200 OK on successful mark as read")
    void markAsRead_existing_returns200() throws Exception {
        when(notificationService.markAsRead(1L))
                .thenReturn(buildResponse(1L, 1L,
                        NotificationType.COMMENT_REPLY, true));

        mockMvc.perform(put("/api/v1/notifications/1/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isRead").value(true))
                .andExpect(jsonPath("$.message")
                        .value("Notification marked as read."));
    }

    @Test
    @DisplayName("PUT /{notificationId}/read - 404 Not Found for unknown notification")
    void markAsRead_notFound_returns404() throws Exception {
        when(notificationService.markAsRead(99L))
                .thenThrow(new NotificationNotFoundException(
                        "Notification not found: 99"));

        mockMvc.perform(put("/api/v1/notifications/99/read"))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════
    // PUT /api/v1/notifications/recipient/{recipientId}/read-all
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PUT /recipient/{recipientId}/read-all - 200 OK returns marked count")
    void markAllAsRead_returns200() throws Exception {
        when(notificationService.markAllAsRead(1L)).thenReturn(5);

        mockMvc.perform(put("/api/v1/notifications/recipient/1/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(5))
                .andExpect(jsonPath("$.message")
                        .value("Marked 5 notifications as read."));
    }

    @Test
    @DisplayName("PUT /recipient/{recipientId}/read-all - 200 OK returns 0 when all already read")
    void markAllAsRead_allAlreadyRead_returns200WithZero() throws Exception {
        when(notificationService.markAllAsRead(1L)).thenReturn(0);

        mockMvc.perform(put("/api/v1/notifications/recipient/1/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(0))
                .andExpect(jsonPath("$.message")
                        .value("Marked 0 notifications as read."));
    }

    // ════════════════════════════════════════════════════════════════
    // DELETE /api/v1/notifications/{notificationId}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("DELETE /{notificationId} - 200 OK on successful delete")
    void deleteNotification_existing_returns200() throws Exception {
        doNothing().when(notificationService).deleteNotification(1L);

        mockMvc.perform(delete("/api/v1/notifications/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Notification deleted."));
    }

    @Test
    @DisplayName("DELETE /{notificationId} - 404 Not Found for unknown notification")
    void deleteNotification_notFound_returns404() throws Exception {
        doThrow(new NotificationNotFoundException("Notification not found: 99"))
                .when(notificationService).deleteNotification(99L);

        mockMvc.perform(delete("/api/v1/notifications/99"))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════
    // DELETE /api/v1/notifications/recipient/{recipientId}/read
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("DELETE /recipient/{recipientId}/read - 200 OK returns deleted count")
    void deleteAllRead_existing_returns200() throws Exception {
        when(notificationService.deleteAllRead(1L)).thenReturn(3);

        mockMvc.perform(delete("/api/v1/notifications/recipient/1/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(3))
                .andExpect(jsonPath("$.message")
                        .value("Deleted 3 read notifications."));
    }

    @Test
    @DisplayName("DELETE /recipient/{recipientId}/read - 200 OK returns 0 when none read")
    void deleteAllRead_noneRead_returns200WithZero() throws Exception {
        when(notificationService.deleteAllRead(1L)).thenReturn(0);

        mockMvc.perform(delete("/api/v1/notifications/recipient/1/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(0))
                .andExpect(jsonPath("$.message")
                        .value("Deleted 0 read notifications."));
    }
}