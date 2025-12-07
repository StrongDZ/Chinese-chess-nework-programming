#include "auth/auth_controller.h"

using namespace std;

AuthController::AuthController(AuthService& svc) : service(svc) {}

// ============================================
// POST /register
// Input: { "username": "...", "password": "...", "avatar_id": 1 }
// ============================================
Json::Value AuthController::handleRegister(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Parse request
        if (!request.isMember("username") || !request.isMember("password")) {
            response["success"] = false;
            response["error"] = "Missing username or password";
            return response;
        }
        
        string username = request["username"].asString();
        string password = request["password"].asString();
        int avatarId = request.get("avatar_id", 1).asInt();  // Default avatar 1
        
        // 2. Call service
        AuthResult result = service.registerUser(username, password, avatarId);
        
        // 3. Build response (no user_id!)
        response["success"] = result.success;
        
        if (result.success) {
            response["message"] = result.message;
            response["data"]["username"] = result.username;
            response["data"]["avatar_id"] = result.avatarId;
        } else {
            response["error"] = result.message;
        }
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Registration error: ") + e.what();
    }
    
    return response;
}

// ============================================
// POST /login
// Input: { "username": "...", "password": "..." }
// Note: Không trả về token nữa - protocol layer sẽ mapping fd -> username
// ============================================
Json::Value AuthController::handleLogin(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Parse request
        if (!request.isMember("username") || !request.isMember("password")) {
            response["success"] = false;
            response["error"] = "Missing username or password";
            return response;
        }
        
        string username = request["username"].asString();
        string password = request["password"].asString();
        
        // 2. Call service
        AuthResult result = service.login(username, password);
        
        // 3. Build response (no user_id, no token!)
        response["success"] = result.success;
        
        if (result.success) {
            response["message"] = result.message;
            response["data"]["username"] = result.username;
            response["data"]["avatar_id"] = result.avatarId;
        } else {
            response["error"] = result.message;
        }
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Login error: ") + e.what();
    }
    
    return response;
}

// ============================================
// POST /logout
// Input: { "username": "..." }
// Note: Thay thế token bằng username
// ============================================
Json::Value AuthController::handleLogout(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Parse request (username thay vì token)
        if (!request.isMember("username")) {
            response["success"] = false;
            response["error"] = "Username required";
            return response;
        }
        
        string username = request["username"].asString();
        
        // 2. Call service
        AuthResult result = service.logout(username);
        
        // 3. Build response
        response["success"] = result.success;
        
        if (result.success) {
            response["message"] = result.message;
        } else {
            response["error"] = result.message;
        }
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Logout error: ") + e.what();
    }
    
    return response;
}

// ============================================
// POST /change-avatar
// Input: { "username": "...", "avatar_id": 5 }
// Note: Thay thế token bằng username
// ============================================
Json::Value AuthController::handleChangeAvatar(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Parse request (username thay vì token)
        if (!request.isMember("username") || !request.isMember("avatar_id")) {
            response["success"] = false;
            response["error"] = "Username and avatar_id required";
            return response;
        }
        
        string username = request["username"].asString();
        int avatarId = request["avatar_id"].asInt();
        
        // 2. Call service
        AuthResult result = service.changeAvatar(username, avatarId);
        
        // 3. Build response
        response["success"] = result.success;
        
        if (result.success) {
            response["message"] = result.message;
            response["data"]["avatar_id"] = result.avatarId;
        } else {
            response["error"] = result.message;
        }
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Change avatar error: ") + e.what();
    }
    
    return response;
}

// ============================================
// GET /avatars
// Output: List of available avatars (1-10)
// ============================================
Json::Value AuthController::handleGetAvatars() {
    Json::Value response;
    
    try {
        response["success"] = true;
        response["data"] = Json::Value(Json::arrayValue);
        
        // Return 10 avatars
        for (int id = 1; id <= 10; id++) {
            Json::Value avatar;
            avatar["id"] = id;
            avatar["filename"] = "avatar_" + to_string(id) + ".jpg";
            response["data"].append(avatar);
        }
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Get avatars error: ") + e.what();
    }
    
    return response;
}
