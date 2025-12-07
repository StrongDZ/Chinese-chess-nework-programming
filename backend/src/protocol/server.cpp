#include <arpa/inet.h>
#include <netinet/in.h>
#include <signal.h>
#include <sys/epoll.h>
#include <sys/socket.h>
#include <unistd.h>

#include <condition_variable>
#include <cstdlib>
#include <cstring>
#include <iostream>
#include <map>
#include <mutex>
#include <queue>
#include <signal.h>
#include <string>
#include <thread>
#include <vector>

#include "../../include/protocol/MessageTypes.h"
#include "../../include/protocol/ThreadPool.h"
#include "../../include/protocol/python_ai_wrapper.h"  // Use Python AI wrapper instead
#include "../../include/protocol/handle_socket.h"
#include "../../include/protocol/server.h"

using namespace std;

// Global AI engine and game state manager (Python-backed)
static PythonAIWrapper g_ai_engine;
static GameStateManager g_game_state;

// Thread-safe message queue for AI responses
struct AIMessage {
  int player_fd;
  MessageType type;
  Payload payload;
};

static std::queue<AIMessage> g_ai_message_queue;
static std::mutex g_ai_queue_mutex;
static std::condition_variable g_ai_queue_cv;

// ===================== Handler Declarations ===================== //
static void handleLogin(const ParsedMessage &pm, int fd,
                        map<int, PlayerInfo> &clients,
                        map<string, int> &username_to_fd);
static void handleChallenge(const ParsedMessage &pm, int fd,
                            map<int, PlayerInfo> &clients,
                            map<string, int> &username_to_fd);
static void handleChallengeResponse(const ParsedMessage &pm, int fd,
                                    map<int, PlayerInfo> &clients,
                                    map<string, int> &username_to_fd);
static void handleMove(const ParsedMessage &pm, int fd,
                       map<int, PlayerInfo> &clients);
static void handleMessage(const ParsedMessage &pm, int fd,
                          map<int, PlayerInfo> &clients);
static void handleRequestAddFriend(const ParsedMessage &pm, int fd,
                                   map<int, PlayerInfo> &clients,
                                   map<string, int> &username_to_fd);
static void handleResponseAddFriend(const ParsedMessage &pm, int fd,
                                    map<int, PlayerInfo> &clients,
                                    map<string, int> &username_to_fd);
static void processMessage(const ParsedMessage &pm, int fd,
                           map<int, PlayerInfo> &clients,
                           map<string, int> &username_to_fd,
                           mutex &clients_mutex);
static void processAIMatch(const ParsedMessage &pm, int fd,
                           map<int, PlayerInfo> &clients,
                           map<string, int> &username_to_fd,
                           mutex &clients_mutex);
static void handleAIMatch(const ParsedMessage &pm, int fd,
                          map<int, PlayerInfo> &clients,
                          map<string, int> &username_to_fd);
static void handleSuggestMove(const ParsedMessage &pm, int fd,
                              map<int, PlayerInfo> &clients);
