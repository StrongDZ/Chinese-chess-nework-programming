#ifndef GAME_HANDLER_H
#define GAME_HANDLER_H

#include "../database/mongodb_client.h"
#include "../database/redis_client.h"
#include <json/json.h>
#include <string>

/**
 * GameHandler - Xử lý logic game cờ tướng (Xiangqi/Chinese Chess)
 * 
 * Cờ tướng:
 * - Bàn cờ 9x10 (9 cột, 10 hàng)
 * - Red (đỏ) đi trước, Black (đen) đi sau
 * - XFEN notation cho board state
 * - Tọa độ: {x: 0-8, y: 0-9}
 * - Lưu trong MongoDB (active_games collection)
 * 
 * Chức năng:
 * - handleCreateGame: Tạo game mới từ challenge (Redis)
 * - handleMakeMove: Thực hiện nước đi
 * - handleOfferDraw: Đề nghị hòa
 * - handleRespondDraw: Chấp nhận/từ chối hòa
 * - handleResign: Đầu hàng
 * - handleGetGame: Lấy thông tin game
 * - handleListGames: Lấy danh sách game
 */
class GameHandler {
private:
    MongoDBClient& mongoClient;
    RedisClient& redisClient;
    
    // Helper: Lấy userId từ token
    std::string getUserIdFromToken(const std::string& token);
    
    // Helper: Validate tọa độ (x: 0-8, y: 0-9)
    bool isValidCoordinate(int x, int y);
    
    // Helper: Check game đã kết thúc chưa
    bool isGameOver(const std::string& gameId);
    
    // Helper: Update player ratings sau khi game kết thúc
    void updatePlayerRatings(const std::string& redId, const std::string& blackId,
                            const std::string& result, const std::string& timeControl);
    
    // Helper: Get time limits based on time_control
    int getTimeLimitSeconds(const std::string& timeControl);
    int getIncrementSeconds(const std::string& timeControl);

public:
    GameHandler(MongoDBClient& mongo, RedisClient& redis);
    
    // Tạo game mới từ challenge đã accept
    Json::Value handleCreateGame(const Json::Value& request);
    
    // Thực hiện nước đi (với tọa độ x,y)
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
