#ifndef CHALLENGE_SERVICE_H
#define CHALLENGE_SERVICE_H

#include "challenge_repository.h"
#include <string>
#include <vector>
#include <optional>

/**
 * ChallengeService - Business Logic Layer cho Challenge
 * 
 * Chịu trách nhiệm:
 * - Validation (username, time_control, etc.)
 * - Business rules (không tự thách đấu, không duplicate pending)
 * - Sử dụng ChallengeRepository để truy cập database
 */

// Result struct cho service operations
struct ChallengeResult {
    bool success;
    std::string message;
    std::optional<Challenge> challenge;
    std::vector<Challenge> challenges; // For list operations
};

class ChallengeService {
private:
    ChallengeRepository& repository;
    
    // TTL cho pending challenges (1 giờ)
    static constexpr int PENDING_TTL_SECONDS = 3600;
    
    // Validation helpers
    bool isValidTimeControl(const std::string& timeControl);
    bool isValidUsername(const std::string& username);
    
public:
    explicit ChallengeService(ChallengeRepository& repo);
    
    // Tạo thách đấu mới
    ChallengeResult createChallenge(const std::string& challengerUsername,
                                     const std::string& challengedUsername,
                                     const std::string& timeControl = "blitz",
                                     bool rated = true,
                                     const std::string& message = "");
    
    // Hủy thách đấu (chỉ challenger được hủy)
    ChallengeResult cancelChallenge(const std::string& username, 
                                     const std::string& challengeId);
    
    // Chấp nhận thách đấu
    ChallengeResult acceptChallenge(const std::string& username, 
                                     const std::string& challengeId);
    
    // Từ chối thách đấu
    ChallengeResult declineChallenge(const std::string& username, 
                                      const std::string& challengeId);
    
    // Lấy danh sách thách đấu
    // filter: "all", "sent", "received", "pending"
    ChallengeResult listChallenges(const std::string& username, 
                                    const std::string& filter = "all");
};

#endif