static void handleAIMove(int player_fd, map<int, PlayerInfo> &clients);

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

  // Initialize AI engine
  // Try to get Pikafish path from environment variable, or use default
  const char *pikafish_path_env = getenv("PIKAFISH_PATH");
  std::string pikafish_path =
      pikafish_path_env ? pikafish_path_env : "pikafish";

  if (!g_ai_engine.initialize(pikafish_path)) {
    cerr << "Warning: Failed to initialize Pikafish engine. AI features will "
            "be unavailable."
         << endl;
    cerr << "Make sure Pikafish is installed and available in PATH, or set "
            "PIKAFISH_PATH environment variable."
         << endl;
  } else {
    cout << "Pikafish AI engine initialized successfully." << endl;
  }

  // Thread pool for processing messages (especially AI)
  ThreadPool threadPool(4); // 4 worker threads

  // Shared data with mutex protection
  map<int, PlayerInfo> clients;    // fd -> PlayerInfo
  map<string, int> username_to_fd; // username -> fd
  mutex clients_mutex;

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
    int nfds = epoll_wait(epoll_fd, events, MAX_EVENTS, -1);
    if (nfds < 0) {
      // Process AI message queue (non-blocking check)
      {
        std::lock_guard<std::mutex> lock(g_ai_queue_mutex);
        while (!g_ai_message_queue.empty()) {
          AIMessage msg = g_ai_message_queue.front();
          g_ai_message_queue.pop();

          // Send message to client (from main thread - thread-safe)
          if (clients.count(msg.player_fd)) {
            sendMessage(msg.player_fd, msg.type, msg.payload);
          }
        }
      }

      fd_set readfds;
      FD_ZERO(&readfds);
      FD_SET(server_fd, &readfds);
      int maxfd = server_fd;

      for (auto &kv : clients) {
        FD_SET(kv.first, &readfds);
        if (kv.first > maxfd)
          maxfd = kv.first;
      }

      // Use timeout to periodically check AI message queue
      struct timeval timeout;
      timeout.tv_sec = 0;
      timeout.tv_usec = 100000; // 100ms timeout

      int ready = select(maxfd + 1, &readfds, nullptr, nullptr, &timeout);
      if (ready < 0) {
        if (errno == EINTR)
          continue;
        perror("epoll_wait");
        break;
      }

      for (int i = 0; i < nfds; i++) {
        int fd = events[i].data.fd;

        // New connection
        if (fd == server_fd) {
          int client_fd = accept(server_fd, nullptr, nullptr);
          if (client_fd >= 0) {
            {
              lock_guard<mutex> lock(clients_mutex);
              clients[client_fd] = PlayerInfo{-1, string(), false, -1};
            }
            cout << "New connection: fd=" << client_fd << endl;

            // Add client to epoll
            ev.events = EPOLLIN | EPOLLET; // Edge-triggered mode
            ev.data.fd = client_fd;
            if (epoll_ctl(epoll_fd, EPOLL_CTL_ADD, client_fd, &ev) < 0) {
              perror("epoll_ctl: client_fd");
              close(client_fd);
              {
                lock_guard<mutex> lock(clients_mutex);
                clients.erase(client_fd);
              }
            }
          }
          continue;
        }

        // Client message - receive in main thread, process in thread pool
        string msg;
        bool shouldClose = false;
        {
          lock_guard<mutex> lock(clients_mutex);
          if (clients.count(fd) == 0) {
            continue; // Client already removed
          }
        }

        if (!recvMessage(fd, msg)) {
          cout << "Client closed: fd=" << fd << endl;
          shouldClose = true;
        } else {
          cout << "[RECV fd=" << fd << "] " << msg << endl;

          auto pm = parseMessage(msg);

          // Determine if message should be processed in thread pool
          bool needsThreadPool = false;
          switch (pm.type) {
          case MessageType::AI_MATCH:
            needsThreadPool = true;
            break;
          // Add other heavy operations here if needed
          default:
            needsThreadPool = false;
            break;
          }

          if (needsThreadPool) {
            // Process in thread pool (non-blocking)
            threadPool.enqueue([pm, fd, &clients, &username_to_fd,
                                &clients_mutex]() {
              processMessage(pm, fd, clients, username_to_fd, clients_mutex);
            });
          } else {
            // Process immediately in main thread (fast operations)
            processMessage(pm, fd, clients, username_to_fd, clients_mutex);
          }
        }

        // Handle client disconnection
        if (shouldClose) {
          epoll_ctl(epoll_fd, EPOLL_CTL_DEL, fd, nullptr);
          {
            lock_guard<mutex> lock(clients_mutex);
            auto itc = clients.find(fd);
            if (itc != clients.end()) {
              if (!itc->second.username.empty()) {
                username_to_fd.erase(itc->second.username);
              }
              int opp = itc->second.opponent_fd;
              if (opp >= 0 && clients.count(opp)) {
                clients[opp].in_game = false;
                clients[opp].opponent_fd = -1;
                sendMessage(
                    opp, MessageType::INFO,
                    InfoPayload{nlohmann::json("opponent_disconnected")});
              }
            }
            close(fd);
            clients.erase(fd);
          }
        }
      }
    }

    close(epoll_fd);
    close(server_fd);
    return 0;
  }
}

// ===================== Message Processing ===================== //

