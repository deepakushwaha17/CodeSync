package com.codesync.notification.repository;

import com.codesync.notification.entity.Notification;
import com.codesync.notification.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository
        extends JpaRepository<Notification, Long> {

    // All notifications for a recipient newest first
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(
            Long recipientId);

    // Only unread notifications
    List<Notification> findByRecipientIdAndIsReadOrderByCreatedAtDesc(
            Long recipientId, Boolean isRead);

    // Count unread
    int countByRecipientIdAndIsRead(Long recipientId, Boolean isRead);

    // By notification type
    List<Notification> findByRecipientIdAndType(
            Long recipientId, NotificationType type);

    // By related entity
    List<Notification> findByRelatedId(Long relatedId);

    // Mark all as read for a recipient
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true " +
            "WHERE n.recipientId = :recipientId " +
            "AND n.isRead = false")
    int markAllAsRead(@Param("recipientId") Long recipientId);

    // Delete all read notifications for a recipient
    @Modifying
    @Query("DELETE FROM Notification n " +
            "WHERE n.recipientId = :recipientId " +
            "AND n.isRead = true")
    int deleteAllRead(@Param("recipientId") Long recipientId);

    // Count all notifications for a recipient
    int countByRecipientId(Long recipientId);
}