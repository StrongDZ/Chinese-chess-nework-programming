// ===================== System Headers ===================== //
#include <arpa/inet.h>
#include <errno.h>
#include <fcntl.h>
#include <netinet/in.h>
#include <signal.h>
#include <sys/epoll.h>
#include <sys/socket.h>
#include <unistd.h>

#include <cstdlib>
#include <cstring>
#include <iostream>
#include <map>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

// ===================== Protocol Headers ===================== //
#include "protocol/handle_socket.h"
#include "protocol/message_types.h"
#include "protocol/server.h"
#include "protocol/thread_pool.h"

// ===================== Database Layer ===================== //
#include "database/mongodb_client.h"

// ===================== Controller Layer ===================== //
#include "ai/ai_controller.h"
#include "ai/ai_service.h"
#include "auth/auth_controller.h"
#include "auth/auth_repository.h"
#include "friend/friend_controller.h"
#include "game/game_controller.h"
#include "player_stat/player_stat_controller.h"

// ===================== Raw IO Handlers ===================== //
// Handler implementations are in separate rawio.cpp files:
// - auth/auth_rawio.cpp: handleLogin, handleRegister, handleLogout
// - friend/friend_rawio.cpp: handleRequestAddFriend, handleResponseAddFriend
// - game/game_rawio.cpp: handleChallenge, handleChallengeResponse, handleMove,
// handleMessage, handleGameHistory
// - ai/ai_rawio.cpp: handleAIMatch, handleSuggestMove, handleAIMove
// These files include their own controller headers as needed.

using namespace std;

// Global controllers (non-static so they can be accessed from other files)
AuthController *g_auth_controller = nullptr;
FriendController *g_friend_controller = nullptr;
GameController *g_game_controller = nullptr;
PlayerStatController *g_player_stat_controller = nullptr;
AIController *g_ai_controller = nullptr;

// Global services (for AI controller)
GameService *g_game_service = nullptr;
AIService *g_ai_service = nullptr;

// Global repositories
AuthRepository *g_auth_repo = nullptr;

// Global client management (protected by g_clients_mutex)
map<int, PlayerInfo> g_clients;    // fd -> PlayerInfo
map<string, int> g_username_to_fd; // username -> fd
mutex g_clients_mutex;

