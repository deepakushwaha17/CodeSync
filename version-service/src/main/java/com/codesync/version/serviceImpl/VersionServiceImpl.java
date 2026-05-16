package com.codesync.version.serviceImpl;

import com.codesync.version.client.NotificationClient;
import com.codesync.version.client.NotificationRequest;
import com.codesync.version.dto.request.CreateBranchRequest;
import com.codesync.version.dto.request.CreateSnapshotRequest;
import com.codesync.version.dto.request.TagSnapshotRequest;
import com.codesync.version.dto.response.DiffResponse;
import com.codesync.version.dto.response.SnapshotResponse;
import com.codesync.version.entity.Snapshot;
import com.codesync.version.exception.SnapshotNotFoundException;
import com.codesync.version.repository.SnapshotRepository;
import com.codesync.version.service.VersionService;
import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HexFormat;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VersionServiceImpl implements VersionService {

    private final SnapshotRepository snapshotRepository;
    private final NotificationClient notificationClient;

    // ─────────────────────────────────────────────────────────────────
    // SNAPSHOT CRUD
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public SnapshotResponse createSnapshot(
            Long authorId, CreateSnapshotRequest request) {

        log.info("Creating snapshot for file: {} by author: {}",
                request.getFileId(), authorId);

        String branch = (request.getBranch() != null
                && !request.getBranch().isBlank())
                ? request.getBranch() : "main";

        // Generate SHA-256 hash of content
        String hash = generateSHA256(request.getContent());

        // Get parent snapshot if not provided
        Long parentId = request.getParentSnapshotId();
        if (parentId == null) {
            parentId = snapshotRepository
                    .findLatestByFileIdAndBranch(
                            request.getFileId(), branch)
                    .map(Snapshot::getSnapshotId)
                    .orElse(null);
        }

        Snapshot snapshot = Snapshot.builder()
                .projectId(request.getProjectId())
                .fileId(request.getFileId())
                .authorId(authorId)
                .message(request.getMessage())
                .content(request.getContent())
                .hash(hash)
                .parentSnapshotId(parentId)
                .branch(branch)
                .build();

        Snapshot saved = snapshotRepository.save(snapshot);

        try {
            notificationClient.sendNotification(
                    NotificationRequest.builder()
                            .recipientId(authorId)
                            .actorId(authorId)
                            .type("SNAPSHOT_CREATED")
                            .title("Snapshot created")
                            .message("Snapshot '" + request.getMessage()
                                    + "' created on branch: " + branch)
                            .relatedId(saved.getSnapshotId())
                            .relatedType("SNAPSHOT")
                            .build());
        } catch (Exception e) {
            log.warn("Could not send snapshot notification: {}",
                    e.getMessage());
        }

        log.info("Snapshot created with ID: {}, hash: {}",
                saved.getSnapshotId(), hash);

        return mapToResponse(saved);
    }

    @Override
    public SnapshotResponse getSnapshotById(Long snapshotId) {
        return mapToResponse(findSnapshotOrThrow(snapshotId));
    }

    // ─────────────────────────────────────────────────────────────────
    // QUERIES
    // ─────────────────────────────────────────────────────────────────

    @Override
    public List<SnapshotResponse> getSnapshotsByFile(Long fileId) {
        return snapshotRepository
                .findByFileIdOrderByCreatedAtDesc(fileId)
                .stream().map(this::mapToResponse)
                .toList();
    }

    @Override
    public List<SnapshotResponse> getSnapshotsByProject(
            Long projectId) {
        return snapshotRepository
                .findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream().map(this::mapToResponse)
                .toList();
    }

    @Override
    public List<SnapshotResponse> getSnapshotsByBranch(
            Long fileId, String branch) {
        return snapshotRepository
                .findByFileIdAndBranchOrderByCreatedAtDesc(
                        fileId, branch)
                .stream().map(this::mapToResponse)
                .toList();
    }

    @Override
    public SnapshotResponse getLatestSnapshot(Long fileId) {
        Snapshot snapshot = snapshotRepository
                .findLatestByFileId(fileId)
                .orElseThrow(() -> new SnapshotNotFoundException(
                        "No snapshots found for file ID: " + fileId));
        return mapToResponse(snapshot);
    }

    @Override
    public List<SnapshotResponse> getFileHistory(Long fileId) {
        return snapshotRepository.findFileHistory(fileId)
                .stream().map(this::mapToResponse)
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────
    // OPERATIONS
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public SnapshotResponse restoreSnapshot(
            Long snapshotId, Long userId) {

        Snapshot original = findSnapshotOrThrow(snapshotId);

        log.info("Restoring snapshot ID: {} for file: {}",
                snapshotId, original.getFileId());

        // Restore creates a NEW snapshot with old content
        // rather than modifying history
        String hash = generateSHA256(original.getContent());

        // Get current latest as parent
        Long parentId = snapshotRepository
                .findLatestByFileId(original.getFileId())
                .map(Snapshot::getSnapshotId)
                .orElse(snapshotId);

        Snapshot restored = Snapshot.builder()
                .projectId(original.getProjectId())
                .fileId(original.getFileId())
                .authorId(userId)
                .message("Restored from snapshot #"
                        + snapshotId
                        + ": " + original.getMessage())
                .content(original.getContent())
                .hash(hash)
                .parentSnapshotId(parentId)
                .branch(original.getBranch())
                .build();

        Snapshot saved = snapshotRepository.save(restored);
        log.info("Restore snapshot created with ID: {}",
                saved.getSnapshotId());

        return mapToResponse(saved);
    }

    @Override
    public DiffResponse diffSnapshots(
            Long snapshotIdA, Long snapshotIdB) {

        Snapshot snapA = findSnapshotOrThrow(snapshotIdA);
        Snapshot snapB = findSnapshotOrThrow(snapshotIdB);

        log.info("Computing diff between snapshots {} and {}",
                snapshotIdA, snapshotIdB);

        // Split content into lines
        List<String> linesA = splitLines(snapA.getContent());
        List<String> linesB = splitLines(snapB.getContent());

        // Compute diff using Myers algorithm
        Patch<String> patch = DiffUtils.diff(linesA, linesB);

        List<DiffResponse.DiffLine> diffLines = new ArrayList<>();
        int addedCount = 0;
        int removedCount = 0;
        int unchangedCount = 0;

        // Build unified diff output
        int lineNumA = 1;
        int lineNumB = 1;


        for (AbstractDelta<String> delta : patch.getDeltas()) {
            int posA = delta.getSource().getPosition();
            //int posB = delta.getTarget().getPosition();

            // Add unchanged lines before this delta
            while (lineNumA <= posA) {
                diffLines.add(DiffResponse.DiffLine.builder()
                        .type("UNCHANGED")
                        .lineNumber(lineNumA)
                        .content(linesA.get(lineNumA - 1))
                        .build());
                lineNumA++;
                lineNumB++;
                unchangedCount++;
            }

            // Add removed lines (from snapshot A)
            if (delta.getType() == DeltaType.DELETE || delta.getType() == DeltaType.CHANGE) {
                for (String line : delta.getSource().getLines()) {
                    diffLines.add(DiffResponse.DiffLine.builder()
                            .type("REMOVED")
                            .lineNumber(lineNumA++)
                            .content(line)
                            .build());
                    removedCount++;
                }
            }

            // Add added lines (from snapshot B)
            if (delta.getType() == DeltaType.INSERT || delta.getType() == DeltaType.CHANGE) {
                for (String line : delta.getTarget().getLines()) {
                    diffLines.add(DiffResponse.DiffLine.builder()
                            .type("ADDED")
                            .lineNumber(lineNumB++)
                            .content(line)
                            .build());
                    addedCount++;
                }
            }
        }

        // Add remaining unchanged lines
        while (lineNumA <= linesA.size()) {
            diffLines.add(DiffResponse.DiffLine.builder()
                    .type("UNCHANGED")
                    .lineNumber(lineNumA)
                    .content(linesA.get(lineNumA - 1))
                    .build());
            lineNumA++;
            unchangedCount++;
        }

        return DiffResponse.builder()
                .snapshotIdA(snapshotIdA)
                .snapshotIdB(snapshotIdB)
                .branchA(snapA.getBranch())
                .branchB(snapB.getBranch())
                .lines(diffLines)
                .addedLines(addedCount)
                .removedLines(removedCount)
                .unchangedLines(unchangedCount)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────
    // BRANCH AND TAG
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public SnapshotResponse createBranch(
            Long userId, CreateBranchRequest request) {

        log.info("Creating branch '{}' for project: {}",
                request.getBranchName(), request.getProjectId());

        // Check branch does not already exist
        if (snapshotRepository.existsByProjectIdAndBranch(
                request.getProjectId(), request.getBranchName())) {
            throw new IllegalArgumentException(
                    "Branch '" + request.getBranchName()
                            + "' already exists.");
        }

        // Get the source snapshot
        Snapshot source = findSnapshotOrThrow(
                request.getFromSnapshotId());

        // Create first snapshot on new branch
        // copying content from source snapshot
        String hash = generateSHA256(source.getContent());

        Snapshot branchSnapshot = Snapshot.builder()
                .projectId(request.getProjectId())
                .fileId(request.getFileId())
                .authorId(userId)
                .message("Branch '" + request.getBranchName()
                        + "' created from snapshot #"
                        + request.getFromSnapshotId())
                .content(source.getContent())
                .hash(hash)
                .parentSnapshotId(request.getFromSnapshotId())
                .branch(request.getBranchName())
                .build();

        Snapshot saved = snapshotRepository.save(branchSnapshot);
        log.info("Branch '{}' created with snapshot ID: {}",
                request.getBranchName(), saved.getSnapshotId());

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public SnapshotResponse tagSnapshot(
            Long snapshotId, TagSnapshotRequest request) {

        Snapshot snapshot = findSnapshotOrThrow(snapshotId);

        // Check tag is not already used
        snapshotRepository.findByTag(request.getTag())
                .ifPresent(s -> {
                    throw new IllegalArgumentException(
                            "Tag '" + request.getTag()
                                    + "' is already used on snapshot #"
                                    + s.getSnapshotId());
                });

        snapshot.setTag(request.getTag());
        Snapshot updated = snapshotRepository.save(snapshot);

        log.info("Tagged snapshot {} with '{}'",
                snapshotId, request.getTag());

        return mapToResponse(updated);
    }

    @Override
    public List<String> getBranchesByProject(Long projectId) {
        return snapshotRepository
                .findDistinctBranchesByProjectId(projectId);
    }

    // ─────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────

    private Snapshot findSnapshotOrThrow(Long snapshotId) {
        return snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new SnapshotNotFoundException(
                        "Snapshot not found with ID: " + snapshotId));
    }

    private List<String> splitLines(String content) {
        if (content == null || content.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.asList(content.split("\n", -1));
    }

    /**
     * Generate SHA-256 hash of content for integrity verification.
     */
    private String generateSHA256(String content) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(
                    content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(
                    "SHA-256 algorithm not available", e);
        }
    }

    private SnapshotResponse mapToResponse(Snapshot s) {
        return SnapshotResponse.builder()
                .snapshotId(s.getSnapshotId())
                .projectId(s.getProjectId())
                .fileId(s.getFileId())
                .authorId(s.getAuthorId())
                .message(s.getMessage())
                .content(s.getContent())
                .hash(s.getHash())
                .parentSnapshotId(s.getParentSnapshotId())
                .branch(s.getBranch())
                .tag(s.getTag())
                .createdAt(s.getCreatedAt())
                .build();
    }
}