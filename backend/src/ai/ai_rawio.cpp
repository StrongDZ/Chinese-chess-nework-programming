#include "ai/ai_controller.h"
#include "ai/ai_service.h"
#include "game/game_service.h"
#include "protocol/handle_socket.h"
#include "protocol/message_types.h"
#include "protocol/server.h"
#include <map>
#include <mutex>
#include <string>
#include <variant>

using namespace std;

// External global variables from server.cpp
extern map<int, PlayerInfo> g_clients;
extern map<string, int> g_username_to_fd;
extern mutex g_clients_mutex;
extern AIController *g_ai_controller;
extern GameService *g_game_service;
extern AIService *g_ai_service;

// Helper function to convert AIMove to MovePayload
// Note: piece name needs to be extracted from board position
MovePayload convertAIMoveToMovePayload(const AIMove &aiMove) {
  MovePayload move;
  move.from.row = aiMove.fromX;
  move.from.col = aiMove.fromY;
  move.to.row = aiMove.toX;
  move.to.col = aiMove.toY;

  // Extract piece from xfen at from position
  // For now, use empty string - will be filled by game logic
  move.piece = ""; // TODO: Extract piece type from xfen at from position

  return move;
}

void handleAIMatch(const ParsedMessage &pm, int fd) {
  lock_guard<mutex> lock(g_clients_mutex);
  auto &sender = g_clients[fd];
  if (sender.username.empty()) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Please LOGIN before starting AI match"});
    return;
  }

  if (sender.in_game) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"You are already in a game"});
    return;
  }

  if (!pm.payload.has_value() ||
      !holds_alternative<AIMatchPayload>(*pm.payload)) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"AI_MATCH requires game_mode and ai_mode"});
    return;
  }

  const auto &p = get<AIMatchPayload>(*pm.payload);
  string game_mode = p.game_mode;
  string ai_mode = p.ai_mode;
  int time_limit = p.time_limit;
  int game_timer = p.game_timer;
  // AI is always black, player is always red
  string playerSide = "red";

  // Validate game_mode
  if (game_mode != "classical" && game_mode != "blitz" &&
      game_mode != "custom") {
    sendMessage(
        fd, MessageType::ERROR,
        ErrorPayload{"Invalid game_mode. Use: classical, blitz, or custom"});
    return;
  }

  // Validate ai_mode
  if (ai_mode != "easy" && ai_mode != "medium" && ai_mode != "hard") {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Invalid ai_mode. Use: easy, medium, or hard"});
    return;
  }

  // Check if AI controller is available
  if (g_ai_controller == nullptr) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"AI service is not available"});
    return;
  }

  // Use game_mode directly as time_control
  string timeControl = game_mode;

  // Create AI game via controller
  nlohmann::json createRequest;
  createRequest["username"] = sender.username;
  createRequest["difficulty"] = ai_mode;       // Use ai_mode for AI difficulty
  createRequest["time_control"] = timeControl; // Use game_mode as time_control
  // player_color is not needed - AI is always black, player is always red
  if (time_limit > 0) {
    createRequest["time_limit"] = time_limit;
  }

  auto response = g_ai_controller->handleCreateAIGame(createRequest);

  if (response["status"] != "success") {
    sendMessage(
        fd, MessageType::ERROR,
        ErrorPayload{response.value("message", "Failed to create AI game")});
    return;
  }

  // Update sender state
  sender.in_game = true;
  sender.opponent_fd = -1; // AI doesn't have a real FD
  sender.is_red = true;    // Player is always red, AI is always black

  if (response.contains("game") && response["game"].contains("game_id")) {
    sender.game_id = response["game"]["game_id"].get<string>();
  }

  // Set current_turn based on game state
  // If game has current_turn, use it (handles case where AI moved first)
  // Otherwise, set based on player side (red always goes first)
  string gameCurrentTurn = "red"; // Default
  if (response.contains("game") && response["game"].contains("current_turn")) {
    gameCurrentTurn = response["game"]["current_turn"].get<string>();
    sender.current_turn = gameCurrentTurn;
    cout << "[handleAIMatch] Set current_turn from game: "
         << sender.current_turn << " (player is red: " << sender.is_red << ")"
         << endl;
  } else {
    // Default: red always goes first
    sender.current_turn = "red";
    cout << "[handleAIMatch] Set current_turn to default: red (player is red: "
         << sender.is_red << ")" << endl;
  }

  // Send GAME_START to player
  GameStartPayload gs;
  gs.opponent = "";                 // Empty for AI games
  gs.game_mode = "ai_" + game_mode; // Use game_mode (classical/blitz/custom)
  gs.time_limit = time_limit;
  gs.game_timer = game_timer;
  gs.opponent_data = nlohmann::json();
  gs.opponent_data["player_is_red"] = (playerSide == "red");
  gs.opponent_data["is_ai_game"] = true;
  gs.opponent_data["ai_difficulty"] = ai_mode; // Use ai_mode (easy/medium/hard)
  if (response.contains("game") && response["game"].contains("game_id")) {
    gs.opponent_data["game_id"] = response["game"]["game_id"];
  }

  sendMessage(fd, MessageType::GAME_START, gs);

  // Player (red) always moves first, AI (black) moves second
  // No need to handle AI's first move here
}

