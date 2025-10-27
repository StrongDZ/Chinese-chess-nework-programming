#ifndef PROTOCOL_H
#define PROTOCOL_H

#include "message_types.h"
#include <json/json.h>
#include <vector>
#include <string>
#include <cstdint>

// Binary protocol format:
// [4 bytes: payload length][1 byte: message type][N bytes: JSON payload]

class Protocol {
public:
    // Encode message to binary format
    static std::vector<uint8_t> encode(MessageType type, const Json::Value& payload);
    
    // Decode binary message
    // Returns: {type, payload}
    static bool decode(const std::vector<uint8_t>& data, MessageType& type, Json::Value& payload);
    
    // Helper: Convert uint32_t to network byte order (big-endian)
    static uint32_t htonl(uint32_t hostlong);
    
    // Helper: Convert from network byte order to host byte order
    static uint32_t ntohl(uint32_t netlong);
    
    // Create success response
    static Json::Value createSuccessResponse(const std::string& message, const Json::Value& data = Json::Value::null);
    
    // Create error response
    static Json::Value createErrorResponse(const std::string& message);
};

#endif // PROTOCOL_H
