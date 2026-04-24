package com.codesync.version.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiffResponse {

    private Long snapshotIdA;
    private Long snapshotIdB;
    private String branchA;
    private String branchB;
    private List<DiffLine> lines;
    private int addedLines;
    private int removedLines;
    private int unchangedLines;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiffLine {
        // "ADDED", "REMOVED", "UNCHANGED"
        private String type;
        private int lineNumber;
        private String content;
    }
}