void handleSuggestMove(const ParsedMessage &pm, int fd) {
  (void)pm; // Unused parameter - function doesn't require payload
  lock_guard<mutex> lock(g_clients_mutex);
  auto &sender = g_clients[fd];

  if (sender.username.empty()) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Please LOGIN before requesting move suggestion"});
    return;
  }

  if (!sender.in_game) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"You are not in a game"});
    return;
  }

  // Check if it's an AI game
  if (sender.opponent_fd != -1) {
    sendMessage(
        fd, MessageType::ERROR,
        ErrorPayload{"Move suggestions are only available in AI games"});
    return;
  }

  // Get current game state
  if (sender.game_id.empty()) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"No active game found"});
    return;
  }

  if (g_ai_controller == nullptr || g_game_service == nullptr) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"AI service is not available"});
    return;
  }

  // Get current game state
  auto gameResult = g_game_service->getGame(sender.game_id);
  if (!gameResult.success || !gameResult.game.has_value()) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"Game not found"});
    return;
  }

  const auto &game = gameResult.game.value();

  // Check if game is still in progress
  if (game.status != "in_progress") {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Game is not in progress"});
    return;
  }

  // Call AI controller to get move suggestion
  nlohmann::json request;
  request["game_id"] = sender.game_id;
  request["xfen"] = game.xfen;

  nlohmann::json response = g_ai_controller->handleSuggestMove(request);

  if (response["status"] != "success") {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{response.value("message",
                                            "Failed to get move suggestion")});
    return;
  }

  // Extract suggested move from response
  if (!response.contains("suggested_move")) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"No move suggestion in response"});
    return;
  }

  // Send suggestion via INFO message
  nlohmann::json infoData;
  infoData["action"] = "suggest_move";
  infoData["suggested_move"] = response["suggested_move"];

  InfoPayload infoPayload;
  infoPayload.data = infoData;

  sendMessage(fd, MessageType::INFO, infoPayload);

  // TODO: Check AI engine availability via Python API
  // if (!g_ai_engine.isReady()) {
  //   sendMessage(fd, MessageType::ERROR,
  //               ErrorPayload{"AI engine is not available"});
  //   return;
  // }

  // TODO: Get current game state via Python API
  // if (!g_game_state.hasGame(fd)) {
  //   sendMessage(
  //       fd, MessageType::ERROR,
  //       ErrorPayload{"No game state found. This feature works with AI
  //       games."});
  //   return;
  // }

  // string current_fen = g_game_state.getCurrentFEN(fd);
  // if (current_fen.empty()) {
  //   sendMessage(fd, MessageType::ERROR,
  //               ErrorPayload{"Failed to get current game position"});
  //   return;
  // }

  // TODO: Get AI suggestion via Python API
  // string ucci_move = g_ai_engine.suggestMove(current_fen);
  // if (ucci_move.empty()) {
  //   sendMessage(fd, MessageType::ERROR,
  //               ErrorPayload{"Failed to get move suggestion from AI"});
  //   return;
  // }

  // Convert UCI move to MovePayload
  // MovePayload suggested_move = parseUCIMove(ucci_move);

  // TODO: Send suggestion to player via Python API
  // sendMessage(fd, MessageType::SUGGEST_MOVE, suggested_move);
}

