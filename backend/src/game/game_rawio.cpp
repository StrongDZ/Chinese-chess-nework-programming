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
  string mode;    // "classical" or "blitz"
  int time_limit; // Time limit in seconds
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

    // Store pending challenge info on target (so they can use it when
    // responding)
    auto &targetPlayer = g_clients[target_fd];
    targetPlayer.pending_challenge_mode = p.mode;
    targetPlayer.pending_challenge_time = p.time_limit;
    targetPlayer.pending_challenger = sender.username;

    // Forward challenge to target with from_user field and mode/time_limit
    ChallengeRequestPayload forwardPayload;
    forwardPayload.from_user = sender.username;
    forwardPayload.to_user = "";
    forwardPayload.mode = p.mode;
    forwardPayload.time_limit = p.time_limit;

    cout << "[CHALLENGE] " << sender.username << " challenges " << target
         << " with mode=" << p.mode << ", time_limit=" << p.time_limit << endl;

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

    // Get mode and time_limit from stored pending challenge info
    string challengeMode = sender.pending_challenge_mode;
    int challengeTime = sender.pending_challenge_time;

    // Clear pending challenge info
    sender.pending_challenge_mode = "";
    sender.pending_challenge_time = 0;
    sender.pending_challenger = "";

    if (!p.accept) {
      // Decline challenge - forward to challenger
      auto it = g_username_to_fd.find(challengerName);
      if (it != g_username_to_fd.end()) {
        ChallengeResponsePayload forwardPayload;
        forwardPayload.from_user = sender.username;
        forwardPayload.to_user = "";
        forwardPayload.accept = false;
        forwardPayload.mode = challengeMode;
        forwardPayload.time_limit = challengeTime;
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

    cout << "[CHALLENGE_RESPONSE] " << sender.username
         << " accepts challenge from " << challengerName
         << " with mode=" << challengeMode << ", time_limit=" << challengeTime
         << endl;

    // Start game for challenger and accepter with mode and time
    handleStartGame(challenger_fd, fd, challengeMode, challengeTime);
  } catch (...) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"Invalid payload"});
  }
}

