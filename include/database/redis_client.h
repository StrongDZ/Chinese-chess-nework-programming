#ifndef REDIS_CLIENT_H
#define REDIS_CLIENT_H

#include <hiredis/hiredis.h>
#include <string>
#include <json/json.h>

// Quản lý kết nối Redis (dùng cho session, cache, real-time data)
class RedisClient {
private:
    redisContext* context;
    std::string host;
    int port;

public:
    RedisClient();
    ~RedisClient();
    
    // Kết nối tới Redis server
    bool connect(const std::string& host = "127.0.0.1", 
                int port = 6379, 
                const std::string& password = "",
                int db = 0);
    
    bool isConnected() const;
    
    // === SESSION MANAGEMENT ===
    // Lưu token -> userId, TTL mặc định 24h
    bool saveSession(const std::string& token, const std::string& userId, int ttl = 86400);
    
    // Lấy userId từ token
    std::string getSession(const std::string& token);
    
    // Gia hạn session
    bool renewSession(const std::string& token, int ttl = 86400);
    
    // Xóa session (logout)
    bool deleteSession(const std::string& token);
    
    // === CHALLENGE CACHE ===
    // Lưu thông tin challenge tạm thời (TTL 5 phút)
    bool saveChallenge(const std::string& challengedUserId, 
                        const std::string& challengeId,
                        const Json::Value& challengeData,
                        int ttl = 300);
    
    Json::Value getChallenge(const std::string& challengedUserId, 
                            const std::string& challengeId);
    
    bool deleteChallenge(const std::string& challengedUserId, 
                        const std::string& challengeId);
    
    // === GAME MESSAGES ===
    // Lưu tin nhắn trong game (chat, draw offers, etc)
    bool addGameMessage(const std::string& gameId, const Json::Value& message);
    
    // Xóa tất cả messages của game
    bool deleteGameMessages(const std::string& gameId);
    
    // === UTILITY ===
    bool ping();
};

#endif
