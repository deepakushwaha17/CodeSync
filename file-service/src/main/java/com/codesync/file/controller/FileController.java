package com.codesync.file.controller;

import com.codesync.file.dto.request.CreateFileRequest;
import com.codesync.file.dto.request.CreateFolderRequest;
import com.codesync.file.dto.request.MoveFileRequest;
import com.codesync.file.dto.request.RenameFileRequest;
import com.codesync.file.dto.request.UpdateFileContentRequest;
import com.codesync.file.dto.response.ApiResponse;
import com.codesync.file.dto.response.FileResponse;
import com.codesync.file.dto.response.FileTreeNode;
import com.codesync.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Tag(name = "File Service",
        description = "File and Editor Management APIs")
public class FileController {

    private final FileService fileService;

    // ── POST /api/v1/files ────────────────────────────────────────────
    @PostMapping
    @Operation(summary = "Create a new file")
    public ResponseEntity<ApiResponse<FileResponse>> createFile(
            @RequestHeader("X-Auth-User-Id") Long userId,
            @Valid @RequestBody CreateFileRequest request) {

        FileResponse response = fileService.createFile(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "File created successfully.", response));
    }

    // ── POST /api/v1/files/folder ─────────────────────────────────────
    @PostMapping("/folder")
    @Operation(summary = "Create a new folder")
    public ResponseEntity<ApiResponse<FileResponse>> createFolder(
            @RequestHeader("X-Auth-User-Id") Long userId,
            @Valid @RequestBody CreateFolderRequest request) {

        FileResponse response =
                fileService.createFolder(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Folder created successfully.", response));
    }

    // ── GET /api/v1/files/{fileId} ────────────────────────────────────
    @GetMapping("/{fileId}")
    @Operation(summary = "Get file by ID")
    public ResponseEntity<ApiResponse<FileResponse>> getFileById(
            @PathVariable Long fileId) {

        FileResponse response = fileService.getFileById(fileId);
        return ResponseEntity.ok(
                ApiResponse.success("File fetched.", response));
    }

    // ── GET /api/v1/files/project/{projectId} ─────────────────────────
    @GetMapping("/project/{projectId}")
    @Operation(summary = "Get all files in a project")
    public ResponseEntity<ApiResponse<List<FileResponse>>>
    getFilesByProject(@PathVariable Long projectId) {

        List<FileResponse> files =
                fileService.getFilesByProject(projectId);
        return ResponseEntity.ok(
                ApiResponse.success("Files fetched.", files));
    }

    // ── GET /api/v1/files/{fileId}/content ────────────────────────────
    @GetMapping("/{fileId}/content")
    @Operation(summary = "Get file content for editor")
    public ResponseEntity<ApiResponse<String>> getFileContent(
            @PathVariable Long fileId) {

        String content = fileService.getFileContent(fileId);
        return ResponseEntity.ok(
                ApiResponse.success("Content fetched.", content));
    }

    // ── GET /api/v1/files/project/{projectId}/tree ────────────────────
    @GetMapping("/project/{projectId}/tree")
    @Operation(summary = "Get full file tree of a project")
    public ResponseEntity<ApiResponse<List<FileTreeNode>>> getFileTree(
            @PathVariable Long projectId) {

        List<FileTreeNode> tree = fileService.getFileTree(projectId);
        return ResponseEntity.ok(
                ApiResponse.success("File tree fetched.", tree));
    }

    // ── GET /api/v1/files/project/{projectId}/search?keyword= ─────────
    @GetMapping("/project/{projectId}/search")
    @Operation(summary = "Search files in a project by keyword")
    public ResponseEntity<ApiResponse<List<FileResponse>>>
    searchInProject(
            @PathVariable Long projectId,
            @RequestParam String keyword) {

        List<FileResponse> results =
                fileService.searchInProject(projectId, keyword);
        return ResponseEntity.ok(
                ApiResponse.success("Search results.", results));
    }

    // ── PUT /api/v1/files/{fileId}/content ────────────────────────────
    @PutMapping("/{fileId}/content")
    @Operation(summary = "Update file content (editor save)")
    public ResponseEntity<ApiResponse<FileResponse>> updateContent(
            @PathVariable Long fileId,
            @RequestHeader("X-Auth-User-Id") Long userId,
            @Valid @RequestBody UpdateFileContentRequest request) {

        FileResponse response = fileService.updateFileContent(
                fileId, userId, request);
        return ResponseEntity.ok(
                ApiResponse.success("Content updated.", response));
    }

    // ── PUT /api/v1/files/{fileId}/rename ─────────────────────────────
    @PutMapping("/{fileId}/rename")
    @Operation(summary = "Rename a file or folder")
    public ResponseEntity<ApiResponse<FileResponse>> renameFile(
            @PathVariable Long fileId,
            @RequestHeader("X-Auth-User-Id") Long userId,
            @Valid @RequestBody RenameFileRequest request) {

        FileResponse response =
                fileService.renameFile(fileId, userId, request);
        return ResponseEntity.ok(
                ApiResponse.success("File renamed.", response));
    }

    // ── PUT /api/v1/files/{fileId}/move ───────────────────────────────
    @PutMapping("/{fileId}/move")
    @Operation(summary = "Move a file or folder")
    public ResponseEntity<ApiResponse<FileResponse>> moveFile(
            @PathVariable Long fileId,
            @RequestHeader("X-Auth-User-Id") Long userId,
            @Valid @RequestBody MoveFileRequest request) {

        FileResponse response =
                fileService.moveFile(fileId, userId, request);
        return ResponseEntity.ok(
                ApiResponse.success("File moved.", response));
    }

    // ── DELETE /api/v1/files/{fileId} ─────────────────────────────────
    @DeleteMapping("/{fileId}")
    @Operation(summary = "Soft delete a file or folder")
    public ResponseEntity<ApiResponse<Void>> deleteFile(
            @PathVariable Long fileId,
            @RequestHeader("X-Auth-User-Id") Long userId) {

        fileService.deleteFile(fileId, userId);
        return ResponseEntity.ok(
                ApiResponse.success("File deleted."));
    }

    // ── POST /api/v1/files/{fileId}/restore ───────────────────────────
    @PostMapping("/{fileId}/restore")
    @Operation(summary = "Restore a soft deleted file")
    public ResponseEntity<ApiResponse<FileResponse>> restoreFile(
            @PathVariable Long fileId,
            @RequestHeader("X-Auth-User-Id") Long userId) {

        FileResponse response =
                fileService.restoreFile(fileId, userId);
        return ResponseEntity.ok(
                ApiResponse.success("File restored.", response));
    }

    // ── GET /api/v1/files/project/{projectId}/count ───────────────────
    @GetMapping("/project/{projectId}/count")
    @Operation(summary = "Get file count in a project")
    public ResponseEntity<ApiResponse<Integer>> getFileCount(
            @PathVariable Long projectId) {

        int count = fileService.getFileCount(projectId);
        return ResponseEntity.ok(
                ApiResponse.success("File count fetched.", count));
    }
}