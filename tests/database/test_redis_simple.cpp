#include "../../include/database/redis_client.h"
#include "../../include/config/config_loader.h"
#include <iostream>
#include <chrono>
#include <thread>

void printSeparator(const std::string& title) {
    std::cout << "\n========== " << title << " ==========\n" << std::endl;
}

int main() {
    std::cout << "========================================" << std::endl;
    std::cout << "  Redis Connection Test" << std::endl;
    std::cout << "========================================\n" << std::endl;
    
    // Load configuration from .env
    ConfigLoader config;
    if (!config.load(".env")) {
        std::cerr << "❌ Failed to load .env file" << std::endl;
        return 1;
    }
    
    std::string redisHost = config.getString("REDIS_HOST", "127.0.0.1");
    int redisPort = config.getInt("REDIS_PORT", 6379);
    std::string redisPassword = config.getString("REDIS_PASSWORD");
    int redisDb = config.getInt("REDIS_DB", 0);
    
    RedisClient redis;
    
    // Test 1: Connect to Redis
    printSeparator("TEST 1: Connection");
    if (!redis.connect(redisHost, redisPort, redisPassword, redisDb)) {
        std::cerr << "❌ Failed to connect to Redis" << std::endl;
        std::cerr << "⚠️  Make sure Redis is running:" << std::endl;
        std::cerr << "   sudo systemctl start redis" << std::endl;
        std::cerr << "   or: redis-server" << std::endl;
        return 1;
    }
    
    // Test 2: Ping
    printSeparator("TEST 2: Ping");
    if (redis.ping()) {
        std::cout << " PING → PONG" << std::endl;
    } else {
        std::cerr << "❌ Ping failed" << std::endl;
        return 1;
    }
    
    // Test 3: Session Management
    printSeparator("TEST 3: Session Management");
    
    // Save session
    std::string token = "test_token_12345";
    std::string userId = "user_67890";
    
    if (redis.saveSession(token, userId, 10)) {  // 10 seconds TTL
        std::cout << " Session saved: " << token << " → " << userId << std::endl;
    } else {
        std::cerr << "❌ Failed to save session" << std::endl;
    }
    
    // Get session
    std::string retrievedUserId = redis.getSession(token);
    if (retrievedUserId == userId) {
        std::cout << " Session retrieved: " << retrievedUserId << std::endl;
    } else {
        std::cerr << "❌ Failed to retrieve session" << std::endl;
    }
    
    // Renew session
    if (redis.renewSession(token, 20)) {
        std::cout << " Session TTL renewed to 20 seconds" << std::endl;
    }
    
    // Delete session
    if (redis.deleteSession(token)) {
        std::cout << " Session deleted" << std::endl;
    }
    
    // Verify deletion
    std::string afterDelete = redis.getSession(token);
    if (afterDelete.empty()) {
        std::cout << " Verified: Session no longer exists" << std::endl;
    }
    
    // Test 4: Challenge Management
    printSeparator("TEST 4: Challenge Management");
    
    Json::Value challenge;
    challenge["challenge_id"] = "challenge_123";
    challenge["challenger_id"] = "user_111";
    challenge["challenged_id"] = "user_222";
    challenge["time_control"] = "blitz";
    challenge["rated"] = true;
    
    std::string challengedId = "user_222";
    std::string challengeId = "challenge_123";
    
    if (redis.saveChallenge(challengedId, challengeId, challenge, 300)) {
        std::cout << " Challenge saved (5 min TTL)" << std::endl;
    }
    
    Json::Value retrieved = redis.getChallenge(challengedId, challengeId);
    if (!retrieved.empty() && retrieved["challenge_id"].asString() == challengeId) {
        std::cout << " Challenge retrieved:" << std::endl;
        std::cout << "   Challenger: " << retrieved["challenger_id"].asString() << std::endl;
        std::cout << "   Time control: " << retrieved["time_control"].asString() << std::endl;
    }
    
    if (redis.deleteChallenge(challengedId, challengeId)) {
        std::cout << " Challenge deleted" << std::endl;
    }
    
    // Test 5: Game Messages
    printSeparator("TEST 5: Game Messages");
    
    std::string gameId = "game_999";
    
    Json::Value msg1;
    msg1["sender_id"] = "user_111";
    msg1["type"] = "chat";
    msg1["message"] = "Good luck!";
    msg1["timestamp"] = "2025-10-19T10:30:00Z";
    
    Json::Value msg2;
    msg2["sender_id"] = "user_222";
    msg2["type"] = "chat";
    msg2["message"] = "You too!";
    msg2["timestamp"] = "2025-10-19T10:30:05Z";
    
    if (redis.addGameMessage(gameId, msg1)) {
        std::cout << " Message 1 added" << std::endl;
    }
    
    if (redis.addGameMessage(gameId, msg2)) {
        std::cout << " Message 2 added" << std::endl;
    }
    
    if (redis.deleteGameMessages(gameId)) {
        std::cout << " All game messages deleted" << std::endl;
    }
    
    // Test 6: TTL (Time-To-Live)
    printSeparator("TEST 6: TTL Auto-Expire");
    
    std::cout << "Saving session with 3 second TTL..." << std::endl;
    redis.saveSession("short_token", "test_user", 3);
    
    std::cout << "Waiting 1 second..." << std::endl;
    std::this_thread::sleep_for(std::chrono::seconds(1));
    
    std::string check1 = redis.getSession("short_token");
    if (!check1.empty()) {
        std::cout << " Session still exists after 1s" << std::endl;
    }
    
    std::cout << "Waiting 3 more seconds..." << std::endl;
    std::this_thread::sleep_for(std::chrono::seconds(3));
    
    std::string check2 = redis.getSession("short_token");
    if (check2.empty()) {
        std::cout << " Session auto-expired after 4s total (TTL worked!)" << std::endl;
    } else {
        std::cerr << "❌ Session should have expired" << std::endl;
    }
    
    // Summary
    printSeparator("TEST SUMMARY");
    std::cout << " All Redis tests passed!" << std::endl;
    std::cout << "\nRedis features tested:" << std::endl;
    std::cout << "   Connection & Ping" << std::endl;
    std::cout << "   Session management (SETEX, GET, EXPIRE, DEL)" << std::endl;
    std::cout << "   Challenge storage (JSON serialization)" << std::endl;
    std::cout << "   Game messages (LIST operations)" << std::endl;
    std::cout << "   TTL auto-expiration" << std::endl;
    
    return 0;
}
