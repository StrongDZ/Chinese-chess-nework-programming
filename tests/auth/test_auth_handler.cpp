/**
 * Test suite for AuthHandler
 * Tests login, register, logout, update profile, and avatar functionality
 */

#include "../../include/auth/auth_handler.h"
#include "../../include/config/avatar_config.h"
#include <iostream>
#include <cassert>

using namespace std;

void testAvatarConfig() {
    cout << "\n=== TESTING AVATAR CONFIG ===" << endl;
    
    // Test valid avatar IDs (1-10)
    for (int id = 1; id <= 10; id++) {
        assert(AvatarConfig::isValidAvatarId(id));
        string path = AvatarConfig::getAvatarPath(id);
        string filename = AvatarConfig::getAvatarFilename(id);
        cout << "Avatar " << id << ": " << path << " (" << filename << ")" << endl;
        assert(path.find("resources/avatars/") != string::npos);
        assert(filename == "avatar_" + to_string(id) + ".jpg");
    }
    
    // Test invalid avatar IDs
    assert(!AvatarConfig::isValidAvatarId(0));
    assert(!AvatarConfig::isValidAvatarId(11));
    assert(!AvatarConfig::isValidAvatarId(-1));
    assert(!AvatarConfig::isValidAvatarId(999));
    
    // Test default avatar
    int defaultId = AvatarConfig::getDefaultAvatarId();
    assert(defaultId >= 1 && defaultId <= 10);
    cout << "Default avatar ID: " << defaultId << endl;
    
    cout << "Avatar config tests passed!" << endl;
}

void testRegister(AuthHandler& authHandler) {
    cout << "\n=== TESTING REGISTER ===" << endl;
    
    // Test 1: Valid registration with all fields
    Json::Value registerReq1;
    registerReq1["username"] = "testuser1";
    registerReq1["email"] = "test1@example.com";
    registerReq1["password"] = "Password123!";
    registerReq1["display_name"] = "Test User 1";
    registerReq1["avatar_id"] = 5;
    registerReq1["country"] = "VN";
    
    Json::Value response1 = authHandler.handleRegister(registerReq1);
    cout << "Register response 1: " << response1.toStyledString() << endl;
    
    if (response1["success"].asBool()) {
        assert(response1["data"]["username"].asString() == "testuser1");
        assert(response1["data"]["email"].asString() == "test1@example.com");
        assert(response1["data"]["avatar_path"].asString().find("avatar_5.jpg") != string::npos);
        cout << "Registration with all fields succeeded" << endl;
    } else {
        cout << "Registration failed (may be duplicate): " << response1["error"].asString() << endl;
    }
    
    // Test 2: Registration with default avatar
    Json::Value registerReq2;
    registerReq2["username"] = "testuser2";
    registerReq2["email"] = "test2@example.com";
    registerReq2["password"] = "Password123!";
    
    Json::Value response2 = authHandler.handleRegister(registerReq2);
    cout << "Register response 2: " << response2.toStyledString() << endl;
    
    if (response2["success"].asBool()) {
        assert(response2["data"].isMember("avatar_path"));
        cout << "Registration with default avatar succeeded" << endl;
    } else {
        cout << "Registration failed: " << response2["error"].asString() << endl;
    }
    
    // Test 3: Invalid avatar ID
    Json::Value registerReq3;
    registerReq3["username"] = "testuser3";
    registerReq3["email"] = "test3@example.com";
    registerReq3["password"] = "Password123!";
    registerReq3["avatar_id"] = 0; // Invalid (old system used 0-9, new uses 1-10)
    
    Json::Value response3 = authHandler.handleRegister(registerReq3);
    cout << "Register response 3: " << response3.toStyledString() << endl;
    assert(!response3["success"].asBool());
    assert(response3["error"].asString().find("1-10") != string::npos);
    cout << "Invalid avatar ID correctly rejected" << endl;
    
    // Test 4: Invalid avatar ID (out of range)
    Json::Value registerReq4;
    registerReq4["username"] = "testuser4";
    registerReq4["email"] = "test4@example.com";
    registerReq4["password"] = "Password123!";
    registerReq4["avatar_id"] = 15; // Too high
    
    Json::Value response4 = authHandler.handleRegister(registerReq4);
    cout << "Register response 4: " << response4.toStyledString() << endl;
    assert(!response4["success"].asBool());
    cout << "Out of range avatar ID correctly rejected" << endl;
}

