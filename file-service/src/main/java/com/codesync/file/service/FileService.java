package com.codesync.file.service;

import com.codesync.file.dto.request.CreateFileRequest;
import com.codesync.file.dto.request.CreateFolderRequest;
import com.codesync.file.dto.request.MoveFileRequest;
import com.codesync.file.dto.request.RenameFileRequest;
import com.codesync.file.dto.request.UpdateFileContentRequest;
import com.codesync.file.dto.response.FileResponse;
import com.codesync.file.dto.response.FileTreeNode;

import java.util.List;

public interface FileService {

    // Create
    FileResponse createFile(Long userId, CreateFileRequest request);
    FileResponse createFolder(Long userId, CreateFolderRequest request);

    // Read
    FileResponse getFileById(Long fileId);
    List<FileResponse> getFilesByProject(Long projectId);
    String getFileContent(Long fileId);
    List<FileTreeNode> getFileTree(Long projectId);
    List<FileResponse> searchInProject(Long projectId, String keyword);

    // Update
    FileResponse updateFileContent(Long fileId, Long userId,
                                   UpdateFileContentRequest request);
    FileResponse renameFile(Long fileId, Long userId,
                            RenameFileRequest request);
    FileResponse moveFile(Long fileId, Long userId,
                          MoveFileRequest request);

    // Delete and Restore
    void deleteFile(Long fileId, Long userId);
    FileResponse restoreFile(Long fileId, Long userId);

    // Stats
    int getFileCount(Long projectId);
}