#include "../../include/challenge/challenge_handler.h"
#include <iostream>
#include <sstream>
#include <iomanip>
#include <chrono>
#include <ctime>

using namespace std;

ChallengeHandler::ChallengeHandler(RedisClient& redis) 
    : redisClient(redis) {}

// Helper: Lấy userId từ token
string ChallengeHandler::getUserIdFromToken(const string& token) {
    if (token.empty()) return "";
    return redisClient.getSession(token);
}

// Helper: Validate time_control
bool ChallengeHandler::isValidTimeControl(const string& timeControl) {
    // Các time control hợp lệ: "blitz", "rapid", "classical", "bullet"
    return (timeControl == "blitz" || 
            timeControl == "rapid" || 
            timeControl == "classical" || 
            timeControl == "bullet");
}

// Helper: Validate rated flag
bool ChallengeHandler::isValidRated(bool rated) {
    return true; // rated có thể là true hoặc false
}

// Helper: Load challenge từ Redis
Json::Value ChallengeHandler::loadChallenge(const string& challengeId) {
    Json::Value challenge;
    
    // Lấy challenge data từ Redis hash
    string key = "challenge:" + challengeId;
    auto fields = redisClient.hgetall(key);
    
    if (fields.empty()) {
        return challenge; // Trả về empty JSON nếu không tìm thấy
    }
    
    // Parse các fields thành JSON
    challenge["challenge_id"] = challengeId;
    challenge["challenger_id"] = fields["challenger_id"];
    challenge["challenged_id"] = fields["challenged_id"];
    challenge["time_control"] = fields["time_control"];
    challenge["rated"] = (fields["rated"] == "true");
    challenge["status"] = fields["status"];
    challenge["created_at"] = fields["created_at"];
    
    return challenge;
}

// Helper: Persist challenge vào Redis
bool ChallengeHandler::persistChallenge(const Json::Value& challenge, int ttlSeconds) {
    string challengeId = challenge["challenge_id"].asString();
    string key = "challenge:" + challengeId;
    
    // Lưu các fields vào hash
    bool success = true;
    success &= redisClient.hset(key, "challenge_id", challengeId);
    success &= redisClient.hset(key, "challenger_id", challenge["challenger_id"].asString());
    success &= redisClient.hset(key, "challenged_id", challenge["challenged_id"].asString());
    success &= redisClient.hset(key, "time_control", challenge["time_control"].asString());
    success &= redisClient.hset(key, "rated", challenge["rated"].asBool() ? "true" : "false");
    success &= redisClient.hset(key, "status", challenge["status"].asString());
    success &= redisClient.hset(key, "created_at", challenge["created_at"].asString());
    
    // Set TTL cho key
    if (success && ttlSeconds > 0) {
        success = redisClient.expire(key, ttlSeconds);
    }
    
    return success;
}

// Helper: Index challenge để có thể query
void ChallengeHandler::indexChallenge(const Json::Value& challenge) {
    string challengeId = challenge["challenge_id"].asString();
    string challengerId = challenge["challenger_id"].asString();
    string challengedId = challenge["challenged_id"].asString();
    string status = challenge["status"].asString();
    
    // Index theo challenger
    redisClient.sadd("challenges:by_challenger:" + challengerId, challengeId);
    
    // Index theo challenged user
    redisClient.sadd("challenges:by_challenged:" + challengedId, challengeId);
    
    // Index theo status (pending, accepted, declined, cancelled)
    redisClient.sadd("challenges:by_status:" + status, challengeId);
    
    // Index tất cả challenges
    redisClient.sadd("challenges:all", challengeId);
}

// Helper: Xóa indexes của challenge
void ChallengeHandler::removeChallengeIndexes(const Json::Value& challenge) {
    string challengeId = challenge["challenge_id"].asString();
    string challengerId = challenge["challenger_id"].asString();
    string challengedId = challenge["challenged_id"].asString();
    string status = challenge["status"].asString();
    
    // Xóa khỏi các indexes
    redisClient.srem("challenges:by_challenger:" + challengerId, challengeId);
    redisClient.srem("challenges:by_challenged:" + challengedId, challengeId);
    redisClient.srem("challenges:by_status:" + status, challengeId);
    redisClient.srem("challenges:all", challengeId);
}

