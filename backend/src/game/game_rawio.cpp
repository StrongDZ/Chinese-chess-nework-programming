#include "game/game_controller.h"
#include "player_stat/player_stat_controller.h"
#include "protocol/handle_socket.h"
#include "protocol/message_types.h"
#include "protocol/server.h"
#include <algorithm>
#include <cstring>
#include <map>
#include <mutex>
#include <string>
#include <variant>
#include <vector>

using namespace std;

// External global variables from server.cpp
extern map<int, PlayerInfo> g_clients;
extern map<string, int> g_username_to_fd;
extern mutex g_clients_mutex;
extern PlayerStatController *g_player_stat_controller;
extern GameController *g_game_controller;

// Forward declaration
void handleAIMove(int player_fd);

// Quick matching queue structure
struct QuickMatchRequest {
  int fd;
  string username;
  int elo;
};

// Global quick matching queue
vector<QuickMatchRequest> g_quick_match_queue;
mutex g_quick_match_mutex;

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
    // Start game for challenger and accepter
    handleStartGame(challenger_fd, fd);
  } catch (...) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"Invalid payload"});
  }
}

void handleStartGame(int player1_fd, int player2_fd) {
  cout << "[handleStartGame] Starting game between fd=" << player1_fd
       << " and fd=" << player2_fd << endl;

  // NOTE: This function assumes g_clients_mutex is already locked by the caller
  // (handleQuickMatching, handleChallengeResponse, etc.)
  // We don't lock it again to avoid deadlock

  cout << "[handleStartGame] Mutex already locked by caller, proceeding..."
       << endl;

  // Check if both players are still available
  if (g_clients.count(player1_fd) == 0 || g_clients.count(player2_fd) == 0) {
    cout << "[handleStartGame] ERROR: Players disconnected - player1_fd="
         << player1_fd << " exists=" << (g_clients.count(player1_fd) > 0)
         << ", player2_fd=" << player2_fd
         << " exists=" << (g_clients.count(player2_fd) > 0) << endl;
    return; // Players disconnected
  }

  auto &player1 = g_clients[player1_fd];
  auto &player2 = g_clients[player2_fd];

  cout << "[handleStartGame] Player1: " << player1.username
       << " (in_game=" << player1.in_game << ")" << endl;
  cout << "[handleStartGame] Player2: " << player2.username
       << " (in_game=" << player2.in_game << ")" << endl;

  if (player1.in_game || player2.in_game) {
    cout << "[handleStartGame] ERROR: One or both players already in game"
         << endl;
    return; // Already in game
  }

  if (player1.username.empty() || player2.username.empty()) {
    cout << "[handleStartGame] ERROR: One or both players not logged in"
         << endl;
    return; // Not logged in
  }

  // Create game in database
  if (g_game_controller == nullptr) {
    cout << "[handleStartGame] ERROR: Game controller not initialized" << endl;
    sendMessage(player1_fd, MessageType::ERROR,
                ErrorPayload{"Game controller not initialized"});
    return;
  }

  nlohmann::json createRequest;
  createRequest["username"] = player1.username;
  createRequest["challenged_username"] = player2.username;
  createRequest["time_control"] = "classical";
  createRequest["rated"] = true;

  cout << "[handleStartGame] Creating game in database: " << player1.username
       << " vs " << player2.username << endl;

  nlohmann::json createResponse =
      g_game_controller->handleCreateGame(createRequest);

  cout << "[handleStartGame] Create game response: " << createResponse.dump()
       << endl;

  if (!createResponse.contains("status") ||
      createResponse["status"] != "success") {
    string errorMsg = createResponse.value("message", "Failed to create game");
    cout << "[handleStartGame] ERROR: Failed to create game - " << errorMsg
         << endl;
    sendMessage(player1_fd, MessageType::ERROR, ErrorPayload{errorMsg});
    return;
  }

  // Get game_id from response
  string game_id = "";
  if (createResponse.contains("game") &&
      createResponse["game"].contains("game_id")) {
    game_id = createResponse["game"]["game_id"].get<string>();
    cout << "[handleStartGame] Game created with ID: " << game_id << endl;
  } else {
    cout << "[handleStartGame] WARNING: No game_id in response" << endl;
  }

  // Determine who is red/black from created game
  bool player1IsRed = false;
  if (createResponse.contains("game")) {
    if (createResponse["game"].contains("red_player")) {
      string redPlayer = createResponse["game"]["red_player"].get<string>();
      player1IsRed = (redPlayer == player1.username);
      cout << "[handleStartGame] red player: " << redPlayer
           << ", Player1 is red: " << player1IsRed << endl;
    }
  }

  // Set up game for both players
  player1.in_game = true;
  player1.opponent_fd = player2_fd;
  player2.in_game = true;
  player2.opponent_fd = player1_fd;

  // Set player colors based on created game
  player1.is_red = player1IsRed;
  player2.is_red = !player1IsRed;

  cout << "[handleStartGame] Game state set - Player1: " << player1.username
       << " (red=" << player1.is_red << "), Player2: " << player2.username
       << " (red=" << player2.is_red << ")" << endl;

  // Send GAME_START to both players
  GameStartPayload gs1, gs2;
  gs1.opponent = player2.username;
  gs1.game_mode = "classical";
  gs1.opponent_data = nlohmann::json();
  gs1.opponent_data["player_is_red"] = player1.is_red;
  gs1.opponent_data["opponent_avatar_id"] = player2.avatar_id;
  if (!game_id.empty()) {
    gs1.opponent_data["game_id"] = game_id;
  }
  gs2.opponent = player1.username;
  gs2.game_mode = "classical";
  gs2.opponent_data = nlohmann::json();
  gs2.opponent_data["player_is_red"] = player2.is_red;
  gs2.opponent_data["opponent_avatar_id"] = player1.avatar_id;
  if (!game_id.empty()) {
    gs2.opponent_data["game_id"] = game_id;
  }

  cout << "[handleStartGame] Sending GAME_START to player1 (fd=" << player1_fd
       << ", username=" << player1.username << ")" << endl;
  sendMessage(player1_fd, MessageType::GAME_START, gs1);

  cout << "[handleStartGame] Sending GAME_START to player2 (fd=" << player2_fd
       << ", username=" << player2.username << ")" << endl;
  sendMessage(player2_fd, MessageType::GAME_START, gs2);

  cout << "[handleStartGame] Game started successfully!" << endl;
}

