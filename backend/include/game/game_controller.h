#ifndef GAME_CONTROLLER_H
#define GAME_CONTROLLER_H

#include "game_service.h"
#include <json/json.h>

/**
 * GameController - Presentation Layer cho Game
 * 
 * Chịu trách nhiệm:
 * - Parse JSON request
 * - Gọi GameService
 * - Format JSON response
 * 
 * NOTE: Sử dụng username thay vì token (protocol layer mapping fd -> username)
 */

class GameController {
private:
    GameService& service;
    
    // Helper: Convert Game to JSON
    Json::Value gameToJson(const Game& game, bool includeMoves = false) const;
    
    // Helper: Convert Move to JSON
    Json::Value moveToJson(const Move& move) const;

public:
    explicit GameController(GameService& svc);
    
    // Tạo game mới từ challenge đã accept
    // Input: { "username": "...", "challenged_username": "...", "time_control": "...", "rated": true/false }
    // NOTE: Called after challenge is accepted
    Json::Value handleCreateGame(const Json::Value& request);
    
    // Thực hiện nước đi
    // Input: { "username": "...", "game_id": "...", "from": {x,y}, "to": {x,y}, 
    //          "piece": "...", "captured": "...", "notation": "...", "xfen_after": "...", "time_taken": N }
    Json::Value handleMakeMove(const Json::Value& request);
    
    // Đề nghị hòa
    // Input: { "username": "...", "game_id": "..." }
    Json::Value handleOfferDraw(const Json::Value& request);
    
    // Chấp nhận/từ chối hòa
    // Input: { "username": "...", "game_id": "...", "accept": true/false }
    Json::Value handleRespondDraw(const Json::Value& request);
    
    // Đầu hàng
    // Input: { "username": "...", "game_id": "..." }
    Json::Value handleResign(const Json::Value& request);
    
    // Lấy thông tin game
    // Input: { "game_id": "..." }
    Json::Value handleGetGame(const Json::Value& request);
    
    // Lấy danh sách game của user
    // Input: { "username": "...", "filter": "active/completed/all" }
    Json::Value handleListGames(const Json::Value& request);
};

#endif
