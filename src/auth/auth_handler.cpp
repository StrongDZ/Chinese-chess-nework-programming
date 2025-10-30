#include "../../include/auth/auth_handler.h"
#include "../../include/auth/auth_handler.h"
#include "../../include/config/avatar_config.h"
#include <bsoncxx/builder/stream/document.hpp>
#include <bsoncxx/json.hpp>
#include <mongocxx/exception/exception.hpp>
#include <openssl/sha.h>
#include <random>
#include <sstream>
#include <iomanip>
#include <regex>

using namespace std;

using bsoncxx::builder::stream::document;
using bsoncxx::builder::stream::finalize;
using bsoncxx::builder::stream::open_document;
using bsoncxx::builder::stream::close_document;

AuthHandler::AuthHandler(MongoDBClient& mongo, RedisClient& redis)
    : mongoClient(mongo), redisClient(redis) {
}

Json::Value AuthHandler::handleLogin(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate input
        if (!request.isMember("username") || !request.isMember("password")) {
            response["success"] = false;
            response["error"] = "Missing username or password";
            return response;
        }

        string username = request["username"].asString();
        string password = request["password"].asString();

        if (username.empty() || password.empty()) {
            response["success"] = false;
            response["error"] = "Username and password cannot be empty";
            return response;
        }
        
        // 2. Hash password
        string passwordHash = hashPassword(password);

        // 3. Query MongoDB for user
        auto db = mongoClient.getDatabase();
        auto users = db["users"];
        
        auto filter = document{}
            << "username" << username
            << "password_hash" << passwordHash
            << finalize;
        
        auto result = users.find_one(filter.view());
        
        if (!result) {
            response["success"] = false;
            response["error"] = "Invalid username or password";
            return response;
        }
        
        // 4. Get user details
        auto doc = result->view();
        string userId = doc["_id"].get_oid().value.to_string();
        string email = string(doc["email"].get_string().value);
        
        // 5. Generate session token
        string token = generateToken();
        
        // 6. Save session to Redis (24 hours TTL)
        if (!redisClient.saveSession(token, userId, 86400)) {
            response["success"] = false;
            response["error"] = "Failed to create session";
            return response;
        }
        
        // 7. Update user status in MongoDB
        auto update = document{}
            << "$set" << open_document
                << "is_online" << true
                << "status" << "active"
                << "last_login" << bsoncxx::types::b_date{chrono::system_clock::now()}
            << close_document
            << finalize;
        
        users.update_one(filter.view(), update.view());
        
        // 8. Return success response
        response["success"] = true;
        response["message"] = "Login successful";
        response["data"]["token"] = token;
        response["data"]["user_id"] = userId;
        response["data"]["username"] = username;
        response["data"]["email"] = email;
        
    } catch (const mongocxx::exception& e) {
        response["success"] = false;
        response["error"] = string("Database error: ") + e.what();
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Login error: ") + e.what();
    }
    
    return response;
}