static void processMessage(const ParsedMessage &pm, int fd,
                           map<int, PlayerInfo> &clients,
                           map<string, int> &username_to_fd,
                           mutex &clients_mutex) {
  lock_guard<mutex> lock(clients_mutex);

  if (clients.count(fd) == 0) {
    return; // Client disconnected
  }

  auto &sender = clients[fd];

  switch (pm.type) {
  case MessageType::LOGIN:
    handleLogin(pm, fd, clients, username_to_fd);
    break;
  case MessageType::REGISTER:
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"REGISTER not implemented"});
    break;
  case MessageType::LOGOUT:
    sendMessage(fd, MessageType::INFO,
                InfoPayload{nlohmann::json{{"logout", "ok"}}});
    shutdown(fd, SHUT_RDWR);
    break;
  case MessageType::PLAYER_LIST: {
    nlohmann::json arr = nlohmann::json::array();
    for (auto &p : clients) {
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
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"QUICK_MATCHING not implemented"});
    break;
  case MessageType::CHALLENGE_REQUEST:
    handleChallenge(pm, fd, clients, username_to_fd);
    break;
  case MessageType::CHALLENGE_CANCEL:
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"CHALLENGE_CANCEL not implemented"});
    break;
  case MessageType::CHALLENGE_RESPONSE:
    handleChallengeResponse(pm, fd, clients, username_to_fd);
    break;
  case MessageType::AI_MATCH:
    // This should be handled in thread pool, but if called directly:
    processAIMatch(pm, fd, clients, username_to_fd, clients_mutex);
    break;
  case MessageType::USER_STATS: {
    if (!pm.payload.has_value() ||
        !holds_alternative<UserStatsPayload>(*pm.payload)) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"USER_STATS requires target_username"});
      break;
    }
    const auto &p = get<UserStatsPayload>(*pm.payload);
    const string &target = p.target_username;
    auto it = username_to_fd.find(target);
    if (it == username_to_fd.end()) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"Target user not found"});
      break;
    }
    int target_fd = it->second;
    if (!clients.count(target_fd)) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"Target user socket missing"});
      break;
    }
    auto &target_player = clients[target_fd];
    nlohmann::json info = {
        {"username", target_player.username},
        {"in_game", target_player.in_game},
    };
    if (target_player.in_game && target_player.opponent_fd >= 0 &&
        clients.count(target_player.opponent_fd)) {
      info["opponent"] = clients[target_player.opponent_fd].username;
    } else {
      info["opponent"] = nullptr;
    }
    sendMessage(fd, MessageType::INFO, InfoPayload{info});
    break;
  }
  case MessageType::LEADER_BOARD:
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"LEADER_BOARD not implemented"});
    break;
  case MessageType::MOVE:
    handleMove(pm, fd, clients);
    break;
  case MessageType::INVALID_MOVE:
    // Server sends this to client, not received from client
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"INVALID_MOVE not a client command"});
    break;
  case MessageType::MESSAGE:
    handleMessage(pm, fd, clients);
    break;
  case MessageType::GAME_END: {
    auto &sender = clients[fd];
    if (!pm.payload.has_value() ||
        !holds_alternative<GameEndPayload>(*pm.payload)) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"GAME_END requires payload win_side"});
      break;
    }
    if (!sender.in_game || sender.opponent_fd < 0) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"You are not in a game"});
      break;
    }
    int opp = sender.opponent_fd;
    if (clients.count(opp)) {
      sendMessage(opp, MessageType::GAME_END, get<GameEndPayload>(*pm.payload));
      clients[opp].in_game = false;
      clients[opp].opponent_fd = -1;
    }
    sender.in_game = false;
    sender.opponent_fd = -1;
    break;
  }
  case MessageType::RESIGN: {
    auto &sender = clients[fd];
    if (!sender.in_game || sender.opponent_fd < 0) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"You are not in a game"});
      break;
    }
    int opp = sender.opponent_fd;
    GameEndPayload gp;
    gp.win_side = string("opponent");
    if (clients.count(opp)) {
      sendMessage(opp, MessageType::GAME_END, gp);
      clients[opp].in_game = false;
      clients[opp].opponent_fd = -1;
    }
    sender.in_game = false;
    sender.opponent_fd = -1;
    break;
  }
  case MessageType::CANCEL_QM:
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"CANCEL_QM not implemented"});
    break;
  case MessageType::DRAW_REQUEST: {
    auto &sender = clients[fd];
    if (!sender.in_game || sender.opponent_fd < 0) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"You are not in a game"});
      break;
    }
    int opp = sender.opponent_fd;
    if (clients.count(opp)) {
      sendMessage(opp, MessageType::DRAW_REQUEST, DrawRequestPayload{});
      sendMessage(fd, MessageType::INFO,
                  InfoPayload{nlohmann::json{{"draw_request_sent", true}}});
    }
    break;
  }
  case MessageType::DRAW_RESPONSE: {
    auto &sender = clients[fd];
    if (!sender.in_game || sender.opponent_fd < 0) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"You are not in a game"});
      break;
    }
    if (!pm.payload.has_value() ||
        !holds_alternative<DrawResponsePayload>(*pm.payload)) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"DRAW_RESPONSE requires accept_draw"});
      break;
    }
    const auto &p = get<DrawResponsePayload>(*pm.payload);
    int opp = sender.opponent_fd;
    if (clients.count(opp)) {
      sendMessage(opp, MessageType::DRAW_RESPONSE, p);
      if (p.accept_draw) {
        // End game as draw
        GameEndPayload gp;
        gp.win_side = "draw";
        sendMessage(fd, MessageType::GAME_END, gp);
        sendMessage(opp, MessageType::GAME_END, gp);
        clients[fd].in_game = false;
        clients[fd].opponent_fd = -1;
        clients[opp].in_game = false;
        clients[opp].opponent_fd = -1;
      }
    }
    break;
  }
  case MessageType::REMATCH_REQUEST: {
    auto &sender = clients[fd];
    if (!sender.in_game || sender.opponent_fd < 0) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"You are not in a game"});
      break;
    }
    int opp = sender.opponent_fd;
    if (clients.count(opp)) {
      sendMessage(opp, MessageType::REMATCH_REQUEST, RematchRequestPayload{});
      sendMessage(fd, MessageType::INFO,
                  InfoPayload{nlohmann::json{{"rematch_request_sent", true}}});
    }
    break;
  }
  case MessageType::REMATCH_RESPONSE: {
    auto &sender = clients[fd];
    if (!sender.in_game || sender.opponent_fd < 0) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"You are not in a game"});
      break;
    }
    if (!pm.payload.has_value() ||
        !holds_alternative<RematchResponsePayload>(*pm.payload)) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"REMATCH_RESPONSE requires accept_rematch"});
      break;
    }
    const auto &p = get<RematchResponsePayload>(*pm.payload);
    int opp = sender.opponent_fd;
    if (clients.count(opp)) {
      sendMessage(opp, MessageType::REMATCH_RESPONSE, p);
      if (p.accept_rematch) {
        // Start new game (reuse existing game setup)
        GameStartPayload gs1, gs2;
        gs1.opponent = clients[opp].username;
        gs1.game_mode = "classic";
        gs1.opponent_data = nlohmann::json();
        gs2.opponent = sender.username;
        gs2.game_mode = "classic";
        gs2.opponent_data = nlohmann::json();
        sendMessage(opp, MessageType::GAME_START, gs1);
        sendMessage(fd, MessageType::GAME_START, gs2);
        // Reinitialize game state
        g_game_state.initializeGame(opp, fd, AIDifficulty::EASY);
        g_game_state.initializeGame(fd, opp, AIDifficulty::EASY);
        // Swap colors
        clients[opp].is_red = !clients[opp].is_red;
        sender.is_red = !sender.is_red;
      }
    }
    break;
  }
  case MessageType::GAME_HISTORY:
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"GAME_HISTORY not implemented"});
    break;
  case MessageType::REPLAY_REQUEST:
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"REPLAY_REQUEST not implemented"});
    break;
  case MessageType::REQUEST_ADD_FRIEND:
    handleRequestAddFriend(pm, fd, clients, username_to_fd);
    break;
  case MessageType::RESPONSE_ADD_FRIEND:
    handleResponseAddFriend(pm, fd, clients, username_to_fd);
    break;
  case MessageType::UNFRIEND: {
    auto &sender = clients[fd];
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

  switch (pm.type) {
  case MessageType::LOGIN:
    handleLogin(pm, fd, clients, username_to_fd);
    break;
  case MessageType::REGISTER:
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"REGISTER not implemented"});
    break;
  case MessageType::LOGOUT: {
    auto &sender = clients[fd];
    if (!sender.username.empty() && username_to_fd.count(sender.username)) {
      username_to_fd.erase(sender.username);
    }
    // Cleanup AI game state if exists
    if (g_game_state.hasGame(fd)) {
      g_game_state.endGame(fd);
    }
    sendMessage(fd, MessageType::INFO,
                InfoPayload{nlohmann::json{{"logout", "ok"}}});
    shutdown(fd, SHUT_RDWR);
    // Connection will be closed by epoll detecting shutdown
    break;
  }
  case MessageType::PLAYER_LIST: {
    nlohmann::json arr = nlohmann::json::array();
    for (auto &p : clients) {
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
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"QUICK_MATCHING not implemented"});
    break;
  case MessageType::CHALLENGE_REQUEST:
    handleChallenge(pm, fd, clients, username_to_fd);
    break;
  case MessageType::CHALLENGE_CANCEL: {
    auto &sender = clients[fd];
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
    auto it = username_to_fd.find(target);
    if (it != username_to_fd.end()) {
      ChallengeCancelPayload forwardPayload;
      forwardPayload.from_user = sender.username;
      forwardPayload.to_user = "";
      sendMessage(it->second, MessageType::CHALLENGE_CANCEL, forwardPayload);
    }
    sendMessage(fd, MessageType::INFO,
                InfoPayload{nlohmann::json{{"challenge_cancelled", true}}});
    break;
  }
  case MessageType::CHALLENGE_RESPONSE:
    handleChallengeResponse(pm, fd, clients, username_to_fd);
    break;
  case MessageType::AI_MATCH:
    handleAIMatch(pm, fd, clients, username_to_fd);
    break;
  case MessageType::SUGGEST_MOVE:
    handleSuggestMove(pm, fd, clients);
    break;
  case MessageType::USER_STATS: {
    if (!pm.payload.has_value() ||
        !holds_alternative<UserStatsPayload>(*pm.payload)) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"USER_STATS requires target_username"});
      break;
    }
    const auto &p = get<UserStatsPayload>(*pm.payload);
    const string &target = p.target_username;
    auto it = username_to_fd.find(target);
    if (it == username_to_fd.end()) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"Target user not found"});
      break;
    }
    int target_fd = it->second;
    if (!clients.count(target_fd)) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"Target user socket missing"});
      break;
    }
    auto &target_player = clients[target_fd];
    nlohmann::json info = {
        {"username", target_player.username},
        {"in_game", target_player.in_game},
    };
    if (target_player.in_game && target_player.opponent_fd >= 0 &&
        clients.count(target_player.opponent_fd)) {
      info["opponent"] = clients[target_player.opponent_fd].username;
    } else {
      info["opponent"] = nullptr;
    }
    sendMessage(fd, MessageType::INFO, InfoPayload{info});
    break;
  }
  case MessageType::LEADER_BOARD:
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"LEADER_BOARD not implemented"});
    break;
  case MessageType::MOVE:
    handleMove(pm, fd, clients);
    break;
  case MessageType::INVALID_MOVE:
    // Server sends this to client, not received from client
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"INVALID_MOVE not a client command"});
    break;
  case MessageType::MESSAGE:
    handleMessage(pm, fd, clients);
    break;
  case MessageType::GAME_END: {
    auto &sender = clients[fd];
    if (!pm.payload.has_value() ||
        !holds_alternative<GameEndPayload>(*pm.payload)) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"GAME_END requires payload win_side"});
      break;
    }
    if (!sender.in_game) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"You are not in a game"});
      break;
    }
    int opp = sender.opponent_fd;

    // Cleanup AI game state if exists
    if (g_game_state.hasGame(fd)) {
      g_game_state.endGame(fd);
    }

    if (opp >= 0 && clients.count(opp)) {
      // Regular PvP game
      sendMessage(opp, MessageType::GAME_END, get<GameEndPayload>(*pm.payload));
      clients[opp].in_game = false;
      clients[opp].opponent_fd = -1;
    } else {
      // AI game (opponent_fd < 0)
      // AI game cleanup already done above
    }
    sender.in_game = false;
    sender.opponent_fd = -1;
    break;
  }
  case MessageType::RESIGN: {
    auto &sender = clients[fd];
    if (!sender.in_game) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"You are not in a game"});
      break;
    }
    int opp = sender.opponent_fd;

    // Cleanup AI game state if exists
    if (g_game_state.hasGame(fd)) {
      g_game_state.endGame(fd);
    }

    GameEndPayload gp;
    gp.win_side = string("opponent");
    if (opp >= 0 && clients.count(opp)) {
      // Regular PvP game
      sendMessage(opp, MessageType::GAME_END, gp);
      clients[opp].in_game = false;
      clients[opp].opponent_fd = -1;
    } else {
      // AI game - just end it
    }
    sender.in_game = false;
    sender.opponent_fd = -1;
    break;
  }
  case MessageType::DRAW_REQUEST:
  case MessageType::DRAW_RESPONSE:
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

