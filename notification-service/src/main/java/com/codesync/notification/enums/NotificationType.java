package com.codesync.notification.enums;

public enum NotificationType {

    // Collaboration
    SESSION_INVITE,
    PARTICIPANT_JOINED,
    PARTICIPANT_LEFT,

    // Comments
    COMMENT_ADDED,
    COMMENT_REPLY,
    COMMENT_MENTION,
    COMMENT_RESOLVED,

    // Version control
    SNAPSHOT_CREATED,

    // Projects
    PROJECT_FORKED,
    MEMBER_ADDED,
    MEMBER_REMOVED,

    // Admin
    BROADCAST,

    // System
    SYSTEM
}