int main(int argc, char **argv) {
  // Ignore SIGPIPE to prevent server crash when Pikafish process crashes
  // This ensures robustness when writing to broken pipes
  signal(SIGPIPE, SIG_IGN);

  int port = 8080;
  if (argc >= 2)
    port = stoi(argv[1]);

  int server_fd = socket(AF_INET, SOCK_STREAM, 0);
  if (server_fd < 0) {
    perror("socket");
    return 1;
  }

  int opt = 1;
  setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

  sockaddr_in addr{};
  addr.sin_family = AF_INET;
  addr.sin_addr.s_addr = INADDR_ANY; // 0.0.0.0
  addr.sin_port = htons(port);
  if (::bind(server_fd, (sockaddr *)&addr, sizeof(addr)) < 0) {
    perror("bind");
    close(server_fd);
    return 1;
  }

  if (listen(server_fd, 64) < 0) {
    perror("listen");
    close(server_fd);
    return 1;
  }

  cout << "Server listening on 0.0.0.0:" << port << endl;

  // Initialize MongoDB
  MongoDBClient mongoClient;
  const char *mongoConnStr = getenv("MONGODB_URI");
  const char *mongoDbName = getenv("MONGODB_DB");
  std::string connStr =
      mongoConnStr ? mongoConnStr : "mongodb://localhost:27017";
  std::string dbName = mongoDbName ? mongoDbName : "chinese_chess";

  if (!mongoClient.connect(connStr, dbName)) {
    cerr << "Warning: Failed to connect to MongoDB. Database features will be "
            "unavailable."
         << endl;
  } else {
    cout << "MongoDB connected successfully." << endl;
  }

  // Initialize Repositories
  AuthRepository authRepo(mongoClient);
  FriendRepository friendRepo(mongoClient);
  GameRepository gameRepo(mongoClient);
  PlayerStatRepository playerStatRepo(mongoClient);

  // Initialize Services
  AuthService authService(authRepo);
  FriendService friendService(friendRepo);
  GameService gameService(gameRepo);
  PlayerStatService playerStatService(playerStatRepo);
  AIService aiService;

  // Initialize AI service (non-blocking - don't fail server startup if AI fails)
  bool aiInitialized = aiService.initialize();
  if (!aiInitialized) {
    cerr << "[Server] Warning: AI service initialization failed. AI features "
            "will be unavailable."
         << endl;
    cerr << "[Server] Make sure AI/ai.py, ai_persistent_wrapper.py and pikafish are available." << endl;
    cerr << "[Server] Server will continue without AI features." << endl;
  } else {
    cout << "[Server] AI service initialized successfully." << endl;
  }
  
  // Continue server startup even if AI failed

  // Initialize Controllers (as global variables)
  AuthController authController(authService);
  FriendController friendController(friendService);
  GameController gameController(gameService);
  PlayerStatController playerStatController(playerStatService);
  AIController aiController(aiService, gameService);

  // Set global pointers
  g_auth_controller = &authController;
  g_friend_controller = &friendController;
  g_game_controller = &gameController;
  g_player_stat_controller = &playerStatController;
  g_ai_controller = &aiController;
  g_game_service = &gameService;
  g_ai_service = &aiService;
  g_auth_repo = &authRepo;

  // TODO: Initialize Python AI service via HTTP API
  // AI features will be called via Python API endpoints instead of C++ wrapper
  cout << "AI service will be called via Python API endpoints" << endl;

  // Thread pool for processing messages (especially AI)
  ThreadPool threadPool(4); // 4 worker threads

  // Start worker threads to process client message queue
  bool stop_workers = false;
  std::vector<std::thread> client_workers =
      startClientMessageWorkers(stop_workers);

  // Setup epoll
  int epoll_fd = epoll_create1(0);
  if (epoll_fd < 0) {
    perror("epoll_create1");
    close(server_fd);
    return 1;
  }

  epoll_event ev;
  ev.events = EPOLLIN;
  ev.data.fd = server_fd;
  if (epoll_ctl(epoll_fd, EPOLL_CTL_ADD, server_fd, &ev) < 0) {
    perror("epoll_ctl: server_fd");
    close(epoll_fd);
    close(server_fd);
    return 1;
  }

  const int MAX_EVENTS = 64;
  epoll_event events[MAX_EVENTS];

  cout << "Using epoll() with thread pool (4 workers)" << endl;

  while (true) {
    // Process AI message queue first (non-blocking check)
    processAIMessageQueue();

    // Use epoll with 100ms timeout to periodically check AI message queue
    int nfds = epoll_wait(epoll_fd, events, MAX_EVENTS, 100);

    if (nfds < 0) {
      if (errno == EINTR) {
        // Interrupted by signal, continue to check AI queue again
        continue;
      }
      perror("epoll_wait");
      break; // Serious error, exit
    }

    if (nfds == 0) {
      // Timeout - no events from clients, but we already checked AI queue above
      // Continue to check AI queue again in next iteration
      continue;
    }

    // Process events from epoll
    for (int i = 0; i < nfds; i++) {
      int fd = events[i].data.fd;

      // New connection
      if (fd == server_fd) {
        int client_fd = accept(server_fd, nullptr, nullptr);
        if (client_fd >= 0) {
          // Set socket to non-blocking mode (REQUIRED for edge-triggered epoll)
          int flags = fcntl(client_fd, F_GETFL, 0);
          if (flags < 0 || fcntl(client_fd, F_SETFL, flags | O_NONBLOCK) < 0) {
            perror("fcntl: set non-blocking");
            close(client_fd);
            continue;
          }

          {
            lock_guard<mutex> lock(g_clients_mutex);
            g_clients[client_fd] = PlayerInfo{-1, string(), false, -1, false, 1,
                                              "", "",       "",    0,  ""};
          }
          // Initialize read buffer for this connection
          initReadBuffer(client_fd);
          cout << "New connection: fd=" << client_fd << endl;

          // Add client to epoll
          ev.events = EPOLLIN | EPOLLET; // Edge-triggered mode
          ev.data.fd = client_fd;
          if (epoll_ctl(epoll_fd, EPOLL_CTL_ADD, client_fd, &ev) < 0) {
            perror("epoll_ctl: client_fd");
            cleanupReadBuffer(client_fd);
            close(client_fd);
            {
              lock_guard<mutex> lock(g_clients_mutex);
              g_clients.erase(client_fd);
            }
            continue;
          }
          // Epoll will trigger when data arrives
        }
        continue;
      }

      // Client message - receive in main thread, process in thread pool
      // In edge-triggered mode, we need to read ALL available data in a loop
      // until we get EAGAIN (no more data) or connection closes
      bool shouldClose = false;
      {
        lock_guard<mutex> lock(g_clients_mutex);
        if (g_clients.count(fd) == 0) {
          continue; // Client already removed
        }
      }

      // Read all available messages in edge-triggered mode
      // Keep reading until we get EAGAIN (no more data) or connection closes
      while (true) {
        string msg;
        if (!recvMessage(fd, msg)) {
          int saved_errno = errno;
          if (saved_errno == EAGAIN || saved_errno == EWOULDBLOCK) {
            // No more data available - normal in edge-triggered mode
            break; // Keep connection open
          }
          // Connection closed or error
          {
            lock_guard<mutex> lock(g_clients_mutex);
            auto it = g_clients.find(fd);
            if (it != g_clients.end() && !it->second.username.empty()) {
              cout << "Client disconnected: fd=" << fd
                   << " user=" << it->second.username << endl;
            }
          }
          shouldClose = true;
          break;
        }

        // Successfully received a message
        auto pm = parseMessage(msg);
        cout << "[RECV fd=" << fd << "] " << messageTypeToString(pm.type) << " "
             << msg << endl;

        // Push message to queue for processing by worker threads
        pushClientMessage(pm, fd);
      }

      // Handle client disconnection
      if (shouldClose) {
        epoll_ctl(epoll_fd, EPOLL_CTL_DEL, fd, nullptr);
        {
          lock_guard<mutex> lock(g_clients_mutex);
          auto itc = g_clients.find(fd);
          if (itc != g_clients.end()) {
            auto &disconnectedPlayer = itc->second;

            if (!disconnectedPlayer.username.empty()) {
              g_username_to_fd.erase(disconnectedPlayer.username);
              cout << "[DISCONNECT] Player " << disconnectedPlayer.username
                   << " disconnected" << endl;
            }

            int opp = disconnectedPlayer.opponent_fd;
            string game_id = disconnectedPlayer.game_id;

            if (opp >= 0 && g_clients.count(opp)) {
              auto &opponent = g_clients[opp];

              // If there's an active game, opponent wins by abandonment
              if (disconnectedPlayer.in_game && !game_id.empty() &&
                  g_game_controller != nullptr) {
                try {
                  // Determine result - opponent wins
                  string result = opponent.is_red ? "red_win" : "black_win";

                  nlohmann::json endRequest;
                  endRequest["game_id"] = game_id;
                  endRequest["result"] = result;
                  endRequest["termination"] = "abandonment";
                  nlohmann::json endResponse =
                      g_game_controller->handleEndGame(endRequest);
                  cout << "[DISCONNECT] Game ended due to abandonment: "
                       << endResponse.dump() << " (Elo calculated if rated)"
                       << endl;

                  // Send GAME_END to opponent
                  GameEndPayload gp;
                  gp.win_side = opponent.username;
                  sendMessage(opp, MessageType::GAME_END, gp);

                } catch (const exception &e) {
                  cerr << "[DISCONNECT] Error ending game: " << e.what()
                       << endl;
                }
              }

              // Clear opponent's game state
              opponent.in_game = false;
              opponent.opponent_fd = -1;
              opponent.game_id = "";
              opponent.current_turn = "";

              // Notify opponent of disconnection
              sendMessage(
                  opp, MessageType::INFO,
                  InfoPayload{nlohmann::json{{"opponent_disconnected", true}}});
            }
          }
          // Cleanup read buffer before closing
          cleanupReadBuffer(fd);
          close(fd);
          g_clients.erase(fd);
        }
      }
    }
  }

  // Stop worker threads
  stopClientMessageWorkers(client_workers, stop_workers);

  close(epoll_fd);
  close(server_fd);
  return 0;
}

