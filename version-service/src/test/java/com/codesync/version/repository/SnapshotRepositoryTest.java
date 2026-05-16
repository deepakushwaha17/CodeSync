package com.codesync.version.repository;

import com.codesync.version.entity.Snapshot;
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
class SnapshotRepositoryTest {

    @Autowired
    private SnapshotRepository snapshotRepository;

    @BeforeEach
    void setUp() { snapshotRepository.deleteAll(); }

    // ── Helper ────────────────────────────────────────────────────────

    private Snapshot build(Long projectId, Long fileId, Long authorId,
                           String message, String content,
                           String hash, String branch, String tag,
                           Long parentId) {
        return Snapshot.builder()
                .projectId(projectId).fileId(fileId).authorId(authorId)
                .message(message).content(content).hash(hash)
                .branch(branch).tag(tag).parentSnapshotId(parentId)
                .build();
    }

    // ── findByProjectIdOrderByCreatedAtDesc ───────────────────────────

    @Test
    @DisplayName("findByProjectIdOrderByCreatedAtDesc - returns all snapshots for project")
    void findByProjectId_existing_returnsAll() {
        snapshotRepository.save(build(1L, 10L, 1L, "msg1", "c1", "h1", "main", null, null));
        snapshotRepository.save(build(1L, 11L, 1L, "msg2", "c2", "h2", "main", null, null));
        snapshotRepository.save(build(2L, 12L, 1L, "msg3", "c3", "h3", "main", null, null));

        List<Snapshot> result =
                snapshotRepository.findByProjectIdOrderByCreatedAtDesc(1L);

        assertThat(result).hasSize(2).allMatch(s -> s.getProjectId().equals(1L));
    }

    @Test
    @DisplayName("findByProjectIdOrderByCreatedAtDesc - returns empty for unknown project")
    void findByProjectId_unknown_returnsEmpty() {
        assertThat(snapshotRepository.findByProjectIdOrderByCreatedAtDesc(99L)).isEmpty();
    }

    // ── findByFileIdOrderByCreatedAtDesc ──────────────────────────────

    @Test
    @DisplayName("findByFileIdOrderByCreatedAtDesc - returns snapshots for file")
    void findByFileId_existing_returnsSnapshots() {
        snapshotRepository.save(build(1L, 10L, 1L, "A", "c1", "h1", "main", null, null));
        snapshotRepository.save(build(1L, 10L, 1L, "B", "c2", "h2", "main", null, null));
        snapshotRepository.save(build(1L, 20L, 1L, "C", "c3", "h3", "main", null, null));

        List<Snapshot> result =
                snapshotRepository.findByFileIdOrderByCreatedAtDesc(10L);

        assertThat(result).hasSize(2).allMatch(s -> s.getFileId().equals(10L));
    }

    // ── findByAuthorIdOrderByCreatedAtDesc ────────────────────────────

    @Test
    @DisplayName("findByAuthorIdOrderByCreatedAtDesc - returns snapshots by author")
    void findByAuthorId_existing_returnsSnapshots() {
        snapshotRepository.save(build(1L, 10L, 5L, "A", "c1", "h1", "main", null, null));
        snapshotRepository.save(build(1L, 11L, 5L, "B", "c2", "h2", "main", null, null));
        snapshotRepository.save(build(1L, 12L, 9L, "C", "c3", "h3", "main", null, null));

        List<Snapshot> result =
                snapshotRepository.findByAuthorIdOrderByCreatedAtDesc(5L);

        assertThat(result).hasSize(2).allMatch(s -> s.getAuthorId().equals(5L));
    }

    // ── findByBranchOrderByCreatedAtDesc ──────────────────────────────

