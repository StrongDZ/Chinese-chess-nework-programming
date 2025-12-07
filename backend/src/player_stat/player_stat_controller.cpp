#include "../../include/player_stat/player_stat_controller.h"

using namespace std;

namespace {
Json::Value statToJson(const PlayerStat& stat) {
    Json::Value json;
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
}

PlayerStatController::PlayerStatController(PlayerStatService& svc)
    : service(svc) {}

Json::Value PlayerStatController::handleGetStats(const Json::Value& request) {
    Json::Value response;

    if (!request.isMember("username")) {
        response["status"] = "error";
        response["message"] = "Missing required field: username";
        return response;
    }

    string username = request["username"].asString();
    string timeControl = request.get("time_control", "all").asString();

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
        response["stats"] = Json::Value(Json::arrayValue);
        for (const auto& stat : result.stats) {
            response["stats"].append(statToJson(stat));
        }
    }

    return response;
}

Json::Value PlayerStatController::handleGetLeaderboard(const Json::Value& request) {
    Json::Value response;

    string timeControl = request.get("time_control", "blitz").asString();
    int limit = request.get("limit", 100).asInt();

    auto result = service.getLeaderboard(timeControl, limit);
    if (!result.success) {
        response["status"] = "error";
        response["message"] = result.message;
        return response;
    }

    response["status"] = "success";
    response["message"] = result.message;
    response["leaderboard"] = Json::Value(Json::arrayValue);

    for (const auto& entry : result.leaderboard) {
        Json::Value json;
        json["username"] = entry.username;
        json["rating"] = entry.rating;
        json["rd"] = entry.rd;
        json["volatility"] = entry.volatility;
        json["wins"] = entry.wins;
        json["losses"] = entry.losses;
        json["draws"] = entry.draws;
        json["time_control"] = timeControl;
        response["leaderboard"].append(json);
    }

    return response;
}
