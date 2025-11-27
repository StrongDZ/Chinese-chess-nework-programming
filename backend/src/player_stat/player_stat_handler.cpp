#include "../../include/player_stat/player_stat_handler.h"
#include <mongocxx/client.hpp>
#include <bsoncxx/builder/stream/document.hpp>
#include <bsoncxx/json.hpp>
#include <cmath>
#include <algorithm>

using namespace std;
using bsoncxx::builder::stream::document;
using bsoncxx::builder::stream::finalize;

PlayerStatsHandler::PlayerStatsHandler(MongoDBClient& mongo, RedisClient& redis)
    : mongoClient(mongo), redisClient(redis) {}

// Lấy userId từ token
string PlayerStatsHandler::getUserIdFromToken(const string& token) {
    return redisClient.getSession(token);
}

// Glicko-2: g(φ) function
double PlayerStatsHandler::g(double phi) {
    return 1.0 / sqrt(1.0 + 3.0 * phi * phi / (M_PI * M_PI));
}

// Glicko-2: E(μ, μj, φj) function - expected score
double PlayerStatsHandler::E(double mu, double mu_j, double phi_j) {
    return 1.0 / (1.0 + exp(-g(phi_j) * (mu - mu_j)));
}

// Glicko-2: Calculate new volatility (σ)
double PlayerStatsHandler::calculateNewVolatility(double phi, double v, double delta, double sigma) {
    double a = log(sigma * sigma);
    double tau_squared = TAU * TAU;
    
    auto f = [&](double x) {
        double ex = exp(x);
        double phi_sq = phi * phi;
        double num = ex * (delta * delta - phi_sq - v - ex);
        double denom = 2.0 * pow(phi_sq + v + ex, 2);
        return num / denom - (x - a) / tau_squared;
    };
    
    // Illinois algorithm
    double A = a;
    double B;
    if (delta * delta > phi * phi + v) {
        B = log(delta * delta - phi * phi - v);
    } else {
        int k = 1;
        while (f(a - k * TAU) < 0) {
            k++;
        }
        B = a - k * TAU;
    }
    
    double fA = f(A);
    double fB = f(B);
    
    while (abs(B - A) > EPSILON) {
        double C = A + (A - B) * fA / (fB - fA);
        double fC = f(C);
        
        if (fC * fB < 0) {
            A = B;
            fA = fB;
        } else {
            fA = fA / 2.0;
        }
        
        B = C;
        fB = fC;
    }
    
    return exp(A / 2.0);
}