    @Test
    @DisplayName("findByBranchOrderByCreatedAtDesc - returns snapshots on branch")
    void findByBranch_existing_returnsSnapshots() {
        snapshotRepository.save(build(1L, 10L, 1L, "A", "c1", "h1", "feature", null, null));
        snapshotRepository.save(build(1L, 11L, 1L, "B", "c2", "h2", "main",    null, null));

        List<Snapshot> result =
                snapshotRepository.findByBranchOrderByCreatedAtDesc("feature");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBranch()).isEqualTo("feature");
    }

    // ── findByFileIdAndBranchOrderByCreatedAtDesc ─────────────────────

    @Test
    @DisplayName("findByFileIdAndBranchOrderByCreatedAtDesc - filters by file AND branch")
    void findByFileIdAndBranch_existing_returnsFiltered() {
        snapshotRepository.save(build(1L, 10L, 1L, "main1",    "c1", "h1", "main",    null, null));
        snapshotRepository.save(build(1L, 10L, 1L, "feature1", "c2", "h2", "feature", null, null));
        snapshotRepository.save(build(1L, 20L, 1L, "other",    "c3", "h3", "feature", null, null));

        List<Snapshot> result =
                snapshotRepository.findByFileIdAndBranchOrderByCreatedAtDesc(10L, "feature");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMessage()).isEqualTo("feature1");
    }

    // ── findByHash ────────────────────────────────────────────────────

    @Test
    @DisplayName("findByHash - returns snapshot with matching hash")
    void findByHash_existing_returnsSnapshot() {
        snapshotRepository.save(build(1L, 10L, 1L, "msg", "content", "abc123hash", "main", null, null));

        Optional<Snapshot> result = snapshotRepository.findByHash("abc123hash");

        assertThat(result).isPresent();
        assertThat(result.get().getHash()).isEqualTo("abc123hash");
    }

    @Test
    @DisplayName("findByHash - returns empty for unknown hash")
    void findByHash_notFound_returnsEmpty() {
        assertThat(snapshotRepository.findByHash("nonexistenthash")).isEmpty();
    }

    // ── findByTag ─────────────────────────────────────────────────────

    @Test
    @DisplayName("findByTag - returns snapshot with matching tag")
    void findByTag_existing_returnsSnapshot() {
        snapshotRepository.save(build(1L, 10L, 1L, "release", "c", "h1", "main", "v1.0.0", null));

        Optional<Snapshot> result = snapshotRepository.findByTag("v1.0.0");

        assertThat(result).isPresent();
        assertThat(result.get().getTag()).isEqualTo("v1.0.0");
    }

    @Test
    @DisplayName("findByTag - returns empty for unknown tag")
    void findByTag_notFound_returnsEmpty() {
        assertThat(snapshotRepository.findByTag("v99.9.9")).isEmpty();
    }

    // ── findLatestByFileIdAndBranch ───────────────────────────────────

    @Test
    @DisplayName("findLatestByFileIdAndBranch - returns most recent snapshot on branch")
    void findLatestByFileIdAndBranch_existing_returnsLatest() {
        snapshotRepository.save(build(1L, 10L, 1L, "first",  "c1", "h1", "main", null, null));
        snapshotRepository.save(build(1L, 10L, 1L, "second", "c2", "h2", "main", null, null));

        Optional<Snapshot> result =
                snapshotRepository.findLatestByFileIdAndBranch(10L, "main");

        assertThat(result).isPresent();
        // Most recent = "second"
        assertThat(result.get().getMessage()).isEqualTo("second");
    }

    @Test
    @DisplayName("findLatestByFileIdAndBranch - returns empty when no snapshots on branch")
    void findLatestByFileIdAndBranch_noMatch_returnsEmpty() {
        Optional<Snapshot> result =
                snapshotRepository.findLatestByFileIdAndBranch(99L, "main");

        assertThat(result).isEmpty();
    }

    // ── findLatestByFileId ────────────────────────────────────────────

    @Test
    @DisplayName("findLatestByFileId - returns most recent snapshot across all branches")
    void findLatestByFileId_existing_returnsLatest() {
        snapshotRepository.save(build(1L, 10L, 1L, "old",    "c1", "h1", "main",    null, null));
        snapshotRepository.save(build(1L, 10L, 1L, "latest", "c2", "h2", "feature", null, null));

        Optional<Snapshot> result =
                snapshotRepository.findLatestByFileId(10L);

        assertThat(result).isPresent();
        assertThat(result.get().getMessage()).isEqualTo("latest");
    }

    @Test
    @DisplayName("findLatestByFileId - returns empty when no snapshots exist")
    void findLatestByFileId_noSnapshots_returnsEmpty() {
        assertThat(snapshotRepository.findLatestByFileId(999L)).isEmpty();
    }

    // ── findDistinctBranchesByProjectId ───────────────────────────────

    @Test
    @DisplayName("findDistinctBranchesByProjectId - returns distinct branch names")
    void findDistinctBranches_multipleBranches_returnsDistinct() {
        snapshotRepository.save(build(1L, 10L, 1L, "A", "c1", "h1", "main",    null, null));
        snapshotRepository.save(build(1L, 11L, 1L, "B", "c2", "h2", "main",    null, null));
        snapshotRepository.save(build(1L, 12L, 1L, "C", "c3", "h3", "feature", null, null));
        snapshotRepository.save(build(1L, 13L, 1L, "D", "c4", "h4", "hotfix",  null, null));

        List<String> branches =
                snapshotRepository.findDistinctBranchesByProjectId(1L);

        assertThat(branches).hasSize(3).containsExactlyInAnyOrder("main", "feature", "hotfix");
    }

    @Test
    @DisplayName("findDistinctBranchesByProjectId - returns empty for unknown project")
    void findDistinctBranches_unknown_returnsEmpty() {
        assertThat(snapshotRepository.findDistinctBranchesByProjectId(99L)).isEmpty();
    }

    // ── existsByProjectIdAndBranch ────────────────────────────────────

    @Test
    @DisplayName("existsByProjectIdAndBranch - returns true when branch exists")
    void existsByProjectIdAndBranch_existing_returnsTrue() {
        snapshotRepository.save(build(1L, 10L, 1L, "msg", "c", "h", "feature", null, null));

        assertThat(snapshotRepository.existsByProjectIdAndBranch(1L, "feature")).isTrue();
    }

    @Test
    @DisplayName("existsByProjectIdAndBranch - returns false when branch does not exist")
    void existsByProjectIdAndBranch_nonExisting_returnsFalse() {
        assertThat(snapshotRepository.existsByProjectIdAndBranch(1L, "ghost-branch")).isFalse();
    }

    // ── findFileHistory ───────────────────────────────────────────────

    @Test
    @DisplayName("findFileHistory - returns all snapshots for file ordered by createdAt ASC")
    void findFileHistory_existing_returnsAscOrdered() {
        Snapshot s1 = snapshotRepository.save(
                build(1L, 10L, 1L, "first",  "c1", "h1", "main", null, null));
        Snapshot s2 = snapshotRepository.save(
                build(1L, 10L, 1L, "second", "c2", "h2", "main", null, s1.getSnapshotId()));
        Snapshot s3 = snapshotRepository.save(
                build(1L, 10L, 1L, "third",  "c3", "h3", "main", null, s2.getSnapshotId()));

        List<Snapshot> history = snapshotRepository.findFileHistory(10L);

        assertThat(history).hasSize(3);
        // Oldest first
        assertThat(history.get(0).getMessage()).isEqualTo("first");
        assertThat(history.get(2).getMessage()).isEqualTo("third");
    }

    @Test
    @DisplayName("findFileHistory - returns empty for file with no snapshots")
    void findFileHistory_noSnapshots_returnsEmpty() {
        assertThat(snapshotRepository.findFileHistory(999L)).isEmpty();
    }
}