package com.codesync.file.dto.response;

import com.codesync.file.enums.FileType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileResponse {

    private Long fileId;
    private Long projectId;
    private String name;
    private String path;
    private FileType fileType;
    private String language;
    private String content;
    private Long size;
    private Long createdById;
    private Long lastEditedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isDeleted;
}