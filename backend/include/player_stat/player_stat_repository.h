#ifndef PLAYER_STAT_REPOSITORY_H
#define PLAYER_STAT_REPOSITORY_H

#include "database/mongodb_client.h"
#include <optional>
#include <string>
#include <vector>

struct PlayerStat {
    std::string username;
    std::string time_control; // blitz, classical
    int rating;
    double rd;
    double volatility;
    int highest_rating;
    int lowest_rating;
    int total_games;
    int wins;
    int losses;
    int draws;
    int win_streak;
    int longest_win_streak;
};

struct LeaderboardEntry {
    std::string username;
    int rating;
    double rd;
    double volatility;
    int wins;
    int losses;
    int draws;
};

class PlayerStatRepository {
private:
    MongoDBClient& mongoClient;

public:
    explicit PlayerStatRepository(MongoDBClient& mongo);

    std::optional<PlayerStat> getStats(const std::string& username, const std::string& timeControl);
    std::vector<PlayerStat> getAllStats(const std::string& username);
    std::vector<LeaderboardEntry> getLeaderboard(const std::string& timeControl, int limit = 100);
};

#endif