void handleAIMove(int player_fd, const string &xfen) {
  // NOTE: This function assumes g_clients_mutex is already locked by the caller
  // (e.g., handleMove in game_rawio.cpp)

  // Check if player is in game
  if (g_clients.count(player_fd) == 0) {
    return; // Player not found
  }

  auto &player = g_clients[player_fd];

  // Check if it's an AI game
  if (player.opponent_fd != -1) {
    return; // Not an AI game
  }

  // Check if player has a game_id
  if (player.game_id.empty()) {
    cerr << "[handleAIMove] No game_id for player " << player.username << endl;
    return;
  }

  // Check if AI controller and services are available
  if (g_ai_controller == nullptr || g_game_service == nullptr ||
      g_ai_service == nullptr) {
    cerr << "[handleAIMove] AI services not available" << endl;
    return;
  }

  // Check if AI service is ready
  if (!g_ai_service->isReady()) {
    cerr << "[handleAIMove] AI service not ready" << endl;
    return;
  }

  // Get current game state (to check status and get AI player info)
  auto gameResult = g_game_service->getGame(player.game_id);
  if (!gameResult.success || !gameResult.game.has_value()) {
    cerr << "[handleAIMove] Game not found: " << player.game_id << endl;
    return;
  }

  const auto &game = gameResult.game.value();

  // Check if game is still in progress
  if (game.status != "in_progress") {
    cout << "[handleAIMove] Game already ended: " << game.status << endl;
    return;
  }

  // Determine AI player and difficulty
  string aiPlayer = "";
  AIDifficulty difficulty = AIDifficulty::MEDIUM;

  if (game.red_player.find("AI_") == 0) {
    aiPlayer = game.red_player;
  } else if (game.black_player.find("AI_") == 0) {
    aiPlayer = game.black_player;
  } else {
    cerr << "[handleAIMove] Not an AI game" << endl;
    return;
  }

  // Parse difficulty from AI player name
  if (aiPlayer.find("easy") != string::npos) {
    difficulty = AIDifficulty::EASY;
  } else if (aiPlayer.find("hard") != string::npos) {
    difficulty = AIDifficulty::HARD;
  }

  // Use provided x-fen if available, otherwise fallback to game.xfen from
  // database
  string currentXfen = xfen.empty() ? game.xfen : xfen;

  if (!xfen.empty()) {
    cout << "[handleAIMove] Using provided x-fen (from move response)" << endl;
  } else {
    cout << "[handleAIMove] Using x-fen from database (fallback)" << endl;
  }

  // Get AI move prediction
  cout << "[handleAIMove] Calculating AI move for " << aiPlayer
       << " (difficulty="
       << (difficulty == AIDifficulty::EASY   ? "easy"
           : difficulty == AIDifficulty::HARD ? "hard"
                                              : "medium")
       << ")..." << endl;
  cout << "[handleAIMove] X-fen: " << currentXfen << endl;

  auto aiMoveResult = g_ai_service->predictMove(currentXfen, difficulty);

  if (!aiMoveResult.success || !aiMoveResult.move.has_value()) {
    cerr << "[handleAIMove] AI failed to calculate move: "
         << aiMoveResult.message << endl;
    return;
  }

  cout << "[handleAIMove] AI move calculated successfully" << endl;

  // Apply AI move to game
  // NOTE: AIMove uses: fromX=row, fromY=col
  // But makeMove expects: fromX=col, fromY=row
  // So we need to swap: makeMove(..., fromY, fromX, toY, toX, ...)
  auto moveResult = g_game_service->makeMove(aiPlayer, player.game_id,
                                             aiMoveResult.move->fromY, // col
                                             aiMoveResult.move->fromX, // row
                                             aiMoveResult.move->toY,   // col
                                             aiMoveResult.move->toX,   // row
                                             "", "", "", "", 0);

  if (!moveResult.success) {
    cerr << "[handleAIMove] Failed to apply AI move: " << moveResult.message
         << endl;
    return;
  }

  // Convert AI move to MovePayload
  MovePayload aiMove = convertAIMoveToMovePayload(aiMoveResult.move.value());

  // Update player's turn tracking
  if (moveResult.game.has_value()) {
    player.current_turn = moveResult.game->current_turn;
  } else {
    // Game ended, set turn to empty
    player.current_turn = "";
  }

  // Send AI move to player
  sendMessage(player_fd, MessageType::MOVE, aiMove);

  cout << "[handleAIMove] AI move sent: " << aiPlayer << " from=("
       << aiMove.from.row << "," << aiMove.from.col << ")"
       << " to=(" << aiMove.to.row << "," << aiMove.to.col << ")"
       << " next_turn=" << player.current_turn << endl;

  // Check if game ended after AI move
  if (moveResult.game.has_value() && moveResult.game->status != "in_progress") {
    // Game ended - send game result
    // TODO: Send GAME_END message if needed
    cout << "[handleAIMove] Game ended: " << moveResult.game->status
         << " result=" << moveResult.game->result << endl;
  }
}

