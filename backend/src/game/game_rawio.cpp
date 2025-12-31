#include "protocol/handle_socket.h"
#include "protocol/message_types.h"
#include "protocol/server.h"
#include <cstring>
#include <map>
#include <mutex>
#include <string>
#include <variant>

using namespace std;

// External global variables from server.cpp
extern map<int, PlayerInfo> g_clients;
extern map<string, int> g_username_to_fd;
extern mutex g_clients_mutex;

// Forward declaration
void handleAIMove(int player_fd);

void handleChallenge(const ParsedMessage &pm, int fd) {
  lock_guard<mutex> lock(g_clients_mutex);
  auto &sender = g_clients[fd];
  if (sender.username.empty()) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Please LOGIN before challenging"});
    return;
  }
  if (!pm.payload.has_value() ||
      !holds_alternative<ChallengeRequestPayload>(*pm.payload)) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"CHALLENGE_REQUEST requires username"});
    return;
  }
  try {
    const auto &p = get<ChallengeRequestPayload>(*pm.payload);
    const string &target = p.to_user;
    if (target.empty()) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"CHALLENGE_REQUEST requires to_user"});
      return;
    }
    auto it = g_username_to_fd.find(target);
    if (it == g_username_to_fd.end()) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"Target user is offline"});
      return;
    }
    int target_fd = it->second;
    if (target_fd == fd) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"Cannot challenge yourself"});
      return;
    }
    // Forward challenge to target with from_user field
    ChallengeRequestPayload forwardPayload;
    forwardPayload.from_user = sender.username;
    forwardPayload.to_user = "";
    sendMessage(target_fd, MessageType::CHALLENGE_REQUEST, forwardPayload);
    sendMessage(fd, MessageType::INFO,
                InfoPayload{nlohmann::json{{"challenge_sent", true},
                                           {"target", target}}});
  } catch (...) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"Invalid payload"});
  }
}

void handleChallengeResponse(const ParsedMessage &pm, int fd) {
  lock_guard<mutex> lock(g_clients_mutex);
  auto &sender = g_clients[fd];
  if (sender.username.empty()) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Please LOGIN before responding to challenge"});
    return;
  }
  if (!pm.payload.has_value() ||
      !holds_alternative<ChallengeResponsePayload>(*pm.payload)) {
    sendMessage(
        fd, MessageType::ERROR,
        ErrorPayload{"CHALLENGE_RESPONSE requires username and accept"});
    return;
  }
  try {
    const auto &p = get<ChallengeResponsePayload>(*pm.payload);
    const string &challengerName = p.to_user;
    if (challengerName.empty()) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"CHALLENGE_RESPONSE requires to_user"});
      return;
    }
    if (!p.accept) {
      // Decline challenge - forward to challenger
      auto it = g_username_to_fd.find(challengerName);
      if (it != g_username_to_fd.end()) {
        ChallengeResponsePayload forwardPayload;
        forwardPayload.from_user = sender.username;
        forwardPayload.to_user = "";
        forwardPayload.accept = false;
        sendMessage(it->second, MessageType::CHALLENGE_RESPONSE,
                    forwardPayload);
      }
      sendMessage(fd, MessageType::INFO,
                  InfoPayload{nlohmann::json{{"challenge_declined", true}}});
      return;
    }
    // Accept challenge
    auto it = g_username_to_fd.find(challengerName);
    if (it == g_username_to_fd.end()) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"Challenger is offline"});
      return;
    }
    int challenger_fd = it->second;
    if (!g_clients.count(challenger_fd)) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"Challenger socket missing"});
      return;
    }
    auto &challenger = g_clients[challenger_fd];
    sender.in_game = true;
    sender.opponent_fd = challenger_fd;
    challenger.in_game = true;
    challenger.opponent_fd = fd;

    // TODO: Initialize game state for both players via Python API
    // g_game_state.initializeGame(challenger_fd, fd, AIDifficulty::EASY);
    // g_game_state.initializeGame(fd, challenger_fd, AIDifficulty::EASY);

    // Set player colors: Challenger is Red (goes first), Accepter is Black
    challenger.is_red = true;
    sender.is_red = false;

    // Send GAME_START to both players
    GameStartPayload gs1, gs2;
    gs1.opponent = challenger.username;
    gs1.game_mode = "classic";
    gs1.opponent_data = nlohmann::json();
    gs2.opponent = sender.username;
    gs2.game_mode = "classic";
    gs2.opponent_data = nlohmann::json();

    sendMessage(challenger_fd, MessageType::GAME_START, gs1);
    sendMessage(fd, MessageType::GAME_START, gs2);
  } catch (...) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"Invalid payload"});
  }
}

