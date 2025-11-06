#include "../../include/database/redis_client.h"
#include <iostream>
#include <cstring>

using namespace std;

RedisClient::RedisClient() : context(nullptr) {}

RedisClient::~RedisClient() {
    if (context) {
        redisFree(context);
        context = nullptr;
    }
}

bool RedisClient::connect(const string& h, int p, const string& password, int db) {
    host = h;
    port = p;
    
    // Kết nối Redis
    context = redisConnect(host.c_str(), port);
    
    if (context == nullptr || context->err) {
        if (context) {
            cerr << " Redis connection error: " << context->errstr << endl;
            redisFree(context);
            context = nullptr;
        } else {
            cerr << " Redis connection error: can't allocate context" << endl;
        }
        return false;
    }
    
    // Authenticate nếu có password
    if (!password.empty()) {
        redisReply* reply = (redisReply*)redisCommand(context, "AUTH %s", password.c_str());
        if (reply == nullptr || reply->type == REDIS_REPLY_ERROR) {
            cerr << " Redis auth failed" << endl;
            if (reply) freeReplyObject(reply);
            return false;
        }
        freeReplyObject(reply);
    }
    
    // Chọn database
    redisReply* reply = (redisReply*)redisCommand(context, "SELECT %d", db);
    if (reply == nullptr || reply->type == REDIS_REPLY_ERROR) {
        cerr << " Redis select db failed" << endl;
        if (reply) freeReplyObject(reply);
        return false;
    }
    freeReplyObject(reply);
    
    cout << " Redis connected: " << host << ":" << port << endl;
    return true;
}

bool RedisClient::isConnected() const {
    return context != nullptr && context->err == 0;
}

bool RedisClient::ping() {
    if (!isConnected()) return false;
    
    redisReply* reply = (redisReply*)redisCommand(context, "PING");
    if (reply == nullptr || reply->type == REDIS_REPLY_ERROR) {
        if (reply) freeReplyObject(reply);
        return false;
    }
    
    bool success = (strcmp(reply->str, "PONG") == 0);
    freeReplyObject(reply);
    return success;
}

// === SESSION MANAGEMENT ===

bool RedisClient::saveSession(const string& token, const string& userId, int ttl) {
    if (!isConnected()) return false;
    
    string key = "session:" + token;
    redisReply* reply = (redisReply*)redisCommand(context, 
        "SETEX %s %d %s", key.c_str(), ttl, userId.c_str());
    
    bool success = (reply != nullptr && reply->type != REDIS_REPLY_ERROR);
    if (reply) freeReplyObject(reply);
    return success;
}

string RedisClient::getSession(const string& token) {
    if (!isConnected()) return "";
    
    string key = "session:" + token;
    redisReply* reply = (redisReply*)redisCommand(context, "GET %s", key.c_str());
    
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
    redisReply* reply = (redisReply*)redisCommand(context, 
        "EXPIRE %s %d", key.c_str(), ttl);
    
    bool success = (reply != nullptr && reply->integer == 1);
    if (reply) freeReplyObject(reply);
    return success;
}

bool RedisClient::deleteSession(const string& token) {
    if (!isConnected()) return false;
    
    string key = "session:" + token;
    redisReply* reply = (redisReply*)redisCommand(context, "DEL %s", key.c_str());
    
    bool success = (reply != nullptr && reply->integer > 0);
    if (reply) freeReplyObject(reply);
    return success;
}

// === CHALLENGE CACHE ===

bool RedisClient::saveChallenge(const string& challengedUserId, 
                                const string& challengeId,
                                const Json::Value& challengeData,
                                int ttl) {
    if (!isConnected()) return false;
    
    Json::StreamWriterBuilder writer;
    string jsonStr = Json::writeString(writer, challengeData);
    
    string key = "challenge:" + challengedUserId + ":" + challengeId;
    redisReply* reply = (redisReply*)redisCommand(context,
        "SETEX %s %d %s", key.c_str(), ttl, jsonStr.c_str());
    
    bool success = (reply != nullptr && reply->type != REDIS_REPLY_ERROR);
    if (reply) freeReplyObject(reply);
    return success;
}