static void processAIMatch(const ParsedMessage &pm, int fd,
                           map<int, PlayerInfo> &clients,
                           map<string, int> &username_to_fd,
                           mutex &clients_mutex) {
  // This function runs in thread pool - AI processing can take time
  auto &sender = clients[fd];
  if (sender.username.empty()) {
    lock_guard<mutex> lock(clients_mutex);
    if (clients.count(fd)) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"Please LOGIN before AI match"});
    }
    return;
  }

  if (!pm.payload.has_value() ||
      !holds_alternative<AIMatchPayload>(*pm.payload)) {
    lock_guard<mutex> lock(clients_mutex);
    if (clients.count(fd)) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"AI_MATCH requires gamemode"});
    }
    return;
  }

  try {
    const auto &p = get<AIMatchPayload>(*pm.payload);
    string gamemode = p.gamemode;

    // Notify client that AI is processing
    {
      lock_guard<mutex> lock(clients_mutex);
      if (clients.count(fd)) {
        sendMessage(fd, MessageType::INFO,
                    InfoPayload{nlohmann::json{{"ai_processing", true},
                                               {"gamemode", gamemode}}});
      }
    }

    // TODO: Call actual AI module here
    // This is where AI calculation happens (can take seconds)
    // Example:
    // AIMove aiMove = aiModule.calculateMove(gamemode, boardState);

    // Simulate AI processing delay
    // std::this_thread::sleep_for(std::chrono::seconds(2));

    // For now, send placeholder response
    {
      lock_guard<mutex> lock(clients_mutex);
      if (clients.count(fd)) {
        // TODO: Replace with actual AI move
        sendMessage(fd, MessageType::ERROR,
                    ErrorPayload{"AI_MATCH: AI module not integrated yet"});
      }
    }
  } catch (...) {
    lock_guard<mutex> lock(clients_mutex);
    if (clients.count(fd)) {
      sendMessage(fd, MessageType::ERROR, ErrorPayload{"Invalid payload"});
    }
  }
}

