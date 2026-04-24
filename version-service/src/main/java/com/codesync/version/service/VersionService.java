package com.codesync.version.service;

import com.codesync.version.dto.request.CreateBranchRequest;
import com.codesync.version.dto.request.CreateSnapshotRequest;
import com.codesync.version.dto.request.TagSnapshotRequest;
import com.codesync.version.dto.response.DiffResponse;
import com.codesync.version.dto.response.SnapshotResponse;

import java.util.List;

public interface VersionService {

    // Snapshot CRUD
    SnapshotResponse createSnapshot(Long authorId,
                                    CreateSnapshotRequest request);
    SnapshotResponse getSnapshotById(Long snapshotId);

    // Queries
    List<SnapshotResponse> getSnapshotsByFile(Long fileId);
    List<SnapshotResponse> getSnapshotsByProject(Long projectId);
    List<SnapshotResponse> getSnapshotsByBranch(
            Long fileId, String branch);
    SnapshotResponse getLatestSnapshot(Long fileId);
    List<SnapshotResponse> getFileHistory(Long fileId);

    // Operations
    SnapshotResponse restoreSnapshot(Long snapshotId, Long userId);
    DiffResponse diffSnapshots(Long snapshotIdA, Long snapshotIdB);

    // Branch and Tag
    SnapshotResponse createBranch(Long userId,
                                  CreateBranchRequest request);
    SnapshotResponse tagSnapshot(Long snapshotId,
                                 TagSnapshotRequest request);
    List<String> getBranchesByProject(Long projectId);
}