#ifndef GAME_CONTROLLER_H
#define GAME_CONTROLLER_H

#include "game_service.h"
#include <nlohmann/json.hpp>

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
  GameService &service;

  // Helper: Convert Game to JSON
  nlohmann::json gameToJson(const Game &game, bool includeMoves = false) const;

  // Helper: Convert Move to JSON
  nlohmann::json moveToJson(const Move &move) const;

public:
  explicit GameController(GameService &svc);

  // Tạo game mới từ challenge đã accept
  // Input: { "username": "...", "challenged_username": "...", "time_control":
  // "...", "rated": true/false } NOTE: Called after challenge is accepted
  nlohmann::json handleCreateGame(const nlohmann::json &request);
  
  // Tạo game với custom board setup (CUSTOM MODE)
  // Input: { 
  //   "red_player": "...", 
  //   "black_player": "...", 
  //   "custom_xfen": "rnbakabnr/9/1c5c1/...",  // Custom initial position
  //   "starting_color": "red"|"black",         // Who moves first
  //   "time_control": "blitz"                   // Optional
  // }
  // NOTE: Custom games are always unrated
  nlohmann::json handleCreateCustomGame(const nlohmann::json &request);

  // Thực hiện nước đi
  // Input: { "username": "...", "game_id": "...", "from": {x,y}, "to": {x,y},
  //          "piece": "...", "captured": "...", "notation": "...",
  //          "xfen_after": "...", "time_taken": N }
  nlohmann::json handleMakeMove(const nlohmann::json &request);

  // Kết thúc game (gọi từ protocol khi checkmate, draw agreement, timeout,
  // etc.) Input: { "game_id": "...", "result": "red_win|black_win|draw",
  // "termination": "checkmate|draw_agreement|..." }
  nlohmann::json handleEndGame(const nlohmann::json &request);

  // Đầu hàng (wrapper gọi endGame)
  // Input: { "username": "...", "game_id": "..." }
  nlohmann::json handleResign(const nlohmann::json &request);

  // ============ Draw Offer Handlers ============

  // Đề nghị hòa
  // Input: { "username": "...", "game_id": "..." }
  nlohmann::json handleOfferDraw(const nlohmann::json &request);

  // Phản hồi đề nghị hòa
  // Input: { "username": "...", "game_id": "...", "accept": true/false }
  nlohmann::json handleRespondToDraw(const nlohmann::json &request);

  // ============ Rematch Handlers ============

  // Yêu cầu rematch sau khi game kết thúc
  // Input: { "username": "...", "game_id": "..." }
  nlohmann::json handleRequestRematch(const nlohmann::json &request);

  // Phản hồi yêu cầu rematch
  // Input: { "username": "...", "game_id": "...", "accept": true/false }
  nlohmann::json handleRespondToRematch(const nlohmann::json &request);

  // Lấy thông tin game
  // Input: { "game_id": "..." }
  nlohmann::json handleGetGame(const nlohmann::json &request);

  // Lấy danh sách game của user
  // Input: { "username": "...", "filter": "active/completed/all" }
  nlohmann::json handleListGames(const nlohmann::json &request);
  
  // ============ Game History & Replay Handlers ============
  
  // Lấy lịch sử game đã hoàn thành (GET_GAME_HISTORY)
  // Input: { "username": "...", "limit": 50, "offset": 0 }
  // Output: { "status": "success", "history": [...], "count": N }
  nlohmann::json handleGetGameHistory(const nlohmann::json &request);
  
  // Lấy chi tiết game với moves để replay (GET_GAME_DETAILS)
  // Input: { "game_id": "..." }
  // Output: { "status": "success", "game": {..., "moves": [...]} }
  nlohmann::json handleGetGameDetails(const nlohmann::json &request);
};

#endif
