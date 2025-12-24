#ifndef PLAYER_STAT_CONTROLLER_H
#define PLAYER_STAT_CONTROLLER_H

#include "player_stat_service.h"
#include <nlohmann/json.hpp>
#include <string>

class PlayerStatController {
private:
  PlayerStatService &service;

public:
  explicit PlayerStatController(PlayerStatService &svc);

  // Get stats for a user. If time_control is omitted or "all", returns
  // aggregated + per-control stats.
  nlohmann::json handleGetStats(const nlohmann::json &request);

  // Get leaderboard for a time control, limited to top N (default 100).
  nlohmann::json handleGetLeaderboard(const nlohmann::json &request);
};

#endif
