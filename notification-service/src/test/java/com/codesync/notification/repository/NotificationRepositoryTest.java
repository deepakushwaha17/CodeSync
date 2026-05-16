package com.codesync.notification.repository;

import com.codesync.notification.entity.Notification;
import com.codesync.notification.enums.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void setUp() { notificationRepository.deleteAll(); }

    // ── Helper ────────────────────────────────────────────────────────

    private Notification build(Long recipientId, Long actorId,
                               NotificationType type,
                               String title, String message,
                               Long relatedId, String relatedType,
                               boolean isRead) {
        return Notification.builder()
                .recipientId(recipientId).actorId(actorId)
                .type(type).title(title).message(message)
                .relatedId(relatedId).relatedType(relatedType)
                .isRead(isRead)
                .build();
    }

    // ── findByRecipientIdOrderByCreatedAtDesc ─────────────────────────

    @Test
    @DisplayName("findByRecipientIdOrderByCreatedAtDesc - returns all notifications newest first")
    void findByRecipientId_returnsAllNewestFirst() {
        notificationRepository.save(build(1L, 2L,
                NotificationType.COMMENT_REPLY,
                "First", "msg1", 10L, "COMMENT", false));
        notificationRepository.save(build(1L, 3L,
                NotificationType.PARTICIPANT_JOINED,
                "Second", "msg2", 20L, "SESSION", false));
        notificationRepository.save(build(2L, 1L,
                NotificationType.COMMENT_REPLY,
                "OtherUser", "msg3", 30L, "COMMENT", false));

        List<Notification> results =
                notificationRepository.findByRecipientIdOrderByCreatedAtDesc(1L);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(n -> n.getRecipientId().equals(1L));
        // Newest first — "Second" saved after "First"
        assertThat(results.get(0).getTitle()).isEqualTo("Second");
    }

    @Test
    @DisplayName("findByRecipientIdOrderByCreatedAtDesc - returns empty for unknown user")
    void findByRecipientId_unknown_returnsEmpty() {
        assertThat(notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(99L)).isEmpty();
    }

    // ── findByRecipientIdAndIsReadOrderByCreatedAtDesc ────────────────

    @Test
    @DisplayName("findByRecipientIdAndIsRead(false) - returns only unread notifications")
    void findByRecipientIdAndIsRead_false_returnsUnread() {
        notificationRepository.save(build(1L, 2L,
                NotificationType.COMMENT_REPLY,
                "Unread", "msg", 1L, "COMMENT", false));
        notificationRepository.save(build(1L, 3L,
                NotificationType.PARTICIPANT_JOINED,
                "Read", "msg", 2L, "SESSION", true));

        List<Notification> unread =
                notificationRepository
                        .findByRecipientIdAndIsReadOrderByCreatedAtDesc(1L, false);

        assertThat(unread).hasSize(1);
        assertThat(unread.get(0).getIsRead()).isFalse();
        assertThat(unread.get(0).getTitle()).isEqualTo("Unread");
    }

    @Test
    @DisplayName("findByRecipientIdAndIsRead(true) - returns only read notifications")
    void findByRecipientIdAndIsRead_true_returnsRead() {
        notificationRepository.save(build(1L, 2L,
                NotificationType.COMMENT_REPLY,
                "Unread", "msg", 1L, "COMMENT", false));
        notificationRepository.save(build(1L, 3L,
                NotificationType.PARTICIPANT_JOINED,
                "Read", "msg", 2L, "SESSION", true));

        List<Notification> read =
                notificationRepository
                        .findByRecipientIdAndIsReadOrderByCreatedAtDesc(1L, true);

        assertThat(read).hasSize(1);
        assertThat(read.get(0).getIsRead()).isTrue();
    }

    @Test
    @DisplayName("findByRecipientIdAndIsRead - returns empty when no match")
    void findByRecipientIdAndIsRead_noMatch_returnsEmpty() {
        notificationRepository.save(build(1L, 2L,
                NotificationType.COMMENT_REPLY,
                "Read", "msg", 1L, "COMMENT", true));

        List<Notification> unread =
                notificationRepository
                        .findByRecipientIdAndIsReadOrderByCreatedAtDesc(1L, false);

        assertThat(unread).isEmpty();
    }

    // ── countByRecipientIdAndIsRead ───────────────────────────────────

    @Test
    @DisplayName("countByRecipientIdAndIsRead(false) - returns correct unread count")
    void countUnread_returnsCorrectCount() {
        notificationRepository.save(build(1L, 2L,
                NotificationType.COMMENT_REPLY,
                "U1", "m", 1L, "COMMENT", false));
        notificationRepository.save(build(1L, 3L,
                NotificationType.COMMENT_REPLY,
                "U2", "m", 2L, "COMMENT", false));
        notificationRepository.save(build(1L, 4L,
                NotificationType.PARTICIPANT_JOINED,
                "R1", "m", 3L, "SESSION", true));

        assertThat(notificationRepository
                .countByRecipientIdAndIsRead(1L, false)).isEqualTo(2);
    }

    @Test
    @DisplayName("countByRecipientIdAndIsRead - returns 0 when none match")
    void countUnread_noneMatch_returnsZero() {
        notificationRepository.save(build(1L, 2L,
                NotificationType.COMMENT_REPLY,
                "Read", "m", 1L, "COMMENT", true));

        assertThat(notificationRepository
                .countByRecipientIdAndIsRead(1L, false)).isZero();
    }

    // ── findByRecipientIdAndType ──────────────────────────────────────

    @Test
    @DisplayName("findByRecipientIdAndType - returns notifications by type")
    void findByRecipientIdAndType_returnsMatchingType() {
        notificationRepository.save(build(1L, 2L,
                NotificationType.COMMENT_REPLY,
                "Reply", "m", 1L, "COMMENT", false));
        notificationRepository.save(build(1L, 3L,
                NotificationType.COMMENT_REPLY,
                "Reply2", "m", 2L, "COMMENT", false));
        notificationRepository.save(build(1L, 4L,
                NotificationType.PARTICIPANT_JOINED,
                "Joined", "m", 3L, "SESSION", false));

        List<Notification> replies =
                notificationRepository.findByRecipientIdAndType(
                        1L, NotificationType.COMMENT_REPLY);

        assertThat(replies).hasSize(2);
        assertThat(replies).allMatch(n ->
                n.getType() == NotificationType.COMMENT_REPLY);
    }

    @Test
    @DisplayName("findByRecipientIdAndType - returns empty when type not found")
    void findByRecipientIdAndType_noMatch_returnsEmpty() {
        notificationRepository.save(build(1L, 2L,
                NotificationType.COMMENT_REPLY,
                "Reply", "m", 1L, "COMMENT", false));

        List<Notification> results =
                notificationRepository.findByRecipientIdAndType(
                        1L, NotificationType.PARTICIPANT_JOINED);

        assertThat(results).isEmpty();
    }

    // ── findByRelatedId ───────────────────────────────────────────────

    @Test
    @DisplayName("findByRelatedId - returns all notifications for related entity")
    void findByRelatedId_existing_returnsNotifications() {
        notificationRepository.save(build(1L, 2L,
                NotificationType.COMMENT_REPLY,
                "A", "m", 100L, "COMMENT", false));
        notificationRepository.save(build(2L, 3L,
                NotificationType.COMMENT_REPLY,
                "B", "m", 100L, "COMMENT", false));
        notificationRepository.save(build(3L, 4L,
                NotificationType.PARTICIPANT_JOINED,
                "C", "m", 200L, "SESSION", false));

        List<Notification> results =
                notificationRepository.findByRelatedId(100L);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(n -> n.getRelatedId().equals(100L));
    }

    @Test
    @DisplayName("findByRelatedId - returns empty for unknown relatedId")
    void findByRelatedId_unknown_returnsEmpty() {
        assertThat(notificationRepository.findByRelatedId(999L)).isEmpty();
    }

    // ── markAllAsRead ─────────────────────────────────────────────────

    @Test
    @DisplayName("markAllAsRead - marks only unread notifications for recipient")
    void markAllAsRead_marksUnreadOnly() {
        notificationRepository.save(build(1L, 2L,
                NotificationType.COMMENT_REPLY,
                "U1", "m", 1L, "COMMENT", false));
        notificationRepository.save(build(1L, 3L,
                NotificationType.COMMENT_REPLY,
                "U2", "m", 2L, "COMMENT", false));
        notificationRepository.save(build(1L, 4L,
                NotificationType.PARTICIPANT_JOINED,
                "R1", "m", 3L, "SESSION", true)); // already read
        notificationRepository.save(build(2L, 1L,
                NotificationType.COMMENT_REPLY,
                "Other", "m", 4L, "COMMENT", false)); // different user

        int updated = notificationRepository.markAllAsRead(1L);

        assertThat(updated).isEqualTo(2); // only 2 were unread for user 1

        // Verify in DB
        List<Notification> stillUnread =
                notificationRepository
                        .findByRecipientIdAndIsReadOrderByCreatedAtDesc(1L, false);
        assertThat(stillUnread).isEmpty();

        // Other user's notifications unchanged
        assertThat(notificationRepository
                .countByRecipientIdAndIsRead(2L, false)).isEqualTo(1);
    }

    @Test
    @DisplayName("markAllAsRead - returns 0 when all are already read")
    void markAllAsRead_allAlreadyRead_returnsZero() {
        notificationRepository.save(build(1L, 2L,
                NotificationType.COMMENT_REPLY,
                "R", "m", 1L, "COMMENT", true));

        int updated = notificationRepository.markAllAsRead(1L);

        assertThat(updated).isZero();
    }

    @Test
    @DisplayName("markAllAsRead - returns 0 for user with no notifications")
    void markAllAsRead_noNotifications_returnsZero() {
        assertThat(notificationRepository.markAllAsRead(99L)).isZero();
    }

    // ── deleteAllRead ─────────────────────────────────────────────────

    @Test
    @DisplayName("deleteAllRead - deletes only read notifications for recipient")
    void deleteAllRead_deletesReadOnly() {
        notificationRepository.save(build(1L, 2L,
                NotificationType.COMMENT_REPLY,
                "Unread", "m", 1L, "COMMENT", false));
        notificationRepository.save(build(1L, 3L,
                NotificationType.PARTICIPANT_JOINED,
                "Read1", "m", 2L, "SESSION", true));
        notificationRepository.save(build(1L, 4L,
                NotificationType.COMMENT_REPLY,
                "Read2", "m", 3L, "COMMENT", true));
        notificationRepository.save(build(2L, 1L,
                NotificationType.COMMENT_REPLY,
                "OtherRead", "m", 4L, "COMMENT", true)); // different user

        int deleted = notificationRepository.deleteAllRead(1L);

        assertThat(deleted).isEqualTo(2);

        // Unread for user 1 still exists
        assertThat(notificationRepository
                .countByRecipientIdAndIsRead(1L, false)).isEqualTo(1);

        // Other user's read notification untouched
        assertThat(notificationRepository
                .countByRecipientIdAndIsRead(2L, true)).isEqualTo(1);
    }

    @Test
    @DisplayName("deleteAllRead - returns 0 when no read notifications exist")
    void deleteAllRead_noneRead_returnsZero() {
        notificationRepository.save(build(1L, 2L,
                NotificationType.COMMENT_REPLY,
                "Unread", "m", 1L, "COMMENT", false));

        assertThat(notificationRepository.deleteAllRead(1L)).isZero();
    }

    // ── countByRecipientId ────────────────────────────────────────────

    @Test
    @DisplayName("countByRecipientId - returns total count including read and unread")
    void countByRecipientId_returnsTotal() {
        notificationRepository.save(build(1L, 2L,
                NotificationType.COMMENT_REPLY,
                "N1", "m", 1L, "COMMENT", false));
        notificationRepository.save(build(1L, 3L,
                NotificationType.COMMENT_REPLY,
                "N2", "m", 2L, "COMMENT", true));
        notificationRepository.save(build(2L, 1L,
                NotificationType.COMMENT_REPLY,
                "N3", "m", 3L, "COMMENT", false));

        assertThat(notificationRepository.countByRecipientId(1L)).isEqualTo(2);
    }

    @Test
    @DisplayName("countByRecipientId - returns 0 for user with no notifications")
    void countByRecipientId_noNotifications_returnsZero() {
        assertThat(notificationRepository.countByRecipientId(99L)).isZero();
    }
}