#ifndef PLAYER_STAT_SERVICE_H
#define PLAYER_STAT_SERVICE_H

#include "player_stat_repository.h"
#include <optional>
#include <string>
#include <vector>

struct PlayerStatResult {
    bool success;
    std::string message;
    std::optional<PlayerStat> stat;
    std::vector<PlayerStat> stats;
    std::vector<LeaderboardEntry> leaderboard;
};

class PlayerStatService {
private:
    PlayerStatRepository& repository;

public:
    explicit PlayerStatService(PlayerStatRepository& repo);

    PlayerStatResult getStats(const std::string& username, const std::string& timeControl = "all");
    PlayerStatResult getLeaderboard(const std::string& timeControl = "blitz", int limit = 100);
    // Get all user stats (both classical and blitz) for all users
    PlayerStatResult getAllUsersStats();
};

#endif