void testLogin(AuthHandler& authHandler) {
    cout << "\n=== TESTING LOGIN ===" << endl;
    
    Json::Value loginReq;
    loginReq["username"] = "testuser1";
    loginReq["password"] = "Password123!";
    
    Json::Value response = authHandler.handleLogin(loginReq);
    cout << "Login response: " << response.toStyledString() << endl;
    
    if (response["success"].asBool()) {
        assert(response["data"].isMember("token"));
        assert(response["data"].isMember("user_id"));
        assert(response["data"]["username"].asString() == "testuser1");
        cout << "Login succeeded, token: " << response["data"]["token"].asString() << endl;
    } else {
        cout << "Login failed (user may not exist): " << response["error"].asString() << endl;
    }
}

void testGetAvatars(AuthHandler& authHandler) {
    cout << "\n=== TESTING GET AVATARS ===" << endl;
    
    Json::Value request;
    Json::Value response = authHandler.handleGetAvatars(request);
    cout << "Get avatars response: " << response.toStyledString() << endl;
    
    assert(response["success"].asBool());
    assert(response["data"].isArray());
    assert(response["data"].size() == 10); // Should have 10 avatars
    
    // Check first avatar
    Json::Value firstAvatar = response["data"][0];
    assert(firstAvatar["id"].asInt() == 1);
    assert(firstAvatar["filename"].asString() == "avatar_1.jpg");
    assert(firstAvatar["path"].asString().find("resources/avatars/") != string::npos);
    
    cout << "Get avatars returned all 10 avatars correctly" << endl;
}

void testUpdateProfile(AuthHandler& authHandler) {
    cout << "\n=== TESTING UPDATE PROFILE ===" << endl;
    
    // First login to get a token
    Json::Value loginReq;
    loginReq["username"] = "testuser1";
    loginReq["password"] = "Password123!";
    
    Json::Value loginResp = authHandler.handleLogin(loginReq);
    
    if (!loginResp["success"].asBool()) {
        cout << "Cannot test update profile - login failed" << endl;
        return;
    }
    
    string token = loginResp["data"]["token"].asString();
    
    // Update avatar
    Json::Value updateReq;
    updateReq["token"] = token;
    updateReq["avatar_id"] = 8;
    updateReq["display_name"] = "Updated Name";
    
    Json::Value response = authHandler.handleUpdateProfile(updateReq);
    cout << "Update profile response: " << response.toStyledString() << endl;
    
    if (response["success"].asBool()) {
        cout << "Profile update succeeded" << endl;
    } else {
        cout << "Profile update failed: " << response["error"].asString() << endl;
    }
    
    // Test invalid avatar update
    Json::Value invalidReq;
    invalidReq["token"] = token;
    invalidReq["avatar_id"] = 0; // Invalid
    
    Json::Value invalidResp = authHandler.handleUpdateProfile(invalidReq);
    cout << "Invalid avatar update response: " << invalidResp.toStyledString() << endl;
    
    if (!invalidResp["success"].asBool() || !invalidResp.isMember("modified_count") || invalidResp["modified_count"].asInt() == 0) {
        cout << "Invalid avatar update correctly handled" << endl;
    }
}

int main() {
    try {
        cout << "=== AUTH HANDLER TEST SUITE ===" << endl;
        cout << "Testing local avatar system (1-10)" << endl;
        
        // Test avatar config first
        testAvatarConfig();
        
        // Initialize database clients
        MongoDBClient mongoClient;
        mongoClient.connect("mongodb://localhost:27017", "chinese_chess_test");
        
        RedisClient redisClient;
        redisClient.connect("127.0.0.1", 6379);
        
        AuthHandler authHandler(mongoClient, redisClient);
        
        // Run tests
        testRegister(authHandler);
        testLogin(authHandler);
        testGetAvatars(authHandler);
        testUpdateProfile(authHandler);
        
        cout << "\n=== ALL TESTS COMPLETED ===" << endl;
        cout << "Avatar system successfully updated to local files (1-10)" << endl;
        
        return 0;
    } catch (const exception& e) {
        cerr << "Test failed with exception: " << e.what() << endl;
        return 1;
    }
}
