#ifndef AUTH_CONTROLLER_H
#define AUTH_CONTROLLER_H

#include "auth_service.h"
#include <json/json.h>

class AuthController {
private:
    AuthService& service;

public:
    explicit AuthController(AuthService& svc);
    
    // POST /register
    // Input: { "username": "...", "password": "...", "avatar_id": 1 }
    Json::Value handleRegister(const Json::Value& request);
    
    // POST /login
    // Input: { "username": "...", "password": "..." }
    // Note: Không trả về token nữa - protocol layer sẽ mapping fd -> username
    Json::Value handleLogin(const Json::Value& request);
    
    // POST /logout
    // Input: { "username": "..." }
    // Note: Thay thế token bằng username
    Json::Value handleLogout(const Json::Value& request);
    
    // POST /change-avatar
    // Input: { "username": "...", "avatar_id": 5 }
    // Note: Thay thế token bằng username
    Json::Value handleChangeAvatar(const Json::Value& request);
    
    // GET /avatars
    // Output: List of available avatars (1-10)
    Json::Value handleGetAvatars();
};

#endif
