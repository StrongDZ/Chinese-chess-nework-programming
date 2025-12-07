#ifndef AUTH_REPOSITORY_H
#define AUTH_REPOSITORY_H

#include "../database/mongodb_client.h"
#include <string>
#include <optional>

// User model đơn giản
struct User {
    std::string username;
    std::string password_hash;
    int avatar_id;
    bool is_online;
    std::string status;
};

class AuthRepository {
private:
    MongoDBClient& mongoClient;

public:
    explicit AuthRepository(MongoDBClient& mongo);
    
    // === User Operations (MongoDB) ===
    
    // Tìm user theo username
    std::optional<User> findByUsername(const std::string& username);
    
    // Check username đã tồn tại chưa
    bool usernameExists(const std::string& username);
    
    // Tạo user mới, return username nếu thành công
    std::string createUser(const std::string& username, 
                           const std::string& passwordHash, 
                           int avatarId);
    
    // Update avatar (by username - vì không còn token)
    bool updateAvatar(const std::string& username, int avatarId);
    
    // Update online status (by username)
    bool updateOnlineStatus(const std::string& username, bool isOnline);
    
    // Update last login (by username)
    bool updateLastLogin(const std::string& username);
    
    // === Player Stats (MongoDB) ===
    
    // Tạo stats mặc định cho user mới (3 time controls)
    void createDefaultStats(const std::string& username);
};

#endif