void handleCancelQM(const ParsedMessage & /*pm*/, int fd) {
  lock_guard<mutex> clients_lock(g_clients_mutex);
  auto &sender = g_clients[fd];

  // Check if user is logged in
  if (sender.username.empty()) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Please LOGIN before canceling quick matching"});
    return;
  }

  // Remove player from quick match queue
  {
    lock_guard<mutex> queue_lock(g_quick_match_mutex);

    // Remove this player from queue
    g_quick_match_queue.erase(remove_if(g_quick_match_queue.begin(),
                                        g_quick_match_queue.end(),
                                        [&](const QuickMatchRequest &req) {
                                          // g_clients_mutex is already locked
                                          // by the outer scope
                                          return req.fd == fd;
                                        }),
                              g_quick_match_queue.end());
  }

  sendMessage(fd, MessageType::INFO,
              InfoPayload{nlohmann::json{{"quick_matching_cancelled", true}}});
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

void handleDrawRequest(const ParsedMessage & /*pm*/, int fd) {
  lock_guard<mutex> lock(g_clients_mutex);
  auto &sender = g_clients[fd];
  if (!sender.in_game || sender.opponent_fd < 0) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"You are not in a game"});
    return;
  }
  int opp = sender.opponent_fd;
  if (g_clients.count(opp) == 0) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"Opponent disconnected"});
    return;
  }

  auto &opponent = g_clients[opp];

  cout << "[DRAW_REQUEST] Player " << sender.username << " (fd=" << fd
       << ") requests draw. Sending to opponent " << opponent.username
       << " (fd=" << opp << ")" << endl;

  // Forward draw request to opponent
  DrawRequestPayload drawReq;
  sendMessage(opp, MessageType::DRAW_REQUEST, drawReq);

  cout << "[DRAW_REQUEST] Draw request sent successfully to opponent" << endl;

  // Try to update database if game controller is available
  if (g_game_controller != nullptr) {
    try {
      // Find active game for these players
      nlohmann::json listRequest;
      listRequest["username"] = sender.username;
      listRequest["filter"] = "active";
      nlohmann::json listResponse =
          g_game_controller->handleListGames(listRequest);

      if (listResponse.contains("status") &&
          listResponse["status"] == "success" &&
          listResponse.contains("games") && listResponse["games"].is_array() &&
          !listResponse["games"].empty()) {
        // Find game with this opponent
        for (const auto &game : listResponse["games"]) {
          if (game.contains("red_player") && game.contains("black_player")) {
            string redPlayer = game["red_player"].get<string>();
            string blackPlayer = game["black_player"].get<string>();

            if ((redPlayer == sender.username &&
                 blackPlayer == opponent.username) ||
                (redPlayer == opponent.username &&
                 blackPlayer == sender.username)) {
              // Found the game - update draw offer in database
              if (game.contains("game_id")) {
                string gameId = game["game_id"].get<string>();
                nlohmann::json offerRequest;
                offerRequest["username"] = sender.username;
                offerRequest["game_id"] = gameId;
                nlohmann::json offerResponse =
                    g_game_controller->handleOfferDraw(offerRequest);
                cout << "[DRAW_REQUEST] Database update result: "
                     << offerResponse.dump() << endl;
              }
              break;
            }
          }
        }
      }
    } catch (const exception &e) {
      cerr << "[DRAW_REQUEST] Error updating database: " << e.what() << endl;
    }
  }
}

