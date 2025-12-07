#ifndef PLAYER_STAT_HANDLER_H
#define PLAYER_STAT_HANDLER_H

#include "database/mongodb_client.h"
#include "../database/redis_client.h"
#include <json/json.h>
#include <string>

/**
 * PlayerStatsHandler - Quản lý thống kê và rating của người chơi
 * 
 * Chức năng:
 * - handleGetStats: Lấy thống kê của player
 * - handleGetLeaderboard: Lấy bảng xếp hạng
 * - handleGetGameHistory: Lấy lịch sử game
 * - updateRating: Cập nhật rating sau game (Glicko-2)
 */
class PlayerStatsHandler {
private:
    MongoDBClient& mongoClient;
    RedisClient& redisClient;
    
    // Glicko-2 constants
    static constexpr double TAU = 0.5;  // System constant
    static constexpr double EPSILON = 0.000001;
    
    // Helper: Lấy userId từ token
    std::string getUserIdFromToken(const std::string& token);
    
    // Glicko-2 functions
    double g(double phi);
    double E(double mu, double mu_j, double phi_j);
    double calculateNewVolatility(double phi, double v, double delta, double sigma);
    
    // Update rating using Glicko-2
    void updateGlicko2Rating(const std::string& playerId, 
                            const std::string& opponentId,
                            double score,
                            const std::string& timeControl);

public:
    PlayerStatsHandler(MongoDBClient& mongo, RedisClient& redis);
    
    // Lấy thống kê của player theo time_control
    Json::Value handleGetStats(const Json::Value& request);
    
    // Lấy bảng xếp hạng
    Json::Value handleGetLeaderboard(const Json::Value& request);
    
    // Lấy lịch sử game của player
    Json::Value handleGetGameHistory(const Json::Value& request);
    
    // Cập nhật thống kê sau game
    Json::Value handleUpdateStats(const Json::Value& request);
};

#endif
