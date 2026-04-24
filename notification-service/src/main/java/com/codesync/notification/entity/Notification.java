package com.codesync.notification.entity;

import com.codesync.notification.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications",
        indexes = {
                @Index(name = "idx_recipient_id",
                        columnList = "recipient_id"),
                @Index(name = "idx_recipient_read",
                        columnList = "recipient_id, is_read")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationId;

    // Who receives this notification
    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    // Who triggered the event (null for system notifications)
    @Column(name = "actor_id")
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    // ID of related entity (sessionId, commentId, snapshotId, etc.)
    private Long relatedId;

    // Type of related entity e.g. "SESSION", "COMMENT", "SNAPSHOT"
    @Column(length = 50)
    private String relatedType;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}