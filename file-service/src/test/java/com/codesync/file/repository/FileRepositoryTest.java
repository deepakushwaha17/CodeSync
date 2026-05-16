package com.codesync.file.repository;

import com.codesync.file.entity.CodeFile;
import com.codesync.file.enums.FileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class FileRepositoryTest {

    @Autowired
    private FileRepository fileRepository;

    @BeforeEach
    void setUp() { fileRepository.deleteAll(); }

    // ── Helper ────────────────────────────────────────────────────────

    private CodeFile buildFile(Long projectId, String name,
                               String path, FileType type,
                               String language, String content,
                               boolean deleted) {
        return CodeFile.builder()
                .projectId(projectId)
                .name(name)
                .path(path)
                .fileType(type)
                .language(language)
                .content(content)
                .size(content != null ? (long) content.length() : 0L)
                .createdById(1L)
                .lastEditedBy(1L)
                .isDeleted(deleted)
                .build();
    }

    // ── findByProjectIdAndIsDeleted ────────────────────────────────────

    @Test
    @DisplayName("findByProjectIdAndIsDeleted - returns only non-deleted files")
    void findByProjectIdAndIsDeleted_false_returnsActiveFiles() {
        fileRepository.save(buildFile(1L, "App.java", "App.java",
                FileType.FILE, "java", "code", false));
        fileRepository.save(buildFile(1L, "Old.java", "Old.java",
                FileType.FILE, "java", "old", true));

        List<CodeFile> results =
                fileRepository.findByProjectIdAndIsDeleted(1L, false);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("App.java");
    }

    @Test
    @DisplayName("findByProjectIdAndIsDeleted - returns only deleted files")
    void findByProjectIdAndIsDeleted_true_returnsDeletedFiles() {
        fileRepository.save(buildFile(1L, "App.java", "App.java",
                FileType.FILE, "java", "code", false));
        fileRepository.save(buildFile(1L, "Old.java", "Old.java",
                FileType.FILE, "java", "old", true));

        List<CodeFile> results =
                fileRepository.findByProjectIdAndIsDeleted(1L, true);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Old.java");
    }

    // ── findByProjectIdAndPath ────────────────────────────────────────

    @Test
    @DisplayName("findByProjectIdAndPath - returns file for matching path")
    void findByProjectIdAndPath_existing_returnsFile() {
        fileRepository.save(buildFile(1L, "App.java", "src/App.java",
                FileType.FILE, "java", "code", false));

        Optional<CodeFile> result =
                fileRepository.findByProjectIdAndPath(1L, "src/App.java");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("App.java");
    }

    @Test
    @DisplayName("findByProjectIdAndPath - returns empty for wrong path")
    void findByProjectIdAndPath_wrongPath_returnsEmpty() {
        fileRepository.save(buildFile(1L, "App.java", "src/App.java",
                FileType.FILE, "java", "code", false));

        Optional<CodeFile> result =
                fileRepository.findByProjectIdAndPath(1L, "wrong/path");

        assertThat(result).isEmpty();
    }

    // ── findByProjectIdAndFileTypeAndIsDeleted ────────────────────────

    @Test
    @DisplayName("findByProjectIdAndFileTypeAndIsDeleted - returns only FILE type")
    void findByFileTypeAndIsDeleted_fileType_returnsFiles() {
        fileRepository.save(buildFile(1L, "App.java", "App.java",
                FileType.FILE, "java", "code", false));
        fileRepository.save(buildFile(1L, "src", "src",
                FileType.FOLDER, null, null, false));

        List<CodeFile> files = fileRepository
                .findByProjectIdAndFileTypeAndIsDeleted(1L, FileType.FILE, false);

        assertThat(files).hasSize(1);
        assertThat(files.get(0).getFileType()).isEqualTo(FileType.FILE);
    }

    @Test
    @DisplayName("findByProjectIdAndFileTypeAndIsDeleted - returns only FOLDER type")
    void findByFileTypeAndIsDeleted_folderType_returnsFolders() {
        fileRepository.save(buildFile(1L, "App.java", "App.java",
                FileType.FILE, "java", "code", false));
        fileRepository.save(buildFile(1L, "src", "src",
                FileType.FOLDER, null, null, false));

        List<CodeFile> folders = fileRepository
                .findByProjectIdAndFileTypeAndIsDeleted(1L, FileType.FOLDER, false);

        assertThat(folders).hasSize(1);
        assertThat(folders.get(0).getName()).isEqualTo("src");
    }

    // ── findByProjectIdAndLanguageAndIsDeleted ────────────────────────

    @Test
    @DisplayName("findByProjectIdAndLanguageAndIsDeleted - returns files by language")
    void findByLanguageAndIsDeleted_java_returnsJavaFiles() {
        fileRepository.save(buildFile(1L, "App.java", "App.java",
                FileType.FILE, "java", "code", false));
        fileRepository.save(buildFile(1L, "Main.py", "Main.py",
                FileType.FILE, "python", "code", false));

        List<CodeFile> results = fileRepository
                .findByProjectIdAndLanguageAndIsDeleted(1L, "java", false);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("App.java");
    }

    // ── findByLastEditedBy ────────────────────────────────────────────

    @Test
    @DisplayName("findByLastEditedBy - returns files edited by specific user")
    void findByLastEditedBy_userId_returnsFiles() {
        CodeFile f1 = buildFile(1L, "A.java", "A.java",
                FileType.FILE, "java", "code", false);
        f1.setLastEditedBy(10L);
        CodeFile f2 = buildFile(1L, "B.java", "B.java",
                FileType.FILE, "java", "code", false);
        f2.setLastEditedBy(20L);
        fileRepository.save(f1);
        fileRepository.save(f2);

        List<CodeFile> results = fileRepository.findByLastEditedBy(10L);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("A.java");
    }

    // ── countByProjectIdAndIsDeleted ──────────────────────────────────

    @Test
    @DisplayName("countByProjectIdAndIsDeleted - returns correct count of active files")
    void countByProjectIdAndIsDeleted_activeFiles_returnsCount() {
        fileRepository.save(buildFile(1L, "A.java", "A.java",
                FileType.FILE, "java", "c", false));
        fileRepository.save(buildFile(1L, "B.java", "B.java",
                FileType.FILE, "java", "c", false));
        fileRepository.save(buildFile(1L, "C.java", "C.java",
                FileType.FILE, "java", "c", true)); // deleted

        int count = fileRepository.countByProjectIdAndIsDeleted(1L, false);

        assertThat(count).isEqualTo(2);
    }

    // ── findByProjectIdAndIsDeletedTrue ───────────────────────────────

    @Test
    @DisplayName("findByProjectIdAndIsDeletedTrue - returns only soft-deleted files")
    void findByProjectIdAndIsDeletedTrue_returnsDeletedFiles() {
        fileRepository.save(buildFile(1L, "Active.java", "Active.java",
                FileType.FILE, "java", "c", false));
        fileRepository.save(buildFile(1L, "Deleted.java", "Deleted.java",
                FileType.FILE, "java", "c", true));

        List<CodeFile> deleted =
                fileRepository.findByProjectIdAndIsDeletedTrue(1L);

        assertThat(deleted).hasSize(1);
        assertThat(deleted.get(0).getName()).isEqualTo("Deleted.java");
    }

    // ── searchInProject ────────────────────────────────────────────────

    @Test
    @DisplayName("searchInProject - matches file name case-insensitively")
    void searchInProject_nameMatch_returnsFiles() {
        fileRepository.save(buildFile(1L, "AppService.java",
                "AppService.java", FileType.FILE, "java",
                "class AppService {}", false));
        fileRepository.save(buildFile(1L, "Main.java",
                "Main.java", FileType.FILE, "java",
                "public class Main {}", false));

        List<CodeFile> results =
                fileRepository.searchInProject(1L, "appservice");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("AppService.java");
    }

    @Test
    @DisplayName("searchInProject - matches content case-insensitively")
    void searchInProject_contentMatch_returnsFiles() {
        fileRepository.save(buildFile(1L, "App.java", "App.java",
                FileType.FILE, "java", "class MySpecialLogic {}", false));
        fileRepository.save(buildFile(1L, "Other.java", "Other.java",
                FileType.FILE, "java", "public class Other {}", false));

        List<CodeFile> results =
                fileRepository.searchInProject(1L, "myspeciallogic");

        assertThat(results).hasSize(1);
    }

    @Test
    @DisplayName("searchInProject - does not return deleted files")
    void searchInProject_deletedFiles_notReturned() {
        fileRepository.save(buildFile(1L, "Secret.java", "Secret.java",
                FileType.FILE, "java", "secret content", true));

        List<CodeFile> results =
                fileRepository.searchInProject(1L, "secret");

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("searchInProject - does not return FOLDER entries")
    void searchInProject_folders_notReturned() {
        fileRepository.save(buildFile(1L, "src", "src",
                FileType.FOLDER, null, "src", false));

        List<CodeFile> results = fileRepository.searchInProject(1L, "src");

        assertThat(results).isEmpty();
    }

    // ── existsByProjectIdAndPath ──────────────────────────────────────

    @Test
    @DisplayName("existsByProjectIdAndPath - returns true when path exists")
    void existsByProjectIdAndPath_existing_returnsTrue() {
        fileRepository.save(buildFile(1L, "App.java", "src/App.java",
                FileType.FILE, "java", "code", false));

        assertThat(fileRepository.existsByProjectIdAndPath(1L, "src/App.java"))
                .isTrue();
    }

    @Test
    @DisplayName("existsByProjectIdAndPath - returns false when path does not exist")
    void existsByProjectIdAndPath_nonExisting_returnsFalse() {
        assertThat(fileRepository.existsByProjectIdAndPath(1L, "ghost/file.java"))
                .isFalse();
    }

    // ── findByProjectIdAndPathStartingWith ────────────────────────────

    @Test
    @DisplayName("findByProjectIdAndPathStartingWith - returns children of folder")
    void findByPathStartingWith_folder_returnsChildren() {
        fileRepository.save(buildFile(1L, "src", "src",
                FileType.FOLDER, null, null, false));
        fileRepository.save(buildFile(1L, "App.java", "src/App.java",
                FileType.FILE, "java", "code", false));
        fileRepository.save(buildFile(1L, "Utils.java", "src/Utils.java",
                FileType.FILE, "java", "code", false));
        fileRepository.save(buildFile(1L, "Root.java", "Root.java",
                FileType.FILE, "java", "code", false));

        List<CodeFile> children = fileRepository
                .findByProjectIdAndPathStartingWith(1L, "src/");

        assertThat(children).hasSize(2).allMatch(f -> f.getPath().startsWith("src/"));

    }

    @Test
    @DisplayName("findByProjectIdAndPathStartingWith - returns empty when no children")
    void findByPathStartingWith_noChildren_returnsEmpty() {
        List<CodeFile> children = fileRepository
                .findByProjectIdAndPathStartingWith(1L, "empty/");

        assertThat(children).isEmpty();
    }
}