void handleDrawResponse(const ParsedMessage &pm, int fd) {
  lock_guard<mutex> lock(g_clients_mutex);
  auto &sender = g_clients[fd];
  if (!sender.in_game || sender.opponent_fd < 0) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"You are not in a game"});
    return;
  }
  if (!pm.payload.has_value() ||
      !holds_alternative<DrawResponsePayload>(*pm.payload)) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"DRAW_RESPONSE requires accept_draw field"});
    return;
  }
  int opp = sender.opponent_fd;
  if (g_clients.count(opp) == 0) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"Opponent disconnected"});
    return;
  }
  const auto &drawResp = get<DrawResponsePayload>(*pm.payload);

  auto &opponent = g_clients[opp];

  cout << "[DRAW_RESPONSE] Player " << sender.username << " (fd=" << fd
       << ") responds to draw request: accept=" << drawResp.accept_draw << endl;

  // Forward response to opponent first
  cout << "[DRAW_RESPONSE] Sending DRAW_RESPONSE to opponent "
       << opponent.username << " (fd=" << opp << ")" << endl;
  sendMessage(opp, MessageType::DRAW_RESPONSE, drawResp);

  // If draw accepted, end game with draw
  if (drawResp.accept_draw) {
    cout << "[DRAW_RESPONSE] Draw accepted - ending game" << endl;

    // Try to update database if game controller is available
    if (g_game_controller != nullptr) {
      try {
        // Find active game for these players
        nlohmann::json listRequest;
        listRequest["username"] = sender.username;
        listRequest["filter"] = "active";
        nlohmann::json listResponse =
            g_game_controller->handleListGames(listRequest);

        if (listResponse.contains("status") &&
            listResponse["status"] == "success" &&
            listResponse.contains("games") &&
            listResponse["games"].is_array() &&
            !listResponse["games"].empty()) {
          // Find game with this opponent
          for (const auto &game : listResponse["games"]) {
            if (game.contains("red_player") && game.contains("black_player")) {
              string redPlayer = game["red_player"].get<string>();
              string blackPlayer = game["black_player"].get<string>();

              if ((redPlayer == sender.username &&
                   blackPlayer == opponent.username) ||
                  (redPlayer == opponent.username &&
                   blackPlayer == sender.username)) {
                // Found the game - update it
                if (game.contains("game_id")) {
                  string gameId = game["game_id"].get<string>();
                  nlohmann::json respondRequest;
                  respondRequest["username"] = sender.username;
                  respondRequest["game_id"] = gameId;
                  respondRequest["accept"] = true;
                  nlohmann::json respondResponse =
                      g_game_controller->handleRespondToDraw(respondRequest);
                  cout << "[DRAW_RESPONSE] Database update result: "
                       << respondResponse.dump() << endl;
                }
                break;
              }
            }
          }
        }
      } catch (const exception &e) {
        cerr << "[DRAW_RESPONSE] Error updating database: " << e.what() << endl;
      }
    }

    // Send GAME_END to both players
    GameEndPayload gp;
    gp.win_side = "draw";

    cout << "[DRAW_RESPONSE] Sending GAME_END (draw) to sender "
         << sender.username << " (fd=" << fd << ")" << endl;
    sendMessage(fd, MessageType::GAME_END, gp);

    cout << "[DRAW_RESPONSE] Sending GAME_END (draw) to opponent "
         << opponent.username << " (fd=" << opp << ")" << endl;
    sendMessage(opp, MessageType::GAME_END, gp);

    // Clean up game state
    sender.in_game = false;
    sender.opponent_fd = -1;
    g_clients[opp].in_game = false;
    g_clients[opp].opponent_fd = -1;

    cout << "[DRAW_RESPONSE] Draw accepted - game ended successfully" << endl;
  } else {
    cout << "[DRAW_RESPONSE] Draw declined by " << sender.username << endl;

    // Try to clear draw offer in database
    if (g_game_controller != nullptr) {
      try {
        // Find active game for these players
        nlohmann::json listRequest;
        listRequest["username"] = sender.username;
        listRequest["filter"] = "active";
        nlohmann::json listResponse =
            g_game_controller->handleListGames(listRequest);

        if (listResponse.contains("status") &&
            listResponse["status"] == "success" &&
            listResponse.contains("games") &&
            listResponse["games"].is_array() &&
            !listResponse["games"].empty()) {
          // Find game with this opponent
          for (const auto &game : listResponse["games"]) {
            if (game.contains("red_player") && game.contains("black_player")) {
              string redPlayer = game["red_player"].get<string>();
              string blackPlayer = game["black_player"].get<string>();

              if ((redPlayer == sender.username &&
                   blackPlayer == opponent.username) ||
                  (redPlayer == opponent.username &&
                   blackPlayer == sender.username)) {
                // Found the game - clear draw offer
                if (game.contains("game_id")) {
                  string gameId = game["game_id"].get<string>();
                  nlohmann::json respondRequest;
                  respondRequest["username"] = sender.username;
                  respondRequest["game_id"] = gameId;
                  respondRequest["accept"] = false;
                  nlohmann::json respondResponse =
                      g_game_controller->handleRespondToDraw(respondRequest);
                  cout << "[DRAW_RESPONSE] Database update result (decline): "
                       << respondResponse.dump() << endl;
                }
                break;
              }
            }
          }
        }
      } catch (const exception &e) {
        cerr << "[DRAW_RESPONSE] Error updating database: " << e.what() << endl;
      }
    }
  }
}