void handleMove(const ParsedMessage &pm, int fd) {
  lock_guard<mutex> lock(g_clients_mutex);
  auto &sender = g_clients[fd];
  if (!sender.in_game) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"You are not in a game"});
    return;
  }

  // Check if AI game (opponent_fd == -1) or PvP game
  // TODO: Check game state via Python API
  bool is_ai_game = (sender.opponent_fd == -1); // && g_game_state.hasGame(fd);

  if (!is_ai_game) {
    // PvP game - check opponent exists
    if (sender.opponent_fd < 0) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"You are not in a game"});
      return;
    }
    int opp = sender.opponent_fd;
    if (g_clients.count(opp) == 0) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"Opponent disconnected"});
      return;
    }
  }
  if (!pm.payload.has_value() || !holds_alternative<MovePayload>(*pm.payload)) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"MOVE requires piece/from/to"});
    return;
  }

  const MovePayload &move = get<MovePayload>(*pm.payload);

  // TODO: Get current board state via Python API
  char board[10][9];
  // bool has_board = g_game_state.getCurrentBoardArray(fd, board);
  // if (!has_board) {
  //   sendMessage(fd, MessageType::ERROR, ErrorPayload{"Game state not
  //   found"}); return;
  // }
  // For now, skip board validation - will be handled by Python API
  memset(board, ' ', sizeof(board));

  // TODO: Validate move via Python API
  // GameStateManager::BoardState board_state = g_game_state.getBoardState(fd);
  // if (!board_state.is_valid) {
  //   sendMessage(fd, MessageType::ERROR, ErrorPayload{"Invalid game state"});
  //   return;
  // }

  // Check if piece belongs to current player
  char piece = board[move.from.row][move.from.col];
  if (piece == ' ') {
    sendMessage(fd, MessageType::INVALID_MOVE,
                InvalidMovePayload{"No piece at source position"});
    return;
  }

  bool piece_is_red = (piece >= 'A' && piece <= 'Z');
  bool piece_is_black = (piece >= 'a' && piece <= 'z');
  bool player_is_red = sender.is_red;

  // TODO: Determine turn via Python API
  // For now, use simple logic: player is red if is_red flag is set
  bool is_red_turn = sender.is_red;
  // if (is_ai_game) {
  //   is_red_turn = board_state.player_turn;
  // } else {
  //   int move_count = board_state.moves.size();
  //   is_red_turn = (move_count % 2 == 0);
  // }

  // Validate piece color matches player and turn
  if (is_red_turn) {
    if (!player_is_red || !piece_is_red) {
      sendMessage(fd, MessageType::INVALID_MOVE,
                  InvalidMovePayload{"Not your turn or wrong piece"});
      return;
    }
  } else {
    if (player_is_red || !piece_is_black) {
      sendMessage(fd, MessageType::INVALID_MOVE,
                  InvalidMovePayload{"Not your turn or wrong piece"});
      return;
    }
  }

  // TODO: Validate move legality via Python API
  // if (!GameStateManager::isValidMoveOnBoard(board, move)) {
  //   sendMessage(
  //       fd, MessageType::INVALID_MOVE,
  //       InvalidMovePayload{"Illegal move: violates Chinese Chess rules"});
  //   return;
  // }

  // TODO: Update game state via Python API
  // if (!g_game_state.applyMove(fd, move)) {
  //   sendMessage(fd, MessageType::ERROR, ErrorPayload{"Failed to apply
  //   move"}); return;
  // }

  // TODO: If PvP, also update opponent's game state via Python API
  // if (!is_ai_game) {
  //   int opp = sender.opponent_fd;
  //   if (g_game_state.hasGame(opp)) {
  //     g_game_state.applyMove(opp, move);
  //   }
  // }

  // Send responses
  sendMessage(fd, MessageType::MOVE, move); // Echo to sender

  if (is_ai_game) {
    // Generate and send AI move
    handleAIMove(fd);
  } else {
    // PvP: send move to opponent
    int opp = sender.opponent_fd;
    sendMessage(opp, MessageType::MOVE, move);
  }
}

void handleMessage(const ParsedMessage &pm, int fd) {
  lock_guard<mutex> lock(g_clients_mutex);
  auto &sender = g_clients[fd];
  if (!sender.in_game || sender.opponent_fd < 0) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"You are not in a game"});
    return;
  }
  if (!pm.payload.has_value() ||
      !holds_alternative<MessagePayload>(*pm.payload)) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"MESSAGE requires message field"});
    return;
  }
  int opp = sender.opponent_fd;
  if (g_clients.count(opp) == 0) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"Opponent disconnected"});
    return;
  }
  sendMessage(opp, MessageType::MESSAGE, get<MessagePayload>(*pm.payload));
}