// Update rating using Glicko-2 algorithm
void PlayerStatsHandler::updateGlicko2Rating(const string& playerId, 
                                            const string& opponentId,
                                            double score,
                                            const string& timeControl) {
    auto db = mongoClient.getDatabase();
    auto stats = db["player_stats"];
    
    // Lấy thống kê của cả 2 players
    auto playerStats = stats.find_one(
        document{} 
            << "user_id" << bsoncxx::oid(playerId)
            << "time_control" << timeControl
            << finalize
    );
    
    auto opponentStats = stats.find_one(
        document{} 
            << "user_id" << bsoncxx::oid(opponentId)
            << "time_control" << timeControl
            << finalize
    );
    
    if (!playerStats || !opponentStats) return;
    
    // Convert to Glicko-2 scale (μ, φ, σ)
    double r = playerStats->view()["rating"].get_int32().value;
    double rd = playerStats->view()["rd"].get_double().value;
    double sigma = playerStats->view()["volatility"].get_double().value;
    
    double r_j = opponentStats->view()["rating"].get_int32().value;
    double rd_j = opponentStats->view()["rd"].get_double().value;
    
    // Step 1: Convert to Glicko-2 scale
    double mu = (r - 1500.0) / 173.7178;
    double phi = rd / 173.7178;
    double mu_j = (r_j - 1500.0) / 173.7178;
    double phi_j = rd_j / 173.7178;
    
    // Step 2: Calculate v (estimated variance)
    double g_phi_j = g(phi_j);
    double E_val = E(mu, mu_j, phi_j);
    double v = 1.0 / (g_phi_j * g_phi_j * E_val * (1.0 - E_val));
    
    // Step 3: Calculate delta
    double delta = v * g_phi_j * (score - E_val);
    
    // Step 4: Calculate new volatility
    double new_sigma = calculateNewVolatility(phi, v, delta, sigma);
    
    // Step 5: Update rating deviation
    double phi_star = sqrt(phi * phi + new_sigma * new_sigma);
    
    // Step 6: Calculate new rating and RD
    double new_phi = 1.0 / sqrt(1.0 / (phi_star * phi_star) + 1.0 / v);
    double new_mu = mu + new_phi * new_phi * g_phi_j * (score - E_val);
    
    // Step 7: Convert back to normal scale
    int new_rating = (int)(new_mu * 173.7178 + 1500.0);
    double new_rd = new_phi * 173.7178;
    
    // Clamp RD between 30 and 350
    new_rd = max(30.0, min(350.0, new_rd));
    
    // Lấy stats hiện tại
    int currentRating = playerStats->view()["rating"].get_int32().value;
    int highestRating = playerStats->view()["highest_rating"].get_int32().value;
    int lowestRating = playerStats->view()["lowest_rating"].get_int32().value;
    int wins = playerStats->view()["wins"].get_int32().value;
    int losses = playerStats->view()["losses"].get_int32().value;
    int draws = playerStats->view()["draws"].get_int32().value;
    int winStreak = playerStats->view()["win_streak"].get_int32().value;
    int longestWinStreak = playerStats->view()["longest_win_streak"].get_int32().value;
    
    // Update counters
    if (score == 1.0) {
        wins++;
        winStreak++;
        longestWinStreak = max(longestWinStreak, winStreak);
    } else if (score == 0.0) {
        losses++;
        winStreak = 0;
    } else {
        draws++;
        winStreak = 0;
    }
    
    // Update highest/lowest
    highestRating = max(highestRating, new_rating);
    lowestRating = min(lowestRating, new_rating);
    
    // Update database
    stats.update_one(
        document{} 
            << "user_id" << bsoncxx::oid(playerId)
            << "time_control" << timeControl
            << finalize,
        document{} 
            << "$set" << bsoncxx::builder::stream::open_document
                << "rating" << new_rating
                << "rd" << new_rd
                << "volatility" << new_sigma
                << "highest_rating" << highestRating
                << "lowest_rating" << lowestRating
                << "wins" << wins
                << "losses" << losses
                << "draws" << draws
                << "win_streak" << winStreak
                << "longest_win_streak" << longestWinStreak
                << "total_games" << (wins + losses + draws)
            << bsoncxx::builder::stream::close_document
            << finalize
    );
}