Json::Value AuthHandler::handleRegister(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate input
        if (!request.isMember("username") || !request.isMember("email") || !request.isMember("password")) {
            response["success"] = false;
            response["error"] = "Missing required fields (username, email, password)";
            return response;
        }
        
        string username = request["username"].asString();
        string email = request["email"].asString();
        string password = request["password"].asString();
        
        // Optional fields
        string displayName = request.get("display_name", username).asString();
        int avatarId = request.get("avatar_id", AvatarConfig::getDefaultAvatarId()).asInt();
        string country = request.get("country", "").asString();
        
        // 2. Validate formats
        if (!isValidUsername(username)) {
            response["success"] = false;
            response["error"] = "Invalid username (3-50 chars, alphanumeric only)";
            return response;
        }
        
        if (!isValidEmail(email)) {
            response["success"] = false;
            response["error"] = "Invalid email format";
            return response;
        }
        
        if (password.length() < 6) {
            response["success"] = false;
            response["error"] = "Password must be at least 6 characters";
            return response;
        }
        
        if (!AvatarConfig::isValidAvatarId(avatarId)) {
            response["success"] = false;
            response["error"] = "Invalid avatar ID (must be 1-10)";
            return response;
        }
        
        if (!country.empty() && !isValidCountryCode(country)) {
            response["success"] = false;
            response["error"] = "Invalid country code (must be 2 chars)";
            return response;
        }
        
        // 3. Hash password
        string passwordHash = hashPassword(password);
        
        // 4. Check if username or email already exists
        auto db = mongoClient.getDatabase();
        auto users = db["users"];
        
        auto existingUser = users.find_one(document{} << "username" << username << finalize);
        if (existingUser) {
            response["success"] = false;
            response["error"] = "Username already exists";
            return response;
        }
        
        auto existingEmail = users.find_one(document{} << "email" << email << finalize);
        if (existingEmail) {
            response["success"] = false;
            response["error"] = "Email already registered";
            return response;
        }
        
        // 5. Get avatar path (local file)
        string avatarPath = AvatarConfig::getAvatarPath(avatarId);
        
        // 6. Create user document
        auto now = chrono::system_clock::now();
        auto userDocBuilder = document{}
            << "username" << username
            << "email" << email
            << "password_hash" << passwordHash
            << "display_name" << displayName
            << "avatar_path" << avatarPath
            << "status" << "active"
            << "is_online" << false
            << "created_at" << bsoncxx::types::b_date{now}
            << "last_login" << bsoncxx::types::b_date{now};
        
        // Add country if provided
        if (!country.empty()) {
            userDocBuilder << "country" << country;
        }
        
        auto userDoc = userDocBuilder << finalize;
        
        auto insertResult = users.insert_one(userDoc.view());
        
        if (!insertResult) {
            response["success"] = false;
            response["error"] = "Failed to create user";
            return response;
        }
        
        string userId = insertResult->inserted_id().get_oid().value.to_string();
        
        // 7. Initialize player stats (3 records: bullet, blitz, classical)
        auto stats = db["player_stats"];
        vector<string> timeControls = {"bullet", "blitz", "classical"};
        
        for (const auto& timeControl : timeControls) {
            int initialRating = 1200;
            if (timeControl == "bullet") initialRating = 1200;
            else if (timeControl == "blitz") initialRating = 1200;
            else initialRating = 1200; // classical
            
            auto statDoc = document{}
                << "user_id" << bsoncxx::oid(userId)
                << "time_control" << timeControl
                << "rating" << initialRating
                << "highest_rating" << initialRating
                << "lowest_rating" << initialRating
                << "rd" << 350.0
                << "volatility" << 0.06
                << "total_games" << 0
                << "wins" << 0
                << "losses" << 0
                << "draws" << 0
                << "win_streak" << 0
                << "longest_win_streak" << 0
                << "total_playtime" << 0
                << "last_game_time" << bsoncxx::types::b_date{now}
                << finalize;
            
            stats.insert_one(statDoc.view());
        }
        
        // 8. Return success
        response["success"] = true;
        response["message"] = "Registration successful";
        response["data"]["user_id"] = userId;
        response["data"]["username"] = username;
        response["data"]["email"] = email;
        response["data"]["display_name"] = displayName;
        response["data"]["avatar_path"] = avatarPath;
        if (!country.empty()) {
            response["data"]["country"] = country;
        }
        
    } catch (const mongocxx::exception& e) {
        response["success"] = false;
        response["error"] = string("Database error: ") + e.what();
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Registration error: ") + e.what();
    }
    
    return response;
}

Json::Value AuthHandler::handleLogout(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate input
        if (!request.isMember("token")) {
            response["success"] = false;
            response["error"] = "Missing session token";
            return response;
        }
        
        string token = request["token"].asString();
        
        // 2. Get user ID from Redis
        string userId = redisClient.getSession(token);
        
        if (userId.empty()) {
            response["success"] = false;
            response["error"] = "Invalid or expired session";
            return response;
        }
        
        // 3. Delete session from Redis
        redisClient.deleteSession(token);
        
        // 4. Update user status in MongoDB
        auto db = mongoClient.getDatabase();
        auto users = db["users"];
        
        auto filter = document{} << "_id" << bsoncxx::oid(userId) << finalize;
        auto update = document{}
            << "$set" << open_document
                << "is_online" << false
                << "status" << "active"
            << close_document
            << finalize;
        
        users.update_one(filter.view(), update.view());
        
        // 5. Return success
        response["success"] = true;
        response["message"] = "Logout successful";
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Logout error: ") + e.what();
    }
    
    return response;
}

//  HELPER METHODS 

