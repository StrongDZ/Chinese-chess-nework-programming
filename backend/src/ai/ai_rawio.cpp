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

void handleAIMatch(const ParsedMessage &pm, int fd) {
  lock_guard<mutex> lock(g_clients_mutex);
  auto &sender = g_clients[fd];
  if (sender.username.empty()) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Please LOGIN before starting AI match"});
    return;
  }

  // TODO: Check AI engine availability via Python API
  // if (!g_ai_engine.isReady()) {
  //   sendMessage(fd, MessageType::ERROR,
  //               ErrorPayload{"AI engine is not available"});
  //   return;
  // }

  // TODO: Cleanup any existing game state via Python API
  // if (g_game_state.hasGame(fd)) {
  //   g_game_state.endGame(fd);
  // }

  if (sender.in_game) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"You are already in a game"});
    return;
  }

  if (!pm.payload.has_value() ||
      !holds_alternative<AIMatchPayload>(*pm.payload)) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"AI_MATCH requires gamemode (easy/medium/hard)"});
    return;
  }

  const auto &p = get<AIMatchPayload>(*pm.payload);
  string gamemode = p.gamemode;

  // Validate gamemode
  if (gamemode != "easy" && gamemode != "medium" && gamemode != "hard") {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Invalid gamemode. Use: easy, medium, or hard"});
    return;
  }

  // TODO: Initialize AI game via Python API
  int ai_fd = -1; // AI doesn't have a real FD

  sender.in_game = true;
  sender.opponent_fd = ai_fd; // Mark as AI game
  sender.is_red =
      true; // Player always plays Red (goes first) when playing against AI

  // g_game_state.initializeGame(fd, ai_fd, difficulty);

  // Send GAME_START to player
  GameStartPayload gs;
  gs.opponent = ""; // Empty for AI games
  gs.game_mode = "ai_" + gamemode;
  gs.opponent_data = nlohmann::json();

  sendMessage(fd, MessageType::GAME_START, gs);
}

void handleSuggestMove(const ParsedMessage &pm, int fd) {
  (void)pm; // Unused parameter - function doesn't require payload
  lock_guard<mutex> lock(g_clients_mutex);
  auto &sender = g_clients[fd];

  if (!sender.in_game) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"You are not in a game"});
    return;
  }

  // TODO: Implement via Python API
  sendMessage(
      fd, MessageType::ERROR,
      ErrorPayload{
          "SUGGEST_MOVE not implemented - will be available via Python API"});
  return;

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

void handleAIMove(int player_fd) {
  (void)player_fd; // Suppress unused parameter warning
  // TODO: Implement via Python API
  // This function will call Python API to get AI move and apply it
  // For now, this is disabled - will be implemented when Python API is ready

  // TODO: Check if game exists via Python API
  // if (!g_game_state.hasGame(player_fd)) {
  //   return; // Not an AI game
  // }

  // TODO: Check AI engine availability via Python API
  // if (!g_ai_engine.isReady()) {
  //   return; // AI engine not available
  // }

  // TODO: Get current game state and difficulty via Python API
  // string position_str = g_game_state.getPositionString(player_fd);
  // if (position_str.empty()) {
  //   return;
  // }

  // TODO: Call Python API to get AI move
  // AIDifficulty difficulty = g_game_state.getAIDifficulty(player_fd);
  // string ucci_move = callPythonAPI("get_best_move", position_str,
  // difficulty); MovePayload ai_move = parseUCIMove(ucci_move);
  // g_game_state.applyMove(player_fd, ai_move);
  // pushAIMessage(player_fd, MessageType::MOVE, ai_move);
}