// LẤY THỐNG KÊ CỦA PLAYER
Json::Value PlayerStatsHandler::handleGetStats(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate
        if (!request.isMember("user_id")) {
            response["success"] = false;
            response["error"] = "user_id required";
            return response;
        }
        
        string userId = request["user_id"].asString();
        string timeControl = request.get("time_control", "all").asString();
        
        auto db = mongoClient.getDatabase();
        auto stats = db["player_stats"];
        
        response["success"] = true;
        response["data"] = Json::Value(Json::objectValue);
        
        if (timeControl == "all") {
            // Lấy tất cả time controls
            vector<string> controls = {"bullet", "blitz", "classical"};
            
            for (const auto& tc : controls) {
                auto statDoc = stats.find_one(
                    document{} 
                        << "user_id" << bsoncxx::oid(userId)
                        << "time_control" << tc
                        << finalize
                );
                
                if (statDoc) {
                    auto stat = statDoc->view();
                    Json::Value tcStats;
                    tcStats["rating"] = stat["rating"].get_int32().value;
                    tcStats["rd"] = stat["rd"].get_double().value;
                    tcStats["volatility"] = stat["volatility"].get_double().value;
                    tcStats["highest_rating"] = stat["highest_rating"].get_int32().value;
                    tcStats["lowest_rating"] = stat["lowest_rating"].get_int32().value;
                    tcStats["total_games"] = stat["total_games"].get_int32().value;
                    tcStats["wins"] = stat["wins"].get_int32().value;
                    tcStats["losses"] = stat["losses"].get_int32().value;
                    tcStats["draws"] = stat["draws"].get_int32().value;
                    tcStats["win_streak"] = stat["win_streak"].get_int32().value;
                    tcStats["longest_win_streak"] = stat["longest_win_streak"].get_int32().value;
                    
                    response["data"][tc] = tcStats;
                }
            }
        } else {
            // Lấy 1 time control cụ thể
            auto statDoc = stats.find_one(
                document{} 
                    << "user_id" << bsoncxx::oid(userId)
                    << "time_control" << timeControl
                    << finalize
            );
            
            if (!statDoc) {
                response["success"] = false;
                response["error"] = "Stats not found";
                return response;
            }
            
            auto stat = statDoc->view();
            response["data"]["rating"] = stat["rating"].get_int32().value;
            response["data"]["rd"] = stat["rd"].get_double().value;
            response["data"]["volatility"] = stat["volatility"].get_double().value;
            response["data"]["highest_rating"] = stat["highest_rating"].get_int32().value;
            response["data"]["lowest_rating"] = stat["lowest_rating"].get_int32().value;
            response["data"]["total_games"] = stat["total_games"].get_int32().value;
            response["data"]["wins"] = stat["wins"].get_int32().value;
            response["data"]["losses"] = stat["losses"].get_int32().value;
            response["data"]["draws"] = stat["draws"].get_int32().value;
            response["data"]["win_streak"] = stat["win_streak"].get_int32().value;
            response["data"]["longest_win_streak"] = stat["longest_win_streak"].get_int32().value;
        }
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Get stats error: ") + e.what();
    }
    
    return response;
}

// LẤY BẢNG XẾP HẠNG
Json::Value PlayerStatsHandler::handleGetLeaderboard(const Json::Value& request) {
    Json::Value response;
    
    try {
        string timeControl = request.get("time_control", "blitz").asString();
        int limit = request.get("limit", 100).asInt();
        
        auto db = mongoClient.getDatabase();
        auto stats = db["player_stats"];
        auto users = db["users"];
        
        // Sort by rating descending
        mongocxx::options::find opts{};
        opts.sort(document{} << "rating" << -1 << finalize);
        opts.limit(limit);
        
        auto cursor = stats.find(
            document{} << "time_control" << timeControl << finalize,
            opts
        );
        
        response["success"] = true;
        response["data"] = Json::Value(Json::arrayValue);
        
        int rank = 1;
        for (auto&& doc : cursor) {
            Json::Value entry;
            entry["rank"] = rank++;
            
            string userId = doc["user_id"].get_oid().value.to_string();
            entry["user_id"] = userId;
            
            // Lấy username
            auto userDoc = users.find_one(
                document{} << "_id" << bsoncxx::oid(userId) << finalize
            );
            
            if (userDoc) {
                entry["username"] = string(userDoc->view()["username"].get_string().value);
                if (userDoc->view()["display_name"]) {
                    entry["display_name"] = string(userDoc->view()["display_name"].get_string().value);
                }
            }
            
            entry["rating"] = doc["rating"].get_int32().value;
            entry["rd"] = doc["rd"].get_double().value;
            entry["total_games"] = doc["total_games"].get_int32().value;
            entry["wins"] = doc["wins"].get_int32().value;
            entry["losses"] = doc["losses"].get_int32().value;
            entry["draws"] = doc["draws"].get_int32().value;
            
            // Win rate
            int totalGames = doc["total_games"].get_int32().value;
            if (totalGames > 0) {
                int wins = doc["wins"].get_int32().value;
                entry["win_rate"] = (double)wins / totalGames * 100.0;
            } else {
                entry["win_rate"] = 0.0;
            }
            
            response["data"].append(entry);
        }
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Get leaderboard error: ") + e.what();
    }
    
    return response;
}

