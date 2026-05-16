package com.codesync.notification.serviceImpl;

import com.codesync.notification.dto.request.SendBulkNotificationRequest;
import com.codesync.notification.dto.request.SendNotificationRequest;
import com.codesync.notification.dto.response.NotificationResponse;
import com.codesync.notification.entity.Notification;
import com.codesync.notification.enums.NotificationType;
import com.codesync.notification.exception.NotificationNotFoundException;
import com.codesync.notification.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationRepository notificationRepository;
    @InjectMocks private NotificationServiceImpl notificationService;

    // ── Fixture ───────────────────────────────────────────────────────

    private Notification build(Long id, Long recipientId, Long actorId,
                               NotificationType type, String title,
                               String message, Long relatedId,
                               String relatedType, boolean isRead) {
        return Notification.builder()
                .notificationId(id).recipientId(recipientId)
                .actorId(actorId).type(type).title(title)
                .message(message).relatedId(relatedId)
                .relatedType(relatedType).isRead(isRead)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private SendNotificationRequest buildRequest(Long recipientId,
                                                 NotificationType type) {
        SendNotificationRequest req = new SendNotificationRequest();
        req.setRecipientId(recipientId);
        req.setActorId(2L);
        req.setType(type);
        req.setTitle("Test Title");
        req.setMessage("Test message body");
        req.setRelatedId(10L);
        req.setRelatedType("COMMENT");
        return req;
    }

    // ════════════════════════════════════════════════════════════════
    // send()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("send - saves notification with isRead=false and returns response")
    void send_valid_savesAndReturnsResponse() {
        SendNotificationRequest req =
                buildRequest(1L, NotificationType.COMMENT_REPLY);

        Notification saved = build(1L, 1L, 2L,
                NotificationType.COMMENT_REPLY,
                "Test Title", "Test message body",
                10L, "COMMENT", false);
        when(notificationRepository.save(any())).thenReturn(saved);

        NotificationResponse response = notificationService.send(req);

        assertThat(response.getNotificationId()).isEqualTo(1L);
        assertThat(response.getRecipientId()).isEqualTo(1L);
        assertThat(response.getType()).isEqualTo(NotificationType.COMMENT_REPLY);
        assertThat(response.getIsRead()).isFalse();
        assertThat(response.getTitle()).isEqualTo("Test Title");
        verify(notificationRepository).save(
                argThat(n -> !n.getIsRead() &&
                        n.getRecipientId().equals(1L)));
    }

    @Test
    @DisplayName("send - saves notification with null actorId for system notifications")
    void send_systemNotification_nullActorId() {
        SendNotificationRequest req =
                buildRequest(1L, NotificationType.SYSTEM);
        req.setActorId(null);

        Notification saved = build(1L, 1L, null,
                NotificationType.SYSTEM,
                "System Alert", "msg", null, null, false);
        when(notificationRepository.save(any())).thenReturn(saved);

        NotificationResponse response = notificationService.send(req);

        assertThat(response.getActorId()).isNull();
        verify(notificationRepository).save(
                argThat(n -> n.getActorId() == null));
    }

    @Test
    @DisplayName("send - saves notification with null relatedId and relatedType")
    void send_noRelatedEntity_savesWithNullFields() {
        SendNotificationRequest req =
                buildRequest(1L, NotificationType.COMMENT_REPLY);
        req.setRelatedId(null);
        req.setRelatedType(null);

        Notification saved = build(1L, 1L, 2L,
                NotificationType.COMMENT_REPLY,
                "Test Title", "msg", null, null, false);
        when(notificationRepository.save(any())).thenReturn(saved);

        NotificationResponse response = notificationService.send(req);

        assertThat(response.getRelatedId()).isNull();
        assertThat(response.getRelatedType()).isNull();
    }

    // ════════════════════════════════════════════════════════════════
    // sendBulk()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("sendBulk - saves one notification per recipient")
    void sendBulk_multipleRecipients_savesAll() {
        SendBulkNotificationRequest req = new SendBulkNotificationRequest();
        req.setRecipientIds(List.of(1L, 2L, 3L));
        req.setActorId(5L);
        req.setType(NotificationType.PARTICIPANT_JOINED);
        req.setTitle("Bulk Title");
        req.setMessage("Bulk message");
        req.setRelatedId(100L);
        req.setRelatedType("SESSION");

        List<Notification> saved = List.of(
                build(1L, 1L, 5L, NotificationType.PARTICIPANT_JOINED,
                        "Bulk Title", "Bulk message", 100L, "SESSION", false),
                build(2L, 2L, 5L, NotificationType.PARTICIPANT_JOINED,
                        "Bulk Title", "Bulk message", 100L, "SESSION", false),
                build(3L, 3L, 5L, NotificationType.PARTICIPANT_JOINED,
                        "Bulk Title", "Bulk message", 100L, "SESSION", false)
        );
        when(notificationRepository.saveAll(any())).thenReturn(saved);

        List<NotificationResponse> responses = notificationService.sendBulk(req);

        assertThat(responses).hasSize(3);
        assertThat(responses).allMatch(r -> !r.getIsRead());
        assertThat(responses).extracting(NotificationResponse::getRecipientId)
                .containsExactlyInAnyOrder(1L, 2L, 3L);

        // saveAll should be called with 3 notifications
        verify(notificationRepository).saveAll(
                argThat(list -> ((List<?>) list).size() == 3));
    }

    @Test
    @DisplayName("sendBulk - saves single notification when one recipient")
    void sendBulk_singleRecipient_savesOne() {
        SendBulkNotificationRequest req = new SendBulkNotificationRequest();
        req.setRecipientIds(List.of(1L));
        req.setActorId(5L);
        req.setType(NotificationType.COMMENT_REPLY);
        req.setTitle("Title"); req.setMessage("msg");

        Notification saved = build(1L, 1L, 5L,
                NotificationType.COMMENT_REPLY,
                "Title", "msg", null, null, false);
        when(notificationRepository.saveAll(any()))
                .thenReturn(List.of(saved));

        List<NotificationResponse> responses = notificationService.sendBulk(req);

        assertThat(responses).hasSize(1);
    }

    @Test
    @DisplayName("sendBulk - returns empty list when recipientIds is empty")
    void sendBulk_emptyRecipients_returnsEmpty() {
        SendBulkNotificationRequest req = new SendBulkNotificationRequest();
        req.setRecipientIds(List.of());
        req.setActorId(5L);
        req.setType(NotificationType.COMMENT_REPLY);
        req.setTitle("T"); req.setMessage("m");

        when(notificationRepository.saveAll(any())).thenReturn(List.of());

        List<NotificationResponse> responses = notificationService.sendBulk(req);

        assertThat(responses).isEmpty();
    }

    // ════════════════════════════════════════════════════════════════
    // getByRecipient()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getByRecipient - returns all notifications newest first")
    void getByRecipient_existing_returnsList() {
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(
                        build(2L, 1L, 2L, NotificationType.COMMENT_REPLY,
                                "Second", "m", 2L, "COMMENT", false),
                        build(1L, 1L, 3L, NotificationType.PARTICIPANT_JOINED,
                                "First", "m", 1L, "SESSION", true)
                ));

        List<NotificationResponse> results =
                notificationService.getByRecipient(1L);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getTitle()).isEqualTo("Second");
    }

    @Test
    @DisplayName("getByRecipient - returns empty list when no notifications")
    void getByRecipient_noNotifications_returnsEmpty() {
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of());

        assertThat(notificationService.getByRecipient(1L)).isEmpty();
    }

    // ════════════════════════════════════════════════════════════════
    // getUnread()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getUnread - returns only unread notifications")
    void getUnread_existing_returnsUnreadOnly() {
        when(notificationRepository
                .findByRecipientIdAndIsReadOrderByCreatedAtDesc(1L, false))
                .thenReturn(List.of(
                        build(1L, 1L, 2L, NotificationType.COMMENT_REPLY,
                                "Unread", "m", 1L, "COMMENT", false)
                ));

        List<NotificationResponse> results = notificationService.getUnread(1L);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getIsRead()).isFalse();
    }

    @Test
    @DisplayName("getUnread - returns empty when all are read")
    void getUnread_allRead_returnsEmpty() {
        when(notificationRepository
                .findByRecipientIdAndIsReadOrderByCreatedAtDesc(1L, false))
                .thenReturn(List.of());

        assertThat(notificationService.getUnread(1L)).isEmpty();
    }

    // ════════════════════════════════════════════════════════════════
    // getById()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getById - returns NotificationResponse for existing notification")
    void getById_existing_returnsResponse() {
        Notification n = build(1L, 1L, 2L,
                NotificationType.COMMENT_REPLY,
                "Title", "msg", 10L, "COMMENT", false);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));

        NotificationResponse response = notificationService.getById(1L);

        assertThat(response.getNotificationId()).isEqualTo(1L);
        assertThat(response.getTitle()).isEqualTo("Title");
    }

    @Test
    @DisplayName("getById - throws NotificationNotFoundException for unknown ID")
    void getById_notFound_throwsException() {
        when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.getById(99L))
                .isInstanceOf(NotificationNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ════════════════════════════════════════════════════════════════
    // getUnreadCount()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getUnreadCount - returns correct unread count")
    void getUnreadCount_returnsCount() {
        when(notificationRepository.countByRecipientIdAndIsRead(1L, false))
                .thenReturn(5);

        assertThat(notificationService.getUnreadCount(1L)).isEqualTo(5);
    }

    @Test
    @DisplayName("getUnreadCount - returns 0 when all notifications are read")
    void getUnreadCount_allRead_returnsZero() {
        when(notificationRepository.countByRecipientIdAndIsRead(1L, false))
                .thenReturn(0);

        assertThat(notificationService.getUnreadCount(1L)).isZero();
    }

    // ════════════════════════════════════════════════════════════════
    // markAsRead()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("markAsRead - sets isRead=true and returns updated response")
    void markAsRead_unreadNotification_marksRead() {
        Notification n = build(1L, 1L, 2L,
                NotificationType.COMMENT_REPLY,
                "Title", "msg", 10L, "COMMENT", false);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse response = notificationService.markAsRead(1L);

        assertThat(response.getIsRead()).isTrue();
        verify(notificationRepository).save(argThat(Notification::getIsRead));
    }

    @Test
    @DisplayName("markAsRead - already-read notification remains read")
    void markAsRead_alreadyRead_remainsRead() {
        Notification n = build(1L, 1L, 2L,
                NotificationType.COMMENT_REPLY,
                "Title", "msg", 10L, "COMMENT", true); // already read
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse response = notificationService.markAsRead(1L);

        assertThat(response.getIsRead()).isTrue();
    }

    @Test
    @DisplayName("markAsRead - throws NotificationNotFoundException for unknown ID")
    void markAsRead_notFound_throwsException() {
        when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(99L))
                .isInstanceOf(NotificationNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ════════════════════════════════════════════════════════════════
    // markAllAsRead()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("markAllAsRead - returns count of notifications marked read")
    void markAllAsRead_returnsUpdatedCount() {
        when(notificationRepository.markAllAsRead(1L)).thenReturn(4);

        int count = notificationService.markAllAsRead(1L);

        assertThat(count).isEqualTo(4);
        verify(notificationRepository).markAllAsRead(1L);
    }

    @Test
    @DisplayName("markAllAsRead - returns 0 when all are already read")
    void markAllAsRead_allAlreadyRead_returnsZero() {
        when(notificationRepository.markAllAsRead(1L)).thenReturn(0);

        assertThat(notificationService.markAllAsRead(1L)).isZero();
    }

    // ════════════════════════════════════════════════════════════════
    // deleteNotification()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("deleteNotification - deletes existing notification")
    void deleteNotification_existing_deletesSuccessfully() {
        Notification n = build(1L, 1L, 2L,
                NotificationType.COMMENT_REPLY,
                "Title", "msg", 10L, "COMMENT", true);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));
        doNothing().when(notificationRepository).delete(n);

        assertThatCode(() -> notificationService.deleteNotification(1L))
                .doesNotThrowAnyException();

        verify(notificationRepository).delete(n);
    }

    @Test
    @DisplayName("deleteNotification - throws NotificationNotFoundException for unknown ID")
    void deleteNotification_notFound_throwsException() {
        when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.deleteNotification(99L))
                .isInstanceOf(NotificationNotFoundException.class)
                .hasMessageContaining("99");

        verify(notificationRepository, never()).delete(any(Notification.class));
    }

    // ════════════════════════════════════════════════════════════════
    // deleteAllRead()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("deleteAllRead - returns count of deleted read notifications")
    void deleteAllRead_returnsDeletedCount() {
        when(notificationRepository.deleteAllRead(1L)).thenReturn(3);

        int count = notificationService.deleteAllRead(1L);

        assertThat(count).isEqualTo(3);
        verify(notificationRepository).deleteAllRead(1L);
    }

    @Test
    @DisplayName("deleteAllRead - returns 0 when no read notifications exist")
    void deleteAllRead_noneRead_returnsZero() {
        when(notificationRepository.deleteAllRead(1L)).thenReturn(0);

        assertThat(notificationService.deleteAllRead(1L)).isZero();
    }
}