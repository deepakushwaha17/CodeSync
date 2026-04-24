package com.codesync.execution.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "supported_languages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportedLanguage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // e.g. "java", "python", "javascript"
    @Column(nullable = false, unique = true, length = 50)
    private String name;

    // Display name e.g. "Java", "Python 3", "Node.js"
    @Column(nullable = false, length = 100)
    private String displayName;

    // Runtime version e.g. "21", "3.11", "18"
    @Column(nullable = false, length = 50)
    private String version;

    // Docker image used for sandbox
    @Column(nullable = false, length = 200)
    private String dockerImage;

    // File extension e.g. ".java", ".py"
    @Column(nullable = false, length = 10)
    private String fileExtension;

    // Example hello world for this language
    @Column(columnDefinition = "TEXT")
    private String helloWorldCode;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}