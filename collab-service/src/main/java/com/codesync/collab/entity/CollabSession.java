package com.codesync.collab.entity;

import com.codesync.collab.enums.SessionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "collab_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollabSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String sessionId;

    // References project-service
    @Column(nullable = false)
    private Long projectId;

    // References file-service
    @Column(nullable = false)
    private Long fileId;

    // References auth-service
    @Column(nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SessionStatus status = SessionStatus.ACTIVE;

    @Column(length = 50)
    private String language;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime endedAt;

    // Max allowed participants
    @Column(nullable = false)
    @Builder.Default
    private Integer maxParticipants = 10;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isPasswordProtected = false;

    // Hashed session password for protected sessions
    private String sessionPassword;

    // Track last activity for auto-cleanup
    private LocalDateTime lastActivityAt;
}