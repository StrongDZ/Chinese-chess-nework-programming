// Test AuthHandler
#include "../../include/auth/auth_handler.h"
#include <iostream>

using namespace std;

int main() {
    cout << "=== AUTH HANDLER TEST ===" << endl;
    
    // Kết nối database
    MongoDBClient mongoClient;
    RedisClient redisClient;
    
    mongoClient.connect(
        "mongodb+srv://admin:admin@chess.p8k9xpw.mongodb.net/chess_server_game?retryWrites=true&w=majority",
        "chess_server_game"
    );
    redisClient.connect("127.0.0.1", 6379);
    
    AuthHandler authHandler(mongoClient, redisClient);
    
    // 1. Test Get Avatars
    cout << "\n--- Test Get Avatars ---" << endl;
    Json::Value avatarsReq;
    Json::Value avatarsResp = authHandler.handleGetAvatars(avatarsReq);
    cout << avatarsResp.toStyledString() << endl;
    
    // 2. Test Register
    cout << "\n--- Test Register ---" << endl;
    Json::Value registerReq;
    registerReq["username"] = "testuser1";
    registerReq["email"] = "test1@example.com";
    registerReq["password"] = "Pass123!";
    registerReq["display_name"] = "Test User 1";
    registerReq["avatar_id"] = 5;  // Avatar image5.jpg
    registerReq["country"] = "VN";
    
    Json::Value registerResp = authHandler.handleRegister(registerReq);
    cout << registerResp.toStyledString() << endl;
    
    // 3. Test Login
    cout << "\n--- Test Login ---" << endl;
    Json::Value loginReq;
    loginReq["username"] = "testuser1";
    loginReq["password"] = "Pass123!";
    
    Json::Value loginResp = authHandler.handleLogin(loginReq);
    cout << loginResp.toStyledString() << endl;
    
    if (loginResp["success"].asBool()) {
        string token = loginResp["data"]["token"].asString();
        
        // 4. Test Update Profile
        cout << "\n--- Test Update Profile ---" << endl;
        Json::Value updateReq;
        updateReq["token"] = token;
        updateReq["avatar_id"] = 8;  // Đổi sang image8.jpg
        updateReq["display_name"] = "Updated Name";
        
        Json::Value updateResp = authHandler.handleUpdateProfile(updateReq);
        cout << updateResp.toStyledString() << endl;
        
        // 5. Test Logout
        cout << "\n--- Test Logout ---" << endl;
        Json::Value logoutReq;
        logoutReq["token"] = token;
        
        Json::Value logoutResp = authHandler.handleLogout(logoutReq);
        cout << logoutResp.toStyledString() << endl;
    }
    
    cout << "\n=== TEST COMPLETED ===" << endl;
    return 0;
}
