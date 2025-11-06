#include "../../include/auth/auth_handler.h"
#include <mongocxx/client.hpp>
#include <bsoncxx/builder/stream/document.hpp>
#include <bsoncxx/json.hpp>
#include <openssl/sha.h>
#include <random>
#include <regex>
#include <iomanip>
#include <sstream>
#include <chrono>

using namespace std;
using bsoncxx::builder::stream::document;
using bsoncxx::builder::stream::finalize;

AuthHandler::AuthHandler(MongoDBClient& mongo, RedisClient& redis)
    : mongoClient(mongo), redisClient(redis) {}

// Hash password bằng SHA256
string AuthHandler::hashPassword(const string& password) {
    unsigned char hash[SHA256_DIGEST_LENGTH];
    SHA256((unsigned char*)password.c_str(), password.length(), hash);
    
    stringstream ss;
    for(int i = 0; i < SHA256_DIGEST_LENGTH; i++) {
        ss << hex << setw(2) << setfill('0') << (int)hash[i];
    }
    return ss.str();
}

// Tạo token ngẫu nhiên
string AuthHandler::generateToken() {
    random_device rd;
    mt19937_64 gen(rd());
    uniform_int_distribution<uint64_t> dis;
    
    stringstream ss;
    ss << hex << dis(gen) << dis(gen);
    return ss.str();
}

// Validate username
bool AuthHandler::isValidUsername(const string& username) {
    if (username.length() < 3 || username.length() > 50) return false;
    regex pattern("^[a-zA-Z0-9_]+$");
    return regex_match(username, pattern);
}

// Validate email
bool AuthHandler::isValidEmail(const string& email) {
    regex pattern(R"(^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$)");
    return regex_match(email, pattern);
}

// Validate avatar ID (1-10)
bool AuthHandler::isValidAvatarId(int avatarId) {
    return avatarId >= 1 && avatarId <= 10;
}

// Validate country code
bool AuthHandler::isValidCountryCode(const string& country) {
    if (country.length() != 2) return false;
    regex pattern("^[A-Z]{2}$");
    return regex_match(country, pattern);
}

// Lấy userId từ token
string AuthHandler::getUserIdFromToken(const string& token) {
    return redisClient.getSession(token);
}

// ĐĂNG KÝ
Json::Value AuthHandler::handleRegister(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Lấy thông tin từ request
        if (!request.isMember("username") || !request.isMember("email") || 
            !request.isMember("password")) {
            response["success"] = false;
            response["error"] = "Missing required fields";
            return response;
        }
        
        string username = request["username"].asString();
        string email = request["email"].asString();
        string password = request["password"].asString();
        
        // Optional fields
        string displayName = request.get("display_name", username).asString();
        int avatarId = request.get("avatar_id", 1).asInt();  // Mặc định avatar 1
        string country = request.get("country", "").asString();
        
        // 2. Validate
        if (!isValidUsername(username)) {
            response["success"] = false;
            response["error"] = "Invalid username (3-50 chars, alphanumeric)";
            return response;
        }
        
        if (!isValidEmail(email)) {
            response["success"] = false;
            response["error"] = "Invalid email format";
            return response;
        }
        
        if (!isValidAvatarId(avatarId)) {
            response["success"] = false;
            response["error"] = "Invalid avatar_id (must be 1-10)";
            return response;
        }
        
        if (!country.empty() && !isValidCountryCode(country)) {
            response["success"] = false;
            response["error"] = "Invalid country code (2 uppercase letters)";
            return response;
        }
        
        // 3. Hash password
        string passwordHash = hashPassword(password);
        
        // 4. Check username/email đã tồn tại chưa
        auto db = mongoClient.getDatabase();
        auto users = db["users"];
        
        auto existingUser = users.find_one(
            document{} << "username" << username << finalize
        );
        if (existingUser) {
            response["success"] = false;
            response["error"] = "Username already exists";
            return response;
        }
        
        auto existingEmail = users.find_one(
            document{} << "email" << email << finalize
        );
        if (existingEmail) {
            response["success"] = false;
            response["error"] = "Email already registered";
            return response;
        }
        
        // 5. Tạo avatar path
        string avatarPath = "resources/avatars/image" + to_string(avatarId) + ".jpg";
        
        // 6. Tạo user document
        auto now = chrono::system_clock::now();
        document userDocBuilder{};
        userDocBuilder
            << "username" << username
            << "email" << email
            << "password_hash" << passwordHash
            << "display_name" << displayName
            << "avatar_url" << avatarPath
            << "status" << "active"
            << "is_online" << false
            << "created_at" << bsoncxx::types::b_date{now}
            << "last_login" << bsoncxx::types::b_date{now};
        
        if (!country.empty()) {
            userDocBuilder << "country" << country;
        }
        
        auto userDoc = userDocBuilder << finalize;
        
        // 7. Insert vào database
        auto insertResult = users.insert_one(userDoc.view());
        if (!insertResult) {
            response["success"] = false;
            response["error"] = "Failed to create user";
            return response;
        }
        
        bsoncxx::oid userOid = insertResult->inserted_id().get_oid().value;
        string userId = userOid.to_string();
        
        // 8. Tạo player_stats cho 3 time_control
        auto stats = db["player_stats"];
        vector<string> timeControls = {"bullet", "blitz", "classical"};
        int initialRating = 1500;
        
        for (const auto& timeControl : timeControls) {
            auto statDoc = document{}
                << "user_id" << userOid
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
        
        // 9. Return success
        response["success"] = true;
        response["message"] = "Registration successful";
        response["data"]["user_id"] = userId;
        response["data"]["username"] = username;
        response["data"]["avatar_url"] = avatarPath;
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Registration error: ") + e.what();
    }
    
    return response;
}