// Helper: Build challenge summary
Json::Value ChallengeHandler::buildChallengeSummary(const Json::Value& challenge) const {
    Json::Value summary;
    summary["challenge_id"] = challenge["challenge_id"];
    summary["challenger_id"] = challenge["challenger_id"];
    summary["challenged_id"] = challenge["challenged_id"];
    summary["time_control"] = challenge["time_control"];
    summary["rated"] = challenge["rated"];
    summary["status"] = challenge["status"];
    summary["created_at"] = challenge["created_at"];
    return summary;
}

// Tạo thách đấu mới
Json::Value ChallengeHandler::handleCreateChallenge(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate request
        if (!request.isMember("token") || !request.isMember("challenged_id")) {
            response["status"] = "error";
            response["message"] = "Missing required fields: token, challenged_id";
            return response;
        }
        
        // 2. Get challenger từ token
        string token = request["token"].asString();
        string challengerId = getUserIdFromToken(token);
        
        if (challengerId.empty()) {
            response["status"] = "error";
            response["message"] = "Invalid or expired token";
            return response;
        }
        
        // 3. Get challenged user
        string challengedId = request["challenged_id"].asString();
        
        if (challengerId == challengedId) {
            response["status"] = "error";
            response["message"] = "Cannot challenge yourself";
            return response;
        }
        
        // 4. Validate optional fields
        string timeControl = request.get("time_control", "blitz").asString();
        bool rated = request.get("rated", true).asBool();
        
        if (!isValidTimeControl(timeControl)) {
            response["status"] = "error";
            response["message"] = "Invalid time_control. Must be: bullet, blitz, rapid, or classical";
            return response;
        }
        
        // 5. Generate challenge ID (timestamp-based)
        auto now = chrono::system_clock::now();
        auto timestamp = chrono::duration_cast<chrono::milliseconds>(now.time_since_epoch()).count();
        string challengeId = "ch_" + to_string(timestamp) + "_" + challengerId.substr(0, 8);
        
        // 6. Get current time string
        time_t rawtime = chrono::system_clock::to_time_t(now);
        struct tm* timeinfo = localtime(&rawtime);
        char buffer[80];
        strftime(buffer, sizeof(buffer), "%Y-%m-%d %H:%M:%S", timeinfo);
        string createdAt(buffer);
        
        // 7. Create challenge object
        Json::Value challenge;
        challenge["challenge_id"] = challengeId;
        challenge["challenger_id"] = challengerId;
        challenge["challenged_id"] = challengedId;
        challenge["time_control"] = timeControl;
        challenge["rated"] = rated;
        challenge["status"] = "pending";
        challenge["created_at"] = createdAt;
        
        // 8. Persist to Redis với TTL 1 giờ cho pending challenges
        if (!persistChallenge(challenge, PENDING_TTL_SECONDS)) {
            response["status"] = "error";
            response["message"] = "Failed to save challenge to Redis";
            return response;
        }
        
        // 9. Index challenge
        indexChallenge(challenge);
        
        // 10. Publish notification (optional, cho real-time updates)
        Json::StreamWriterBuilder writer;
        string notificationData = Json::writeString(writer, challenge);
        redisClient.publish("challenge:created", notificationData);
        
        // 11. Success response
        response["status"] = "success";
        response["message"] = "Challenge created successfully";
        response["challenge"] = buildChallengeSummary(challenge);
        
    } catch (const exception& e) {
        response["status"] = "error";
        response["message"] = string("Exception: ") + e.what();
    }
    
    return response;
}