// LẤY LỊCH SỬ GAME
Json::Value PlayerStatsHandler::handleGetGameHistory(const Json::Value& request) {
    Json::Value response;
    
    try {
        if (!request.isMember("token")) {
            response["success"] = false;
            response["error"] = "Token required";
            return response;
        }
        
        string token = request["token"].asString();
        string userId = getUserIdFromToken(token);
        
        if (userId.empty()) {
            response["success"] = false;
            response["error"] = "Invalid or expired token";
            return response;
        }
        
        int limit = request.get("limit", 20).asInt();
        
        auto db = mongoClient.getDatabase();
        auto games = db["games"];
        
        // Sort by created_at descending
        mongocxx::options::find opts{};
        opts.sort(document{} << "created_at" << -1 << finalize);
        opts.limit(limit);
        
        auto cursor = games.find(
            document{} 
                << "$or" << bsoncxx::builder::stream::open_array
                    << bsoncxx::builder::stream::open_document
                        << "white_player_id" << bsoncxx::oid(userId)
                    << bsoncxx::builder::stream::close_document
                    << bsoncxx::builder::stream::open_document
                        << "black_player_id" << bsoncxx::oid(userId)
                    << bsoncxx::builder::stream::close_document
                << bsoncxx::builder::stream::close_array
                << finalize,
            opts
        );
        
        response["success"] = true;
        response["data"] = Json::Value(Json::arrayValue);
        
        for (auto&& doc : cursor) {
            Json::Value game;
            game["game_id"] = doc["_id"].get_oid().value.to_string();
            game["white_player_id"] = doc["white_player_id"].get_oid().value.to_string();
            game["black_player_id"] = doc["black_player_id"].get_oid().value.to_string();
            game["time_control"] = string(doc["time_control"].get_string().value);
            game["status"] = string(doc["status"].get_string().value);
            game["rated"] = doc["rated"].get_bool().value;
            
            if (doc["result"]) {
                game["result"] = string(doc["result"].get_string().value);
            }
            
            // Player's color
            string whiteId = doc["white_player_id"].get_oid().value.to_string();
            game["player_color"] = (whiteId == userId) ? "white" : "black";
            
            response["data"].append(game);
        }
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Get game history error: ") + e.what();
    }
    
    return response;
}

// CẬP NHẬT THỐNG KÊ SAU GAME
Json::Value PlayerStatsHandler::handleUpdateStats(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate
        if (!request.isMember("game_id")) {
            response["success"] = false;
            response["error"] = "game_id required";
            return response;
        }
        
        string gameId = request["game_id"].asString();
        
        auto db = mongoClient.getDatabase();
        auto games = db["games"];
        
        // 2. Lấy game info
        auto gameDoc = games.find_one(
            document{} << "_id" << bsoncxx::oid(gameId) << finalize
        );
        
        if (!gameDoc) {
            response["success"] = false;
            response["error"] = "Game not found";
            return response;
        }
        
        auto game = gameDoc->view();
        string status = string(game["status"].get_string().value);
        
        if (status == "ongoing") {
            response["success"] = false;
            response["error"] = "Game still ongoing";
            return response;
        }
        
        if (!game["result"]) {
            response["success"] = false;
            response["error"] = "Game has no result";
            return response;
        }
        
        string result = string(game["result"].get_string().value);
        string whiteId = game["white_player_id"].get_oid().value.to_string();
        string blackId = game["black_player_id"].get_oid().value.to_string();
        string timeControl = string(game["time_control"].get_string().value);
        bool rated = game["rated"].get_bool().value;
        
        if (!rated) {
            response["success"] = false;
            response["error"] = "Game is not rated";
            return response;
        }
        
        // 3. Calculate scores for Glicko-2
        double whiteScore = (result == "white_win") ? 1.0 : (result == "draw") ? 0.5 : 0.0;
        double blackScore = 1.0 - whiteScore;
        
        // 4. Update both players' ratings
        updateGlicko2Rating(whiteId, blackId, whiteScore, timeControl);
        updateGlicko2Rating(blackId, whiteId, blackScore, timeControl);
        
        response["success"] = true;
        response["message"] = "Stats updated successfully";
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Update stats error: ") + e.what();
    }
    
    return response;
}
