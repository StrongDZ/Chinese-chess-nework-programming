#ifndef PLAYER_STAT_CONTROLLER_H
#define PLAYER_STAT_CONTROLLER_H

#include "player_stat_service.h"
#include <json/json.h>
#include <string>

class PlayerStatController {
private:
    PlayerStatService &service;

public:
    explicit PlayerStatController(PlayerStatService &svc);

    // Get stats for a user. If time_control is omitted or "all", returns aggregated + per-control stats.
    Json::Value handleGetStats(const Json::Value &request);

    // Get leaderboard for a time control, limited to top N (default 100).
    Json::Value handleGetLeaderboard(const Json::Value &request);
};

#endif
