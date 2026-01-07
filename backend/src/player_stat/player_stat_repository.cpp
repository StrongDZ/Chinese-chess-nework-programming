#include "player_stat/player_stat_repository.h"
#include <bsoncxx/builder/stream/document.hpp>
#include <bsoncxx/json.hpp>

using namespace std;
using bsoncxx::builder::stream::document;
using bsoncxx::builder::stream::finalize;
using bsoncxx::builder::stream::open_array;
using bsoncxx::builder::stream::close_array;
using bsoncxx::builder::stream::open_document;
using bsoncxx::builder::stream::close_document;

PlayerStatRepository::PlayerStatRepository(MongoDBClient& mongo)
    : mongoClient(mongo) {}

optional<PlayerStat> PlayerStatRepository::getStats(const string& username, const string& timeControl) {
    try {
        auto db = mongoClient.getDatabase();
        auto stats = db["player_stats"];

        auto result = stats.find_one(document{}
            << "username" << username
            << "time_control" << timeControl
            << finalize);

        if (!result) return nullopt;

        auto v = result->view();
        PlayerStat s{};
        s.username = string(v["username"].get_string().value);
        s.time_control = string(v["time_control"].get_string().value);
        s.rating = v["rating"].get_int32().value;
        s.rd = v["rd"].get_double().value;
        s.volatility = v["volatility"].get_double().value;
        s.highest_rating = v["highest_rating"].get_int32().value;
        s.lowest_rating = v["lowest_rating"].get_int32().value;
        s.total_games = v["total_games"].get_int32().value;
        s.wins = v["wins"].get_int32().value;
        s.losses = v["losses"].get_int32().value;
        s.draws = v["draws"].get_int32().value;
        s.win_streak = v["win_streak"].get_int32().value;
        s.longest_win_streak = v["longest_win_streak"].get_int32().value;
        return s;
    } catch (const exception&) {
        return nullopt;
    }
}

vector<PlayerStat> PlayerStatRepository::getAllStats(const string& username) {
    vector<PlayerStat> out;
    try {
        auto db = mongoClient.getDatabase();
        auto stats = db["player_stats"];
        // Only return valid time controls: blitz and classical
        auto cursor = stats.find(document{} 
            << "username" << username 
            << "$or" << open_array
                << open_document << "time_control" << "blitz" << close_document
                << open_document << "time_control" << "classical" << close_document
            << close_array
            << finalize);
        for (auto&& doc : cursor) {
            PlayerStat s{};
            s.username = string(doc["username"].get_string().value);
            s.time_control = string(doc["time_control"].get_string().value);
            s.rating = doc["rating"].get_int32().value;
            s.rd = doc["rd"].get_double().value;
            s.volatility = doc["volatility"].get_double().value;
            s.highest_rating = doc["highest_rating"].get_int32().value;
            s.lowest_rating = doc["lowest_rating"].get_int32().value;
            s.total_games = doc["total_games"].get_int32().value;
            s.wins = doc["wins"].get_int32().value;
            s.losses = doc["losses"].get_int32().value;
            s.draws = doc["draws"].get_int32().value;
            s.win_streak = doc["win_streak"].get_int32().value;
            s.longest_win_streak = doc["longest_win_streak"].get_int32().value;
            out.push_back(s);
        }
    } catch (const exception&) {
        // swallow and return what we have
    }
    return out;
}

vector<LeaderboardEntry> PlayerStatRepository::getLeaderboard(const string& timeControl, int limit) {
    vector<LeaderboardEntry> out;
    try {
        auto db = mongoClient.getDatabase();
        auto stats = db["player_stats"];
        mongocxx::options::find opts{};
        opts.sort(document{} << "rating" << -1 << finalize);
        opts.limit(limit);

        auto cursor = stats.find(document{} << "time_control" << timeControl << finalize, opts);
        for (auto&& doc : cursor) {
            LeaderboardEntry e{};
            e.username = string(doc["username"].get_string().value);
            e.rating = doc["rating"].get_int32().value;
            e.rd = doc["rd"].get_double().value;
            e.volatility = doc["volatility"].get_double().value;
            e.wins = doc["wins"].get_int32().value;
            e.losses = doc["losses"].get_int32().value;
            e.draws = doc["draws"].get_int32().value;
            out.push_back(e);
        }
    } catch (const exception&) {
        // swallow and return what we have
    }
    return out;
}
