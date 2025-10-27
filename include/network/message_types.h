#ifndef MESSAGE_TYPES_H
#define MESSAGE_TYPES_H

#include <cstdint>

// Binary message type codes (1 byte)
enum class MessageType : uint8_t {
    // Authentication (0x01 - 0x0F)
    LOGIN_REQUEST = 0x01,
    REGISTER_REQUEST = 0x02,
    LOGOUT_REQUEST = 0x03,
    
    // Game Management (0x10 - 0x1F)
    MAKE_MOVE = 0x10,
    GET_GAME_STATE = 0x11,
    RESIGN = 0x12,
    OFFER_DRAW = 0x13,
    ACCEPT_DRAW = 0x14,
    DECLINE_DRAW = 0x15,
    
    // Challenge System (0x20 - 0x2F)
    SEND_CHALLENGE = 0x20,
    ACCEPT_CHALLENGE = 0x21,
    DECLINE_CHALLENGE = 0x22,
    CANCEL_CHALLENGE = 0x23,
    GET_CHALLENGES = 0x24,
    
    // Response Types (0xF0 - 0xFF)
    SUCCESS_RESPONSE = 0xF0,
    ERROR_RESPONSE = 0xF1,
    NOTIFICATION = 0xF2
};

// Helper to convert MessageType to string
inline const char* messageTypeToString(MessageType type) {
    switch (type) {
        case MessageType::LOGIN_REQUEST: return "LOGIN_REQUEST";
        case MessageType::REGISTER_REQUEST: return "REGISTER_REQUEST";
        case MessageType::LOGOUT_REQUEST: return "LOGOUT_REQUEST";
        case MessageType::MAKE_MOVE: return "MAKE_MOVE";
        case MessageType::GET_GAME_STATE: return "GET_GAME_STATE";
        case MessageType::RESIGN: return "RESIGN";
        case MessageType::OFFER_DRAW: return "OFFER_DRAW";
        case MessageType::ACCEPT_DRAW: return "ACCEPT_DRAW";
        case MessageType::DECLINE_DRAW: return "DECLINE_DRAW";
        case MessageType::SEND_CHALLENGE: return "SEND_CHALLENGE";
        case MessageType::ACCEPT_CHALLENGE: return "ACCEPT_CHALLENGE";
        case MessageType::DECLINE_CHALLENGE: return "DECLINE_CHALLENGE";
        case MessageType::CANCEL_CHALLENGE: return "CANCEL_CHALLENGE";
        case MessageType::GET_CHALLENGES: return "GET_CHALLENGES";
        case MessageType::SUCCESS_RESPONSE: return "SUCCESS_RESPONSE";
        case MessageType::ERROR_RESPONSE: return "ERROR_RESPONSE";
        case MessageType::NOTIFICATION: return "NOTIFICATION";
        default: return "UNKNOWN";
    }
}

#endif // MESSAGE_TYPES_H
