#include <arpa/inet.h>
#include <netinet/in.h>
#include <sys/epoll.h>
#include <sys/socket.h>
#include <unistd.h>

#include <cstring>
#include <iostream>
#include <map>
#include <mutex>
#include <string>

#include "../include/protocol/MessageTypes.h"
#include "../include/protocol/ThreadPool.h"
#include "../include/protocol/server.h"
#include "handle_socket.cpp"

using namespace std;

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

int main(int argc, char **argv) {
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
  if (bind(server_fd, (sockaddr *)&addr, sizeof(addr)) < 0) {
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
          threadPool.enqueue(
              [pm, fd, &clients, &username_to_fd, &clients_mutex]() {
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
              sendMessage(opp, MessageType::INFO,
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
    auto &s = clients[fd];
    nlohmann::json info = {
        {"username", s.username},
        {"in_game", s.in_game},
    };
    if (s.in_game && s.opponent_fd >= 0 && clients.count(s.opponent_fd)) {
      info["opponent"] = clients[s.opponent_fd].username;
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
  case MessageType::REQUEST_ADD_FRIEND:
    handleRequestAddFriend(pm, fd, clients, username_to_fd);
    break;
  case MessageType::RESPONSE_ADD_FRIEND:
    handleResponseAddFriend(pm, fd, clients, username_to_fd);
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
    const string &target = p.username;
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
    sendMessage(target_fd, MessageType::CHALLENGE_REQUEST,
                ChallengeRequestPayload{sender.username});
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
    if (!p.accept) {
      // Decline challenge
      sendMessage(fd, MessageType::INFO,
                  InfoPayload{nlohmann::json{{"challenge_declined", true}}});
      return;
    }
    // Accept challenge
    const string &challengerName = p.username;
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

    // Send GAME_START to both players
    GameStartPayload gs1, gs2;
    gs1.opponent = challenger.username;
    gs1.game_mode = "classic"; // Default, can be enhanced
    gs2.opponent = sender.username;
    gs2.game_mode = "classic";

    sendMessage(challenger_fd, MessageType::GAME_START, gs1);
    sendMessage(fd, MessageType::GAME_START, gs2);
  } catch (...) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"Invalid payload"});
  }
}

static void handleMove(const ParsedMessage &pm, int fd,
                       map<int, PlayerInfo> &clients) {
  auto &sender = clients[fd];
  if (!sender.in_game || sender.opponent_fd < 0) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"You are not in a game"});
    return;
  }
  int opp = sender.opponent_fd;
  if (clients.count(opp) == 0) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"Opponent disconnected"});
    return;
  }
  if (!pm.payload.has_value() || !holds_alternative<MovePayload>(*pm.payload)) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"MOVE requires piece/from/to"});
    return;
  }
  sendMessage(opp, MessageType::MOVE, get<MovePayload>(*pm.payload));
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