// ===================== Handler Implementations ===================== //
static void handleLogin(const ParsedMessage &pm, int fd,
                        map<int, PlayerInfo> &clients,
                        map<string, int> &username_to_fd) {
  auto &sender = clients[fd];
  if (!pm.payload.has_value() ||
      !holds_alternative<LoginPayload>(*pm.payload)) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"LOGIN requires username and password"});
    return;
  }
  try {
    const auto &p = get<LoginPayload>(*pm.payload);
    const string &username = p.username;
    const string &password = p.password;
    if (username_to_fd.count(username) && username_to_fd[username] != fd) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"Username already in use"});
      return;
    }
    if (!sender.username.empty() && username_to_fd.count(sender.username)) {
      username_to_fd.erase(sender.username);
    }
    sender.username = username;
    username_to_fd[sender.username] = fd;
    sendMessage(fd, MessageType::AUTHENTICATED);
  } catch (...) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"Invalid payload"});
  }
}

static void handleChallenge(const ParsedMessage &pm, int fd,
                            map<int, PlayerInfo> &clients,
                            map<string, int> &username_to_fd) {
  auto &sender = clients[fd];
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
    const string &target = p.to_user; // Client -> Server uses to_user
    if (target.empty()) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"CHALLENGE_REQUEST requires to_user"});
      return;
    }
    auto it = username_to_fd.find(target);
    if (it == username_to_fd.end()) {
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

static void handleChallengeResponse(const ParsedMessage &pm, int fd,
                                    map<int, PlayerInfo> &clients,
                                    map<string, int> &username_to_fd) {
  auto &sender = clients[fd];
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
    const string &challengerName = p.to_user; // Client -> Server uses to_user
    if (challengerName.empty()) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"CHALLENGE_RESPONSE requires to_user"});
      return;
    }
    if (!p.accept) {
      // Decline challenge - forward to challenger
      auto it = username_to_fd.find(challengerName);
      if (it != username_to_fd.end()) {
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
    auto it = username_to_fd.find(challengerName);
    if (it == username_to_fd.end()) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"Challenger is offline"});
      return;
    }
    int challenger_fd = it->second;
    if (!clients.count(challenger_fd)) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"Challenger socket missing"});
      return;
    }
    auto &challenger = clients[challenger_fd];
    sender.in_game = true;
    sender.opponent_fd = challenger_fd;
    challenger.in_game = true;
    challenger.opponent_fd = fd;

    // Initialize game state for both players (PvP game)
    // Challenger (người thách đấu) plays Red, Accepter (người chấp nhận) plays
    // Black Use EASY as dummy difficulty for PvP (not used for AI)
    g_game_state.initializeGame(challenger_fd, fd, AIDifficulty::EASY);
    g_game_state.initializeGame(fd, challenger_fd, AIDifficulty::EASY);

    // Set player colors: Challenger is Red (goes first), Accepter is Black
    challenger.is_red = true;
    sender.is_red = false;

    // Send GAME_START to both players
    GameStartPayload gs1, gs2;
    gs1.opponent = challenger.username;
    gs1.game_mode = "classic";            // Default, can be enhanced
    gs1.opponent_data = nlohmann::json(); // Can be enhanced with player stats
    gs2.opponent = sender.username;
    gs2.game_mode = "classic";
    gs2.opponent_data = nlohmann::json(); // Can be enhanced with player stats

    sendMessage(challenger_fd, MessageType::GAME_START, gs1);
    sendMessage(fd, MessageType::GAME_START, gs2);
  } catch (...) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"Invalid payload"});
  }
}

