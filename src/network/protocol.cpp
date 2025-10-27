#include "../../include/network/protocol.h"
#include <arpa/inet.h>
#include <cstring>
#include <sstream>

using namespace std;

vector<uint8_t> Protocol::encode(MessageType type, const Json::Value& payload) {
    // Serialize JSON to string
    Json::StreamWriterBuilder writer;
    writer["indentation"] = "";  // Compact JSON
    string jsonStr = Json::writeString(writer, payload);
    
    // Calculate lengths
    uint32_t payloadLength = 1 + jsonStr.size();  // 1 byte type + JSON
    uint32_t networkLength = htonl(payloadLength);
    
    // Build binary message
    vector<uint8_t> message;
    message.reserve(4 + payloadLength);
    
    // Add length (4 bytes, big-endian)
    const uint8_t* lengthBytes = reinterpret_cast<const uint8_t*>(&networkLength);
    message.insert(message.end(), lengthBytes, lengthBytes + 4);
    
    // Add message type (1 byte)
    message.push_back(static_cast<uint8_t>(type));
    
    // Add JSON payload
    message.insert(message.end(), jsonStr.begin(), jsonStr.end());
    
    return message;
}

bool Protocol::decode(const vector<uint8_t>& data, MessageType& type, Json::Value& payload) {
    // Minimum message: 4 bytes length + 1 byte type + 2 bytes JSON "{}"
    if (data.size() < 7) {
        return false;
    }
    
    // Read length (first 4 bytes)
    uint32_t networkLength;
    memcpy(&networkLength, data.data(), 4);
    uint32_t payloadLength = ntohl(networkLength);
    
    // Verify total size
    if (data.size() != 4 + payloadLength) {
        return false;
    }
    
    // Read message type (byte 5)
    type = static_cast<MessageType>(data[4]);
    
    // Read JSON payload (bytes 6+)
    string jsonStr(data.begin() + 5, data.end());
    
    // Parse JSON
    Json::CharReaderBuilder reader;
    string errors;
    istringstream stream(jsonStr);
    
    if (!Json::parseFromStream(reader, stream, &payload, &errors)) {
        return false;
    }
    
    return true;
}

uint32_t Protocol::htonl(uint32_t hostlong) {
    return ::htonl(hostlong);  // Use system htonl
}

uint32_t Protocol::ntohl(uint32_t netlong) {
    return ::ntohl(netlong);  // Use system ntohl
}

Json::Value Protocol::createSuccessResponse(const string& message, const Json::Value& data) {
    Json::Value response;
    response["success"] = true;
    response["message"] = message;
    
    if (!data.isNull()) {
        response["data"] = data;
    }
    
    return response;
}


// error handling
Json::Value Protocol::createErrorResponse(const string& message) {
    Json::Value response;
    response["success"] = false;
    response["error"] = message;
    return response;
}
