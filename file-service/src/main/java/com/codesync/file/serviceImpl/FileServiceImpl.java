package com.codesync.file.serviceImpl;

import com.codesync.file.dto.request.CreateFileRequest;
import com.codesync.file.dto.request.CreateFolderRequest;
import com.codesync.file.dto.request.MoveFileRequest;
import com.codesync.file.dto.request.RenameFileRequest;
import com.codesync.file.dto.request.UpdateFileContentRequest;
import com.codesync.file.dto.response.FileResponse;
import com.codesync.file.dto.response.FileTreeNode;
import com.codesync.file.entity.CodeFile;
import com.codesync.file.enums.FileType;
import com.codesync.file.exception.FileAlreadyExistsException;
import com.codesync.file.exception.FileNotFoundException;
import com.codesync.file.repository.FileRepository;
import com.codesync.file.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final FileRepository fileRepository;

    // ─────────────────────────────────────────────────────────────────
    // CREATE
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public FileResponse createFile(Long userId,
                                   CreateFileRequest request) {
        // Build full path
        String fullPath = buildPath(
                request.getParentPath(), request.getName());

        log.info("Creating file at path: {} in project: {}",
                fullPath, request.getProjectId());

        // Check for duplicate path
        if (fileRepository.existsByProjectIdAndPath(
                request.getProjectId(), fullPath)) {
            throw new FileAlreadyExistsException(
                    "A file already exists at path: " + fullPath);
        }

        String content = request.getContent() != null
                ? request.getContent() : "";

        CodeFile file = CodeFile.builder()
                .projectId(request.getProjectId())
                .name(request.getName())
                .path(fullPath)
                .fileType(FileType.FILE)
                .language(request.getLanguage())
                .content(content)
                .size((long) content.length())
                .createdById(userId)
                .lastEditedBy(userId)
                .isDeleted(false)
                .build();

        CodeFile saved = fileRepository.save(file);
        log.info("File created with ID: {}", saved.getFileId());
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public FileResponse createFolder(Long userId,
                                     CreateFolderRequest request) {
        String fullPath = buildPath(
                request.getParentPath(), request.getName());

        log.info("Creating folder at path: {} in project: {}",
                fullPath, request.getProjectId());

        if (fileRepository.existsByProjectIdAndPath(
                request.getProjectId(), fullPath)) {
            throw new FileAlreadyExistsException(
                    "A folder already exists at path: " + fullPath);
        }

        CodeFile folder = CodeFile.builder()
                .projectId(request.getProjectId())
                .name(request.getName())
                .path(fullPath)
                .fileType(FileType.FOLDER)
                .content(null)
                .size(0L)
                .createdById(userId)
                .lastEditedBy(userId)
                .isDeleted(false)
                .build();

        CodeFile saved = fileRepository.save(folder);
        log.info("Folder created with ID: {}", saved.getFileId());
        return mapToResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────────────

    @Override
    public FileResponse getFileById(Long fileId) {
        CodeFile file = findFileOrThrow(fileId);
        return mapToResponse(file);
    }

    @Override
    public List<FileResponse> getFilesByProject(Long projectId) {
        return fileRepository
                .findByProjectIdAndIsDeleted(projectId, false)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public String getFileContent(Long fileId) {
        CodeFile file = findFileOrThrow(fileId);

        if (file.getFileType() == FileType.FOLDER) {
            throw new IllegalArgumentException(
                    "Cannot get content of a folder.");
        }

        return file.getContent() != null ? file.getContent() : "";
    }

    @Override
    public List<FileTreeNode> getFileTree(Long projectId) {
        List<CodeFile> allFiles = fileRepository
                .findByProjectIdAndIsDeleted(projectId, false);

        // Build tree starting from root (path has no "/" separator)
        return buildTree(allFiles, "");
    }

    @Override
    public List<FileResponse> searchInProject(
            Long projectId, String keyword) {
        return fileRepository
                .searchInProject(projectId, keyword)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────
    // UPDATE
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public FileResponse updateFileContent(
            Long fileId, Long userId,
            UpdateFileContentRequest request) {

        CodeFile file = findFileOrThrow(fileId);

        if (file.getFileType() == FileType.FOLDER) {
            throw new IllegalArgumentException(
                    "Cannot update content of a folder.");
        }

        file.setContent(request.getContent());
        file.setSize((long) request.getContent().length());
        file.setLastEditedBy(userId);

        CodeFile updated = fileRepository.save(file);
        log.info("File content updated for ID: {} by user: {}",
                fileId, userId);

        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public FileResponse renameFile(Long fileId, Long userId,
                                   RenameFileRequest request) {
        CodeFile file = findFileOrThrow(fileId);

        String oldPath = file.getPath();
        String parentPath = getParentPath(oldPath);
        String newPath = buildPath(parentPath, request.getNewName());

        log.info("Renaming file from {} to {}", oldPath, newPath);

        // Check if new path already exists
        if (fileRepository.existsByProjectIdAndPath(
                file.getProjectId(), newPath)) {
            throw new FileAlreadyExistsException(
                    "A file/folder already exists with name: "
                            + request.getNewName());
        }

        // If renaming a folder — update all children paths too
        if (file.getFileType() == FileType.FOLDER) {
            updateChildrenPaths(file.getProjectId(),
                    oldPath, newPath);
        }

        file.setName(request.getNewName());
        file.setPath(newPath);
        file.setLastEditedBy(userId);

        CodeFile updated = fileRepository.save(file);
        log.info("File renamed successfully to: {}", newPath);
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public FileResponse moveFile(Long fileId, Long userId,
                                 MoveFileRequest request) {
        CodeFile file = findFileOrThrow(fileId);

        String oldPath = file.getPath();
        String newPath = buildPath(
                request.getNewParentPath(), file.getName());

        log.info("Moving file from {} to {}", oldPath, newPath);

        if (fileRepository.existsByProjectIdAndPath(
                file.getProjectId(), newPath)) {
            throw new FileAlreadyExistsException(
                    "A file already exists at destination: " + newPath);
        }

        // Update children if moving a folder
        if (file.getFileType() == FileType.FOLDER) {
            updateChildrenPaths(file.getProjectId(),
                    oldPath, newPath);
        }

        file.setPath(newPath);
        file.setLastEditedBy(userId);

        CodeFile updated = fileRepository.save(file);
        log.info("File moved to: {}", newPath);
        return mapToResponse(updated);
    }

    // ─────────────────────────────────────────────────────────────────
    // DELETE AND RESTORE
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteFile(Long fileId, Long userId) {
        CodeFile file = findFileOrThrow(fileId);

        // Soft delete
        file.setIsDeleted(true);
        file.setLastEditedBy(userId);
        fileRepository.save(file);

        // If folder — soft delete all children
        if (file.getFileType() == FileType.FOLDER) {
            List<CodeFile> children = fileRepository
                    .findByProjectIdAndPathStartingWith(
                            file.getProjectId(), file.getPath() + "/");
            children.forEach(child -> {
                child.setIsDeleted(true);
                child.setLastEditedBy(userId);
            });
            fileRepository.saveAll(children);
        }

        log.info("File/folder soft deleted: ID {}", fileId);
    }

    @Override
    @Transactional
    public FileResponse restoreFile(Long fileId, Long userId) {
        CodeFile file = fileRepository.findById(fileId)
                .orElseThrow(() -> new FileNotFoundException(
                        "File not found with ID: " + fileId));

        file.setIsDeleted(false);
        file.setLastEditedBy(userId);
        CodeFile restored = fileRepository.save(file);

        log.info("File restored: ID {}", fileId);
        return mapToResponse(restored);
    }

    // ─────────────────────────────────────────────────────────────────
    // STATS
    // ─────────────────────────────────────────────────────────────────

    @Override
    public int getFileCount(Long projectId) {
        return fileRepository.countByProjectIdAndIsDeleted(
                projectId, false);
    }

    // ─────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────

    private CodeFile findFileOrThrow(Long fileId) {
        return fileRepository.findById(fileId)
                .filter(f -> !f.getIsDeleted())
                .orElseThrow(() -> new FileNotFoundException(
                        "File not found with ID: " + fileId));
    }

    /**
     * Build full path from parent path and file name.
     * Examples:
     *   ("", "App.java")      → "App.java"
     *   ("src", "App.java")   → "src/App.java"
     *   ("src/main", "Utils") → "src/main/Utils"
     */
    private String buildPath(String parentPath, String name) {
        if (parentPath == null || parentPath.isBlank()) {
            return name;
        }
        return parentPath + "/" + name;
    }

    /**
     * Get parent path from full path.
     * Examples:
     *   "src/main/App.java" → "src/main"
     *   "App.java"          → ""
     */
    private String getParentPath(String fullPath) {
        int lastSlash = fullPath.lastIndexOf("/");
        if (lastSlash == -1) return "";
        return fullPath.substring(0, lastSlash);
    }

    /**
     * When a folder is renamed or moved,
     * update paths of all its children.
     */
    private void updateChildrenPaths(
            Long projectId, String oldPrefix, String newPrefix) {

        List<CodeFile> children = fileRepository
                .findByProjectIdAndPathStartingWith(
                        projectId, oldPrefix + "/");

        children.forEach(child -> {
            String updatedPath = child.getPath()
                    .replaceFirst(oldPrefix, newPrefix);
            child.setPath(updatedPath);
        });

        fileRepository.saveAll(children);
        log.info("Updated {} children paths from {} to {}",
                children.size(), oldPrefix, newPrefix);
    }

    /**
     * Recursively build file tree from flat list.
     * parentPath="" means root level items.
     */
    private List<FileTreeNode> buildTree(
            List<CodeFile> allFiles, String parentPath) {

        return allFiles.stream()
                .filter(f -> {
                    String parent = getParentPath(f.getPath());
                    return parent.equals(parentPath);
                })
                .map(f -> {
                    FileTreeNode node = FileTreeNode.builder()
                            .fileId(f.getFileId())
                            .name(f.getName())
                            .path(f.getPath())
                            .fileType(f.getFileType())
                            .language(f.getLanguage())
                            .build();

                    // Recursively add children for folders
                    if (f.getFileType() == FileType.FOLDER) {
                        node.setChildren(
                                buildTree(allFiles, f.getPath()));
                    }

                    return node;
                })
                .toList();
    }

    private FileResponse mapToResponse(CodeFile f) {
        return FileResponse.builder()
                .fileId(f.getFileId())
                .projectId(f.getProjectId())
                .name(f.getName())
                .path(f.getPath())
                .fileType(f.getFileType())
                .language(f.getLanguage())
                .content(f.getContent())
                .size(f.getSize())
                .createdById(f.getCreatedById())
                .lastEditedBy(f.getLastEditedBy())
                .createdAt(f.getCreatedAt())
                .updatedAt(f.getUpdatedAt())
                .isDeleted(f.getIsDeleted())
                .build();
    }
}