Json::Value RedisClient::getChallenge(const string& challengedUserId, 
                                      const string& challengeId) {
    Json::Value result;
    if (!isConnected()) return result;
    
    string key = "challenge:" + challengedUserId + ":" + challengeId;
    redisReply* reply = (redisReply*)redisCommand(context, "GET %s", key.c_str());
    
    if (reply && reply->type == REDIS_REPLY_STRING) {
        Json::CharReaderBuilder reader;
        string errors;
        istringstream iss(reply->str);
        Json::parseFromStream(reader, iss, &result, &errors);
    }
    
    if (reply) freeReplyObject(reply);
    return result;
}

bool RedisClient::deleteChallenge(const string& challengedUserId, 
                                  const string& challengeId) {
    if (!isConnected()) return false;
    
    string key = "challenge:" + challengedUserId + ":" + challengeId;
    redisReply* reply = (redisReply*)redisCommand(context, "DEL %s", key.c_str());
    
    bool success = (reply != nullptr && reply->integer > 0);
    if (reply) freeReplyObject(reply);
    return success;
}

// === GAME MESSAGES ===

bool RedisClient::addGameMessage(const string& gameId, const Json::Value& message) {
    if (!isConnected()) return false;
    
    Json::StreamWriterBuilder writer;
    string jsonStr = Json::writeString(writer, message);
    
    string key = "game:messages:" + gameId;
    redisReply* reply = (redisReply*)redisCommand(context,
        "RPUSH %s %s", key.c_str(), jsonStr.c_str());
    
    bool success = (reply != nullptr && reply->type == REDIS_REPLY_INTEGER);
    if (reply) freeReplyObject(reply);
    return success;
}

bool RedisClient::deleteGameMessages(const string& gameId) {
    if (!isConnected()) return false;
    
    string key = "game:messages:" + gameId;
    redisReply* reply = (redisReply*)redisCommand(context, "DEL %s", key.c_str());
    
    bool success = (reply != nullptr);
    if (reply) freeReplyObject(reply);
    return success;
}

// === GENERIC REDIS OPERATIONS ===

// SET key value với TTL (giây)
bool RedisClient::set(const string& key, const string& value, int ttl) {
    if (!isConnected()) return false;
    
    redisReply* reply = nullptr;
    
    if (ttl > 0) {
        reply = (redisReply*)redisCommand(context, "SETEX %s %d %s", 
                                         key.c_str(), ttl, value.c_str());
    } else {
        reply = (redisReply*)redisCommand(context, "SET %s %s", 
                                         key.c_str(), value.c_str());
    }
    
    bool success = (reply != nullptr && reply->type == REDIS_REPLY_STATUS);
    if (reply) freeReplyObject(reply);
    return success;
}

// GET key
string RedisClient::get(const string& key) {
    if (!isConnected()) return "";
    
    redisReply* reply = (redisReply*)redisCommand(context, "GET %s", key.c_str());
    
    string value = "";
    if (reply && reply->type == REDIS_REPLY_STRING) {
        value = string(reply->str, reply->len);
    }
    
    if (reply) freeReplyObject(reply);
    return value;
}

// DEL key
bool RedisClient::del(const string& key) {
    if (!isConnected()) return false;
    
    redisReply* reply = (redisReply*)redisCommand(context, "DEL %s", key.c_str());
    
    bool success = (reply != nullptr && reply->type == REDIS_REPLY_INTEGER);
    if (reply) freeReplyObject(reply);
    return success;
}

