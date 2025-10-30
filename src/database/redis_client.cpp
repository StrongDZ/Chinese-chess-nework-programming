#include "../../include/database/redis_client.h"
#include <iostream>
#include <sstream>
using namespace std;

RedisClient::RedisClient() : context(nullptr), host("127.0.0.1"), port(6379) {
}

RedisClient::~RedisClient() {
    if (context) {
        redisFree(context);
        context = nullptr;
    }
}

bool RedisClient::connect(const string& h, int p, const string& password, int db) {
    host = h;
    port = p;
    
    context = redisConnect(host.c_str(), port);
    
    if (context == nullptr || context->err) {
        if (context) {
            cerr << "✗ Redis connection error: " << context->errstr << endl;
            redisFree(context);
            context = nullptr;
        } else {
            cerr << "✗ Redis connection error: can't allocate context" << endl;
        }
        return false;
    }
    
    // Authenticate if password is provided
    if (!password.empty()) {
        redisReply* authReply = (redisReply*)redisCommand(context, "AUTH %s", password.c_str());
        if (authReply == nullptr || authReply->type == REDIS_REPLY_ERROR) {
            cerr << "✗ Redis authentication failed" << endl;
            if (authReply) freeReplyObject(authReply);
            redisFree(context);
            context = nullptr;
            return false;
        }
        freeReplyObject(authReply);
    }
    
    // Select database if specified
    if (db != 0) {
        redisReply* selectReply = (redisReply*)redisCommand(context, "SELECT %d", db);
        if (selectReply == nullptr || selectReply->type == REDIS_REPLY_ERROR) {
            cerr << "✗ Redis database selection failed" << endl;
            if (selectReply) freeReplyObject(selectReply);
            redisFree(context);
            context = nullptr;
            return false;
        }
        freeReplyObject(selectReply);
    }
    
    cout << " Connected to Redis at " << host << ":" << port << " (DB: " << db << ")" << endl;
    return true;
}

bool RedisClient::isConnected() const {
    return context != nullptr && context->err == 0;
}

bool RedisClient::ping() {
    if (!isConnected()) {
        return false;
    }
    
    redisReply* reply = (redisReply*)redisCommand(context, "PING");
    if (reply == nullptr) {
        return false;
    }
    
    bool success = (reply->type == REDIS_REPLY_STATUS && 
                string(reply->str) == "PONG");
    
    freeReplyObject(reply);
    return success;
}

//  SESSION METHODS 

bool RedisClient::saveSession(const string& token, const string& userId, int ttl) {
    if (!isConnected()) return false;
    
    string key = "session:" + token;
    redisReply* reply = (redisReply*)redisCommand(
        context, "SETEX %s %d %s",
        key.c_str(), ttl, userId.c_str()
    );
    
    bool success = (reply && reply->type != REDIS_REPLY_ERROR);
    if (reply) freeReplyObject(reply);
    return success;
}

string RedisClient::getSession(const string& token) {
    if (!isConnected()) return "";
    
    string key = "session:" + token;
    redisReply* reply = (redisReply*)redisCommand(
        context, "GET %s", key.c_str()
    );
    
    string userId;
    if (reply && reply->type == REDIS_REPLY_STRING) {
        userId = reply->str;
    }
    
    if (reply) freeReplyObject(reply);
    return userId;
}

bool RedisClient::renewSession(const string& token, int ttl) {
    if (!isConnected()) return false;
    
    string key = "session:" + token;
    redisReply* reply = (redisReply*)redisCommand(
        context, "EXPIRE %s %d",
        key.c_str(), ttl
    );
    
    bool success = (reply && reply->integer == 1);
    if (reply) freeReplyObject(reply);
    return success;
}

bool RedisClient::deleteSession(const string& token) {
    if (!isConnected()) return false;
    
    string key = "session:" + token;
    redisReply* reply = (redisReply*)redisCommand(
        context, "DEL %s", key.c_str()
    );
    
    bool success = (reply && reply->integer == 1);
    if (reply) freeReplyObject(reply);
    return success;
}

//  CHALLENGE METHODS 

bool RedisClient::saveChallenge(const string& challengedUserId, 
                            const string& challengeId,
                            const Json::Value& challengeData,
                            int ttl) {
    if (!isConnected()) return false;
    
    Json::StreamWriterBuilder writer;
    string jsonStr = Json::writeString(writer, challengeData);
    
    string key = "challenge:" + challengedUserId + ":" + challengeId;
    redisReply* reply = (redisReply*)redisCommand(
        context, "SETEX %s %d %s",
        key.c_str(), ttl, jsonStr.c_str()
    );
    
    bool success = (reply && reply->type != REDIS_REPLY_ERROR);
    if (reply) freeReplyObject(reply);
    return success;
}

Json::Value RedisClient::getChallenge(const string& challengedUserId, 
                                        const string& challengeId) {
    Json::Value challenge;
    if (!isConnected()) return challenge;
    
    string key = "challenge:" + challengedUserId + ":" + challengeId;
    redisReply* reply = (redisReply*)redisCommand(
        context, "GET %s", key.c_str()
    );
    
    if (reply && reply->type == REDIS_REPLY_STRING) {
        Json::CharReaderBuilder reader;
        string errors;
        istringstream stream(reply->str);
        Json::parseFromStream(reader, stream, &challenge, &errors);
    }
    
    if (reply) freeReplyObject(reply);
    return challenge;
}

bool RedisClient::deleteChallenge(const string& challengedUserId, 
                                    const string& challengeId) {
    if (!isConnected()) return false;
    
    string key = "challenge:" + challengedUserId + ":" + challengeId;
    redisReply* reply = (redisReply*)redisCommand(
        context, "DEL %s", key.c_str()
    );
    
    bool success = (reply && reply->integer == 1);
    if (reply) freeReplyObject(reply);
    return success;
}

//  GAME MESSAGE METHODS 

bool RedisClient::addGameMessage(const string& gameId, const Json::Value& message) {
    if (!isConnected()) return false;
    
    Json::StreamWriterBuilder writer;
    string jsonStr = Json::writeString(writer, message);
    
    string key = "game:" + gameId + ":messages";
    redisReply* reply = (redisReply*)redisCommand(
        context, "LPUSH %s %s",
        key.c_str(), jsonStr.c_str()
    );
    
    bool success = (reply && reply->type != REDIS_REPLY_ERROR);
    if (reply) freeReplyObject(reply);
    return success;
}

bool RedisClient::deleteGameMessages(const string& gameId) {
    if (!isConnected()) return false;
    
    string key = "game:" + gameId + ":messages";
    redisReply* reply = (redisReply*)redisCommand(
        context, "DEL %s", key.c_str()
    );
    
    bool success = (reply && reply->integer >= 0); // DEL returns 0 if key doesn't exist (OK)
    if (reply) freeReplyObject(reply);
    return success;
}