static void handleMove(const ParsedMessage &pm, int fd,
                       map<int, PlayerInfo> &clients) {
  auto &sender = clients[fd];
  if (!sender.in_game) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"You are not in a game"});
    return;
  }

  // Check if AI game (opponent_fd == -1) or PvP game
  bool is_ai_game = (sender.opponent_fd == -1) && g_game_state.hasGame(fd);

  if (!is_ai_game) {
    // PvP game - check opponent exists
    if (sender.opponent_fd < 0) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"You are not in a game"});
      return;
    }
    int opp = sender.opponent_fd;
    if (clients.count(opp) == 0) {
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

  // 1. Get current board state (works for both AI and PvP games)
  char board[10][9];
  bool has_board = g_game_state.getCurrentBoardArray(fd, board);

  if (!has_board) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"Game state not found"});
    return;
  }

  // 2. Check if it's player's turn (validate piece color matches player)
  GameStateManager::BoardState board_state = g_game_state.getBoardState(fd);
  if (!board_state.is_valid) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"Invalid game state"});
    return;
  }

  // Check if piece belongs to current player
  char piece = board[move.from.row][move.from.col];
  if (piece == ' ') {
    sendMessage(fd, MessageType::INVALID_MOVE,
                InvalidMovePayload{"No piece at source position"});
    return;
  }

  bool piece_is_red = (piece >= 'A' && piece <= 'Z');
  bool piece_is_black = (piece >= 'a' && piece <= 'z');

  // Determine which player should move based on turn
  // In AI game: player is always Red (goes first), player_turn indicates
  // player's turn In PvP game: use move count to determine turn (even = Red,
  // odd = Black)
  bool is_red_turn;
  bool player_is_red = sender.is_red;

  if (is_ai_game) {
    // AI game: player is Red, player_turn indicates if it's player's turn
    is_red_turn = board_state.player_turn;
  } else {
    // PvP game: use total move count to determine turn
    // Even number of moves (0, 2, 4...) = Red's turn (challenger)
    // Odd number of moves (1, 3, 5...) = Black's turn (accepter)
    int move_count = board_state.moves.size();
    is_red_turn = (move_count % 2 == 0);
  }

  // Check if it's this player's turn
  bool should_be_red_turn = is_red_turn;

  // Validate piece color matches player and turn
  if (should_be_red_turn) {
    // It's Red's turn
    if (!player_is_red) {
      sendMessage(fd, MessageType::INVALID_MOVE,
                  InvalidMovePayload{"Not your turn: Red side should move"});
      return;
    }
    if (!piece_is_red) {
      sendMessage(fd, MessageType::INVALID_MOVE,
                  InvalidMovePayload{
                      "You are playing Red, but trying to move Black piece"});
      return;
    }
  } else {
    // It's Black's turn
    if (player_is_red) {
      sendMessage(fd, MessageType::INVALID_MOVE,
                  InvalidMovePayload{"Not your turn: Black side should move"});
      return;
    }
    if (!piece_is_black) {
      sendMessage(fd, MessageType::INVALID_MOVE,
                  InvalidMovePayload{
                      "You are playing Black, but trying to move Red piece"});
      return;
    }
  }

  // 3. Update game state (for both AI and PvP)
  if (!g_game_state.applyMove(fd, move)) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"Failed to apply move"});
    return;
  }

  // 5. If PvP, also update opponent's game state to keep them in sync
  if (!is_ai_game) {
    int opp = sender.opponent_fd;
    if (g_game_state.hasGame(opp)) {
      g_game_state.applyMove(opp, move);
    }
  }

  // 6. Send responses
  sendMessage(fd, MessageType::MOVE, move); // Echo to sender

  if (is_ai_game) {
    // Generate and send AI move
    handleAIMove(fd, clients);
  } else {
    // PvP: send move to opponent
    int opp = sender.opponent_fd;
    sendMessage(opp, MessageType::MOVE, move);
  }
}

static void handleMessage(const ParsedMessage &pm, int fd,
                          map<int, PlayerInfo> &clients) {
  auto &sender = clients[fd];
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
  if (clients.count(opp) == 0) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"Opponent disconnected"});
    return;
  }
  sendMessage(opp, MessageType::MESSAGE, get<MessagePayload>(*pm.payload));
}

