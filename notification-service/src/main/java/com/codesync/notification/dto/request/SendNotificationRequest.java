package com.codesync.notification.dto.request;

import com.codesync.notification.enums.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendNotificationRequest {

    @NotNull(message = "Recipient ID is required")
    private Long recipientId;

    // Actor who triggered the event — null for system
    private Long actorId;

    @NotNull(message = "Notification type is required")
    private NotificationType type;

    @NotBlank(message = "Title is required")
    @Size(max = 200,
            message = "Title must be under 200 characters")
    private String title;

    @NotBlank(message = "Message is required")
    @Size(max = 1000,
            message = "Message must be under 1000 characters")
    private String message;

    // Optional related entity info
    private Long relatedId;

    @Size(max = 50)
    private String relatedType;
}