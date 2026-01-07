package application.network;

/**
 * Message types matching the C++ server protocol.
 */
public enum MessageType {
    // Authentication
    LOGIN,
    REGISTER,
    LOGOUT,
    AUTHENTICATED,

    // Game Management
    QUICK_MATCHING,
    CANCEL_QM,
    CHALLENGE_CANCEL,
    CHALLENGE_REQUEST,
    CHALLENGE_RESPONSE,
    AI_MATCH,

    // Game Flow
    GAME_START,
    MOVE,
    INVALID_MOVE,
    MESSAGE,
    GAME_END,

    // Game Control
    RESIGN,
    DRAW_REQUEST,
    DRAW_RESPONSE,
    REMATCH_REQUEST,
    REMATCH_RESPONSE,

    // Data Management
    USER_STATS,
    GAME_HISTORY,
    REPLAY_REQUEST,
    LEADER_BOARD,
    PLAYER_LIST,
    INFO,

    // Friend Management
    REQUEST_ADD_FRIEND,
    RESPONSE_ADD_FRIEND,
    UNFRIEND,

    // AI
    SUGGEST_MOVE,

    // System
    ERROR,
    UNKNOWN;

    /**
     * Convert enum to protocol string (uppercase).
     */
    public String toProtocolString() {
        return this == UNKNOWN ? "UNKNOWN" : name();
    }

    /**
     * Parse protocol string to MessageType.
     */
    public static MessageType fromString(String str) {
        if (str == null || str.isEmpty()) {
            return UNKNOWN;
        }
        try {
            return valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}