static void handleRequestAddFriend(const ParsedMessage &pm, int fd,
                                   map<int, PlayerInfo> &clients,
                                   map<string, int> &username_to_fd) {
  auto &sender = clients[fd];
  if (sender.username.empty()) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Please LOGIN before sending friend request"});
    return;
  }
  if (!pm.payload.has_value() ||
      !holds_alternative<RequestAddFriendPayload>(*pm.payload)) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"REQUEST_ADD_FRIEND requires to_user"});
    return;
  }
  try {
    const auto &p = get<RequestAddFriendPayload>(*pm.payload);
    const string &target = p.to_user;
    auto it = username_to_fd.find(target);
    if (it == username_to_fd.end()) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"Target user is offline"});
      return;
    }
    int target_fd = it->second;
    if (target_fd == fd) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"Cannot send friend request to yourself"});
      return;
    }
    // Forward request to target user with from_user field
    RequestAddFriendPayload forwardPayload;
    forwardPayload.from_user = sender.username;
    forwardPayload.to_user = "";
    sendMessage(target_fd, MessageType::REQUEST_ADD_FRIEND, forwardPayload);
    sendMessage(fd, MessageType::INFO,
                InfoPayload{nlohmann::json{{"friend_request_sent", true},
                                           {"to_user", target}}});
  } catch (...) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"Invalid payload"});
  }
}

static void handleResponseAddFriend(const ParsedMessage &pm, int fd,
                                    map<int, PlayerInfo> &clients,
                                    map<string, int> &username_to_fd) {
  auto &sender = clients[fd];
  if (sender.username.empty()) {
    sendMessage(
        fd, MessageType::ERROR,
        ErrorPayload{"Please LOGIN before responding to friend request"});
    return;
  }
  if (!pm.payload.has_value() ||
      !holds_alternative<ResponseAddFriendPayload>(*pm.payload)) {
    sendMessage(
        fd, MessageType::ERROR,
        ErrorPayload{"RESPONSE_ADD_FRIEND requires to_user and accept"});
    return;
  }
  try {
    const auto &p = get<ResponseAddFriendPayload>(*pm.payload);
    const string &requesterName = p.to_user;
    auto it = username_to_fd.find(requesterName);
    if (it == username_to_fd.end()) {
      sendMessage(fd, MessageType::ERROR, ErrorPayload{"Requester is offline"});
      return;
    }
    int requester_fd = it->second;
    if (!clients.count(requester_fd)) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"Requester socket missing"});
      return;
    }
    // Forward response to requester with from_user field
    ResponseAddFriendPayload forwardPayload;
    forwardPayload.from_user = sender.username;
    forwardPayload.to_user = "";
    forwardPayload.accept = p.accept;
    sendMessage(requester_fd, MessageType::RESPONSE_ADD_FRIEND, forwardPayload);
    sendMessage(fd, MessageType::INFO,
                InfoPayload{nlohmann::json{{"friend_response_sent", true},
                                           {"to_user", requesterName},
                                           {"accept", p.accept}}});
  } catch (...) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"Invalid payload"});
  }
}

static void handleAIMatch(const ParsedMessage &pm, int fd,
                          map<int, PlayerInfo> &clients,
                          map<string, int> &username_to_fd) {
  auto &sender = clients[fd];
  if (sender.username.empty()) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Please LOGIN before starting AI match"});
    return;
  }

  if (!g_ai_engine.isReady()) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"AI engine is not available"});
    return;
  }

  // Cleanup any existing game state before starting new game
  if (g_game_state.hasGame(fd)) {
    std::cout << "[AI] Cleaning up existing game state for player_fd=" << fd
              << std::endl;
    g_game_state.endGame(fd);
  }

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

  // Convert gamemode string to AIDifficulty
  AIDifficulty difficulty = AIDifficulty::MEDIUM;
  if (gamemode == "easy") {
    difficulty = AIDifficulty::EASY;
  } else if (gamemode == "medium") {
    difficulty = AIDifficulty::MEDIUM;
  } else if (gamemode == "hard") {
    difficulty = AIDifficulty::HARD;
  } else {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Invalid gamemode. Use: easy, medium, or hard"});
    return;
  }

  // Initialize AI game
  // Use a special FD value for AI (negative to distinguish from real FDs)
  int ai_fd = -1; // AI doesn't have a real FD

  sender.in_game = true;
  sender.opponent_fd = ai_fd; // Mark as AI game
  sender.is_red =
      true; // Player always plays Red (goes first) when playing against AI

  g_game_state.initializeGame(fd, ai_fd, difficulty);

  // Send GAME_START to player
  GameStartPayload gs;
  gs.opponent = ""; // Empty for AI games
  gs.game_mode = "ai_" + gamemode;
  gs.opponent_data = nlohmann::json(); // Can be enhanced with AI info

  sendMessage(fd, MessageType::GAME_START, gs);
}

static void handleSuggestMove(const ParsedMessage &pm, int fd,
                              map<int, PlayerInfo> &clients) {
  auto &sender = clients[fd];

  if (!sender.in_game) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"You are not in a game"});
    return;
  }

  if (!g_ai_engine.isReady()) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"AI engine is not available"});
    return;
  }

  // Get current game state
  if (!g_game_state.hasGame(fd)) {
    sendMessage(
        fd, MessageType::ERROR,
        ErrorPayload{"No game state found. This feature works with AI games."});
    return;
  }

  string current_fen = g_game_state.getCurrentFEN(fd);
  if (current_fen.empty()) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Failed to get current game position"});
    return;
  }

  // Get AI suggestion (using HARD difficulty for best move)
  string ucci_move = g_ai_engine.suggestMove(current_fen);
  if (ucci_move.empty()) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Failed to get move suggestion from AI"});
    return;
  }

  // Convert UCI move to MovePayload
  MovePayload suggested_move = PythonAIWrapper::parseUCIMove(ucci_move);

  // Send suggestion to player
  sendMessage(fd, MessageType::SUGGEST_MOVE, suggested_move);
}

