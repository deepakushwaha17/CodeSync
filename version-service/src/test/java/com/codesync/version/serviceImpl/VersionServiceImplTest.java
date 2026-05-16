package com.codesync.version.serviceImpl;

import com.codesync.version.client.NotificationClient;
import com.codesync.version.dto.request.CreateBranchRequest;
import com.codesync.version.dto.request.CreateSnapshotRequest;
import com.codesync.version.dto.request.TagSnapshotRequest;
import com.codesync.version.dto.response.DiffResponse;
import com.codesync.version.dto.response.SnapshotResponse;
import com.codesync.version.entity.Snapshot;
import com.codesync.version.exception.SnapshotNotFoundException;
import com.codesync.version.repository.SnapshotRepository;
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
class VersionServiceImplTest {

    @Mock private SnapshotRepository snapshotRepository;
    @Mock private NotificationClient notificationClient;

    @InjectMocks private VersionServiceImpl versionService;

    // ── Fixture ───────────────────────────────────────────────────────

    private Snapshot buildSnapshot(Long id, Long projectId, Long fileId,
                                   String message, String content,
                                   String branch, String tag, Long parentId) {
        return Snapshot.builder()
                .snapshotId(id)
                .projectId(projectId)
                .fileId(fileId)
                .authorId(1L)
                .message(message)
                .content(content)
                .hash("sha256hash")
                .branch(branch)
                .tag(tag)
                .parentSnapshotId(parentId)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ════════════════════════════════════════════════════════════════
    // createSnapshot()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("createSnapshot - saves snapshot and returns SnapshotResponse")
    void createSnapshot_valid_savesAndReturns() {
        CreateSnapshotRequest req = new CreateSnapshotRequest();
        req.setProjectId(1L);
        req.setFileId(10L);
        req.setMessage("Initial commit");
        req.setContent("class App {}");
        req.setBranch("main");

        when(snapshotRepository.findLatestByFileIdAndBranch(10L, "main"))
                .thenReturn(Optional.empty());

        Snapshot saved = buildSnapshot(1L, 1L, 10L, "Initial commit",
                "class App {}", "main", null, null);
        when(snapshotRepository.save(any())).thenReturn(saved);
        doNothing().when(notificationClient).sendNotification(any());

        SnapshotResponse response = versionService.createSnapshot(1L, req);

        assertThat(response.getSnapshotId()).isEqualTo(1L);
        assertThat(response.getMessage()).isEqualTo("Initial commit");
        assertThat(response.getBranch()).isEqualTo("main");
        assertThat(response.getHash()).isNotBlank();
        verify(snapshotRepository).save(any(Snapshot.class));
    }

    @Test
    @DisplayName("createSnapshot - defaults branch to 'main' when blank")
    void createSnapshot_blankBranch_defaultsToMain() {
        CreateSnapshotRequest req = new CreateSnapshotRequest();
        req.setProjectId(1L);
        req.setFileId(10L);
        req.setMessage("msg");
        req.setContent("content");
        req.setBranch(""); // blank

        when(snapshotRepository.findLatestByFileIdAndBranch(10L, "main"))
                .thenReturn(Optional.empty());
        Snapshot saved = buildSnapshot(1L, 1L, 10L, "msg", "content", "main", null, null);
        when(snapshotRepository.save(any())).thenReturn(saved);
        doNothing().when(notificationClient).sendNotification(any());

        SnapshotResponse response = versionService.createSnapshot(1L, req);

        assertThat(response.getBranch()).isEqualTo("main");
    }

    @Test
    @DisplayName("createSnapshot - defaults branch to 'main' when null")
    void createSnapshot_nullBranch_defaultsToMain() {
        CreateSnapshotRequest req = new CreateSnapshotRequest();
        req.setProjectId(1L);
        req.setFileId(10L);
        req.setMessage("msg");
        req.setContent("content");
        req.setBranch(null);

        when(snapshotRepository.findLatestByFileIdAndBranch(10L, "main"))
                .thenReturn(Optional.empty());
        Snapshot saved = buildSnapshot(1L, 1L, 10L, "msg", "content", "main", null, null);
        when(snapshotRepository.save(any())).thenReturn(saved);
        doNothing().when(notificationClient).sendNotification(any());

        versionService.createSnapshot(1L, req);

        verify(snapshotRepository).findLatestByFileIdAndBranch(10L, "main");
    }

    @Test
    @DisplayName("createSnapshot - auto-resolves parentSnapshotId from latest when not provided")
    void createSnapshot_noParentId_autoResolvesFromLatest() {
        CreateSnapshotRequest req = new CreateSnapshotRequest();
        req.setProjectId(1L);
        req.setFileId(10L);
        req.setMessage("second commit");
        req.setContent("updated");
        req.setBranch("main");
        req.setParentSnapshotId(null);

        Snapshot existing = buildSnapshot(5L, 1L, 10L, "first",
                "original", "main", null, null);
        when(snapshotRepository.findLatestByFileIdAndBranch(10L, "main"))
                .thenReturn(Optional.of(existing));

        Snapshot saved = buildSnapshot(6L, 1L, 10L, "second commit",
                "updated", "main", null, 5L);
        when(snapshotRepository.save(any())).thenReturn(saved);
        doNothing().when(notificationClient).sendNotification(any());

        SnapshotResponse response = versionService.createSnapshot(1L, req);

        assertThat(response.getParentSnapshotId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("createSnapshot - generates consistent SHA-256 hash for same content")
    void createSnapshot_sameContent_sameHash() {
        String content = "class App {}";

        CreateSnapshotRequest req1 = new CreateSnapshotRequest();
        req1.setProjectId(1L); req1.setFileId(10L);
        req1.setMessage("A"); req1.setContent(content); req1.setBranch("main");

        CreateSnapshotRequest req2 = new CreateSnapshotRequest();
        req2.setProjectId(1L); req2.setFileId(11L);
        req2.setMessage("B"); req2.setContent(content); req2.setBranch("main");

        when(snapshotRepository.findLatestByFileIdAndBranch(anyLong(), eq("main")))
                .thenReturn(Optional.empty());

        ArgumentCaptor<Snapshot> captor = ArgumentCaptor.forClass(Snapshot.class);
        when(snapshotRepository.save(captor.capture()))
                .thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(notificationClient).sendNotification(any());

        versionService.createSnapshot(1L, req1);
        String hash1 = captor.getValue().getHash();

        versionService.createSnapshot(1L, req2);
        String hash2 = captor.getValue().getHash();

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("createSnapshot - notification failure does not throw (graceful)")
    void createSnapshot_notificationFails_doesNotPropagate() {
        CreateSnapshotRequest req = new CreateSnapshotRequest();
        req.setProjectId(1L); req.setFileId(10L);
        req.setMessage("msg"); req.setContent("c"); req.setBranch("main");

        when(snapshotRepository.findLatestByFileIdAndBranch(10L, "main"))
                .thenReturn(Optional.empty());
        Snapshot saved = buildSnapshot(1L, 1L, 10L, "msg", "c", "main", null, null);
        when(snapshotRepository.save(any())).thenReturn(saved);
        doThrow(new RuntimeException("notification down"))
                .when(notificationClient).sendNotification(any());

        assertThatCode(() -> versionService.createSnapshot(1L, req))
                .doesNotThrowAnyException();
    }

    // ════════════════════════════════════════════════════════════════
    // getSnapshotById()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getSnapshotById - returns SnapshotResponse for existing ID")
    void getSnapshotById_existing_returnsResponse() {
        Snapshot snap = buildSnapshot(1L, 1L, 10L, "msg", "c", "main", null, null);
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(snap));

        SnapshotResponse response = versionService.getSnapshotById(1L);

        assertThat(response.getSnapshotId()).isEqualTo(1L);
        assertThat(response.getMessage()).isEqualTo("msg");
    }

    @Test
    @DisplayName("getSnapshotById - throws SnapshotNotFoundException for unknown ID")
    void getSnapshotById_notFound_throwsException() {
        when(snapshotRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> versionService.getSnapshotById(99L))
                .isInstanceOf(SnapshotNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ════════════════════════════════════════════════════════════════
    // getSnapshotsByFile()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getSnapshotsByFile - returns list of snapshots for file")
    void getSnapshotsByFile_existing_returnsList() {
        when(snapshotRepository.findByFileIdOrderByCreatedAtDesc(10L))
                .thenReturn(List.of(
                        buildSnapshot(1L, 1L, 10L, "A", "c1", "main", null, null),
                        buildSnapshot(2L, 1L, 10L, "B", "c2", "main", null, null)
                ));

        List<SnapshotResponse> result = versionService.getSnapshotsByFile(10L);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("getSnapshotsByFile - returns empty list when no snapshots")
    void getSnapshotsByFile_noSnapshots_returnsEmpty() {
        when(snapshotRepository.findByFileIdOrderByCreatedAtDesc(10L))
                .thenReturn(List.of());

        assertThat(versionService.getSnapshotsByFile(10L)).isEmpty();
    }

    // ════════════════════════════════════════════════════════════════
    // getSnapshotsByProject()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getSnapshotsByProject - returns all snapshots for project")
    void getSnapshotsByProject_existing_returnsList() {
        when(snapshotRepository.findByProjectIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(
                        buildSnapshot(1L, 1L, 10L, "A", "c1", "main", null, null),
                        buildSnapshot(2L, 1L, 11L, "B", "c2", "main", null, null)
                ));

        List<SnapshotResponse> result = versionService.getSnapshotsByProject(1L);

        assertThat(result).hasSize(2);
    }

    // ════════════════════════════════════════════════════════════════
    // getSnapshotsByBranch()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getSnapshotsByBranch - returns snapshots on specific branch")
    void getSnapshotsByBranch_existing_returnsList() {
        when(snapshotRepository.findByFileIdAndBranchOrderByCreatedAtDesc(10L, "feature"))
                .thenReturn(List.of(
                        buildSnapshot(1L, 1L, 10L, "feat", "c", "feature", null, null)
                ));

        List<SnapshotResponse> result = versionService.getSnapshotsByBranch(10L, "feature");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBranch()).isEqualTo("feature");
    }

    @Test
    @DisplayName("getSnapshotsByBranch - returns empty when no snapshots on branch")
    void getSnapshotsByBranch_noSnapshots_returnsEmpty() {
        when(snapshotRepository.findByFileIdAndBranchOrderByCreatedAtDesc(10L, "ghost"))
                .thenReturn(List.of());

        assertThat(versionService.getSnapshotsByBranch(10L, "ghost")).isEmpty();
    }

    // ════════════════════════════════════════════════════════════════
    // getLatestSnapshot()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getLatestSnapshot - returns most recent snapshot for file")
    void getLatestSnapshot_existing_returnsLatest() {
        Snapshot latest = buildSnapshot(5L, 1L, 10L, "latest", "c", "main", null, null);
        when(snapshotRepository.findLatestByFileId(10L)).thenReturn(Optional.of(latest));

        SnapshotResponse response = versionService.getLatestSnapshot(10L);

        assertThat(response.getSnapshotId()).isEqualTo(5L);
        assertThat(response.getMessage()).isEqualTo("latest");
    }

    @Test
    @DisplayName("getLatestSnapshot - throws SnapshotNotFoundException when no snapshots")
    void getLatestSnapshot_noSnapshots_throwsException() {
        when(snapshotRepository.findLatestByFileId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> versionService.getLatestSnapshot(99L))
                .isInstanceOf(SnapshotNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ════════════════════════════════════════════════════════════════
    // getFileHistory()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getFileHistory - returns ordered history for file")
    void getFileHistory_existing_returnsHistory() {
        when(snapshotRepository.findFileHistory(10L))
                .thenReturn(List.of(
                        buildSnapshot(1L, 1L, 10L, "first",  "c1", "main", null, null),
                        buildSnapshot(2L, 1L, 10L, "second", "c2", "main", null, 1L),
                        buildSnapshot(3L, 1L, 10L, "third",  "c3", "main", null, 2L)
                ));

        List<SnapshotResponse> history = versionService.getFileHistory(10L);

        assertThat(history).hasSize(3);
        assertThat(history.get(0).getMessage()).isEqualTo("first");
        assertThat(history.get(2).getMessage()).isEqualTo("third");
    }

    // ════════════════════════════════════════════════════════════════
    // restoreSnapshot()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("restoreSnapshot - creates new snapshot with same content as original")
    void restoreSnapshot_existing_createsNewSnapshot() {
        Snapshot original = buildSnapshot(3L, 1L, 10L, "old msg",
                "old content", "main", null, null);
        Snapshot latest = buildSnapshot(7L, 1L, 10L, "latest",
                "newer content", "main", null, null);

        when(snapshotRepository.findById(3L)).thenReturn(Optional.of(original));
        when(snapshotRepository.findLatestByFileId(10L)).thenReturn(Optional.of(latest));

        Snapshot restored = buildSnapshot(8L, 1L, 10L,
                "Restored from snapshot #3: old msg",
                "old content", "main", null, 7L);
        when(snapshotRepository.save(any())).thenReturn(restored);

        SnapshotResponse response = versionService.restoreSnapshot(3L, 99L);

        assertThat(response.getContent()).isEqualTo("old content");
        assertThat(response.getMessage()).contains("Restored from snapshot #3");
        assertThat(response.getParentSnapshotId()).isEqualTo(7L);
        verify(snapshotRepository).save(any(Snapshot.class));
    }

    @Test
    @DisplayName("restoreSnapshot - uses original snapshotId as parent if no latest found")
    void restoreSnapshot_noLatest_usesOriginalAsParent() {
        Snapshot original = buildSnapshot(3L, 1L, 10L, "only msg",
                "only content", "main", null, null);

        when(snapshotRepository.findById(3L)).thenReturn(Optional.of(original));
        when(snapshotRepository.findLatestByFileId(10L)).thenReturn(Optional.empty());

        ArgumentCaptor<Snapshot> captor = ArgumentCaptor.forClass(Snapshot.class);
        when(snapshotRepository.save(captor.capture()))
                .thenAnswer(inv -> inv.getArgument(0));

        versionService.restoreSnapshot(3L, 1L);

        assertThat(captor.getValue().getParentSnapshotId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("restoreSnapshot - throws SnapshotNotFoundException for unknown ID")
    void restoreSnapshot_notFound_throwsException() {
        when(snapshotRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> versionService.restoreSnapshot(99L, 1L))
                .isInstanceOf(SnapshotNotFoundException.class);
    }

    // ════════════════════════════════════════════════════════════════
    // diffSnapshots()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("diffSnapshots - returns ADDED, REMOVED, UNCHANGED lines correctly")
    void diffSnapshots_differentContent_returnsDiff() {
        Snapshot snapA = buildSnapshot(1L, 1L, 10L, "A",
                "line1\nline2\nline3", "main", null, null);
        Snapshot snapB = buildSnapshot(2L, 1L, 10L, "B",
                "line1\nline2modified\nline3\nline4", "main", null, null);

        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(snapA));
        when(snapshotRepository.findById(2L)).thenReturn(Optional.of(snapB));

        DiffResponse diff = versionService.diffSnapshots(1L, 2L);

        assertThat(diff.getSnapshotIdA()).isEqualTo(1L);
        assertThat(diff.getSnapshotIdB()).isEqualTo(2L);
        assertThat(diff.getLines()).isNotEmpty();
        assertThat(diff.getAddedLines()).isPositive();
        assertThat(diff.getRemovedLines()).isPositive();
        assertThat(diff.getUnchangedLines()).isNotNegative();

        // line1 and line3 should appear as UNCHANGED
        long unchanged = diff.getLines().stream()
                .filter(l -> l.getType().equals("UNCHANGED")).count();
        assertThat(unchanged).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("diffSnapshots - returns all UNCHANGED when content is identical")
    void diffSnapshots_sameContent_allUnchanged() {
        String content = "line1\nline2\nline3";
        Snapshot snapA = buildSnapshot(1L, 1L, 10L, "A", content, "main", null, null);
        Snapshot snapB = buildSnapshot(2L, 1L, 10L, "B", content, "main", null, null);

        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(snapA));
        when(snapshotRepository.findById(2L)).thenReturn(Optional.of(snapB));

        DiffResponse diff = versionService.diffSnapshots(1L, 2L);

        assertThat(diff.getAddedLines()).isZero();
        assertThat(diff.getRemovedLines()).isZero();
        assertThat(diff.getUnchangedLines()).isEqualTo(3);
    }

    @Test
    @DisplayName("diffSnapshots - handles empty content in both snapshots")
    void diffSnapshots_emptyContent_returnsEmptyDiff() {
        Snapshot snapA = buildSnapshot(1L, 1L, 10L, "A", "", "main", null, null);
        Snapshot snapB = buildSnapshot(2L, 1L, 10L, "B", "", "main", null, null);

        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(snapA));
        when(snapshotRepository.findById(2L)).thenReturn(Optional.of(snapB));

        DiffResponse diff = versionService.diffSnapshots(1L, 2L);

        assertThat(diff.getLines()).isEmpty();
        assertThat(diff.getAddedLines()).isZero();
        assertThat(diff.getRemovedLines()).isZero();
    }

    @Test
    @DisplayName("diffSnapshots - throws SnapshotNotFoundException for unknown snapshotIdA")
    void diffSnapshots_unknownA_throwsException() {
        when(snapshotRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> versionService.diffSnapshots(99L, 2L))
                .isInstanceOf(SnapshotNotFoundException.class);
    }

    @Test
    @DisplayName("diffSnapshots - throws SnapshotNotFoundException for unknown snapshotIdB")
    void diffSnapshots_unknownB_throwsException() {
        Snapshot snapA = buildSnapshot(1L, 1L, 10L, "A", "c", "main", null, null);
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(snapA));
        when(snapshotRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> versionService.diffSnapshots(1L, 99L))
                .isInstanceOf(SnapshotNotFoundException.class);
    }

    // ════════════════════════════════════════════════════════════════
    // createBranch()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("createBranch - creates first snapshot on new branch")
    void createBranch_valid_createsSnapshotOnBranch() {
        CreateBranchRequest req = new CreateBranchRequest();
        req.setProjectId(1L);
        req.setFileId(10L);
        req.setBranchName("feature-login");
        req.setFromSnapshotId(5L);

        when(snapshotRepository.existsByProjectIdAndBranch(1L, "feature-login"))
                .thenReturn(false);

        Snapshot source = buildSnapshot(5L, 1L, 10L, "main snapshot",
                "source content", "main", null, null);
        when(snapshotRepository.findById(5L)).thenReturn(Optional.of(source));

        Snapshot saved = buildSnapshot(6L, 1L, 10L,
                "Branch 'feature-login' created from snapshot #5",
                "source content", "feature-login", null, 5L);
        when(snapshotRepository.save(any())).thenReturn(saved);

        SnapshotResponse response = versionService.createBranch(1L, req);

        assertThat(response.getBranch()).isEqualTo("feature-login");
        assertThat(response.getContent()).isEqualTo("source content");
        assertThat(response.getParentSnapshotId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("createBranch - throws IllegalArgumentException when branch already exists")
    void createBranch_branchAlreadyExists_throwsException() {
        CreateBranchRequest req = new CreateBranchRequest();
        req.setProjectId(1L);
        req.setFileId(10L);
        req.setBranchName("main");
        req.setFromSnapshotId(1L);

        when(snapshotRepository.existsByProjectIdAndBranch(1L, "main"))
                .thenReturn(true);

        assertThatThrownBy(() -> versionService.createBranch(1L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'main' already exists");

        verify(snapshotRepository, never()).save(any());
    }

    @Test
    @DisplayName("createBranch - throws SnapshotNotFoundException for unknown source snapshot")
    void createBranch_unknownSourceSnapshot_throwsException() {
        CreateBranchRequest req = new CreateBranchRequest();
        req.setProjectId(1L);
        req.setFileId(10L);
        req.setBranchName("new-branch");
        req.setFromSnapshotId(999L);

        when(snapshotRepository.existsByProjectIdAndBranch(1L, "new-branch"))
                .thenReturn(false);
        when(snapshotRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> versionService.createBranch(1L, req))
                .isInstanceOf(SnapshotNotFoundException.class);
    }

    // ════════════════════════════════════════════════════════════════
    // tagSnapshot()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("tagSnapshot - adds tag to snapshot successfully")
    void tagSnapshot_uniqueTag_updatesSnapshot() {
        Snapshot snap = buildSnapshot(1L, 1L, 10L, "release", "c", "main", null, null);
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(snap));
        when(snapshotRepository.findByTag("v1.0.0")).thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TagSnapshotRequest req = new TagSnapshotRequest();
        req.setTag("v1.0.0");

        SnapshotResponse response = versionService.tagSnapshot(1L, req);

        assertThat(response.getTag()).isEqualTo("v1.0.0");
        verify(snapshotRepository).save(argThat(s -> "v1.0.0".equals(s.getTag())));
    }

    @Test
    @DisplayName("tagSnapshot - throws IllegalArgumentException when tag is already used")
    void tagSnapshot_duplicateTag_throwsException() {
        Snapshot snap = buildSnapshot(1L, 1L, 10L, "msg", "c", "main", null, null);
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(snap));

        Snapshot alreadyTagged = buildSnapshot(99L, 1L, 10L, "old", "c",
                "main", "v1.0.0", null);
        when(snapshotRepository.findByTag("v1.0.0")).thenReturn(Optional.of(alreadyTagged));

        TagSnapshotRequest req = new TagSnapshotRequest();
        req.setTag("v1.0.0");

        assertThatThrownBy(() -> versionService.tagSnapshot(1L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'v1.0.0' is already used");
    }

    @Test
    @DisplayName("tagSnapshot - throws SnapshotNotFoundException for unknown snapshot")
    void tagSnapshot_unknownSnapshot_throwsException() {
        when(snapshotRepository.findById(99L)).thenReturn(Optional.empty());

        TagSnapshotRequest req = new TagSnapshotRequest();
        req.setTag("v2.0.0");

        assertThatThrownBy(() -> versionService.tagSnapshot(99L, req))
                .isInstanceOf(SnapshotNotFoundException.class);
    }

    // ════════════════════════════════════════════════════════════════
    // getBranchesByProject()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getBranchesByProject - returns distinct branch names")
    void getBranchesByProject_existing_returnsBranches() {
        when(snapshotRepository.findDistinctBranchesByProjectId(1L))
                .thenReturn(List.of("main", "feature", "hotfix"));

        List<String> branches = versionService.getBranchesByProject(1L);

        assertThat(branches).containsExactlyInAnyOrder("main", "feature", "hotfix");
    }

    @Test
    @DisplayName("getBranchesByProject - returns empty list for unknown project")
    void getBranchesByProject_unknown_returnsEmpty() {
        when(snapshotRepository.findDistinctBranchesByProjectId(99L))
                .thenReturn(List.of());

        assertThat(versionService.getBranchesByProject(99L)).isEmpty();
    }
}