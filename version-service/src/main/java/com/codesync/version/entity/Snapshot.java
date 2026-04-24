package com.codesync.version.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Snapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long snapshotId;

    // References project-service
    @Column(nullable = false)
    private Long projectId;

    // References file-service
    @Column(nullable = false)
    private Long fileId;

    // References auth-service
    @Column(nullable = false)
    private Long authorId;

    // Commit message
    @Column(nullable = false, length = 500)
    private String message;

    // Full file content at this point in time
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // SHA-256 hash of content for integrity check
    @Column(nullable = false, length = 64)
    private String hash;

    // Points to previous snapshot — null means first snapshot
    private Long parentSnapshotId;

    // Branch name — default is "main"
    @Column(nullable = false, length = 100)
    @Builder.Default
    private String branch = "main";

    // Optional release tag e.g. "v1.0.0"
    @Column(length = 100)
    private String tag;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}