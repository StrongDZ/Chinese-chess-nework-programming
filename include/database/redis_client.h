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
    
    // === GENERIC REDIS OPERATIONS ===
    // SET key value với TTL
    bool set(const std::string& key, const std::string& value, int ttl = 0);
    
    // GET key
    std::string get(const std::string& key);
    
    // DEL key
    bool del(const std::string& key);
    
    // PUBLISH message vào channel (pub/sub)
    bool publish(const std::string& channel, const std::string& message);
    
    // HSET - Set field trong hash
    bool hset(const std::string& key, const std::string& field, const std::string& value);
    
    // HGET - Get field từ hash
    std::string hget(const std::string& key, const std::string& field);
    
    // HGETALL - Get tất cả fields từ hash
    std::map<std::string, std::string> hgetall(const std::string& key);
    
    // EXPIRE - Set TTL cho key
    bool expire(const std::string& key, int ttl);
    
    // SADD - Thêm vào set
    bool sadd(const std::string& key, const std::string& member);
    
    // SMEMBERS - Lấy tất cả members từ set
    std::vector<std::string> smembers(const std::string& key);
    
    // SREM - Xóa member từ set
    bool srem(const std::string& key, const std::string& member);
    
    // === UTILITY ===
    bool ping();
};

#endif
