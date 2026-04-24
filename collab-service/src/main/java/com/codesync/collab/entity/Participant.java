package com.codesync.collab.entity;

import com.codesync.collab.enums.ParticipantRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "participants",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {"session_id", "user_id"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long participantId;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    // References auth-service
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParticipantRole role;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    // Null if still in session
    private LocalDateTime leftAt;

    // Live cursor position
    @Column
    @Builder.Default
    private Integer cursorLine = 0;

    @Column
    @Builder.Default
    private Integer cursorCol = 0;

    // Unique color for cursor display in editor
    @Column(length = 10)
    private String color;
}