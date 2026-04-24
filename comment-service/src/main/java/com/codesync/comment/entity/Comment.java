package com.codesync.comment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long commentId;

    // References project-service
    @Column(nullable = false)
    private Long projectId;

    // References file-service
    @Column(nullable = false)
    private Long fileId;

    // References auth-service
    @Column(nullable = false)
    private Long authorId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // Line number in the file this comment is anchored to
    @Column(nullable = false)
    private Integer lineNumber;

    // Optional column number
    private Integer columnNumber;

    // Null for top-level comments
    // Set to parent commentId for replies
    private Long parentCommentId;

    // True when the code issue has been addressed
    @Column(nullable = false)
    @Builder.Default
    private Boolean resolved = false;

    // References version-service snapshot
    // Links comment to exact file state
    private Long snapshotId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}