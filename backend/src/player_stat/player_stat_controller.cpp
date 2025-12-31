#include "player_stat/player_stat_controller.h"

using namespace std;

namespace {
nlohmann::json statToJson(const PlayerStat &stat) {
  nlohmann::json json;
  json["username"] = stat.username;
  json["time_control"] = stat.time_control;
  json["rating"] = stat.rating;
  json["rd"] = stat.rd;
  json["volatility"] = stat.volatility;
  json["highest_rating"] = stat.highest_rating;
  json["lowest_rating"] = stat.lowest_rating;
  json["total_games"] = stat.total_games;
  json["wins"] = stat.wins;
  json["losses"] = stat.losses;
  json["draws"] = stat.draws;
  json["win_streak"] = stat.win_streak;
  json["longest_win_streak"] = stat.longest_win_streak;
  return json;
}
} // namespace

PlayerStatController::PlayerStatController(PlayerStatService &svc)
    : service(svc) {}

nlohmann::json
PlayerStatController::handleGetStats(const nlohmann::json &request) {
  nlohmann::json response;

  if (!request.contains("username")) {
    response["status"] = "error";
    response["message"] = "Missing required field: username";
    return response;
  }

  string username = request["username"].get<string>();
  string timeControl = request.value("time_control", string("all"));

  auto result = service.getStats(username, timeControl);
  if (!result.success) {
    response["status"] = "error";
    response["message"] = result.message;
    return response;
  }

  response["status"] = "success";
  response["message"] = result.message;

  if (result.stat) {
    response["stat"] = statToJson(*result.stat);
  }

  if (!result.stats.empty()) {
    response["stats"] = nlohmann::json::array();
    for (const auto &stat : result.stats) {
      response["stats"].push_back(statToJson(stat));
    }
  }

  return response;
}

nlohmann::json
PlayerStatController::handleGetLeaderboard(const nlohmann::json &request) {
  nlohmann::json response;

  string timeControl = request.value("time_control", string("blitz"));
  int limit = request.value("limit", 100);

  auto result = service.getLeaderboard(timeControl, limit);
  if (!result.success) {
    response["status"] = "error";
    response["message"] = result.message;
    return response;
  }

  response["status"] = "success";
  response["message"] = result.message;
  response["leaderboard"] = nlohmann::json::array();

  for (const auto &entry : result.leaderboard) {
    nlohmann::json json;
    json["username"] = entry.username;
    json["rating"] = entry.rating;
    json["rd"] = entry.rd;
    json["volatility"] = entry.volatility;
    json["wins"] = entry.wins;
    json["losses"] = entry.losses;
    json["draws"] = entry.draws;
    json["time_control"] = timeControl;
    response["leaderboard"].push_back(json);
  }

  return response;
}