// Hủy thách đấu
Json::Value ChallengeHandler::handleCancelChallenge(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate request
        if (!request.isMember("token") || !request.isMember("challenge_id")) {
            response["status"] = "error";
            response["message"] = "Missing required fields: token, challenge_id";
            return response;
        }
        
        // 2. Get user từ token
        string token = request["token"].asString();
        string userId = getUserIdFromToken(token);
        
        if (userId.empty()) {
            response["status"] = "error";
            response["message"] = "Invalid or expired token";
            return response;
        }
        
        // 3. Load challenge
        string challengeId = request["challenge_id"].asString();
        Json::Value challenge = loadChallenge(challengeId);
        
        if (challenge.empty() || !challenge.isMember("challenge_id")) {
            response["status"] = "error";
            response["message"] = "Challenge not found or expired";
            return response;
        }
        
        // 4. Verify user is the challenger
        if (challenge["challenger_id"].asString() != userId) {
            response["status"] = "error";
            response["message"] = "Only the challenger can cancel the challenge";
            return response;
        }
        
        // 5. Check if challenge is still pending
        if (challenge["status"].asString() != "pending") {
            response["status"] = "error";
            response["message"] = "Can only cancel pending challenges";
            return response;
        }
        
        // 6. Update status to cancelled
        challenge["status"] = "cancelled";
        
        // 7. Remove old indexes
        removeChallengeIndexes(loadChallenge(challengeId));
        
        // 8. Persist với TTL ngắn hơn (5 phút)
        persistChallenge(challenge, FINALIZED_TTL_SECONDS);
        
        // 9. Update indexes
        indexChallenge(challenge);
        
        // 10. Publish notification
        Json::StreamWriterBuilder writer;
        string notificationData = Json::writeString(writer, challenge);
        redisClient.publish("challenge:cancelled", notificationData);
        
        // 11. Success response
        response["status"] = "success";
        response["message"] = "Challenge cancelled successfully";
        response["challenge"] = buildChallengeSummary(challenge);
        
    } catch (const exception& e) {
        response["status"] = "error";
        response["message"] = string("Exception: ") + e.what();
    }
    
    return response;
}

// Chấp nhận thách đấu
Json::Value ChallengeHandler::handleAcceptChallenge(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate request
        if (!request.isMember("token") || !request.isMember("challenge_id")) {
            response["status"] = "error";
            response["message"] = "Missing required fields: token, challenge_id";
            return response;
        }
        
        // 2. Get user từ token
        string token = request["token"].asString();
        string userId = getUserIdFromToken(token);
        
        if (userId.empty()) {
            response["status"] = "error";
            response["message"] = "Invalid or expired token";
            return response;
        }
        
        // 3. Load challenge
        string challengeId = request["challenge_id"].asString();
        Json::Value challenge = loadChallenge(challengeId);
        
        if (challenge.empty() || !challenge.isMember("challenge_id")) {
            response["status"] = "error";
            response["message"] = "Challenge not found or expired";
            return response;
        }
        
        // 4. Verify user is the challenged player
        if (challenge["challenged_id"].asString() != userId) {
            response["status"] = "error";
            response["message"] = "Only the challenged player can accept";
            return response;
        }
        
        // 5. Check if challenge is still pending
        if (challenge["status"].asString() != "pending") {
            response["status"] = "error";
            response["message"] = "Challenge is no longer pending";
            return response;
        }
        
        // 6. Update status to accepted
        challenge["status"] = "accepted";
        
        // 7. Remove old indexes
        removeChallengeIndexes(loadChallenge(challengeId));
        
        // 8. Persist với TTL ngắn (5 phút)
        persistChallenge(challenge, FINALIZED_TTL_SECONDS);
        
        // 9. Update indexes
        indexChallenge(challenge);
        
        // 10. Publish notification
        Json::StreamWriterBuilder writer;
        string notificationData = Json::writeString(writer, challenge);
        redisClient.publish("challenge:accepted", notificationData);
        
        // 11. Success response
        response["status"] = "success";
        response["message"] = "Challenge accepted successfully";
        response["challenge"] = buildChallengeSummary(challenge);
        response["next_step"] = "Create game session using this challenge data";
        
    } catch (const exception& e) {
        response["status"] = "error";
        response["message"] = string("Exception: ") + e.what();
    }
    
    return response;
}

