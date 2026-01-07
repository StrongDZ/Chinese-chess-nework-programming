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
#include "auth/auth_controller.h"
#include "friend/friend_controller.h"
#include "game/game_controller.h"
#include "player_stat/player_stat_controller.h"

// ===================== Raw IO Handlers ===================== //
// Handler implementations are in separate rawio.cpp files:
// - auth/auth_rawio.cpp: handleLogin, handleRegister
// - friend/friend_rawio.cpp: handleRequestAddFriend, handleResponseAddFriend
// - game/game_rawio.cpp: handleChallenge, handleChallengeResponse, handleMove,
// handleMessage
// - ai/ai_rawio.cpp: handleAIMatch, handleSuggestMove, handleAIMove
// These files include their own controller headers as needed.

using namespace std;

// Global controllers (non-static so they can be accessed from other files)
AuthController *g_auth_controller = nullptr;
FriendController *g_friend_controller = nullptr;
GameController *g_game_controller = nullptr;
PlayerStatController *g_player_stat_controller = nullptr;

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

  // Initialize Controllers (as global variables)
  AuthController authController(authService);
  FriendController friendController(friendService);
  GameController gameController(gameService);
  PlayerStatController playerStatController(playerStatService);

  // Set global pointers
  g_auth_controller = &authController;
  g_friend_controller = &friendController;
  g_game_controller = &gameController;
  g_player_stat_controller = &playerStatController;

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
            g_clients[client_fd] = PlayerInfo{-1, string(), false, -1};
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
            if (!itc->second.username.empty()) {
              g_username_to_fd.erase(itc->second.username);
            }
            int opp = itc->second.opponent_fd;
            if (opp >= 0 && g_clients.count(opp)) {
              g_clients[opp].in_game = false;
              g_clients[opp].opponent_fd = -1;
              sendMessage(opp, MessageType::INFO,
                          InfoPayload{nlohmann::json("opponent_disconnected")});
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
        arr.push_back(p.second.username);
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

    // TODO: Cleanup AI game state via Python API
    // if (g_game_state.hasGame(fd)) {
    //   g_game_state.endGame(fd);
    // }

    if (opp >= 0 && g_clients.count(opp)) {
      // Regular PvP game
      sendMessage(opp, MessageType::GAME_END, get<GameEndPayload>(*pm.payload));
      g_clients[opp].in_game = false;
      g_clients[opp].opponent_fd = -1;
    } else {
      // AI game (opponent_fd < 0)
      // AI game cleanup already done above
    }
    sender.in_game = false;
    sender.opponent_fd = -1;
    break;
  }
  case MessageType::RESIGN:
    handleResign(pm, fd);
    break;
  case MessageType::CANCEL_QM:
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"CANCEL_QM not implemented"});
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
  case MessageType::REPLAY_REQUEST:
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Feature not implemented"});
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
    const auto &p = get<UnfriendPayload>(*pm.payload);
    // TODO: Implement unfriend logic (database operation)
    sendMessage(fd, MessageType::INFO,
                InfoPayload{nlohmann::json{{"unfriend", "ok"},
                                           {"to_user", p.to_user}}});
    break;
  }
  case MessageType::LOGOUT: {
    lock_guard<mutex> lock(g_clients_mutex);
    if (g_clients.count(fd) == 0) {
      return;
    }
    auto &sender = g_clients[fd];
    if (!sender.username.empty() && g_username_to_fd.count(sender.username)) {
      g_username_to_fd.erase(sender.username);
    }
    // TODO: Cleanup AI game state via Python API
    // if (g_game_state.hasGame(fd)) {
    //   g_game_state.endGame(fd);
    // }
    sendMessage(fd, MessageType::INFO,
                InfoPayload{nlohmann::json{{"logout", "ok"}}});
    shutdown(fd, SHUT_RDWR);
    // Connection will be closed by epoll detecting shutdown
    break;
  }
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
  case MessageType::SUGGEST_MOVE:
    handleSuggestMove(pm, fd);
    break;
  case MessageType::INFO:
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Unsupported inbound message"});
    break;
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
// - auth/auth_rawio.cpp: handleLogin, handleRegister
// - friend/friend_rawio.cpp: handleRequestAddFriend, handleResponseAddFriend
// - game/game_rawio.cpp: handleChallenge, handleChallengeResponse, handleMove,
// handleMessage
// - ai/ai_rawio.cpp: handleAIMatch, handleSuggestMove, handleAIMove
