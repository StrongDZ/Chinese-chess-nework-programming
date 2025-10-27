#ifndef REDIS_CLIENT_H
#define REDIS_CLIENT_H

#include <hiredis/hiredis.h>
#include <string>
#include <json/json.h>

class RedisClient {
private:
    redisContext* context;
    std::string host;
    int port;

public:
    RedisClient();
    ~RedisClient();
    
    // Connect to Redis
    bool connect(const std::string& host = "127.0.0.1", int port = 6379);
    bool isConnected() const;
    
    //  SESSION METHODS 
    bool saveSession(const std::string& token, const std::string& userId, int ttl = 86400);
    std::string getSession(const std::string& token);
    bool renewSession(const std::string& token, int ttl = 86400);
    bool deleteSession(const std::string& token);
    
    //  CHALLENGE METHODS 
    bool saveChallenge(const std::string& challengedUserId, 
                        const std::string& challengeId,
                        const Json::Value& challengeData,
                        int ttl = 300);
    Json::Value getChallenge(const std::string& challengedUserId, 
                            const std::string& challengeId);
    bool deleteChallenge(const std::string& challengedUserId, 
                        const std::string& challengeId);
    
    //  GAME MESSAGE METHODS 
    bool addGameMessage(const std::string& gameId, const Json::Value& message);
    bool deleteGameMessages(const std::string& gameId);
    
    //  UTILITY 
    bool ping();
};

#endif // REDIS_CLIENT_H
