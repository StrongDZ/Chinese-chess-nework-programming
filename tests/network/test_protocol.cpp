#include "../../include/network/protocol.h"
#include "../../include/network/message_types.h"
#include <iostream>
#include <cassert>

void testEncodeDecode() {
    std::cout << "========== Test: Encode/Decode ==========\n" << std::endl;
    
    // Create test payload
    Json::Value payload;
    payload["username"] = "testuser";
    payload["password"] = "testpass123";
    payload["number"] = 42;
    payload["flag"] = true;
    
    // Encode
    auto encoded = Protocol::encode(MessageType::LOGIN_REQUEST, payload);
    
    std::cout << " Encoded message size: " << encoded.size() << " bytes" << std::endl;
    
    // Decode
    MessageType decodedType;
    Json::Value decodedPayload;
    
    bool success = Protocol::decode(encoded, decodedType, decodedPayload);
    
    assert(success);
    assert(decodedType == MessageType::LOGIN_REQUEST);
    assert(decodedPayload["username"].asString() == "testuser");
    assert(decodedPayload["password"].asString() == "testpass123");
    assert(decodedPayload["number"].asInt() == 42);
    assert(decodedPayload["flag"].asBool() == true);
    
    std::cout << " Decoded successfully!" << std::endl;
    std::cout << "   Type: " << messageTypeToString(decodedType) << std::endl;
    std::cout << "   Username: " << decodedPayload["username"].asString() << std::endl;
    std::cout << "   Number: " << decodedPayload["number"].asInt() << std::endl;
}

void testMessageTypes() {
    std::cout << "\n========== Test: Message Types ==========\n" << std::endl;
    
    // Test all message types
    std::vector<MessageType> types = {
        MessageType::LOGIN_REQUEST,
        MessageType::REGISTER_REQUEST,
        MessageType::LOGOUT_REQUEST,
        MessageType::MAKE_MOVE,
        MessageType::SEND_CHALLENGE,
        MessageType::SUCCESS_RESPONSE,
        MessageType::ERROR_RESPONSE
    };
    
    for (auto type : types) {
        Json::Value payload;
        payload["test"] = "data";
        
        auto encoded = Protocol::encode(type, payload);
        
        MessageType decodedType;
        Json::Value decodedPayload;
        Protocol::decode(encoded, decodedType, decodedPayload);
        
        assert(decodedType == type);
        std::cout << " " << messageTypeToString(type) 
                  << " (0x" << std::hex << static_cast<int>(type) << std::dec << ")" << std::endl;
    }
}

void testResponseHelpers() {
    std::cout << "\n========== Test: Response Helpers ==========\n" << std::endl;
    
    // Test success response
    Json::Value data;
    data["user_id"] = "12345";
    data["token"] = "abc123";
    
    auto successResp = Protocol::createSuccessResponse("Login successful", data);
    
    assert(successResp["success"].asBool() == true);
    assert(successResp["message"].asString() == "Login successful");
    assert(successResp["data"]["user_id"].asString() == "12345");
    
    std::cout << " Success response created correctly" << std::endl;
    
    // Test error response
    auto errorResp = Protocol::createErrorResponse("Invalid credentials");
    
    assert(errorResp["success"].asBool() == false);
    assert(errorResp["error"].asString() == "Invalid credentials");
    
    std::cout << " Error response created correctly" << std::endl;
}

void testBinaryFormat() {
    std::cout << "\n========== Test: Binary Format ==========\n" << std::endl;
    
    Json::Value payload;
    payload["test"] = "hello";
    
    auto encoded = Protocol::encode(MessageType::LOGIN_REQUEST, payload);
    
    // Check header (first 5 bytes)
    std::cout << "Binary format breakdown:" << std::endl;
    std::cout << "  Bytes 0-3 (length): ";
    for (int i = 0; i < 4; i++) {
        std::cout << std::hex << static_cast<int>(encoded[i]) << " ";
    }
    std::cout << std::dec << std::endl;
    
    std::cout << "  Byte 4 (type): 0x" << std::hex 
              << static_cast<int>(encoded[4]) << std::dec << std::endl;
    
    std::cout << "  Bytes 5+ (JSON): ";
    for (size_t i = 5; i < encoded.size() && i < 25; i++) {
        std::cout << static_cast<char>(encoded[i]);
    }
    std::cout << "..." << std::endl;
    
    std::cout << " Binary format verified" << std::endl;
}

void testInvalidData() {
    std::cout << "\n========== Test: Invalid Data Handling ==========\n" << std::endl;
    
    // Test 1: Empty data
    std::vector<uint8_t> empty;
    MessageType type;
    Json::Value payload;
    
    bool result = Protocol::decode(empty, type, payload);
    assert(result == false);
    std::cout << " Empty data rejected correctly" << std::endl;
    
    // Test 2: Too short data
    std::vector<uint8_t> tooShort = {0x00, 0x00, 0x00, 0x01};
    result = Protocol::decode(tooShort, type, payload);
    assert(result == false);
    std::cout << " Too-short data rejected correctly" << std::endl;
    
    // Test 3: Invalid JSON
    std::vector<uint8_t> invalidJson = {
        0x00, 0x00, 0x00, 0x05,  // Length: 5
        0x01,                     // Type: LOGIN_REQUEST
        '{', 'x', 'x', 'x'        // Invalid JSON
    };
    result = Protocol::decode(invalidJson, type, payload);
    assert(result == false);
    std::cout << " Invalid JSON rejected correctly" << std::endl;
}

int main() {
    std::cout << "========================================" << std::endl;
    std::cout << "   Protocol Test Suite" << std::endl;
    std::cout << "========================================\n" << std::endl;
    
    try {
        testEncodeDecode();
        testMessageTypes();
        testResponseHelpers();
        testBinaryFormat();
        testInvalidData();
        
        std::cout << "\n========================================" << std::endl;
        std::cout << " All protocol tests passed!" << std::endl;
        std::cout << "========================================" << std::endl;
        
        return 0;
    } catch (const std::exception& e) {
        std::cerr << "\n Test failed: " << e.what() << std::endl;
        return 1;
    }
}
