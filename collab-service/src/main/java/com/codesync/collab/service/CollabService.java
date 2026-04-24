package com.codesync.collab.service;

import com.codesync.collab.dto.request.CreateSessionRequest;
import com.codesync.collab.dto.request.JoinSessionRequest;
import com.codesync.collab.dto.response.ParticipantResponse;
import com.codesync.collab.dto.response.SessionResponse;

import java.util.List;

public interface CollabService {

    // Session lifecycle
    SessionResponse createSession(Long ownerId,
                                  CreateSessionRequest request);
    SessionResponse getSessionById(String sessionId);
    List<SessionResponse> getSessionsByProject(Long projectId);
    SessionResponse getActiveSession(Long projectId, Long fileId);
    void endSession(String sessionId, Long ownerId);

    // Participant management
    ParticipantResponse joinSession(String sessionId, Long userId,
                                    JoinSessionRequest request);
    void leaveSession(String sessionId, Long userId);
    void kickParticipant(String sessionId, Long ownerId,
                         Long userId);
    List<ParticipantResponse> getParticipants(String sessionId);

    // Cursor update (persists to DB)
    ParticipantResponse updateCursor(String sessionId, Long userId,
                                     Integer line, Integer col);

    // Redis operations
    void saveContentToRedis(String sessionId, String content);
    String getContentFromRedis(String sessionId);
    void updateLastActivity(String sessionId);
}