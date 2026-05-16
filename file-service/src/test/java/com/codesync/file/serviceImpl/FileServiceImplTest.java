package com.codesync.file.serviceImpl;

import com.codesync.file.dto.request.*;
import com.codesync.file.dto.response.FileResponse;
import com.codesync.file.dto.response.FileTreeNode;
import com.codesync.file.entity.CodeFile;
import com.codesync.file.enums.FileType;
import com.codesync.file.exception.FileAlreadyExistsException;
import com.codesync.file.exception.FileNotFoundException;
import com.codesync.file.repository.FileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileServiceImplTest {

    @Mock private FileRepository fileRepository;
    @InjectMocks private FileServiceImpl fileService;

    // ── Fixture ───────────────────────────────────────────────────────

    private CodeFile buildFile(Long id, Long projectId, String name,
                               String path, FileType type,
                               String content, boolean deleted) {
        return CodeFile.builder()
                .fileId(id)
                .projectId(projectId)
                .name(name)
                .path(path)
                .fileType(type)
                .language("java")
                .content(content)
                .size(content != null ? (long) content.length() : 0L)
                .createdById(1L)
                .lastEditedBy(1L)
                .isDeleted(deleted)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ════════════════════════════════════════════════════════════════
    // createFile()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("createFile - success: saves file and returns FileResponse")
    void createFile_validRequest_savesAndReturnsResponse() {
        CreateFileRequest req = new CreateFileRequest();
        req.setProjectId(1L);
        req.setName("App.java");
        req.setParentPath("src");
        req.setLanguage("java");
        req.setContent("public class App {}");

        when(fileRepository.existsByProjectIdAndPath(1L, "src/App.java"))
                .thenReturn(false);
        CodeFile saved = buildFile(10L, 1L, "App.java",
                "src/App.java", FileType.FILE, "public class App {}", false);
        when(fileRepository.save(any())).thenReturn(saved);

        FileResponse response = fileService.createFile(1L, req);

        assertThat(response.getName()).isEqualTo("App.java");
        assertThat(response.getPath()).isEqualTo("src/App.java");
        assertThat(response.getFileType()).isEqualTo(FileType.FILE);
        verify(fileRepository).save(any(CodeFile.class));
    }

    @Test
    @DisplayName("createFile - throws FileAlreadyExistsException when path is taken")
    void createFile_duplicatePath_throwsException() {
        CreateFileRequest req = new CreateFileRequest();
        req.setProjectId(1L);
        req.setName("App.java");
        req.setParentPath("src");

        when(fileRepository.existsByProjectIdAndPath(1L, "src/App.java"))
                .thenReturn(true);

        assertThatThrownBy(() -> fileService.createFile(1L, req))
                .isInstanceOf(FileAlreadyExistsException.class)
                .hasMessageContaining("src/App.java");

        verify(fileRepository, never()).save(any());
    }

    @Test
    @DisplayName("createFile - empty parentPath creates file at root level")
    void createFile_emptyParentPath_createsAtRoot() {
        CreateFileRequest req = new CreateFileRequest();
        req.setProjectId(1L);
        req.setName("Root.java");
        req.setParentPath("");
        req.setContent("code");

        when(fileRepository.existsByProjectIdAndPath(1L, "Root.java"))
                .thenReturn(false);
        CodeFile saved = buildFile(1L, 1L, "Root.java", "Root.java",
                FileType.FILE, "code", false);
        when(fileRepository.save(any())).thenReturn(saved);

        FileResponse response = fileService.createFile(1L, req);

        assertThat(response.getPath()).isEqualTo("Root.java");
    }

    @Test
    @DisplayName("createFile - null content defaults to empty string")
    void createFile_nullContent_defaultsToEmpty() {
        CreateFileRequest req = new CreateFileRequest();
        req.setProjectId(1L);
        req.setName("Empty.java");
        req.setParentPath("");
        req.setContent(null);

        when(fileRepository.existsByProjectIdAndPath(1L, "Empty.java"))
                .thenReturn(false);
        CodeFile saved = buildFile(1L, 1L, "Empty.java", "Empty.java",
                FileType.FILE, "", false);
        when(fileRepository.save(any())).thenReturn(saved);

        FileResponse response = fileService.createFile(1L, req);

        assertThat(response.getContent()).isEmpty();
    }

    // ════════════════════════════════════════════════════════════════
    // createFolder()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("createFolder - success: saves folder and returns FileResponse")
    void createFolder_validRequest_savesAndReturnsResponse() {
        CreateFolderRequest req = new CreateFolderRequest();
        req.setProjectId(1L);
        req.setName("utils");
        req.setParentPath("src");

        when(fileRepository.existsByProjectIdAndPath(1L, "src/utils"))
                .thenReturn(false);
        CodeFile saved = buildFile(2L, 1L, "utils", "src/utils",
                FileType.FOLDER, null, false);
        when(fileRepository.save(any())).thenReturn(saved);

        FileResponse response = fileService.createFolder(1L, req);

        assertThat(response.getFileType()).isEqualTo(FileType.FOLDER);
        assertThat(response.getPath()).isEqualTo("src/utils");
    }

    @Test
    @DisplayName("createFolder - throws FileAlreadyExistsException when folder path taken")
    void createFolder_duplicatePath_throwsException() {
        CreateFolderRequest req = new CreateFolderRequest();
        req.setProjectId(1L);
        req.setName("utils");
        req.setParentPath("src");

        when(fileRepository.existsByProjectIdAndPath(1L, "src/utils"))
                .thenReturn(true);

        assertThatThrownBy(() -> fileService.createFolder(1L, req))
                .isInstanceOf(FileAlreadyExistsException.class);
    }

    // ════════════════════════════════════════════════════════════════
    // getFileById()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getFileById - returns FileResponse for existing non-deleted file")
    void getFileById_existing_returnsResponse() {
        CodeFile file = buildFile(1L, 1L, "App.java", "App.java",
                FileType.FILE, "code", false);
        when(fileRepository.findById(1L)).thenReturn(Optional.of(file));

        FileResponse response = fileService.getFileById(1L);

        assertThat(response.getFileId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("App.java");
    }

    @Test
    @DisplayName("getFileById - throws FileNotFoundException for missing file")
    void getFileById_notFound_throwsException() {
        when(fileRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.getFileById(99L))
                .isInstanceOf(FileNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("getFileById - throws FileNotFoundException for soft-deleted file")
    void getFileById_softDeleted_throwsException() {
        CodeFile deleted = buildFile(1L, 1L, "Del.java", "Del.java",
                FileType.FILE, "code", true);
        when(fileRepository.findById(1L)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> fileService.getFileById(1L))
                .isInstanceOf(FileNotFoundException.class);
    }

    // ════════════════════════════════════════════════════════════════
    // getFilesByProject()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getFilesByProject - returns all active files in a project")
    void getFilesByProject_returnsActiveFiles() {
        when(fileRepository.findByProjectIdAndIsDeleted(1L, false))
                .thenReturn(List.of(
                        buildFile(1L, 1L, "A.java", "A.java", FileType.FILE, "c", false),
                        buildFile(2L, 1L, "B.java", "B.java", FileType.FILE, "c", false)
                ));

        List<FileResponse> results = fileService.getFilesByProject(1L);

        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("getFilesByProject - returns empty list when project has no files")
    void getFilesByProject_noFiles_returnsEmpty() {
        when(fileRepository.findByProjectIdAndIsDeleted(1L, false))
                .thenReturn(List.of());

        assertThat(fileService.getFilesByProject(1L)).isEmpty();
    }

    // ════════════════════════════════════════════════════════════════
    // getFileContent()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getFileContent - returns content string for existing file")
    void getFileContent_existingFile_returnsContent() {
        CodeFile file = buildFile(1L, 1L, "App.java", "App.java",
                FileType.FILE, "class App {}", false);
        when(fileRepository.findById(1L)).thenReturn(Optional.of(file));

        String content = fileService.getFileContent(1L);

        assertThat(content).isEqualTo("class App {}");
    }

    @Test
    @DisplayName("getFileContent - returns empty string when content is null")
    void getFileContent_nullContent_returnsEmptyString() {
        CodeFile file = buildFile(1L, 1L, "App.java", "App.java",
                FileType.FILE, null, false);
        when(fileRepository.findById(1L)).thenReturn(Optional.of(file));

        String content = fileService.getFileContent(1L);

        assertThat(content).isEmpty();
    }

    @Test
    @DisplayName("getFileContent - throws IllegalArgumentException for a FOLDER")
    void getFileContent_folder_throwsIllegalArgument() {
        CodeFile folder = buildFile(1L, 1L, "src", "src",
                FileType.FOLDER, null, false);
        when(fileRepository.findById(1L)).thenReturn(Optional.of(folder));

        assertThatThrownBy(() -> fileService.getFileContent(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot get content of a folder");
    }

    // ════════════════════════════════════════════════════════════════
    // getFileTree()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getFileTree - builds tree with folder containing children")
    void getFileTree_withNested_buildsCorrectTree() {
        CodeFile srcFolder = buildFile(1L, 1L, "src", "src",
                FileType.FOLDER, null, false);
        CodeFile appFile   = buildFile(2L, 1L, "App.java", "src/App.java",
                FileType.FILE, "code", false);
        CodeFile rootFile  = buildFile(3L, 1L, "Root.java", "Root.java",
                FileType.FILE, "code", false);

        when(fileRepository.findByProjectIdAndIsDeleted(1L, false))
                .thenReturn(List.of(srcFolder, appFile, rootFile));

        List<FileTreeNode> tree = fileService.getFileTree(1L);

        // Root level should have src folder and Root.java
        assertThat(tree).hasSize(2);

        // Find the src folder in tree
        FileTreeNode srcNode = tree.stream()
                .filter(n -> n.getName().equals("src"))
                .findFirst().orElseThrow();

        // src folder should have App.java as child
        assertThat(srcNode.getChildren()).hasSize(1);
        assertThat(srcNode.getChildren().get(0).getName()).isEqualTo("App.java");
    }

    @Test
    @DisplayName("getFileTree - returns empty list when project has no files")
    void getFileTree_noFiles_returnsEmptyList() {
        when(fileRepository.findByProjectIdAndIsDeleted(1L, false))
                .thenReturn(List.of());

        assertThat(fileService.getFileTree(1L)).isEmpty();
    }

    // ════════════════════════════════════════════════════════════════
    // searchInProject()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("searchInProject - returns matched files")
    void searchInProject_keyword_returnsMatches() {
        CodeFile match = buildFile(1L, 1L, "AppService.java",
                "AppService.java", FileType.FILE,
                "class AppService {}", false);
        when(fileRepository.searchInProject(1L, "appservice"))
                .thenReturn(List.of(match));

        List<FileResponse> results =
                fileService.searchInProject(1L, "appservice");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("AppService.java");
    }

    @Test
    @DisplayName("searchInProject - returns empty list when no match")
    void searchInProject_noMatch_returnsEmpty() {
        when(fileRepository.searchInProject(1L, "xyz")).thenReturn(List.of());

        assertThat(fileService.searchInProject(1L, "xyz")).isEmpty();
    }

    // ════════════════════════════════════════════════════════════════
    // updateFileContent()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("updateFileContent - updates content, size, and lastEditedBy")
    void updateFileContent_file_updatesSuccessfully() {
        CodeFile file = buildFile(1L, 1L, "App.java", "App.java",
                FileType.FILE, "old code", false);
        when(fileRepository.findById(1L)).thenReturn(Optional.of(file));
        when(fileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateFileContentRequest req = new UpdateFileContentRequest();
        req.setContent("new code");

        FileResponse response = fileService.updateFileContent(1L, 99L, req);

        assertThat(response.getContent()).isEqualTo("new code");
        assertThat(response.getSize()).isEqualTo(8L); // "new code".length()
        assertThat(response.getLastEditedBy()).isEqualTo(99L);
    }

    @Test
    @DisplayName("updateFileContent - throws IllegalArgumentException for a FOLDER")
    void updateFileContent_folder_throwsIllegalArgument() {
        CodeFile folder = buildFile(1L, 1L, "src", "src",
                FileType.FOLDER, null, false);
        when(fileRepository.findById(1L)).thenReturn(Optional.of(folder));

        UpdateFileContentRequest req = new UpdateFileContentRequest();
        req.setContent("content");

        assertThatThrownBy(() -> fileService.updateFileContent(1L, 1L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot update content of a folder");
    }

    @Test
    @DisplayName("updateFileContent - throws FileNotFoundException for missing file")
    void updateFileContent_notFound_throwsException() {
        when(fileRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.updateFileContent(
                99L, 1L, new UpdateFileContentRequest()))
                .isInstanceOf(FileNotFoundException.class);
    }

    // ════════════════════════════════════════════════════════════════
    // renameFile()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("renameFile - renames file and updates path")
    void renameFile_file_updatesNameAndPath() {
        CodeFile file = buildFile(1L, 1L, "Old.java", "src/Old.java",
                FileType.FILE, "code", false);
        when(fileRepository.findById(1L)).thenReturn(Optional.of(file));
        when(fileRepository.existsByProjectIdAndPath(1L, "src/New.java"))
                .thenReturn(false);
        when(fileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RenameFileRequest req = new RenameFileRequest();
        req.setNewName("New.java");

        FileResponse response = fileService.renameFile(1L, 1L, req);

        assertThat(response.getName()).isEqualTo("New.java");
        assertThat(response.getPath()).isEqualTo("src/New.java");
    }

    @Test
    @DisplayName("renameFile - throws FileAlreadyExistsException when new name is taken")
    void renameFile_newNameTaken_throwsException() {
        CodeFile file = buildFile(1L, 1L, "Old.java", "src/Old.java",
                FileType.FILE, "code", false);
        when(fileRepository.findById(1L)).thenReturn(Optional.of(file));
        when(fileRepository.existsByProjectIdAndPath(1L, "src/Taken.java"))
                .thenReturn(true);

        RenameFileRequest req = new RenameFileRequest();
        req.setNewName("Taken.java");

        assertThatThrownBy(() -> fileService.renameFile(1L, 1L, req))
                .isInstanceOf(FileAlreadyExistsException.class);
    }

    @Test
    @DisplayName("renameFile - folder rename also updates all children paths")
    void renameFile_folder_updatesChildrenPaths() {
        CodeFile folder = buildFile(1L, 1L, "oldSrc", "oldSrc",
                FileType.FOLDER, null, false);
        CodeFile child = buildFile(2L, 1L, "App.java", "oldSrc/App.java",
                FileType.FILE, "code", false);

        when(fileRepository.findById(1L)).thenReturn(Optional.of(folder));
        when(fileRepository.existsByProjectIdAndPath(1L, "newSrc"))
                .thenReturn(false);
        when(fileRepository.findByProjectIdAndPathStartingWith(1L, "oldSrc/"))
                .thenReturn(List.of(child));
        when(fileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fileRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        RenameFileRequest req = new RenameFileRequest();
        req.setNewName("newSrc");

        fileService.renameFile(1L, 1L, req);

        // Children paths should be updated
        assertThat(child.getPath()).isEqualTo("newSrc/App.java");
        verify(fileRepository).saveAll(any());
    }

    // ════════════════════════════════════════════════════════════════
    // moveFile()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("moveFile - moves file to new parent path")
    void moveFile_file_updatesPath() {
        CodeFile file = buildFile(1L, 1L, "App.java", "src/App.java",
                FileType.FILE, "code", false);
        when(fileRepository.findById(1L)).thenReturn(Optional.of(file));
        when(fileRepository.existsByProjectIdAndPath(1L, "main/App.java"))
                .thenReturn(false);
        when(fileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MoveFileRequest req = new MoveFileRequest();
        req.setNewParentPath("main");

        FileResponse response = fileService.moveFile(1L, 1L, req);

        assertThat(response.getPath()).isEqualTo("main/App.java");
    }

    @Test
    @DisplayName("moveFile - throws FileAlreadyExistsException when destination is taken")
    void moveFile_destinationTaken_throwsException() {
        CodeFile file = buildFile(1L, 1L, "App.java", "src/App.java",
                FileType.FILE, "code", false);
        when(fileRepository.findById(1L)).thenReturn(Optional.of(file));
        when(fileRepository.existsByProjectIdAndPath(1L, "main/App.java"))
                .thenReturn(true);

        MoveFileRequest req = new MoveFileRequest();
        req.setNewParentPath("main");

        assertThatThrownBy(() -> fileService.moveFile(1L, 1L, req))
                .isInstanceOf(FileAlreadyExistsException.class);
    }

    @Test
    @DisplayName("moveFile - folder move also updates all children paths")
    void moveFile_folder_updatesChildrenPaths() {
        CodeFile folder = buildFile(1L, 1L, "utils", "src/utils",
                FileType.FOLDER, null, false);
        CodeFile child = buildFile(2L, 1L, "Helper.java",
                "src/utils/Helper.java", FileType.FILE, "code", false);

        when(fileRepository.findById(1L)).thenReturn(Optional.of(folder));
        when(fileRepository.existsByProjectIdAndPath(1L, "main/utils"))
                .thenReturn(false);
        when(fileRepository.findByProjectIdAndPathStartingWith(1L, "src/utils/"))
                .thenReturn(List.of(child));
        when(fileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fileRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        MoveFileRequest req = new MoveFileRequest();
        req.setNewParentPath("main");

        fileService.moveFile(1L, 1L, req);

        assertThat(child.getPath()).isEqualTo("main/utils/Helper.java");
    }

    // ════════════════════════════════════════════════════════════════
    // deleteFile()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("deleteFile - soft deletes a single file (isDeleted=true)")
    void deleteFile_file_setsIsDeletedTrue() {
        CodeFile file = buildFile(1L, 1L, "App.java", "App.java",
                FileType.FILE, "code", false);
        when(fileRepository.findById(1L)).thenReturn(Optional.of(file));
        when(fileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        fileService.deleteFile(1L, 99L);

        verify(fileRepository).save(argThat(f -> f.getIsDeleted() && f.getLastEditedBy().equals(99L)));
    }

    @Test
    @DisplayName("deleteFile - soft deletes folder and all its children")
    void deleteFile_folder_softDeletesAllChildren() {
        CodeFile folder = buildFile(1L, 1L, "src", "src",
                FileType.FOLDER, null, false);
        CodeFile child1 = buildFile(2L, 1L, "App.java", "src/App.java",
                FileType.FILE, "code", false);
        CodeFile child2 = buildFile(3L, 1L, "Utils.java", "src/Utils.java",
                FileType.FILE, "code", false);

        when(fileRepository.findById(1L)).thenReturn(Optional.of(folder));
        when(fileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fileRepository.findByProjectIdAndPathStartingWith(1L, "src/"))
                .thenReturn(List.of(child1, child2));
        when(fileRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        fileService.deleteFile(1L, 99L);

        assertThat(folder.getIsDeleted()).isTrue();
        assertThat(child1.getIsDeleted()).isTrue();
        assertThat(child2.getIsDeleted()).isTrue();
        verify(fileRepository).saveAll(any());
    }

    @Test
    @DisplayName("deleteFile - throws FileNotFoundException for missing file")
    void deleteFile_notFound_throwsException() {
        when(fileRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.deleteFile(99L, 1L))
                .isInstanceOf(FileNotFoundException.class);
    }

    // ════════════════════════════════════════════════════════════════
    // restoreFile()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("restoreFile - sets isDeleted=false and returns FileResponse")
    void restoreFile_deletedFile_restoredSuccessfully() {
        CodeFile deleted = buildFile(1L, 1L, "App.java", "App.java",
                FileType.FILE, "code", true);
        when(fileRepository.findById(1L)).thenReturn(Optional.of(deleted));
        when(fileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FileResponse response = fileService.restoreFile(1L, 99L);

        assertThat(response.getIsDeleted()).isFalse();
        assertThat(response.getLastEditedBy()).isEqualTo(99L);
    }

    @Test
    @DisplayName("restoreFile - throws FileNotFoundException when ID does not exist")
    void restoreFile_notFound_throwsException() {
        when(fileRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.restoreFile(99L, 1L))
                .isInstanceOf(FileNotFoundException.class);
    }

    // ════════════════════════════════════════════════════════════════
    // getFileCount()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getFileCount - returns count of active files in project")
    void getFileCount_activeFiles_returnsCorrectCount() {
        when(fileRepository.countByProjectIdAndIsDeleted(1L, false)).thenReturn(5);

        int count = fileService.getFileCount(1L);

        assertThat(count).isEqualTo(5);
    }

    @Test
    @DisplayName("getFileCount - returns 0 when project has no active files")
    void getFileCount_noFiles_returnsZero() {
        when(fileRepository.countByProjectIdAndIsDeleted(1L, false)).thenReturn(0);

        assertThat(fileService.getFileCount(1L)).isZero();
    }
}