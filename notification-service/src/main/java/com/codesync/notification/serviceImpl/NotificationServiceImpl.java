package com.codesync.notification.serviceImpl;

import com.codesync.notification.dto.request.SendBulkNotificationRequest;
import com.codesync.notification.dto.request.SendNotificationRequest;
import com.codesync.notification.dto.response.NotificationResponse;
import com.codesync.notification.entity.Notification;
import com.codesync.notification.exception.NotificationNotFoundException;
import com.codesync.notification.repository.NotificationRepository;
import com.codesync.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl
        implements NotificationService {

    private final NotificationRepository notificationRepository;

    // ─────────────────────────────────────────────────────────────────
    // SEND
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public NotificationResponse send(
            SendNotificationRequest request) {

        log.info("Sending {} notification to user: {}",
                request.getType(), request.getRecipientId());

        Notification notification = Notification.builder()
                .recipientId(request.getRecipientId())
                .actorId(request.getActorId())
                .type(request.getType())
                .title(request.getTitle())
                .message(request.getMessage())
                .relatedId(request.getRelatedId())
                .relatedType(request.getRelatedType())
                .isRead(false)
                .build();

        Notification saved =
                notificationRepository.save(notification);

        log.info("Notification saved with ID: {}",
                saved.getNotificationId());

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public List<NotificationResponse> sendBulk(
            SendBulkNotificationRequest request) {

        log.info("Sending bulk {} notification to {} recipients",
                request.getType(),
                request.getRecipientIds().size());

        List<Notification> notifications = new ArrayList<>();

        for (Long recipientId : request.getRecipientIds()) {
            notifications.add(Notification.builder()
                    .recipientId(recipientId)
                    .actorId(request.getActorId())
                    .type(request.getType())
                    .title(request.getTitle())
                    .message(request.getMessage())
                    .relatedId(request.getRelatedId())
                    .relatedType(request.getRelatedType())
                    .isRead(false)
                    .build());
        }

        List<Notification> saved =
                notificationRepository.saveAll(notifications);

        log.info("Bulk notification sent to {} users",
                saved.size());

        return saved.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────
    // GET
    // ─────────────────────────────────────────────────────────────────

    @Override
    public List<NotificationResponse> getByRecipient(
            Long recipientId) {
        return notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(recipientId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<NotificationResponse> getUnread(Long recipientId) {
        return notificationRepository
                .findByRecipientIdAndIsReadOrderByCreatedAtDesc(
                        recipientId, false)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public NotificationResponse getById(Long notificationId) {
        return mapToResponse(findOrThrow(notificationId));
    }

    @Override
    public int getUnreadCount(Long recipientId) {
        return notificationRepository.countByRecipientIdAndIsRead(
                recipientId, false);
    }

    // ─────────────────────────────────────────────────────────────────
    // READ STATE
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long notificationId) {
        Notification notification = findOrThrow(notificationId);
        notification.setIsRead(true);
        Notification updated =
                notificationRepository.save(notification);

        log.info("Notification {} marked as read", notificationId);
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public int markAllAsRead(Long recipientId) {
        int count = notificationRepository
                .markAllAsRead(recipientId);
        log.info("Marked {} notifications as read for user: {}",
                count, recipientId);
        return count;
    }

    // ─────────────────────────────────────────────────────────────────
    // DELETE
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteNotification(Long notificationId) {
        Notification notification = findOrThrow(notificationId);
        notificationRepository.delete(notification);
        log.info("Notification {} deleted", notificationId);
    }

    @Override
    @Transactional
    public int deleteAllRead(Long recipientId) {
        int count = notificationRepository
                .deleteAllRead(recipientId);
        log.info("Deleted {} read notifications for user: {}",
                count, recipientId);
        return count;
    }

    // ─────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────

    private Notification findOrThrow(Long notificationId) {
        return notificationRepository.findById(notificationId)
                .orElseThrow(() ->
                        new NotificationNotFoundException(
                                "Notification not found: "
                                        + notificationId));
    }

    private NotificationResponse mapToResponse(Notification n) {
        return NotificationResponse.builder()
                .notificationId(n.getNotificationId())
                .recipientId(n.getRecipientId())
                .actorId(n.getActorId())
                .type(n.getType())
                .title(n.getTitle())
                .message(n.getMessage())
                .relatedId(n.getRelatedId())
                .relatedType(n.getRelatedType())
                .isRead(n.getIsRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}