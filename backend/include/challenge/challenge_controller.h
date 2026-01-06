#ifndef CHALLENGE_CONTROLLER_H
#define CHALLENGE_CONTROLLER_H

#include "challenge_service.h"
#include <nlohmann/json.hpp>

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
  ChallengeService &service;

  // Helper: Convert Challenge to JSON
  nlohmann::json challengeToJson(const Challenge &challenge) const;

public:
  explicit ChallengeController(ChallengeService &svc);

  // Tạo thách đấu mới
  // Input: { "username": "...", "challenged_username": "...", "time_control":
  // "...", "rated": true/false, "message": "..." }
  nlohmann::json handleCreateChallenge(const nlohmann::json &request);

  // Hủy thách đấu (chỉ challenger được hủy)
  // Input: { "username": "...", "challenge_id": "..." }
  nlohmann::json handleCancelChallenge(const nlohmann::json &request);

  // Chấp nhận thách đấu
  // Input: { "username": "...", "challenge_id": "..." }
  nlohmann::json handleAcceptChallenge(const nlohmann::json &request);

  // Từ chối thách đấu
  // Input: { "username": "...", "challenge_id": "..." }
  nlohmann::json handleDeclineChallenge(const nlohmann::json &request);

  // Lấy danh sách thách đấu
  // Input: { "username": "...", "filter": "all/sent/received/pending" }
  nlohmann::json handleListChallenges(const nlohmann::json &request);
};

#endif