string AuthHandler::hashPassword(const string& password) {
    unsigned char hash[SHA256_DIGEST_LENGTH];
    SHA256(reinterpret_cast<const unsigned char*>(password.c_str()), password.length(), hash);
    
    stringstream ss;
    for (int i = 0; i < SHA256_DIGEST_LENGTH; i++) {
        ss << hex << setw(2) << setfill('0') << static_cast<int>(hash[i]);
    }
    
    return ss.str();
}

string AuthHandler::generateToken() {
    static const char chars[] = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    static random_device rd;
    static mt19937 gen(rd());
    static uniform_int_distribution<> dis(0, sizeof(chars) - 2);
    
    string token;
    token.reserve(64);
    
    for (int i = 0; i < 64; i++) {
        token += chars[dis(gen)];
    }
    
    return token;
}

bool AuthHandler::isValidUsername(const string& username) {
    if (username.length() < 3 || username.length() > 50) {
        return false;
    }
    
    // Only alphanumeric and underscore
    regex pattern("^[a-zA-Z0-9_]+$");
    return regex_match(username, pattern);
}

bool AuthHandler::isValidEmail(const string& email) {
    regex pattern(R"(^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$)");
    return regex_match(email, pattern);
}

bool AuthHandler::isValidCountryCode(const string& country) {
    if (country.length() != 2) return false;
    // Check if it's uppercase letters
    regex pattern("^[A-Z]{2}$");
    return regex_match(country, pattern);
}

string AuthHandler::getUserIdFromToken(const string& token) {
    return redisClient.getSession(token);
}

Json::Value AuthHandler::handleUpdateProfile(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate token
        if (!request.isMember("token")) {
            response["success"] = false;
            response["error"] = "Missing session token";
            return response;
        }
        
        string token = request["token"].asString();
        string userId = getUserIdFromToken(token);
        
        if (userId.empty()) {
            response["success"] = false;
            response["error"] = "Invalid or expired session";
            return response;
        }
        
        // 2. Build update document
        auto updateBuilder = document{} << "$set" << open_document;
        bool hasUpdate = false;
        
        // Display name
        if (request.isMember("display_name")) {
            string displayName = request["display_name"].asString();
            if (!displayName.empty() && displayName.length() <= 100) {
                updateBuilder << "display_name" << displayName;
                hasUpdate = true;
            }
        }
        
        // Avatar ID
        if (request.isMember("avatar_id")) {
            int avatarId = request["avatar_id"].asInt();
            if (AvatarConfig::isValidAvatarId(avatarId)) {
                string avatarPath = AvatarConfig::getAvatarPath(avatarId);
                updateBuilder << "avatar_path" << avatarPath;
                hasUpdate = true;
            }
        }
        
        // Country
        if (request.isMember("country")) {
            string country = request["country"].asString();
            if (!country.empty() && isValidCountryCode(country)) {
                updateBuilder << "country" << country;
                hasUpdate = true;
            }
        }
        
        if (!hasUpdate) {
            response["success"] = false;
            response["error"] = "No valid fields to update";
            return response;
        }
        
        auto update = updateBuilder << close_document << finalize;
        
        // 3. Update user
        auto db = mongoClient.getDatabase();
        auto users = db["users"];
        
        auto filter = document{} << "_id" << bsoncxx::oid(userId) << finalize;
        auto result = users.update_one(filter.view(), update.view());
        
        if (!result || result->modified_count() == 0) {
            response["success"] = false;
            response["error"] = "Failed to update profile";
            return response;
        }
        
        // 4. Return success
        response["success"] = true;
        response["message"] = "Profile updated successfully";
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Update profile error: ") + e.what();
    }
    
    return response;
}

Json::Value AuthHandler::handleGetAvatars(const Json::Value& request) {
    Json::Value response;
    
    try {
        response["success"] = true;
        response["data"] = Json::Value(Json::arrayValue);
        
        // Return all 10 avatars with their local paths
        for (int id = 1; id <= AvatarConfig::TOTAL_AVATARS; id++) {
            Json::Value avatar;
            avatar["id"] = id;
            avatar["filename"] = AvatarConfig::getAvatarFilename(id);
            avatar["path"] = AvatarConfig::getAvatarPath(id);
            response["data"].append(avatar);
        }
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Get avatars error: ") + e.what();
    }
    
    return response;
}