void handleResign(const ParsedMessage & /*pm*/, int fd) {
  lock_guard<mutex> lock(g_clients_mutex);
  if (g_clients.count(fd) == 0) {
    return;
  }
  auto &sender = g_clients[fd];
  if (!sender.in_game) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"You are not in a game"});
    return;
  }
  int opp = sender.opponent_fd;

  cout << "[RESIGN] Player " << sender.username << " (fd=" << fd
       << ") resigns. Opponent fd=" << opp << endl;

  // TODO: Cleanup AI game state via Python API
  // if (g_game_state.hasGame(fd)) {
  //   g_game_state.endGame(fd);
  // }

  if (opp >= 0 && g_clients.count(opp)) {
    // Regular PvP game - opponent wins
    auto &opponent = g_clients[opp];

    // Create separate payloads for each player
    GameEndPayload opponentWinPayload;
    opponentWinPayload.win_side = opponent.username; // Opponent wins

    GameEndPayload senderLosePayload;
    senderLosePayload.win_side =
        opponent.username; // Sender loses (opponent wins)

    // Send GAME_END to opponent first (they win)
    cout << "[RESIGN] Sending GAME_END to opponent " << opponent.username
         << " (fd=" << opp << ") - win_side=" << opponentWinPayload.win_side
         << endl;
    sendMessage(opp, MessageType::GAME_END, opponentWinPayload);

    // Send GAME_END to sender (they lose)
    cout << "[RESIGN] Sending GAME_END to sender " << sender.username
         << " (fd=" << fd << ") - win_side=" << senderLosePayload.win_side
         << endl;
    sendMessage(fd, MessageType::GAME_END, senderLosePayload);

    // Update game state for both players
    opponent.in_game = false;
    opponent.opponent_fd = -1;
    sender.in_game = false;
    sender.opponent_fd = -1;

    // Try to update database if game controller is available
    if (g_game_controller != nullptr) {
      try {
        // Find active game for these players
        nlohmann::json listRequest;
        listRequest["username"] = sender.username;
        listRequest["filter"] = "active";
        nlohmann::json listResponse =
            g_game_controller->handleListGames(listRequest);

        if (listResponse.contains("status") &&
            listResponse["status"] == "success" &&
            listResponse.contains("games") &&
            listResponse["games"].is_array() &&
            !listResponse["games"].empty()) {
          // Find game with this opponent
          for (const auto &game : listResponse["games"]) {
            if (game.contains("red_player") && game.contains("black_player")) {
              string redPlayer = game["red_player"].get<string>();
              string blackPlayer = game["black_player"].get<string>();

              if ((redPlayer == sender.username &&
                   blackPlayer == opponent.username) ||
                  (redPlayer == opponent.username &&
                   blackPlayer == sender.username)) {
                // Found the game - update it
                if (game.contains("game_id")) {
                  string gameId = game["game_id"].get<string>();
                  nlohmann::json resignRequest;
                  resignRequest["username"] = sender.username;
                  resignRequest["game_id"] = gameId;
                  nlohmann::json resignResponse =
                      g_game_controller->handleResign(resignRequest);
                  cout << "[RESIGN] Database update result: "
                       << resignResponse.dump() << endl;
                }
                break;
              }
            }
          }
        }
      } catch (const exception &e) {
        cerr << "[RESIGN] Error updating database: " << e.what() << endl;
      }
    }

    cout << "[RESIGN] Resignation processed successfully" << endl;
  } else {
    // AI game - just end it
    GameEndPayload gp;
    gp.win_side = "ai";
    sendMessage(fd, MessageType::GAME_END, gp);
    sender.in_game = false;
    sender.opponent_fd = -1;
    cout << "[RESIGN] AI game ended" << endl;
  }
}

