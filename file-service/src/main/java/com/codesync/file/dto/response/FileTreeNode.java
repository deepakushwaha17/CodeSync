package com.codesync.file.dto.response;

import com.codesync.file.enums.FileType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileTreeNode {

    private Long fileId;
    private String name;
    private String path;
    private FileType fileType;
    private String language;

    // Children nodes — only populated for FOLDER type
    private List<FileTreeNode> children;
}