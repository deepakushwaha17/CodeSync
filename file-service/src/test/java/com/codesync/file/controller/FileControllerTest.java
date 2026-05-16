package com.codesync.file.controller;

import com.codesync.file.dto.request.*;
import com.codesync.file.dto.response.FileResponse;
import com.codesync.file.dto.response.FileTreeNode;
import com.codesync.file.enums.FileType;
import com.codesync.file.exception.FileAlreadyExistsException;
import com.codesync.file.exception.FileNotFoundException;
import com.codesync.file.service.FileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@WebMvcTest(
        value = FileController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class
        }
)
@AutoConfigureMockMvc(addFilters = false)
class FileControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private FileService fileService;

    // ── Fixture ───────────────────────────────────────────────────────

    private FileResponse buildFileResponse(Long id, String name,
                                           String path, FileType type) {
        return FileResponse.builder()
                .fileId(id).projectId(1L).name(name).path(path)
                .fileType(type).language("java").content("code")
                .size(4L).createdById(1L).lastEditedBy(1L)
                .isDeleted(false).createdAt(LocalDateTime.now())
                .build();
    }

    // ════════════════════════════════════════════════════════════════
    // POST /api/v1/files
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST / - 201 Created on successful file creation")
    void createFile_valid_returns201() throws Exception {
        CreateFileRequest req = new CreateFileRequest();
        req.setProjectId(1L);
        req.setName("App.java");
        req.setParentPath("src");
        req.setLanguage("java");
        req.setContent("code");

        when(fileService.createFile(eq(10L), any()))
                .thenReturn(buildFileResponse(1L, "App.java", "src/App.java", FileType.FILE));

        mockMvc.perform(post("/api/v1/files")
                        .header("X-Auth-User-Id", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("App.java"))
                .andExpect(jsonPath("$.data.path").value("src/App.java"));
    }

    @Test
    @DisplayName("POST / - 409 Conflict when path already exists")
    void createFile_pathConflict_returns409() throws Exception {
        CreateFileRequest req = new CreateFileRequest();
        req.setProjectId(1L);
        req.setName("App.java");
        req.setParentPath("src");

        when(fileService.createFile(eq(10L), any()))
                .thenThrow(new FileAlreadyExistsException("A file already exists at path: src/App.java"));

        mockMvc.perform(post("/api/v1/files")
                        .header("X-Auth-User-Id", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST / - 400 Bad Request when required fields are missing")
    void createFile_missingName_returns400() throws Exception {
        CreateFileRequest req = new CreateFileRequest();
        req.setProjectId(1L); // name is missing

        mockMvc.perform(post("/api/v1/files")
                        .header("X-Auth-User-Id", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ════════════════════════════════════════════════════════════════
    // POST /api/v1/files/folder
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /folder - 201 Created on successful folder creation")
    void createFolder_valid_returns201() throws Exception {
        CreateFolderRequest req = new CreateFolderRequest();
        req.setProjectId(1L);
        req.setName("utils");
        req.setParentPath("src");

        when(fileService.createFolder(eq(10L), any()))
                .thenReturn(buildFileResponse(2L, "utils", "src/utils", FileType.FOLDER));

        mockMvc.perform(post("/api/v1/files/folder")
                        .header("X-Auth-User-Id", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fileType").value("FOLDER"));
    }

    @Test
    @DisplayName("POST /folder - 409 Conflict when folder path already exists")
    void createFolder_pathConflict_returns409() throws Exception {
        CreateFolderRequest req = new CreateFolderRequest();
        req.setProjectId(1L);
        req.setName("utils");
        req.setParentPath("src");

        when(fileService.createFolder(eq(10L), any()))
                .thenThrow(new FileAlreadyExistsException("Folder exists."));

        mockMvc.perform(post("/api/v1/files/folder")
                        .header("X-Auth-User-Id", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/files/{fileId}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /{fileId} - 200 OK for existing file")
    void getFileById_existing_returns200() throws Exception {
        when(fileService.getFileById(1L))
                .thenReturn(buildFileResponse(1L, "App.java", "App.java", FileType.FILE));

        mockMvc.perform(get("/api/v1/files/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileId").value(1))
                .andExpect(jsonPath("$.data.name").value("App.java"));
    }

    @Test
    @DisplayName("GET /{fileId} - 404 Not Found for missing or deleted file")
    void getFileById_notFound_returns404() throws Exception {
        when(fileService.getFileById(99L))
                .thenThrow(new FileNotFoundException("File not found with ID: 99"));

        mockMvc.perform(get("/api/v1/files/99"))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/files/project/{projectId}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /project/{projectId} - 200 OK returns all project files")
    void getFilesByProject_returns200() throws Exception {
        when(fileService.getFilesByProject(1L)).thenReturn(List.of(
                buildFileResponse(1L, "A.java", "A.java", FileType.FILE),
                buildFileResponse(2L, "B.java", "B.java", FileType.FILE)
        ));

        mockMvc.perform(get("/api/v1/files/project/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("GET /project/{projectId} - 200 OK returns empty list when no files")
    void getFilesByProject_noFiles_returnsEmptyList() throws Exception {
        when(fileService.getFilesByProject(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/files/project/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/files/{fileId}/content
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /{fileId}/content - 200 OK returns file content string")
    void getFileContent_existing_returns200() throws Exception {
        when(fileService.getFileContent(1L)).thenReturn("class App {}");

        mockMvc.perform(get("/api/v1/files/1/content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("class App {}"));
    }

    @Test
    @DisplayName("GET /{fileId}/content - 400 Bad Request for a FOLDER")
    void getFileContent_folder_returns400() throws Exception {
        when(fileService.getFileContent(1L))
                .thenThrow(new IllegalArgumentException("Cannot get content of a folder."));

        mockMvc.perform(get("/api/v1/files/1/content"))
                .andExpect(status().isBadRequest());
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/files/project/{projectId}/tree
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /project/{projectId}/tree - 200 OK returns file tree")
    void getFileTree_returns200() throws Exception {
        FileTreeNode node = FileTreeNode.builder()
                .fileId(1L).name("src").path("src")
                .fileType(FileType.FOLDER).children(List.of())
                .build();

        when(fileService.getFileTree(1L)).thenReturn(List.of(node));

        mockMvc.perform(get("/api/v1/files/project/1/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("src"))
                .andExpect(jsonPath("$.data[0].fileType").value("FOLDER"));
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/files/project/{projectId}/search
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /project/{projectId}/search - 200 OK returns matched files")
    void searchInProject_keyword_returns200() throws Exception {
        when(fileService.searchInProject(1L, "app")).thenReturn(
                List.of(buildFileResponse(1L, "App.java", "App.java", FileType.FILE)));

        mockMvc.perform(get("/api/v1/files/project/1/search")
                        .param("keyword", "app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("App.java"));
    }

    @Test
    @DisplayName("GET /project/{projectId}/search - 200 OK returns empty list when no match")
    void searchInProject_noMatch_returnsEmpty() throws Exception {
        when(fileService.searchInProject(1L, "xyz")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/files/project/1/search")
                        .param("keyword", "xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ════════════════════════════════════════════════════════════════
    // PUT /api/v1/files/{fileId}/content
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PUT /{fileId}/content - 200 OK on successful content update")
    void updateContent_valid_returns200() throws Exception {
        UpdateFileContentRequest req = new UpdateFileContentRequest();
        req.setContent("new content");

        when(fileService.updateFileContent(eq(1L), eq(10L), any()))
                .thenReturn(buildFileResponse(1L, "App.java", "App.java", FileType.FILE));

        mockMvc.perform(put("/api/v1/files/1/content")
                        .header("X-Auth-User-Id", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PUT /{fileId}/content - 400 Bad Request for a FOLDER")
    void updateContent_folder_returns400() throws Exception {
        UpdateFileContentRequest req = new UpdateFileContentRequest();
        req.setContent("content");

        when(fileService.updateFileContent(eq(1L), eq(10L), any()))
                .thenThrow(new IllegalArgumentException("Cannot update content of a folder."));

        mockMvc.perform(put("/api/v1/files/1/content")
                        .header("X-Auth-User-Id", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /{fileId}/content - 404 Not Found for missing file")
    void updateContent_notFound_returns404() throws Exception {
        UpdateFileContentRequest req = new UpdateFileContentRequest();
        req.setContent("content");

        when(fileService.updateFileContent(eq(99L), eq(10L), any()))
                .thenThrow(new FileNotFoundException("File not found."));

        mockMvc.perform(put("/api/v1/files/99/content")
                        .header("X-Auth-User-Id", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════
    // PUT /api/v1/files/{fileId}/rename
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PUT /{fileId}/rename - 200 OK on successful rename")
    void renameFile_valid_returns200() throws Exception {
        RenameFileRequest req = new RenameFileRequest();
        req.setNewName("Renamed.java");

        when(fileService.renameFile(eq(1L), eq(10L), any()))
                .thenReturn(buildFileResponse(1L, "Renamed.java", "src/Renamed.java", FileType.FILE));

        mockMvc.perform(put("/api/v1/files/1/rename")
                        .header("X-Auth-User-Id", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Renamed.java"));
    }

    @Test
    @DisplayName("PUT /{fileId}/rename - 409 Conflict when new name is taken")
    void renameFile_nameTaken_returns409() throws Exception {
        RenameFileRequest req = new RenameFileRequest();
        req.setNewName("Taken.java");

        when(fileService.renameFile(eq(1L), eq(10L), any()))
                .thenThrow(new FileAlreadyExistsException("Name already exists."));

        mockMvc.perform(put("/api/v1/files/1/rename")
                        .header("X-Auth-User-Id", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    // ════════════════════════════════════════════════════════════════
    // PUT /api/v1/files/{fileId}/move
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PUT /{fileId}/move - 200 OK on successful move")
    void moveFile_valid_returns200() throws Exception {
        MoveFileRequest req = new MoveFileRequest();
        req.setNewParentPath("main");

        when(fileService.moveFile(eq(1L), eq(10L), any()))
                .thenReturn(buildFileResponse(1L, "App.java", "main/App.java", FileType.FILE));

        mockMvc.perform(put("/api/v1/files/1/move")
                        .header("X-Auth-User-Id", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.path").value("main/App.java"));
    }

    @Test
    @DisplayName("PUT /{fileId}/move - 409 Conflict when destination path is taken")
    void moveFile_destinationTaken_returns409() throws Exception {
        MoveFileRequest req = new MoveFileRequest();
        req.setNewParentPath("main");

        when(fileService.moveFile(eq(1L), eq(10L), any()))
                .thenThrow(new FileAlreadyExistsException("File exists at destination."));

        mockMvc.perform(put("/api/v1/files/1/move")
                        .header("X-Auth-User-Id", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    // ════════════════════════════════════════════════════════════════
    // DELETE /api/v1/files/{fileId}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("DELETE /{fileId} - 200 OK on successful soft delete")
    void deleteFile_valid_returns200() throws Exception {
        doNothing().when(fileService).deleteFile(1L, 10L);

        mockMvc.perform(delete("/api/v1/files/1")
                        .header("X-Auth-User-Id", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("File deleted."));
    }

    @Test
    @DisplayName("DELETE /{fileId} - 404 Not Found for missing file")
    void deleteFile_notFound_returns404() throws Exception {
        doThrow(new FileNotFoundException("File not found."))
                .when(fileService).deleteFile(99L, 10L);

        mockMvc.perform(delete("/api/v1/files/99")
                        .header("X-Auth-User-Id", 10L))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════
    // POST /api/v1/files/{fileId}/restore
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /{fileId}/restore - 200 OK on successful restore")
    void restoreFile_deleted_returns200() throws Exception {
        when(fileService.restoreFile(1L, 10L))
                .thenReturn(buildFileResponse(1L, "App.java", "App.java", FileType.FILE));

        mockMvc.perform(post("/api/v1/files/1/restore")
                        .header("X-Auth-User-Id", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("File restored."))
                .andExpect(jsonPath("$.data.fileId").value(1));
    }

    @Test
    @DisplayName("POST /{fileId}/restore - 404 Not Found for unknown file")
    void restoreFile_notFound_returns404() throws Exception {
        when(fileService.restoreFile(99L, 10L))
                .thenThrow(new FileNotFoundException("File not found."));

        mockMvc.perform(post("/api/v1/files/99/restore")
                        .header("X-Auth-User-Id", 10L))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/files/project/{projectId}/count
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /project/{projectId}/count - 200 OK returns file count")
    void getFileCount_returns200WithCount() throws Exception {
        when(fileService.getFileCount(1L)).thenReturn(7);

        mockMvc.perform(get("/api/v1/files/project/1/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(7));
    }

    @Test
    @DisplayName("GET /project/{projectId}/count - 200 OK returns 0 for empty project")
    void getFileCount_emptyProject_returnsZero() throws Exception {
        when(fileService.getFileCount(1L)).thenReturn(0);

        mockMvc.perform(get("/api/v1/files/project/1/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(0));
    }
}