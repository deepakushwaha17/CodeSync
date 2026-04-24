package com.codesync.notification.dto.request;

import com.codesync.notification.enums.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SendBulkNotificationRequest {

    // List of recipient user IDs
    @NotEmpty(message = "At least one recipient is required")
    private List<Long> recipientIds;

    // Optional actor
    private Long actorId;

    @NotNull(message = "Notification type is required")
    private NotificationType type;

    @NotBlank(message = "Title is required")
    @Size(max = 200)
    private String title;

    @NotBlank(message = "Message is required")
    @Size(max = 1000)
    private String message;

    private Long relatedId;

    @Size(max = 50)
    private String relatedType;
}