void handleAIQuit(const ParsedMessage & /*pm*/, int fd) {
  lock_guard<mutex> lock(g_clients_mutex);
  auto &sender = g_clients[fd];

  // Nếu không còn trong game và game_id cũng rỗng, có thể đã xử lý AI_QUIT rồi
  // Chỉ gửi ERROR nếu thực sự chưa từng là AI game
  if (!sender.in_game && sender.game_id.empty() && sender.opponent_fd != -1) {
    // Không phải AI game và không trong game → ERROR
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"You are not in a game"});
    return;
  }

  // Nếu đã xử lý AI_QUIT rồi (in_game = false, game_id = "", opponent_fd = -1)
  // thì chỉ cần gửi lại INFO, không cần xử lý lại
  if (!sender.in_game && sender.game_id.empty() && sender.opponent_fd == -1) {
    cout << "[handleAIQuit] AI_QUIT already processed, sending confirmation" << endl;
    sendMessage(fd, MessageType::INFO,
                InfoPayload{nlohmann::json{
                    {"ai_quit", true}, {"message", "Game quit successfully"}}});
    return;
  }

  // Chỉ cho phép quit AI game (opponent_fd == -1)
  if (sender.opponent_fd != -1) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"AI_QUIT is only available in AI games"});
    return;
  }

  if (g_game_service == nullptr) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Game service not available"});
    return;
  }

  string game_id = sender.game_id;
  string username = sender.username;

  cout << "[handleAIQuit] Player " << username << " (fd=" << fd
       << ") quits AI game, game_id=" << game_id << endl;

  // Xóa game khỏi active games (không tính là resign, không cập nhật điểm)
  // Chỉ đơn giản là xóa game và reset player state
  try {
    // Reset player state
    sender.in_game = false;
    sender.opponent_fd = -1;
    sender.game_id = "";
    sender.current_turn = "";
    sender.is_red = false;

    // Xóa game từ database (không tính điểm, không archive)
    if (g_game_service != nullptr && !game_id.empty()) {
      bool deleted = g_game_service->deleteGame(game_id);
      if (deleted) {
        cout << "[handleAIQuit] Deleted game " << game_id
             << " from active_games (not archived, no rating change)" << endl;
      } else {
        cout << "[handleAIQuit] Game " << game_id
             << " not found or already deleted" << endl;
      }
    }

    // Gửi thông báo thành công
    sendMessage(fd, MessageType::INFO,
                InfoPayload{nlohmann::json{
                    {"ai_quit", true}, {"message", "Game quit successfully"}}});

    cout << "[handleAIQuit] AI game quit successfully for " << username << endl;
  } catch (const exception &e) {
    cerr << "[handleAIQuit] Error quitting AI game: " << e.what() << endl;
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"Failed to quit game"});
  }
}
