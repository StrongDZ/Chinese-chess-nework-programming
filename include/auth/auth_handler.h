#ifndef AUTH_HANDLER_H
#define AUTH_HANDLER_H

#include "../database/mongodb_client.h"
#include "../database/redis_client.h"
#include "../config/avatar_config.h"
#include <json/json.h>
#include <string>

class AuthHandler {
private:
    MongoDBClient& mongoClient;
    RedisClient& redisClient;

public:
    AuthHandler(MongoDBClient& mongo, RedisClient& redis);
    
    // Handle 0x01 LOGIN_REQUEST
    Json::Value handleLogin(const Json::Value& request);
    
    // Handle 0x02 REGISTER_REQUEST
    Json::Value handleRegister(const Json::Value& request);
    
    // Handle 0x03 LOGOUT_REQUEST
    Json::Value handleLogout(const Json::Value& request);
    
    // Update user profile (display name, avatar, country)
    Json::Value handleUpdateProfile(const Json::Value& request);
    
    // Get available avatars list
    Json::Value handleGetAvatars(const Json::Value& request);

private:
    // Hash password with SHA256
    std::string hashPassword(const std::string& password);
    
    // Generate random session token
    std::string generateToken();
    
    // Validate username format (3-50 chars, alphanumeric)
    bool isValidUsername(const std::string& username);
    
    // Validate email format
    bool isValidEmail(const std::string& email);
    
    // Validate country code (2 chars)
    bool isValidCountryCode(const std::string& country);
    
    // Get user ID from token
    std::string getUserIdFromToken(const std::string& token);
};

#endif // AUTH_HANDLER_H
