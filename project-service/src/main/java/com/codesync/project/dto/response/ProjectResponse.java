package com.codesync.project.dto.response;

import com.codesync.project.enums.Visibility;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResponse {

    private Long projectId;
    private Long ownerId;
    private String name;
    private String description;
    private String language;
    private Visibility visibility;
    private Long templateId;
    private Boolean isArchived;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer starCount;
    private Integer forkCount;
    private Long forkedFromId;
    private Integer memberCount;
}