// PUBLISH message vào channel (pub/sub cho real-time notifications)
bool RedisClient::publish(const string& channel, const string& message) {
    if (!isConnected()) return false;
    
    redisReply* reply = (redisReply*)redisCommand(context, 
        "PUBLISH %s %s", channel.c_str(), message.c_str());
    
    bool success = (reply != nullptr && reply->type == REDIS_REPLY_INTEGER);
    if (reply) freeReplyObject(reply);
    return success;
}

// HSET - Set field trong hash
bool RedisClient::hset(const string& key, const string& field, const string& value) {
    if (!isConnected()) return false;
    
    redisReply* reply = (redisReply*)redisCommand(context, 
        "HSET %s %s %s", key.c_str(), field.c_str(), value.c_str());
    
    bool success = (reply != nullptr && reply->type == REDIS_REPLY_INTEGER);
    if (reply) freeReplyObject(reply);
    return success;
}

// HGET - Get field từ hash
string RedisClient::hget(const string& key, const string& field) {
    if (!isConnected()) return "";
    
    redisReply* reply = (redisReply*)redisCommand(context, 
        "HGET %s %s", key.c_str(), field.c_str());
    
    string value = "";
    if (reply && reply->type == REDIS_REPLY_STRING) {
        value = string(reply->str, reply->len);
    }
    
    if (reply) freeReplyObject(reply);
    return value;
}

// HGETALL - Get tất cả fields từ hash
map<string, string> RedisClient::hgetall(const string& key) {
    if (!isConnected()) return {};
    
    redisReply* reply = (redisReply*)redisCommand(context, "HGETALL %s", key.c_str());
    
    map<string, string> result;
    if (reply && reply->type == REDIS_REPLY_ARRAY) {
        for (size_t i = 0; i < reply->elements; i += 2) {
            if (i + 1 < reply->elements) {
                string field(reply->element[i]->str, reply->element[i]->len);
                string value(reply->element[i + 1]->str, reply->element[i + 1]->len);
                result[field] = value;
            }
        }
    }
    
    if (reply) freeReplyObject(reply);
    return result;
}

// EXPIRE - Set TTL cho key (seconds)
bool RedisClient::expire(const string& key, int ttl) {
    if (!isConnected()) return false;
    
    redisReply* reply = (redisReply*)redisCommand(context, 
        "EXPIRE %s %d", key.c_str(), ttl);
    
    bool success = (reply != nullptr && reply->type == REDIS_REPLY_INTEGER);
    if (reply) freeReplyObject(reply);
    return success;
}

// SADD - Thêm vào set
bool RedisClient::sadd(const string& key, const string& member) {
    if (!isConnected()) return false;
    
    redisReply* reply = (redisReply*)redisCommand(context, 
        "SADD %s %s", key.c_str(), member.c_str());
    
    bool success = (reply != nullptr && reply->type == REDIS_REPLY_INTEGER);
    if (reply) freeReplyObject(reply);
    return success;
}

// SMEMBERS - Lấy tất cả members từ set
vector<string> RedisClient::smembers(const string& key) {
    if (!isConnected()) return {};
    
    redisReply* reply = (redisReply*)redisCommand(context, "SMEMBERS %s", key.c_str());
    
    vector<string> members;
    if (reply && reply->type == REDIS_REPLY_ARRAY) {
        for (size_t i = 0; i < reply->elements; i++) {
            if (reply->element[i]->type == REDIS_REPLY_STRING) {
                members.push_back(string(reply->element[i]->str, reply->element[i]->len));
            }
        }
    }
    
    if (reply) freeReplyObject(reply);
    return members;
}

// SREM - Xóa member từ set
bool RedisClient::srem(const string& key, const string& member) {
    if (!isConnected()) return false;
    
    redisReply* reply = (redisReply*)redisCommand(context, 
        "SREM %s %s", key.c_str(), member.c_str());
    
    bool success = (reply != nullptr && reply->type == REDIS_REPLY_INTEGER);
    if (reply) freeReplyObject(reply);
    return success;
}

// === UTILITY ===