void handleQuickMatching(const ParsedMessage & /*pm*/, int fd) {
  lock_guard<mutex> clients_lock(g_clients_mutex);
  auto &sender = g_clients[fd];

  // Check if user is logged in
  if (sender.username.empty()) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Please LOGIN before quick matching"});
    return;
  }

  // Check if already in game
  if (sender.in_game) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"You are already in a game"});
    return;
  }

  // Query ELO from database (default to 1200 if not found)
  int player_elo = 1200;
  if (g_player_stat_controller != nullptr) {
    try {
      nlohmann::json request;
      request["username"] = sender.username;
      request["time_control"] =
          "classical"; // Use classical rating for quick matching

      nlohmann::json response =
          g_player_stat_controller->handleGetStats(request);

      if (response.contains("status") && response["status"] == "success") {
        if (response.contains("stat") && response["stat"].contains("rating")) {
          player_elo = response["stat"]["rating"].get<int>();
        } else if (response.contains("stats") && response["stats"].is_array() &&
                   !response["stats"].empty() &&
                   response["stats"][0].contains("rating")) {
          // If multiple stats returned, use first one
          player_elo = response["stats"][0]["rating"].get<int>();
        }
      }
    } catch (...) {
      // If query fails, use default 1200
      player_elo = 1200;
    }
  }

  // Try to find a matching opponent
  QuickMatchRequest matched_opponent;
  bool found_match = false;

  {
    lock_guard<mutex> queue_lock(g_quick_match_mutex);

    // Remove any stale entries (disconnected clients)
    // Note: g_clients_mutex is already locked by clients_lock above, so we can
    // access g_clients directly
    g_quick_match_queue.erase(
        remove_if(g_quick_match_queue.begin(), g_quick_match_queue.end(),
                  [&](const QuickMatchRequest &req) {
                    // g_clients_mutex is already locked by the outer scope
                    return g_clients.count(req.fd) == 0 ||
                           g_clients[req.fd].in_game ||
                           g_clients[req.fd].username != req.username;
                  }),
        g_quick_match_queue.end());

    // Find matching opponent (within 300 ELO range)
    for (auto it = g_quick_match_queue.begin(); it != g_quick_match_queue.end();
         ++it) {
      int elo_diff = abs(it->elo - player_elo);
      if (elo_diff <= 300 && it->fd != fd) {
        // Check if opponent is still available
        if (g_clients.count(it->fd) > 0 && !g_clients[it->fd].in_game &&
            g_clients[it->fd].username == it->username) {
          matched_opponent = *it;
          found_match = true;
          g_quick_match_queue.erase(it);
          cout << "[QUICK_MATCH] Found match: " << sender.username
               << " (fd=" << fd << ", elo=" << player_elo << ") <-> "
               << matched_opponent.username << " (fd=" << matched_opponent.fd
               << ", elo=" << matched_opponent.elo << ")" << endl;
          break;
        }
      }
    }

    // If no match found, add current player to queue
    if (!found_match) {
      QuickMatchRequest new_request;
      new_request.fd = fd;
      new_request.username = sender.username;
      new_request.elo = player_elo;
      g_quick_match_queue.push_back(new_request);

      cout << "[QUICK_MATCH] Added to queue: " << sender.username
           << " (fd=" << fd << ", elo=" << player_elo
           << "), queue size=" << g_quick_match_queue.size() << endl;

      sendMessage(fd, MessageType::INFO,
                  InfoPayload{nlohmann::json{{"quick_matching", true},
                                             {"status", "waiting"}}});
      return;
    }
  }

  // Match found! Start game
  if (found_match) {
    int opponent_fd = matched_opponent.fd;

    // Double-check opponent is still available
    if (g_clients.count(opponent_fd) == 0 || g_clients[opponent_fd].in_game ||
        g_clients[opponent_fd].username != matched_opponent.username) {
      // Opponent no longer available, add both players back to queue
      // (opponent was removed from queue earlier, so add them back)
      cout << "[QUICK_MATCH] Match found but opponent unavailable: "
           << sender.username << " <-> " << matched_opponent.username << endl;

      lock_guard<mutex> queue_lock(g_quick_match_mutex);
      QuickMatchRequest new_request;
      new_request.fd = fd;
      new_request.username = sender.username;
      new_request.elo = player_elo;
      g_quick_match_queue.push_back(new_request);

      sendMessage(fd, MessageType::INFO,
                  InfoPayload{nlohmann::json{{"quick_matching", true},
                                             {"status", "waiting"}}});
      return;
    }

    // Start game for matched players
    cout << "[QUICK_MATCH] Starting game: " << sender.username << " <-> "
         << matched_opponent.username << endl;
    handleStartGame(fd, opponent_fd);
  }
}

