#ifndef CHALLENGE_HANDLER_H
#define CHALLENGE_HANDLER_H

#include "../database/redis_client.h"
#include <json/json.h>
#include <string>

/**
 * ChallengeHandler - Xử lý các thách đấu giữa người chơi
 * 
 * Chức năng:
 * - handleCreateChallenge: Tạo thách đấu mới
 * - handleCancelChallenge: Hủy thách đấu
 * - handleAcceptChallenge: Chấp nhận thách đấu
 * - handleDeclineChallenge: Từ chối thách đấu
 * - handleListChallenges: Lấy danh sách thách đấu
 */
class ChallengeHandler {
private:
    RedisClient& redisClient;
    static constexpr int PENDING_TTL_SECONDS = 3600;
    static constexpr int FINALIZED_TTL_SECONDS = 300;
    
    // Helper: Lấy userId từ token
    std::string getUserIdFromToken(const std::string& token);
    
    // Helper: Validate time_control
    bool isValidTimeControl(const std::string& timeControl);
    
    // Helper: Validate rated flag
    bool isValidRated(bool rated);

    // Internal helpers for Redis-backed storage
    Json::Value loadChallenge(const std::string& challengeId);
    bool persistChallenge(const Json::Value& challenge, int ttlSeconds);
    void indexChallenge(const Json::Value& challenge);
    void removeChallengeIndexes(const Json::Value& challenge);
    Json::Value buildChallengeSummary(const Json::Value& challenge) const;

public:
    explicit ChallengeHandler(RedisClient& redis);
    
    // Tạo thách đấu mới
    Json::Value handleCreateChallenge(const Json::Value& request);
    
    // Hủy thách đấu
    Json::Value handleCancelChallenge(const Json::Value& request);
    
    // Chấp nhận thách đấu
    Json::Value handleAcceptChallenge(const Json::Value& request);
    
    // Từ chối thách đấu
    Json::Value handleDeclineChallenge(const Json::Value& request);
    
    // Lấy danh sách thách đấu (của user hoặc tất cả public)
    Json::Value handleListChallenges(const Json::Value& request);
};

#endif
