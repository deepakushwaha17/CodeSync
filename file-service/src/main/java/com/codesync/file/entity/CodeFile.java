package com.codesync.file.entity;

import com.codesync.file.enums.FileType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "code_files",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {"project_id", "path"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long fileId;

    // References project-service — stored as ID only
    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 255)
    private String name;

    // Full path e.g. "src/main/App.java" or "src/main"
    @Column(nullable = false, length = 1000)
    private String path;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FileType fileType;

    // Programming language e.g. "java", "python", "javascript"
    @Column(length = 50)
    private String language;

    // Full file content stored as TEXT
    @Column(columnDefinition = "TEXT")
    private String content;

    // File size in bytes
    @Column
    private Long size;

    // References auth-service user ID
    @Column(nullable = false)
    private Long createdById;

    // Last user to edit this file
    @Column
    private Long lastEditedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // Soft delete flag — file is hidden but not removed from DB
    @Column(nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;
}