package com.codesync.version.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotResponse {

    private Long snapshotId;
    private Long projectId;
    private Long fileId;
    private Long authorId;
    private String message;
    private String content;
    private String hash;
    private Long parentSnapshotId;
    private String branch;
    private String tag;
    private LocalDateTime createdAt;
}