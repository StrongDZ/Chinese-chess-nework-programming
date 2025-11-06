#ifndef GAME_HANDLER_H
#define GAME_HANDLER_H

#include "../database/mongodb_client.h"
#include "../database/redis_client.h"
#include <json/json.h>
#include <string>

/**
 * GameHandler - Xử lý logic game cờ tướng
 * 
 * Chức năng:
 * - handleCreateGame: Tạo game mới từ challenge
 * - handleMakeMove: Thực hiện nước đi
 * - handleOfferDraw: Đề nghị hòa
 * - handleRespondDraw: Chấp nhận/từ chối hòa
 * - handleResign: Đầu hàng
 * - handleGetGame: Lấy thông tin game
 */
class GameHandler {
private:
    MongoDBClient& mongoClient;
    RedisClient& redisClient;
    
    // Helper: Lấy userId từ token
    std::string getUserIdFromToken(const std::string& token);
    
    // Helper: Validate nước đi (cơ bản)
    bool isValidMove(const std::string& from, const std::string& to);
    
    // Helper: Check game đã kết thúc chưa
    bool isGameOver(const std::string& gameId);
    
    // Helper: Cập nhật rating sau game
    void updatePlayerRatings(const std::string& whiteId, const std::string& blackId,
                            const std::string& result, const std::string& timeControl);

public:
    GameHandler(MongoDBClient& mongo, RedisClient& redis);
    
    // Tạo game mới từ challenge đã accept
    Json::Value handleCreateGame(const Json::Value& request);
    
    // Thực hiện nước đi
    Json::Value handleMakeMove(const Json::Value& request);
    
    // Đề nghị hòa
    Json::Value handleOfferDraw(const Json::Value& request);
    
    // Chấp nhận/từ chối hòa
    Json::Value handleRespondDraw(const Json::Value& request);
    
    // Đầu hàng
    Json::Value handleResign(const Json::Value& request);
    
    // Lấy thông tin game
    Json::Value handleGetGame(const Json::Value& request);
    
    // Lấy danh sách game của user
    Json::Value handleListGames(const Json::Value& request);
};

#endif