// ===================== Message Processing ===================== //

void processMessage(const ParsedMessage &pm, int fd) {
  string username;
  {
    lock_guard<mutex> lock(g_clients_mutex);
    if (g_clients.count(fd) == 0) {
      cout << "[PROCESS fd=" << fd << "] Client not found, ignoring" << endl;
      return; // Client disconnected
    }
    username = g_clients[fd].username;
  }

  if (!username.empty()) {
    cout << "[PROCESS fd=" << fd << " user=" << username << "] "
         << messageTypeToString(pm.type) << endl;
  } else {
    cout << "[PROCESS fd=" << fd << "] " << messageTypeToString(pm.type)
         << endl;
  }

  switch (pm.type) {
  case MessageType::LOGIN:
    handleLogin(pm, fd);
    break;
  case MessageType::REGISTER:
    handleRegister(pm, fd);
    break;
  case MessageType::PLAYER_LIST: {
    lock_guard<mutex> lock(g_clients_mutex);
    nlohmann::json arr = nlohmann::json::array();
    for (auto &p : g_clients) {
      if (!p.second.username.empty()) {
        // Return player info with in_game status
        nlohmann::json playerInfo;
        playerInfo["username"] = p.second.username;
        playerInfo["in_game"] = p.second.in_game;
        arr.push_back(playerInfo);
      }
    }
    sendMessage(fd, MessageType::INFO, InfoPayload{arr});
    break;
  }
  case MessageType::AUTHENTICATED:
    sendMessage(fd, MessageType::AUTHENTICATED);
    break;
  case MessageType::QUICK_MATCHING:
    handleQuickMatching(pm, fd);
    break;
  case MessageType::CHALLENGE_REQUEST:
    handleChallenge(pm, fd);
    break;

  case MessageType::CHALLENGE_RESPONSE:
    handleChallengeResponse(pm, fd);
    break;
  case MessageType::AI_MATCH:
    // This should be handled in thread pool, but if called directly:
    handleAIMatch(pm, fd);
    break;
  case MessageType::CUSTOM_GAME:
    handleCustomGame(pm, fd);
    break;
  case MessageType::SUGGEST_MOVE:
    handleSuggestMove(pm, fd);
    break;
  case MessageType::AI_QUIT:
    handleAIQuit(pm, fd);
    break;
  case MessageType::USER_STATS:
    handleUserStats(pm, fd);
    break;
  case MessageType::LEADER_BOARD:
    handleLeaderBoard(pm, fd);
    break;
  case MessageType::MOVE:
    handleMove(pm, fd);
    break;
  case MessageType::INVALID_MOVE:
    // Server sends this to client, not received from client
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"INVALID_MOVE not a client command"});
    break;
  case MessageType::MESSAGE:
    handleMessage(pm, fd);
    break;
  case MessageType::GAME_END: {
    if (!pm.payload.has_value() ||
        !holds_alternative<GameEndPayload>(*pm.payload)) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"GAME_END requires payload win_side"});
      break;
    }
    lock_guard<mutex> lock(g_clients_mutex);
    if (g_clients.count(fd) == 0) {
      return;
    }
    auto &sender = g_clients[fd];
    if (!sender.in_game) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"You are not in a game"});
      break;
    }
    int opp = sender.opponent_fd;
    string game_id = sender.game_id;
    const auto &gameEndPayload = get<GameEndPayload>(*pm.payload);

    cout << "[GAME_END] Player " << sender.username
         << " reports game end: win_side=" << gameEndPayload.win_side
         << ", game_id=" << game_id << endl;

    if (opp >= 0 && g_clients.count(opp)) {
      // Regular PvP game
      auto &opponent = g_clients[opp];

      // Update database to end game and calculate Elo FIRST
      int redRatingChange = 0;
      int blackRatingChange = 0;
      int redNewRating = 0;
      int blackNewRating = 0;
      
      if (g_game_controller != nullptr && !game_id.empty()) {
        try {
          // Determine result based on win_side
          string result;
          if (gameEndPayload.win_side == "draw") {
            result = "draw";
          } else if (gameEndPayload.win_side == sender.username) {
            // Sender wins
            result = sender.is_red ? "red_win" : "black_win";
          } else if (gameEndPayload.win_side == opponent.username) {
            // Opponent wins
            result = opponent.is_red ? "red_win" : "black_win";
          } else if (gameEndPayload.win_side == "red") {
            result = "red_win";
          } else if (gameEndPayload.win_side == "black") {
            result = "black_win";
          } else {
            // Try to infer from username
            result =
                sender.is_red ? "black_win" : "red_win"; // Assume opponent won
          }

          nlohmann::json endRequest;
          endRequest["game_id"] = game_id;
          endRequest["result"] = result;
          endRequest["termination"] = "checkmate";
          nlohmann::json endResponse =
              g_game_controller->handleEndGame(endRequest);
          cout << "[GAME_END] Database update result: " << endResponse.dump()
               << " (Elo calculated if rated game)" << endl;
          
          // Extract rating changes from response
          if (endResponse.contains("red_rating_change")) {
            redRatingChange = endResponse["red_rating_change"].get<int>();
            blackRatingChange = endResponse["black_rating_change"].get<int>();
            redNewRating = endResponse["red_new_rating"].get<int>();
            blackNewRating = endResponse["black_new_rating"].get<int>();
          }
        } catch (const exception &e) {
          cerr << "[GAME_END] Error updating database: " << e.what() << endl;
        }
      }

      // Create GameEndPayload with rating changes
      GameEndPayload payloadWithRating;
      payloadWithRating.win_side = gameEndPayload.win_side;
      payloadWithRating.red_rating_change = redRatingChange;
      payloadWithRating.black_rating_change = blackRatingChange;
      payloadWithRating.red_new_rating = redNewRating;
      payloadWithRating.black_new_rating = blackNewRating;

      // Send GAME_END with rating changes to both players
      sendMessage(fd, MessageType::GAME_END, payloadWithRating);
      sendMessage(opp, MessageType::GAME_END, payloadWithRating);

      // Clear game state for both players
      opponent.in_game = false;
      opponent.opponent_fd = -1;
      opponent.game_id = "";
      opponent.current_turn = "";
    }

    // Clear sender's game state
    sender.in_game = false;
    sender.opponent_fd = -1;
    sender.game_id = "";
    sender.current_turn = "";

    cout << "[GAME_END] Game ended successfully" << endl;
    break;
  }
  case MessageType::RESIGN:
    handleResign(pm, fd);
    break;
  case MessageType::CANCEL_QM:
    handleCancelQM(pm, fd);
    break;
  case MessageType::DRAW_REQUEST:
    handleDrawRequest(pm, fd);
    break;
  case MessageType::DRAW_RESPONSE:
    handleDrawResponse(pm, fd);
    break;
  case MessageType::REMATCH_REQUEST:
  case MessageType::REMATCH_RESPONSE:
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Feature not implemented"});
    break;
  case MessageType::GAME_HISTORY:
    handleGameHistory(pm, fd);
    break;
  case MessageType::REPLAY_REQUEST:
    handleReplayRequest(pm, fd);
    break;
  case MessageType::REQUEST_ADD_FRIEND:
    handleRequestAddFriend(pm, fd);
    break;
  case MessageType::RESPONSE_ADD_FRIEND:
    handleResponseAddFriend(pm, fd);
    break;
  case MessageType::UNFRIEND: {
    lock_guard<mutex> lock(g_clients_mutex);
    if (g_clients.count(fd) == 0) {
      return;
    }
    auto &sender = g_clients[fd];
    if (sender.username.empty()) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"Please LOGIN before unfriending"});
      break;
    }
    if (!pm.payload.has_value() ||
        !holds_alternative<UnfriendPayload>(*pm.payload)) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"UNFRIEND requires to_user"});
      break;
    }
    if (g_friend_controller == nullptr) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"Friend controller not initialized"});
      break;
    }
    try {
      const auto &p = get<UnfriendPayload>(*pm.payload);
      // Call controller to handle unfriend (database operation)
      nlohmann::json request;
      request["username"] = sender.username;
      request["friend_username"] = p.to_user;
      nlohmann::json response = g_friend_controller->handleUnfriend(request);

      // Send response via INFO message
      sendMessage(fd, MessageType::INFO, InfoPayload{response});
    } catch (...) {
      sendMessage(fd, MessageType::ERROR, ErrorPayload{"Invalid payload"});
    }
    break;
  }
  case MessageType::LOGOUT:
    handleLogout(pm, fd);
    break;
  case MessageType::CHALLENGE_CANCEL: {
    lock_guard<mutex> lock(g_clients_mutex);
    if (g_clients.count(fd) == 0) {
      return;
    }
    auto &sender = g_clients[fd];
    if (sender.username.empty()) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"Please LOGIN before canceling challenge"});
      break;
    }
    if (!pm.payload.has_value() ||
        !holds_alternative<ChallengeCancelPayload>(*pm.payload)) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"CHALLENGE_CANCEL requires to_user"});
      break;
    }
    const auto &p = get<ChallengeCancelPayload>(*pm.payload);
    const string &target = p.to_user;
    auto it = g_username_to_fd.find(target);
    if (it != g_username_to_fd.end()) {
      ChallengeCancelPayload forwardPayload;
      forwardPayload.from_user = sender.username;
      forwardPayload.to_user = "";
      sendMessage(it->second, MessageType::CHALLENGE_CANCEL, forwardPayload);
    }
    sendMessage(fd, MessageType::INFO,
                InfoPayload{nlohmann::json{{"challenge_cancelled", true}}});
    break;
  }
  case MessageType::INFO: {
    // Handle INFO messages with special actions (e.g., list_friends)
    if (pm.payload.has_value() && holds_alternative<InfoPayload>(*pm.payload)) {
      try {
        const auto &info = get<InfoPayload>(*pm.payload);
        if (info.data.is_object() && info.data.contains("action")) {
          string action = info.data["action"].get<string>();
          if (action == "list_friends") {
            // Request friends list
            lock_guard<mutex> lock(g_clients_mutex);
            if (g_clients.count(fd) == 0) {
              return;
            }
            auto &sender = g_clients[fd];
            if (sender.username.empty()) {
              sendMessage(
                  fd, MessageType::ERROR,
                  ErrorPayload{"Please LOGIN before requesting friends list"});
              break;
            }
            if (g_friend_controller == nullptr) {
              sendMessage(fd, MessageType::ERROR,
                          ErrorPayload{"Friend controller not initialized"});
              break;
            }
            // Call controller to get friends list
            nlohmann::json request;
            request["username"] = sender.username;
            nlohmann::json response =
                g_friend_controller->handleListFriends(request);
            // Send response via INFO message
            sendMessage(fd, MessageType::INFO, InfoPayload{response});
            break;
          } else if (action == "list_all_received_requests") {
            // Request all received friend requests (pending + accepted)
            lock_guard<mutex> lock(g_clients_mutex);
            if (g_clients.count(fd) == 0) {
              return;
            }
            auto &sender = g_clients[fd];
            if (sender.username.empty()) {
              sendMessage(
                  fd, MessageType::ERROR,
                  ErrorPayload{
                      "Please LOGIN before requesting friend requests"});
              break;
            }
            if (g_friend_controller == nullptr) {
              sendMessage(fd, MessageType::ERROR,
                          ErrorPayload{"Friend controller not initialized"});
              break;
            }
            // Call controller to get all received requests
            nlohmann::json request;
            request["username"] = sender.username;
            nlohmann::json response =
                g_friend_controller->handleListAllReceivedRequests(request);
            // Send response via INFO message
            sendMessage(fd, MessageType::INFO, InfoPayload{response});
            break;
          } else if (action == "search_users") {
            // Search users by username prefix
            lock_guard<mutex> lock(g_clients_mutex);
            if (g_clients.count(fd) == 0) {
              return;
            }
            auto &sender = g_clients[fd];
            if (sender.username.empty()) {
              sendMessage(fd, MessageType::ERROR,
                          ErrorPayload{"Please LOGIN before searching users"});
              break;
            }

            // Get search query from request
            string searchQuery = "";
            if (info.data.contains("search_query") &&
                info.data["search_query"].is_string()) {
              searchQuery = info.data["search_query"].get<string>();
            }

            if (searchQuery.empty()) {
              sendMessage(fd, MessageType::ERROR,
                          ErrorPayload{"search_query is required"});
              break;
            }

            // Search users in database
            vector<string> usernames =
                g_auth_repo->searchUsers(searchQuery, 50);

            // Exclude current user from results
            usernames.erase(
                remove(usernames.begin(), usernames.end(), sender.username),
                usernames.end());

            // Send results via INFO message
            nlohmann::json arr = nlohmann::json::array();
            for (const auto &username : usernames) {
              arr.push_back(username);
            }
            sendMessage(fd, MessageType::INFO, InfoPayload{arr});
            break;
          } else if (action == "get_active_game") {
            // Get active game for user (for restore after reconnect)
            lock_guard<mutex> lock(g_clients_mutex);
            if (g_clients.count(fd) == 0) {
              return;
            }
            auto &sender = g_clients[fd];
            if (sender.username.empty()) {
              sendMessage(
                  fd, MessageType::ERROR,
                  ErrorPayload{"Please LOGIN before requesting active game"});
              break;
            }
            if (g_game_controller == nullptr) {
              sendMessage(fd, MessageType::ERROR,
                          ErrorPayload{"Game controller not initialized"});
              break;
            }

            // Get active games for this user
            nlohmann::json request;
            request["username"] = sender.username;
            request["filter"] = "active";
            nlohmann::json response =
                g_game_controller->handleListGames(request);

            // Check if there's an active game
            if (response.contains("games") && response["games"].is_array() &&
                response["games"].size() > 0) {
              // Get the first (most recent) active game
              nlohmann::json game = response["games"][0];

              // Restore game state for this player
              string redPlayer = game.value("red_player", "");
              string blackPlayer = game.value("black_player", "");
              string gameId = game.value("game_id", "");
              string gameMode = game.value("time_control", "classical");
              string currentTurn = game.value("current_turn", "red");

              // Determine if this player is red or black
              bool isRed = (sender.username == redPlayer);
              string opponentUsername = isRed ? blackPlayer : redPlayer;

              // Update player state with game_id and current_turn
              sender.in_game = true;
              sender.is_red = isRed;
              sender.game_id = gameId;
              sender.current_turn = currentTurn;

              // Check if opponent is online and update opponent_fd
              if (g_username_to_fd.count(opponentUsername)) {
                int opponentFd = g_username_to_fd[opponentUsername];
                if (g_clients.count(opponentFd) > 0) {
                  sender.opponent_fd = opponentFd;
                  // Also update opponent's reference to this player
                  auto &opponent = g_clients[opponentFd];
                  if (opponent.in_game) {
                    opponent.opponent_fd = fd;
                    // Sync game_id and current_turn with opponent
                    opponent.game_id = gameId;
                    opponent.current_turn = currentTurn;
                  }
                }
              } else {
                sender.opponent_fd = -1; // Opponent offline
              }

              cout << "[GET_ACTIVE_GAME] Restored game for " << sender.username
                   << ": game_id=" << gameId << ", is_red=" << isRed
                   << ", current_turn=" << currentTurn << endl;

              // Get full game details with moves for restore
              nlohmann::json detailsRequest;
              detailsRequest["game_id"] = gameId;
              nlohmann::json detailsResponse =
                  g_game_controller->handleGetGame(detailsRequest);

              // Send active game response with game details
              nlohmann::json activeGameResponse;
              activeGameResponse["action"] = "active_game_restore";
              activeGameResponse["has_active_game"] = true;
              activeGameResponse["game_id"] = gameId;
              activeGameResponse["opponent"] = opponentUsername;
              activeGameResponse["game_mode"] = gameMode;
              activeGameResponse["is_red"] = isRed;
              activeGameResponse["current_turn"] = currentTurn;

              // Include game state (XFEN) and moves for board restore
              if (detailsResponse.contains("game")) {
                activeGameResponse["xfen"] =
                    detailsResponse["game"].value("xfen", "");
                if (detailsResponse["game"].contains("moves")) {
                  activeGameResponse["moves"] =
                      detailsResponse["game"]["moves"];
                }
              }

              sendMessage(fd, MessageType::INFO,
                          InfoPayload{activeGameResponse});
              
              // If it's an AI game and it's AI's turn, trigger AI move
              // Check if opponent is AI (opponent_fd == -1 or opponent starts with "AI_")
              bool isAIGame = (sender.opponent_fd == -1) || 
                              (opponentUsername.find("AI_") == 0);
              
              if (isAIGame && currentTurn == "black") {
                cout << "[GET_ACTIVE_GAME] AI game restored, current_turn=black, triggering AI move" << endl;
                // Get xfen from response for AI move
                string xfenForAI = "";
                if (detailsResponse.contains("game")) {
                  xfenForAI = detailsResponse["game"].value("xfen", "");
                }
                // Trigger AI move (will be called after response is sent)
                // Use a small delay to ensure response is sent first
                handleAIMove(fd, xfenForAI);
              }
            } else {
              // No active game
              nlohmann::json noGameResponse;
              noGameResponse["action"] = "active_game_restore";
              noGameResponse["has_active_game"] = false;
              sendMessage(fd, MessageType::INFO, InfoPayload{noGameResponse});
            }
            break;
          }
        }
      } catch (...) {
        // Not a special action, fall through to error
      }
    }
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Unsupported inbound message"});
    break;
  }
  default:
    if (!sendMessage(fd, MessageType::ERROR,
                     ErrorPayload{"Unknown message type"})) {
      // Send failed, client likely disconnected
      // Will be handled by epoll detecting closed socket
    }
    break;
  }
}

// ===================== Handler Implementations ===================== //
// Handler implementations have been moved to module-specific rawio.cpp files:
// - auth/auth_rawio.cpp: handleLogin, handleRegister, handleLogout
// - friend/friend_rawio.cpp: handleRequestAddFriend, handleResponseAddFriend
// - game/game_rawio.cpp: handleChallenge, handleChallengeResponse, handleMove,
// handleMessage, handleGameHistory
// - ai/ai_rawio.cpp: handleAIMatch, handleSuggestMove, handleAIMove
