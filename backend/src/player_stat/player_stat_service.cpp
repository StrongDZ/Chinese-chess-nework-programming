#include "player_stat/player_stat_service.h"
#include <algorithm>

using namespace std;

namespace {
bool isValidTimeControl(const string& tc, bool allowAll) {
    return tc == "bullet" || tc == "blitz" || tc == "classical" || (allowAll && tc == "all");
}
}

PlayerStatService::PlayerStatService(PlayerStatRepository& repo)
    : repository(repo) {}

PlayerStatResult PlayerStatService::getStats(const string& username, const string& timeControl) {
    PlayerStatResult result{false, "", nullopt, {}, {}};

    if (username.empty()) {
        result.message = "username is required";
        return result;
    }

    if (!isValidTimeControl(timeControl, true)) {
        result.message = "Invalid time_control (use bullet|blitz|classical|all)";
        return result;
    }

    if (timeControl == "all") {
        result.stats = repository.getAllStats(username);
        if (result.stats.empty()) {
            result.message = "Stats not found";
            return result;
        }
        result.success = true;
        result.message = "Stats retrieved";
        return result;
    }

    result.stat = repository.getStats(username, timeControl);
    if (!result.stat) {
        result.message = "Stats not found";
        return result;
    }

    result.success = true;
    result.message = "Stats retrieved";
    return result;
}

PlayerStatResult PlayerStatService::getLeaderboard(const string& timeControl, int limit) {
    PlayerStatResult result{false, "", nullopt, {}, {}};

    if (!isValidTimeControl(timeControl, false)) {
        result.message = "Invalid time_control (use bullet|blitz|classical)";
        return result;
    }

    if (limit <= 0) {
        limit = 100;
    }

    result.leaderboard = repository.getLeaderboard(timeControl, limit);
    result.success = true;
    result.message = "Leaderboard retrieved";
    return result;
}