static void handleAIMove(int player_fd, map<int, PlayerInfo> &clients) {
  if (!g_game_state.hasGame(player_fd)) {
    return; // Not an AI game
  }

  if (!g_ai_engine.isReady()) {
    return; // AI engine not available
  }

  // OPTIMISTIC VALIDATION: Snapshot state hash before AI thinking
  // This allows us to detect if board changed while AI was thinking (race condition)
  std::size_t state_hash_before = g_game_state.getPositionHash(player_fd);
  if (state_hash_before == 0) {
    return; // Invalid state
  }

  // Get current game state and difficulty BEFORE spawning thread
  string position_str = g_game_state.getPositionString(player_fd);
  if (position_str.empty()) {
    return;
  }

  AIDifficulty difficulty = g_game_state.getAIDifficulty(player_fd);

  // Spawn worker thread to handle AI thinking (non-blocking)
  std::thread ai_thread([player_fd, position_str, difficulty, state_hash_before]() {
    try {
      // 1. Call Engine (Blocking operation - now in separate thread)
      string ucci_move = g_ai_engine.getBestMove(position_str, difficulty);

      if (ucci_move.empty()) {
        // Queue error message
        std::lock_guard<std::mutex> lock(g_ai_queue_mutex);
        g_ai_message_queue.push({player_fd, MessageType::ERROR,
                                 ErrorPayload{"AI failed to generate move"}});
        g_ai_queue_cv.notify_one();
        return;
      }

      // 2. Convert move
      MovePayload ai_move = PythonAIWrapper::parseUCIMove(ucci_move);
      if (ai_move.from.row < 0 || ai_move.from.col < 0 || ai_move.to.row < 0 ||
          ai_move.to.col < 0) {
        std::lock_guard<std::mutex> lock(g_ai_queue_mutex);
        g_ai_message_queue.push(
            {player_fd, MessageType::ERROR,
             ErrorPayload{"AI generated invalid move format"}});
        g_ai_queue_cv.notify_one();
        return;
      }

      // 2. Check if game still exists (client might have disconnected)
      if (!g_game_state.hasGame(player_fd)) {
        std::cout << "[AI] Game ended while AI thinking, cancelling move"
                  << std::endl;
        return; // Game ended, don't send move
      }

      // 3. OPTIMISTIC VALIDATION: Check if state hash changed (race condition detection)
      std::size_t state_hash_after = g_game_state.getPositionHash(player_fd);
      if (state_hash_after == 0 || state_hash_after != state_hash_before) {
        // State changed while AI was thinking - reject move to prevent invalid state
        std::cerr << "[AI] Warning: Game state changed while AI thinking (hash mismatch). "
                  << "Rejecting move to prevent race condition." << std::endl;
        std::lock_guard<std::mutex> lock(g_ai_queue_mutex);
        g_ai_message_queue.push(
            {player_fd, MessageType::ERROR,
             ErrorPayload{"Game state changed, AI move rejected (please try again)"}});
        g_ai_queue_cv.notify_one();
        return;
      }

      // 4. LIGHTWEIGHT CHECK - C++ native, no Python calls
      // Get board for lightweight validation (only if needed for critical checks)
      char board[10][9];
      bool has_board = g_game_state.getCurrentBoardArray(player_fd, board);
      
      if (has_board) {
        // Lightweight validation: coordinates, piece exists, basic sanity
        // This prevents critical errors (out of bounds, missing piece, etc.)
        if (!GameStateManager::quickValidateMove(ai_move, board)) {
          std::cerr << "[AI] CRITICAL ERROR: AI generated invalid move format: " << ucci_move
                    << " - Engine panic detected!" << std::endl;
          std::lock_guard<std::mutex> lock(g_ai_queue_mutex);
          g_ai_message_queue.push(
              {player_fd, MessageType::ERROR,
               ErrorPayload{"AI engine error: invalid move format (critical error)"}});
          g_ai_queue_cv.notify_one();
          return;
        }
      } else {
        // Can't get board, but state hash matches - proceed with caution
        // Pikafish already validated, and applyMove() will do final check
        std::cout << "[AI] Note: Could not get board, but state hash matches. "
                  << "Trusting Pikafish engine validation." << std::endl;
      }

      // 5. Update Game State (thread-safe - GameStateManager has mutex)
      // applyMove() will do final validation and apply the move
      // applyMove will also do basic validation
      bool ok = g_game_state.applyMove(player_fd, ai_move);

      if (ok) {
        // 5. Queue message to be sent by main thread
        std::lock_guard<std::mutex> lock(g_ai_queue_mutex);
        g_ai_message_queue.push({player_fd, MessageType::MOVE, ai_move});
        g_ai_queue_cv.notify_one();
      } else {
        std::lock_guard<std::mutex> lock(g_ai_queue_mutex);
        g_ai_message_queue.push({player_fd, MessageType::ERROR,
                                 ErrorPayload{"Failed to apply AI move"}});
        g_ai_queue_cv.notify_one();
      }
    } catch (const std::exception &e) {
      std::cerr << "[AI] Exception in AI thread: " << e.what() << std::endl;
      std::lock_guard<std::mutex> lock(g_ai_queue_mutex);
      g_ai_message_queue.push(
          {player_fd, MessageType::ERROR, ErrorPayload{"AI thread error"}});
      g_ai_queue_cv.notify_one();
    }
  });

  // Detach thread - it will run independently and clean up when done
  ai_thread.detach();
}