void handleGameHistory(const ParsedMessage &pm, int fd) {
  lock_guard<mutex> lock(g_clients_mutex);
  auto &sender = g_clients[fd];

  if (sender.username.empty()) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Please LOGIN before requesting game history"});
    return;
  }

  if (!pm.payload.has_value() ||
      !holds_alternative<GameHistoryPayload>(*pm.payload)) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"GAME_HISTORY requires username"});
    return;
  }

  if (g_game_controller == nullptr) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Game controller not initialized"});
    return;
  }

  try {
    const auto &p = get<GameHistoryPayload>(*pm.payload);

    cout << "[GAME_HISTORY] Request from user: " << sender.username 
         << ", target: " << p.username 
         << ", limit: " << p.limit 
         << ", offset: " << p.offset << endl;

    // Build request JSON for controller
    nlohmann::json request;
    request["username"] = p.username;
    request["limit"] = p.limit;
    request["offset"] = p.offset;

    nlohmann::json response = g_game_controller->handleGetGameHistory(request);

    cout << "[GAME_HISTORY] Response status: " 
         << (response.contains("status") ? response["status"].get<string>() : "no status")
         << ", history count: " 
         << (response.contains("count") ? to_string(response["count"].get<int>()) : "no count")
         << endl;

    // Send response via GAME_HISTORY message type
    sendMessage(fd, MessageType::GAME_HISTORY, InfoPayload{response});
  } catch (const exception &e) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Failed to handle GAME_HISTORY: " + string(e.what())});
  } catch (...) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Failed to handle GAME_HISTORY"});
  }
}
