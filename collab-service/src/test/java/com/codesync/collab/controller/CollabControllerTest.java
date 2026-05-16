package com.codesync.collab.controller;

import com.codesync.collab.dto.request.CreateSessionRequest;
import com.codesync.collab.dto.request.JoinSessionRequest;
import com.codesync.collab.dto.response.ParticipantResponse;
import com.codesync.collab.dto.response.SessionResponse;
import com.codesync.collab.enums.ParticipantRole;
import com.codesync.collab.enums.SessionStatus;
import com.codesync.collab.exception.SessionAccessException;
import com.codesync.collab.exception.SessionNotFoundException;
import com.codesync.collab.service.CollabService;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@WebMvcTest(CollabController.class)
@AutoConfigureMockMvc(addFilters = false)
class CollabControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private CollabService collabService;

    // ── Fixtures ──────────────────────────────────────────────────────

    private SessionResponse buildSessionResponse(String id, SessionStatus status) {
        return SessionResponse.builder()
                .sessionId(id).projectId(1L).fileId(10L).ownerId(1L)
                .status(status).language("java").maxParticipants(10)
                .isPasswordProtected(false).activeParticipantCount(1)
                .webSocketTopic("/topic/session/" + id)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private ParticipantResponse buildParticipantResponse(Long userId,
                                                         ParticipantRole role) {
        return ParticipantResponse.builder()
                .participantId(1L).sessionId("sess-1").userId(userId)
                .role(role).cursorLine(0).cursorCol(0)
                .color("#FF6B6B").isActive(true)
                .joinedAt(LocalDateTime.now())
                .build();
    }

    // ════════════════════════════════════════════════════════════════
    // POST /api/v1/sessions
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST / - 201 Created on successful session creation")
    void createSession_valid_returns201() throws Exception {
        CreateSessionRequest req = new CreateSessionRequest();
        req.setProjectId(1L); req.setFileId(10L);
        req.setLanguage("java"); req.setMaxParticipants(10);
        req.setIsPasswordProtected(false);

        when(collabService.createSession(eq(1L), any()))
                .thenReturn(buildSessionResponse("sess-1", SessionStatus.ACTIVE));

        mockMvc.perform(post("/api/v1/sessions")
                        .header("X-Auth-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sessionId").value("sess-1"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.webSocketTopic")
                        .value("/topic/session/sess-1"));
    }

    @Test
    @DisplayName("POST / - 400 Bad Request when required fields are missing")
    void createSession_missingFields_returns400() throws Exception {
        CreateSessionRequest req = new CreateSessionRequest();
        // projectId and fileId missing

        mockMvc.perform(post("/api/v1/sessions")
                        .header("X-Auth-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/sessions/{sessionId}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /{sessionId} - 200 OK for existing session")
    void getSession_existing_returns200() throws Exception {
        when(collabService.getSessionById("sess-1"))
                .thenReturn(buildSessionResponse("sess-1", SessionStatus.ACTIVE));

        mockMvc.perform(get("/api/v1/sessions/sess-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value("sess-1"))
                .andExpect(jsonPath("$.data.activeParticipantCount").value(1));
    }

    @Test
    @DisplayName("GET /{sessionId} - 404 Not Found for unknown session")
    void getSession_notFound_returns404() throws Exception {
        when(collabService.getSessionById("ghost"))
                .thenThrow(new SessionNotFoundException("Session not found: ghost"));

        mockMvc.perform(get("/api/v1/sessions/ghost"))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/sessions/project/{projectId}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /project/{projectId} - 200 OK returns project sessions")
    void getByProject_existing_returns200() throws Exception {
        when(collabService.getSessionsByProject(1L)).thenReturn(List.of(
                buildSessionResponse("sess-1", SessionStatus.ACTIVE),
                buildSessionResponse("sess-2", SessionStatus.ENDED)
        ));

        mockMvc.perform(get("/api/v1/sessions/project/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("GET /project/{projectId} - 200 OK returns empty list for new project")
    void getByProject_noSessions_returnsEmpty() throws Exception {
        when(collabService.getSessionsByProject(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/sessions/project/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/sessions/active?projectId=&fileId=
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /active - 200 OK returns active session for file")
    void getActiveSession_existing_returns200() throws Exception {
        when(collabService.getActiveSession(1L, 10L))
                .thenReturn(buildSessionResponse("sess-1", SessionStatus.ACTIVE));

        mockMvc.perform(get("/api/v1/sessions/active")
                        .param("projectId", "1")
                        .param("fileId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /active - 404 Not Found when no active session")
    void getActiveSession_noActive_returns404() throws Exception {
        when(collabService.getActiveSession(1L, 10L))
                .thenThrow(new SessionNotFoundException("No active session found for this file."));

        mockMvc.perform(get("/api/v1/sessions/active")
                        .param("projectId", "1")
                        .param("fileId", "10"))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════
    // POST /api/v1/sessions/{sessionId}/join
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /{sessionId}/join - 200 OK on successful join")
    void joinSession_valid_returns200() throws Exception {
        JoinSessionRequest req = new JoinSessionRequest();

        when(collabService.joinSession(eq("sess-1"), eq(5L), any()))
                .thenReturn(buildParticipantResponse(5L, ParticipantRole.EDITOR));

        mockMvc.perform(post("/api/v1/sessions/sess-1/join")
                        .header("X-Auth-User-Id", 5L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(5))
                .andExpect(jsonPath("$.data.role").value("EDITOR"))
                .andExpect(jsonPath("$.data.isActive").value(true));
    }

    @Test
    @DisplayName("POST /{sessionId}/join - 404 Not Found for unknown session")
    void joinSession_notFound_returns404() throws Exception {
        when(collabService.joinSession(eq("ghost"), eq(5L), any()))
                .thenThrow(new SessionNotFoundException("Session not found: ghost"));

        mockMvc.perform(post("/api/v1/sessions/ghost/join")
                        .header("X-Auth-User-Id", 5L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JoinSessionRequest())))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════
    // POST /api/v1/sessions/{sessionId}/leave
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /{sessionId}/leave - 200 OK on successful leave")
    void leaveSession_valid_returns200() throws Exception {
        doNothing().when(collabService).leaveSession("sess-1", 5L);

        mockMvc.perform(post("/api/v1/sessions/sess-1/leave")
                        .header("X-Auth-User-Id", 5L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Left session."));
    }

    @Test
    @DisplayName("POST /{sessionId}/leave - 404 Not Found when not in session")
    void leaveSession_notInSession_returns404() throws Exception {
        doThrow(new SessionNotFoundException("Participant not found in session."))
                .when(collabService).leaveSession("sess-1", 99L);

        mockMvc.perform(post("/api/v1/sessions/sess-1/leave")
                        .header("X-Auth-User-Id", 99L))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════
    // POST /api/v1/sessions/{sessionId}/end
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /{sessionId}/end - 200 OK on successful end")
    void endSession_owner_returns200() throws Exception {
        doNothing().when(collabService).endSession("sess-1", 1L);

        mockMvc.perform(post("/api/v1/sessions/sess-1/end")
                        .header("X-Auth-User-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Session ended."));
    }

    @Test
    @DisplayName("POST /{sessionId}/end - 403 Forbidden for non-owner")
    void endSession_nonOwner_returns403() throws Exception {
        doThrow(new SessionAccessException("Only the session owner can end the session."))
                .when(collabService).endSession("sess-1", 99L);

        mockMvc.perform(post("/api/v1/sessions/sess-1/end")
                        .header("X-Auth-User-Id", 99L))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /{sessionId}/end - 404 Not Found for unknown session")
    void endSession_notFound_returns404() throws Exception {
        doThrow(new SessionNotFoundException("Session not found: ghost"))
                .when(collabService).endSession("ghost", 1L);

        mockMvc.perform(post("/api/v1/sessions/ghost/end")
                        .header("X-Auth-User-Id", 1L))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════
    // POST /api/v1/sessions/{sessionId}/kick/{targetUserId}
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /{sessionId}/kick/{targetUserId} - 200 OK on successful kick")
    void kickParticipant_owner_returns200() throws Exception {
        doNothing().when(collabService).kickParticipant("sess-1", 1L, 5L);

        mockMvc.perform(post("/api/v1/sessions/sess-1/kick/5")
                        .header("X-Auth-User-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Participant kicked."));
    }

    @Test
    @DisplayName("POST /{sessionId}/kick/{targetUserId} - 403 Forbidden for non-owner")
    void kickParticipant_nonOwner_returns403() throws Exception {
        doThrow(new SessionAccessException("Only the session owner can kick participants."))
                .when(collabService).kickParticipant("sess-1", 99L, 5L);

        mockMvc.perform(post("/api/v1/sessions/sess-1/kick/5")
                        .header("X-Auth-User-Id", 99L))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /{sessionId}/kick/{targetUserId} - 403 Forbidden when trying to kick owner")
    void kickParticipant_kickingOwner_returns403() throws Exception {
        doThrow(new SessionAccessException("Cannot kick the session owner."))
                .when(collabService).kickParticipant("sess-1", 1L, 1L);

        mockMvc.perform(post("/api/v1/sessions/sess-1/kick/1")
                        .header("X-Auth-User-Id", 1L))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /{sessionId}/kick/{targetUserId} - 404 Not Found when target not in session")
    void kickParticipant_targetNotFound_returns404() throws Exception {
        doThrow(new SessionNotFoundException("Participant not found in session."))
                .when(collabService).kickParticipant("sess-1", 1L, 99L);

        mockMvc.perform(post("/api/v1/sessions/sess-1/kick/99")
                        .header("X-Auth-User-Id", 1L))
                .andExpect(status().isNotFound());
    }

    // ════════════════════════════════════════════════════════════════
    // GET /api/v1/sessions/{sessionId}/participants
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /{sessionId}/participants - 200 OK returns active participants")
    void getParticipants_existing_returns200() throws Exception {
        when(collabService.getParticipants("sess-1")).thenReturn(List.of(
                buildParticipantResponse(1L, ParticipantRole.HOST),
                buildParticipantResponse(2L, ParticipantRole.EDITOR)
        ));

        mockMvc.perform(get("/api/v1/sessions/sess-1/participants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].role").value("HOST"))
                .andExpect(jsonPath("$.data[1].role").value("EDITOR"));
    }

    @Test
    @DisplayName("GET /{sessionId}/participants - 404 Not Found for unknown session")
    void getParticipants_notFound_returns404() throws Exception {
        when(collabService.getParticipants("ghost"))
                .thenThrow(new SessionNotFoundException("Session not found: ghost"));

        mockMvc.perform(get("/api/v1/sessions/ghost/participants"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /{sessionId}/participants - 200 OK returns empty list when all left")
    void getParticipants_allLeft_returnsEmpty() throws Exception {
        when(collabService.getParticipants("sess-1")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/sessions/sess-1/participants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}