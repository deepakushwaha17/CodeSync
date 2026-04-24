package com.codesync.collab.controller;

import com.codesync.collab.dto.request.CreateSessionRequest;
import com.codesync.collab.dto.request.JoinSessionRequest;
import com.codesync.collab.dto.response.ApiResponse;
import com.codesync.collab.dto.response.ParticipantResponse;
import com.codesync.collab.dto.response.SessionResponse;
import com.codesync.collab.service.CollabService;
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
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
@Tag(name = "Collab Service",
        description = "Real-time Collaboration Session APIs")
public class CollabController {

    private final CollabService collabService;

    // ── POST /api/v1/sessions ─────────────────────────────────────────
    @PostMapping
    @Operation(summary = "Create a new collaboration session")
    public ResponseEntity<ApiResponse<SessionResponse>>
    createSession(
            @RequestHeader("X-Auth-User-Id") Long userId,
            @Valid @RequestBody CreateSessionRequest request) {

        SessionResponse response =
                collabService.createSession(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Session created.", response));
    }

    // ── GET /api/v1/sessions/{sessionId} ──────────────────────────────
    @GetMapping("/{sessionId}")
    @Operation(summary = "Get session by ID")
    public ResponseEntity<ApiResponse<SessionResponse>>
    getSession(@PathVariable String sessionId) {

        SessionResponse response =
                collabService.getSessionById(sessionId);
        return ResponseEntity.ok(
                ApiResponse.success("Session fetched.", response));
    }

    // ── GET /api/v1/sessions/project/{projectId} ──────────────────────
    @GetMapping("/project/{projectId}")
    @Operation(summary = "Get all sessions for a project")
    public ResponseEntity<ApiResponse<List<SessionResponse>>>
    getByProject(@PathVariable Long projectId) {

        List<SessionResponse> sessions =
                collabService.getSessionsByProject(projectId);
        return ResponseEntity.ok(
                ApiResponse.success("Sessions fetched.", sessions));
    }

    // ── GET /api/v1/sessions/active?projectId=&fileId= ────────────────
    @GetMapping("/active")
    @Operation(summary = "Get active session for a file")
    public ResponseEntity<ApiResponse<SessionResponse>>
    getActiveSession(
            @RequestParam Long projectId,
            @RequestParam Long fileId) {

        SessionResponse response =
                collabService.getActiveSession(projectId, fileId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Active session fetched.", response));
    }

    // ── POST /api/v1/sessions/{sessionId}/join ────────────────────────
    @PostMapping("/{sessionId}/join")
    @Operation(summary = "Join a collaboration session")
    public ResponseEntity<ApiResponse<ParticipantResponse>>
    joinSession(
            @PathVariable String sessionId,
            @RequestHeader("X-Auth-User-Id") Long userId,
            @RequestBody JoinSessionRequest request) {

        ParticipantResponse response =
                collabService.joinSession(sessionId, userId,
                        request);
        return ResponseEntity.ok(
                ApiResponse.success("Joined session.", response));
    }

    // ── POST /api/v1/sessions/{sessionId}/leave ───────────────────────
    @PostMapping("/{sessionId}/leave")
    @Operation(summary = "Leave a collaboration session")
    public ResponseEntity<ApiResponse<Void>> leaveSession(
            @PathVariable String sessionId,
            @RequestHeader("X-Auth-User-Id") Long userId) {

        collabService.leaveSession(sessionId, userId);
        return ResponseEntity.ok(
                ApiResponse.success("Left session."));
    }

    // ── POST /api/v1/sessions/{sessionId}/end ─────────────────────────
    @PostMapping("/{sessionId}/end")
    @Operation(summary = "End a collaboration session")
    public ResponseEntity<ApiResponse<Void>> endSession(
            @PathVariable String sessionId,
            @RequestHeader("X-Auth-User-Id") Long userId) {

        collabService.endSession(sessionId, userId);
        return ResponseEntity.ok(
                ApiResponse.success("Session ended."));
    }

    // ── POST /api/v1/sessions/{sessionId}/kick/{userId} ───────────────
    @PostMapping("/{sessionId}/kick/{targetUserId}")
    @Operation(summary = "Kick a participant from the session")
    public ResponseEntity<ApiResponse<Void>> kickParticipant(
            @PathVariable String sessionId,
            @PathVariable Long targetUserId,
            @RequestHeader("X-Auth-User-Id") Long ownerId) {

        collabService.kickParticipant(sessionId, ownerId,
                targetUserId);
        return ResponseEntity.ok(
                ApiResponse.success("Participant kicked."));
    }

    // ── GET /api/v1/sessions/{sessionId}/participants ─────────────────
    @GetMapping("/{sessionId}/participants")
    @Operation(summary = "Get active participants in a session")
    public ResponseEntity<ApiResponse<List<ParticipantResponse>>>
    getParticipants(@PathVariable String sessionId) {

        List<ParticipantResponse> participants =
                collabService.getParticipants(sessionId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Participants fetched.", participants));
    }
}