void handleStartGame(int player1_fd, int player2_fd, const string &mode,
                     int time_limit) {
  cout << "[handleStartGame] Starting game between fd=" << player1_fd
       << " and fd=" << player2_fd << ", mode=" << mode
       << ", time_limit=" << time_limit << endl;

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
  createRequest["time_control"] = mode.empty() ? "classical" : mode;
  createRequest["rated"] = true;
  if (time_limit > 0) {
    createRequest["time_limit"] = time_limit;
  }

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
  player1.game_id = game_id;
  player1.current_turn = "red"; // Red always goes first

  player2.in_game = true;
  player2.opponent_fd = player1_fd;
  player2.game_id = game_id;
  player2.current_turn = "red"; // Red always goes first

  // Set player colors based on created game
  player1.is_red = player1IsRed;
  player2.is_red = !player1IsRed;

  cout << "[handleStartGame] Game state set - Player1: " << player1.username
       << " (red=" << player1.is_red << "), Player2: " << player2.username
       << " (red=" << player2.is_red << ")" << endl;

  // Send GAME_START to both players
  string gameMode = mode.empty() ? "classical" : mode;
  GameStartPayload gs1, gs2;
  gs1.opponent = player2.username;
  gs1.game_mode = gameMode;
  gs1.time_limit = time_limit;
  gs1.opponent_data = nlohmann::json();
  gs1.opponent_data["player_is_red"] = player1.is_red;
  gs1.opponent_data["opponent_avatar_id"] = player2.avatar_id;
  if (!game_id.empty()) {
    gs1.opponent_data["game_id"] = game_id;
  }
  gs2.opponent = player1.username;
  gs2.game_mode = gameMode;
  gs2.time_limit = time_limit;
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
  bool is_ai_game = (sender.opponent_fd == -1);

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

  // Validate turn based on current_turn tracking
  bool is_red_turn = (sender.current_turn == "red");
  bool player_is_red = sender.is_red;

  // Check if it's this player's turn
  if ((is_red_turn && !player_is_red) || (!is_red_turn && player_is_red)) {
    sendMessage(fd, MessageType::INVALID_MOVE,
                InvalidMovePayload{"Not your turn"});
    return;
  }

  // Calculate next turn
  string nextTurn = is_red_turn ? "black" : "red";

  // Save move to database if game_controller is available and we have a game_id
  if (g_game_controller != nullptr && !sender.game_id.empty()) {
    try {
      nlohmann::json moveRequest;
      moveRequest["username"] = sender.username;
      moveRequest["game_id"] = sender.game_id;
      moveRequest["from"]["x"] = move.from.col;
      moveRequest["from"]["y"] = move.from.row;
      moveRequest["to"]["x"] = move.to.col;
      moveRequest["to"]["y"] = move.to.row;
      moveRequest["piece"] = move.piece;
      moveRequest["captured"] = ""; // Will be determined by service
      moveRequest["notation"] = "";
      moveRequest["time_taken"] = 0;

      nlohmann::json moveResponse =
          g_game_controller->handleMakeMove(moveRequest);

      if (!moveResponse.contains("status") ||
          moveResponse["status"] != "success") {
        string errorMsg = moveResponse.value("message", "Invalid move");
        cout << "[MOVE] Database error: " << errorMsg << endl;
        sendMessage(fd, MessageType::INVALID_MOVE,
                    InvalidMovePayload{errorMsg});
        return;
      }

      cout << "[MOVE] Move saved to database: " << sender.username
           << " game_id=" << sender.game_id << endl;

    } catch (const exception &e) {
      cerr << "[MOVE] Exception saving move: " << e.what() << endl;
      // Continue anyway to not break gameplay
    }
  }

  // Update turn tracking for both players
  sender.current_turn = nextTurn;

  // NOTE: Do NOT echo MOVE back to sender - they have already moved locally
  // Only send to opponent to sync their board

  if (is_ai_game) {
    // Generate and send AI move
    handleAIMove(fd);
  } else {
    // PvP: send move to opponent and update their turn tracking
    int opp = sender.opponent_fd;
    if (g_clients.count(opp) > 0) {
      g_clients[opp].current_turn = nextTurn;
      sendMessage(opp, MessageType::MOVE, move);
    }
  }

  cout << "[MOVE] Move processed: " << sender.username << " from=("
       << move.from.row << "," << move.from.col << ")"
       << " to=(" << move.to.row << "," << move.to.col << ")"
       << " next_turn=" << nextTurn << endl;
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
  string game_id = sender.game_id;

  cout << "[DRAW_REQUEST] Player " << sender.username << " (fd=" << fd
       << ") requests draw. Sending to opponent " << opponent.username
       << " (fd=" << opp << "), game_id=" << game_id << endl;

  // Forward draw request to opponent
  DrawRequestPayload drawReq;
  sendMessage(opp, MessageType::DRAW_REQUEST, drawReq);

  cout << "[DRAW_REQUEST] Draw request sent successfully to opponent" << endl;

  // Update database with game_id directly
  if (g_game_controller != nullptr && !game_id.empty()) {
    try {
      nlohmann::json offerRequest;
      offerRequest["username"] = sender.username;
      offerRequest["game_id"] = game_id;
      nlohmann::json offerResponse =
          g_game_controller->handleOfferDraw(offerRequest);
      cout << "[DRAW_REQUEST] Database update result: " << offerResponse.dump()
           << endl;
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
  string game_id = sender.game_id;

  cout << "[DRAW_RESPONSE] Player " << sender.username << " (fd=" << fd
       << ") responds to draw request: accept=" << drawResp.accept_draw
       << ", game_id=" << game_id << endl;

  // Forward response to opponent first
  cout << "[DRAW_RESPONSE] Sending DRAW_RESPONSE to opponent "
       << opponent.username << " (fd=" << opp << ")" << endl;
  sendMessage(opp, MessageType::DRAW_RESPONSE, drawResp);

  // If draw accepted, end game with draw
  if (drawResp.accept_draw) {
    cout << "[DRAW_RESPONSE] Draw accepted - ending game" << endl;

    // Update database with game_id directly (this also calculates Elo for rated
    // games)
    if (g_game_controller != nullptr && !game_id.empty()) {
      try {
        nlohmann::json respondRequest;
        respondRequest["username"] = sender.username;
        respondRequest["game_id"] = game_id;
        respondRequest["accept"] = true;
        nlohmann::json respondResponse =
            g_game_controller->handleRespondToDraw(respondRequest);
        cout << "[DRAW_RESPONSE] Database update result: "
             << respondResponse.dump() << " (Elo calculated if rated game)"
             << endl;
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

    // Clean up game state for both players
    sender.in_game = false;
    sender.opponent_fd = -1;
    sender.game_id = "";
    sender.current_turn = "";

    opponent.in_game = false;
    opponent.opponent_fd = -1;
    opponent.game_id = "";
    opponent.current_turn = "";

    cout << "[DRAW_RESPONSE] Draw accepted - game ended successfully" << endl;
  } else {
    cout << "[DRAW_RESPONSE] Draw declined by " << sender.username << endl;

    // Clear draw offer in database
    if (g_game_controller != nullptr && !game_id.empty()) {
      try {
        nlohmann::json respondRequest;
        respondRequest["username"] = sender.username;
        respondRequest["game_id"] = game_id;
        respondRequest["accept"] = false;
        nlohmann::json respondResponse =
            g_game_controller->handleRespondToDraw(respondRequest);
        cout << "[DRAW_RESPONSE] Database update result (decline): "
             << respondResponse.dump() << endl;
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
  string game_id = sender.game_id;

  cout << "[RESIGN] Player " << sender.username << " (fd=" << fd
       << ") resigns. Opponent fd=" << opp << ", game_id=" << game_id << endl;

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

    // Update database with game_id directly (this also calculates Elo)
    if (g_game_controller != nullptr && !game_id.empty()) {
      try {
        nlohmann::json resignRequest;
        resignRequest["username"] = sender.username;
        resignRequest["game_id"] = game_id;
        nlohmann::json resignResponse =
            g_game_controller->handleResign(resignRequest);
        cout << "[RESIGN] Database update result: " << resignResponse.dump()
             << " (Elo calculated if rated game)" << endl;
      } catch (const exception &e) {
        cerr << "[RESIGN] Error updating database: " << e.what() << endl;
      }
    }

    // Clear game state for both players
    opponent.in_game = false;
    opponent.opponent_fd = -1;
    opponent.game_id = "";
    opponent.current_turn = "";

    sender.in_game = false;
    sender.opponent_fd = -1;
    sender.game_id = "";
    sender.current_turn = "";

    cout << "[RESIGN] Resignation processed successfully" << endl;
  } else {
    // AI game - just end it
    GameEndPayload gp;
    gp.win_side = "ai";
    sendMessage(fd, MessageType::GAME_END, gp);
    sender.in_game = false;
    sender.opponent_fd = -1;
    sender.game_id = "";
    sender.current_turn = "";
    cout << "[RESIGN] AI game ended" << endl;
  }
}

void handleQuickMatching(const ParsedMessage &pm, int fd) {
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

  // Parse mode and time_limit from payload
  string matchMode = "classical";
  int matchTimeLimit = 0;

  if (pm.payload.has_value() &&
      holds_alternative<QuickMatchingPayload>(*pm.payload)) {
    const auto &p = get<QuickMatchingPayload>(*pm.payload);
    matchMode = p.mode.empty() ? "classical" : p.mode;
    matchTimeLimit = p.time_limit;
  }

  cout << "[QUICK_MATCH] Request from " << sender.username
       << " mode=" << matchMode << ", time_limit=" << matchTimeLimit << endl;

  // Query ELO from database (default to 1200 if not found)
  int player_elo = 1200;
  if (g_player_stat_controller != nullptr) {
    try {
      nlohmann::json request;
      request["username"] = sender.username;
      request["time_control"] = matchMode; // Use matching mode for rating

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

    // Find matching opponent (within 300 ELO range AND same mode AND same
    // time_limit)
    for (auto it = g_quick_match_queue.begin(); it != g_quick_match_queue.end();
         ++it) {
      int elo_diff = abs(it->elo - player_elo);
      // Match only if same mode AND same time_limit
      bool sameMode = (it->mode == matchMode);
      bool sameTime = (it->time_limit == matchTimeLimit);

      if (elo_diff <= 300 && it->fd != fd && sameMode && sameTime) {
        // Check if opponent is still available
        if (g_clients.count(it->fd) > 0 && !g_clients[it->fd].in_game &&
            g_clients[it->fd].username == it->username) {
          matched_opponent = *it;
          found_match = true;
          g_quick_match_queue.erase(it);
          cout << "[QUICK_MATCH] Found match: " << sender.username
               << " (fd=" << fd << ", elo=" << player_elo
               << ", mode=" << matchMode << ", time=" << matchTimeLimit
               << ") <-> " << matched_opponent.username
               << " (fd=" << matched_opponent.fd
               << ", elo=" << matched_opponent.elo
               << ", mode=" << matched_opponent.mode
               << ", time=" << matched_opponent.time_limit << ")" << endl;
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
      new_request.mode = matchMode;
      new_request.time_limit = matchTimeLimit;
      g_quick_match_queue.push_back(new_request);

      cout << "[QUICK_MATCH] Added to queue: " << sender.username
           << " (fd=" << fd << ", elo=" << player_elo << ", mode=" << matchMode
           << ", time=" << matchTimeLimit
           << "), queue size=" << g_quick_match_queue.size() << endl;

      sendMessage(fd, MessageType::INFO,
                  InfoPayload{nlohmann::json{{"quick_matching", true},
                                             {"status", "waiting"},
                                             {"mode", matchMode},
                                             {"time_limit", matchTimeLimit}}});
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
      new_request.mode = matchMode;
      new_request.time_limit = matchTimeLimit;
      g_quick_match_queue.push_back(new_request);

      sendMessage(fd, MessageType::INFO,
                  InfoPayload{nlohmann::json{{"quick_matching", true},
                                             {"status", "waiting"},
                                             {"mode", matchMode},
                                             {"time_limit", matchTimeLimit}}});
      return;
    }

    // Start game for matched players with mode and time
    cout << "[QUICK_MATCH] Starting game: " << sender.username << " <-> "
         << matched_opponent.username << " (mode=" << matchMode
         << ", time=" << matchTimeLimit << ")" << endl;
    handleStartGame(fd, opponent_fd, matchMode, matchTimeLimit);
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
         << ", target: " << p.username << ", limit: " << p.limit
         << ", offset: " << p.offset << endl;

    // Build request JSON for controller
    nlohmann::json request;
    request["username"] = p.username;
    request["limit"] = p.limit;
    request["offset"] = p.offset;

    nlohmann::json response = g_game_controller->handleGetGameHistory(request);

    cout << "[GAME_HISTORY] Response status: "
         << (response.contains("status") ? response["status"].get<string>()
                                         : "no status")
         << ", history count: "
         << (response.contains("count")
                 ? to_string(response["count"].get<int>())
                 : "no count")
         << endl;

    // Send response via GAME_HISTORY message type
    sendMessage(fd, MessageType::GAME_HISTORY, InfoPayload{response});
  } catch (const exception &e) {
    sendMessage(
        fd, MessageType::ERROR,
        ErrorPayload{"Failed to handle GAME_HISTORY: " + string(e.what())});
  } catch (...) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Failed to handle GAME_HISTORY"});
  }
}

void handleReplayRequest(const ParsedMessage &pm, int fd) {
  lock_guard<mutex> lock(g_clients_mutex);
  auto &sender = g_clients[fd];

  if (sender.username.empty()) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Please LOGIN before requesting replay"});
    return;
  }

  if (!pm.payload.has_value() ||
      !holds_alternative<ReplayRequestPayload>(*pm.payload)) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"REPLAY_REQUEST requires game_id"});
    return;
  }

  if (g_game_controller == nullptr) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Game controller not initialized"});
    return;
  }

  try {
    const auto &p = get<ReplayRequestPayload>(*pm.payload);

    cout << "[REPLAY_REQUEST] Request from user: " << sender.username 
         << ", game_id: " << p.game_id << endl;

    // Build request for handleGetGameDetails
    nlohmann::json request;
    request["game_id"] = p.game_id;

    // Call game controller to get full game details with moves
    nlohmann::json response = g_game_controller->handleGetGameDetails(request);

    cout << "[REPLAY_REQUEST] Response status: " << response["status"].get<string>() << endl;
    if (response.contains("game") && response["game"].contains("moves")) {
      cout << "[REPLAY_REQUEST] Found " << response["game"]["moves"].size() << " moves" << endl;
    } else {
      cout << "[REPLAY_REQUEST] No moves found in response" << endl;
    }

    // Send response via INFO message type (để InfoHandler có thể xử lý)
    sendMessage(fd, MessageType::INFO, InfoPayload{response});
  } catch (const exception &e) {
    cout << "[REPLAY_REQUEST] Exception: " << e.what() << endl;
    nlohmann::json errorResponse;
    errorResponse["status"] = "error";
    errorResponse["message"] = string("Failed to get replay data: ") + e.what();
    sendMessage(fd, MessageType::INFO, InfoPayload{errorResponse});
  } catch (...) {
    cout << "[REPLAY_REQUEST] Unknown exception" << endl;
    nlohmann::json errorResponse;
    errorResponse["status"] = "error";
    errorResponse["message"] = "Failed to get replay data";
    sendMessage(fd, MessageType::INFO, InfoPayload{errorResponse});
  }
}
