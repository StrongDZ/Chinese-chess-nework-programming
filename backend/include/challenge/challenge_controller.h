#ifndef CHALLENGE_CONTROLLER_H
#define CHALLENGE_CONTROLLER_H

#include "challenge_service.h"
#include <json/json.h>

/**
 * ChallengeController - Presentation Layer cho Challenge
 * 
 * Chịu trách nhiệm:
 * - Parse JSON request
 * - Gọi ChallengeService
 * - Format JSON response
 * 
 * NOTE: Sử dụng username thay vì token (protocol layer mapping fd -> username)
 */

class ChallengeController {
private:
    ChallengeService& service;
    
    // Helper: Convert Challenge to JSON
    Json::Value challengeToJson(const Challenge& challenge) const;

public:
    explicit ChallengeController(ChallengeService& svc);
    
    // Tạo thách đấu mới
    // Input: { "username": "...", "challenged_username": "...", "time_control": "...", "rated": true/false, "message": "..." }
    Json::Value handleCreateChallenge(const Json::Value& request);
    
    // Hủy thách đấu (chỉ challenger được hủy)
    // Input: { "username": "...", "challenge_id": "..." }
    Json::Value handleCancelChallenge(const Json::Value& request);
    
    // Chấp nhận thách đấu
    // Input: { "username": "...", "challenge_id": "..." }
    Json::Value handleAcceptChallenge(const Json::Value& request);
    
    // Từ chối thách đấu
    // Input: { "username": "...", "challenge_id": "..." }
    Json::Value handleDeclineChallenge(const Json::Value& request);
    
    // Lấy danh sách thách đấu
    // Input: { "username": "...", "filter": "all/sent/received/pending" }
    Json::Value handleListChallenges(const Json::Value& request);
};

#endif