// ĐĂNG NHẬP
Json::Value AuthHandler::handleLogin(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Lấy thông tin
        if (!request.isMember("username") || !request.isMember("password")) {
            response["success"] = false;
            response["error"] = "Missing username or password";
            return response;
        }
        
        string username = request["username"].asString();
        string password = request["password"].asString();
        string passwordHash = hashPassword(password);
        
        // 2. Tìm user
        auto db = mongoClient.getDatabase();
        auto users = db["users"];
        
        auto userDoc = users.find_one(
            document{} << "username" << username << finalize
        );
        
        if (!userDoc) {
            response["success"] = false;
            response["error"] = "Invalid username or password";
            return response;
        }
        
        auto user = userDoc->view();
        
        // 3. Check password
        string storedHash = string(user["password_hash"].get_string().value);
        if (storedHash != passwordHash) {
            response["success"] = false;
            response["error"] = "Invalid username or password";
            return response;
        }
        
        // 4. Check account status
        string status = string(user["status"].get_string().value);
        if (status == "banned") {
            response["success"] = false;
            response["error"] = "Account is banned";
            return response;
        }
        
        // 5. Tạo session token
        string token = generateToken();
        string userId = user["_id"].get_oid().value.to_string();
        
        // Lưu session vào Redis (TTL 24h)
        redisClient.saveSession(token, userId, 86400);
        
        // 6. Update last_login và is_online
        auto now = chrono::system_clock::now();
        users.update_one(
            document{} << "_id" << bsoncxx::oid(userId) << finalize,
            document{} << "$set" << bsoncxx::builder::stream::open_document
                      << "last_login" << bsoncxx::types::b_date{now}
                      << "is_online" << true
                      << bsoncxx::builder::stream::close_document
                      << finalize
        );
        
        // 7. Return success với token
        response["success"] = true;
        response["message"] = "Login successful";
        response["data"]["token"] = token;
        response["data"]["user_id"] = userId;
        response["data"]["username"] = string(user["username"].get_string().value);
        response["data"]["display_name"] = string(user["display_name"].get_string().value);
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Login error: ") + e.what();
    }
    
    return response;
}

// ĐĂNG XUẤT
Json::Value AuthHandler::handleLogout(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Lấy token
        if (!request.isMember("token")) {
            response["success"] = false;
            response["error"] = "Token required";
            return response;
        }
        
        string token = request["token"].asString();
        string userId = getUserIdFromToken(token);
        
        if (userId.empty()) {
            response["success"] = false;
            response["error"] = "Invalid or expired token";
            return response;
        }
        
        // 2. Update is_online = false
        auto db = mongoClient.getDatabase();
        auto users = db["users"];
        
        users.update_one(
            document{} << "_id" << bsoncxx::oid(userId) << finalize,
            document{} << "$set" << bsoncxx::builder::stream::open_document
                      << "is_online" << false
                      << bsoncxx::builder::stream::close_document
                      << finalize
        );
        
        // 3. Xóa session khỏi Redis
        redisClient.deleteSession(token);
        
        response["success"] = true;
        response["message"] = "Logout successful";
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Logout error: ") + e.what();
    }
    
    return response;
}

// CẬP NHẬT PROFILE
Json::Value AuthHandler::handleUpdateProfile(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate token
        if (!request.isMember("token")) {
            response["success"] = false;
            response["error"] = "Token required";
            return response;
        }
        
        string token = request["token"].asString();
        string userId = getUserIdFromToken(token);
        
        if (userId.empty()) {
            response["success"] = false;
            response["error"] = "Invalid or expired token";
            return response;
        }
        
        // 2. Build update document
        auto db = mongoClient.getDatabase();
        auto users = db["users"];
        
        document updateBuilder{};
        bool hasUpdate = false;
        
        // Display name
        if (request.isMember("display_name")) {
            string displayName = request["display_name"].asString();
            if (!displayName.empty()) {
                updateBuilder << "display_name" << displayName;
                hasUpdate = true;
            }
        }
        
        // Avatar
        if (request.isMember("avatar_id")) {
            int avatarId = request["avatar_id"].asInt();
            if (isValidAvatarId(avatarId)) {
                string avatarPath = "resources/avatars/image" + to_string(avatarId) + ".jpg";
                updateBuilder << "avatar_url" << avatarPath;
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
        
        // 3. Update database
        auto result = users.update_one(
            document{} << "_id" << bsoncxx::oid(userId) << finalize,
            document{} << "$set" << updateBuilder << finalize
        );
        
        if (!result || result->modified_count() == 0) {
            response["success"] = false;
            response["error"] = "Failed to update profile";
            return response;
        }
        
        response["success"] = true;
        response["message"] = "Profile updated successfully";
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Update profile error: ") + e.what();
    }
    
    return response;
}

// LẤY DANH SÁCH AVATAR
Json::Value AuthHandler::handleGetAvatars(const Json::Value& request) {
    Json::Value response;
    
    try {
        response["success"] = true;
        response["data"] = Json::Value(Json::arrayValue);
        
        // Trả về 10 avatar
        for (int id = 1; id <= 10; id++) {
            Json::Value avatar;
            avatar["id"] = id;
            avatar["filename"] = "image" + to_string(id) + ".jpg";
            avatar["path"] = "resources/avatars/image" + to_string(id) + ".jpg";
            response["data"].append(avatar);
        }
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Get avatars error: ") + e.what();
    }
    
    return response;
}
