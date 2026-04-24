package com.codesync.collab.dto.request;

import lombok.Data;

@Data
public class JoinSessionRequest {

    // Required only if session is password protected
    private String sessionPassword;
}