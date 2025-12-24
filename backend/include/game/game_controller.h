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

  // Lấy thông tin game
  // Input: { "game_id": "..." }
  nlohmann::json handleGetGame(const nlohmann::json &request);

  // Lấy danh sách game của user
  // Input: { "username": "...", "filter": "active/completed/all" }
  nlohmann::json handleListGames(const nlohmann::json &request);
};

#endif