// Từ chối thách đấu
Json::Value ChallengeHandler::handleDeclineChallenge(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate request
        if (!request.isMember("token") || !request.isMember("challenge_id")) {
            response["status"] = "error";
            response["message"] = "Missing required fields: token, challenge_id";
            return response;
        }
        
        // 2. Get user từ token
        string token = request["token"].asString();
        string userId = getUserIdFromToken(token);
        
        if (userId.empty()) {
            response["status"] = "error";
            response["message"] = "Invalid or expired token";
            return response;
        }
        
        // 3. Load challenge
        string challengeId = request["challenge_id"].asString();
        Json::Value challenge = loadChallenge(challengeId);
        
        if (challenge.empty() || !challenge.isMember("challenge_id")) {
            response["status"] = "error";
            response["message"] = "Challenge not found or expired";
            return response;
        }
        
        // 4. Verify user is the challenged player
        if (challenge["challenged_id"].asString() != userId) {
            response["status"] = "error";
            response["message"] = "Only the challenged player can decline";
            return response;
        }
        
        // 5. Check if challenge is still pending
        if (challenge["status"].asString() != "pending") {
            response["status"] = "error";
            response["message"] = "Challenge is no longer pending";
            return response;
        }
        
        // 6. Update status to declined
        challenge["status"] = "declined";
        
        // 7. Remove old indexes
        removeChallengeIndexes(loadChallenge(challengeId));
        
        // 8. Persist với TTL ngắn (5 phút)
        persistChallenge(challenge, FINALIZED_TTL_SECONDS);
        
        // 9. Update indexes
        indexChallenge(challenge);
        
        // 10. Publish notification
        Json::StreamWriterBuilder writer;
        string notificationData = Json::writeString(writer, challenge);
        redisClient.publish("challenge:declined", notificationData);
        
        // 11. Success response
        response["status"] = "success";
        response["message"] = "Challenge declined successfully";
        response["challenge"] = buildChallengeSummary(challenge);
        
    } catch (const exception& e) {
        response["status"] = "error";
        response["message"] = string("Exception: ") + e.what();
    }
    
    return response;
}

// Lấy danh sách thách đấu
Json::Value ChallengeHandler::handleListChallenges(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate request
        if (!request.isMember("token")) {
            response["status"] = "error";
            response["message"] = "Missing required field: token";
            return response;
        }
        
        // 2. Get user từ token
        string token = request["token"].asString();
        string userId = getUserIdFromToken(token);
        
        if (userId.empty()) {
            response["status"] = "error";
            response["message"] = "Invalid or expired token";
            return response;
        }
        
        // 3. Get filter options
        string filter = request.get("filter", "all").asString(); // "all", "sent", "received", "pending"
        
        vector<string> challengeIds;
        
        // 4. Query based on filter
        if (filter == "sent") {
            // Challenges sent by user
            challengeIds = redisClient.smembers("challenges:by_challenger:" + userId);
        } else if (filter == "received") {
            // Challenges received by user
            challengeIds = redisClient.smembers("challenges:by_challenged:" + userId);
        } else if (filter == "pending") {
            // All pending challenges for user (sent or received)
            auto sent = redisClient.smembers("challenges:by_challenger:" + userId);
            auto received = redisClient.smembers("challenges:by_challenged:" + userId);
            
            // Merge và filter by pending status
            for (const auto& id : sent) {
                Json::Value ch = loadChallenge(id);
                if (!ch.empty() && ch["status"].asString() == "pending") {
                    challengeIds.push_back(id);
                }
            }
            for (const auto& id : received) {
                Json::Value ch = loadChallenge(id);
                if (!ch.empty() && ch["status"].asString() == "pending") {
                    challengeIds.push_back(id);
                }
            }
        } else { // "all"
            // All challenges for user (sent or received)
            auto sent = redisClient.smembers("challenges:by_challenger:" + userId);
            auto received = redisClient.smembers("challenges:by_challenged:" + userId);
            challengeIds.insert(challengeIds.end(), sent.begin(), sent.end());
            challengeIds.insert(challengeIds.end(), received.begin(), received.end());
        }
        
        // 5. Load full challenge data
        Json::Value challenges(Json::arrayValue);
        for (const auto& challengeId : challengeIds) {
            Json::Value challenge = loadChallenge(challengeId);
            if (!challenge.empty() && challenge.isMember("challenge_id")) {
                challenges.append(buildChallengeSummary(challenge));
            }
        }
        
        // 6. Success response
        response["status"] = "success";
        response["message"] = "Challenges retrieved successfully";
        response["challenges"] = challenges;
        response["count"] = static_cast<int>(challenges.size());
        
    } catch (const exception& e) {
        response["status"] = "error";
        response["message"] = string("Exception: ") + e.what();
    }
    
    return response;
}
