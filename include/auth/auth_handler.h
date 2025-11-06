#ifndef AUTH_HANDLER_H
#define AUTH_HANDLER_H

#include "../database/mongodb_client.h"
#include "../database/redis_client.h"
#include <json/json.h>
#include <string>

class AuthHandler {
private:
    MongoDBClient& mongoClient;
    RedisClient& redisClient;

public:
    AuthHandler(MongoDBClient& mongo, RedisClient& redis);
    
    // Đăng ký tài khoản mới
    Json::Value handleRegister(const Json::Value& request);
    
    // Đăng nhập
    Json::Value handleLogin(const Json::Value& request);
    
    // Đăng xuất
    Json::Value handleLogout(const Json::Value& request);
    
    // Cập nhật profile (display_name, avatar, country)
    Json::Value handleUpdateProfile(const Json::Value& request);
    
    // Lấy danh sách 10 avatar
    Json::Value handleGetAvatars(const Json::Value& request);

private:
    // Hash password bằng SHA256
    std::string hashPassword(const std::string& password);
    
    // Tạo random token cho session
    std::string generateToken();
    
    // Validate username (3-50 ký tự, chữ + số)
    bool isValidUsername(const std::string& username);
    
    // Validate email format
    bool isValidEmail(const std::string& email);
    
    // Validate avatar_id (1-10)
    bool isValidAvatarId(int avatarId);
    
    // Validate country code (2 chữ cái)
    bool isValidCountryCode(const std::string& country);
    
    // Lấy userId từ token (Redis)
    std::string getUserIdFromToken(const std::string& token);
};

#endif
