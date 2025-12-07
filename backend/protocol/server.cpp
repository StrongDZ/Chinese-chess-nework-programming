#include <arpa/inet.h>
#include <netinet/in.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <unistd.h>

#include <cstdlib>
#include <cstring>
#include <iostream>
#include <map>
#include <string>
#include <vector>
#include <thread>
#include <queue>
#include <mutex>
#include <condition_variable>

#include "../include/protocol/MessageTypes.h"
#include "../include/protocol/server.h"
#include "../include/protocol/ai_engine.h"
#include "../include/protocol/handle_socket.h"

using namespace std;

// Global AI engine and game state manager
static PikafishEngine g_ai_engine;
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
  const char* pikafish_path_env = getenv("PIKAFISH_PATH");
  std::string pikafish_path = pikafish_path_env ? pikafish_path_env : "pikafish";
  
  if (!g_ai_engine.initialize(pikafish_path)) {
    cerr << "Warning: Failed to initialize Pikafish engine. AI features will be unavailable." << endl;
    cerr << "Make sure Pikafish is installed and available in PATH, or set PIKAFISH_PATH environment variable." << endl;
  } else {
    cout << "Pikafish AI engine initialized successfully." << endl;
  }

  map<int, PlayerInfo> clients;    // fd -> PlayerInfo
  map<string, int> username_to_fd; // username -> fd

  while (true) {
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
      perror("select");
      break;
    }

    // New connection
    if (FD_ISSET(server_fd, &readfds)) {
      int client_fd = accept(server_fd, nullptr, nullptr);
      if (client_fd >= 0) {
        clients[client_fd] = PlayerInfo{-1, string(), false, -1};
        cout << "New connection: fd=" << client_fd << endl;
      }
    }

    // Client messages
    vector<int> toClose;
    for (auto &kv : clients) {
      int fd = kv.first;
      if (!FD_ISSET(fd, &readfds))
        continue;

      string msg;
      if (!recvMessage(fd, msg)) {
        cout << "Client closed: fd=" << fd << endl;
        toClose.push_back(fd);
        continue;
      }

      cout << "[RECV fd=" << fd << "] " << msg << endl;

      auto pm = parseMessage(msg);
      auto &sender = clients[fd];

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
        toClose.push_back(fd);
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
      case MessageType::CHALLENGE_CANCEL:
        sendMessage(fd, MessageType::ERROR,
                    ErrorPayload{"CHALLENGE_CANCEL not implemented"});
        break;
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
          sendMessage(opp, MessageType::GAME_END,
                      get<GameEndPayload>(*pm.payload));
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
          toClose.push_back(fd);
        }
        break;
      }
    }

    for (int fd : toClose) {
      auto itc = clients.find(fd);
      if (itc != clients.end()) {
        if (!itc->second.username.empty()) {
          username_to_fd.erase(itc->second.username);
        }
        
        // Cleanup AI game state if exists
        if (g_game_state.hasGame(fd)) {
          g_game_state.endGame(fd);
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

  close(server_fd);
  return 0;
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

    // Initialize game state for both players (PvP game)
    // Challenger (người thách đấu) plays Red, Accepter (người chấp nhận) plays Black
    // Use EASY as dummy difficulty for PvP (not used for AI)
    g_game_state.initializeGame(challenger_fd, fd, AIDifficulty::EASY);
    g_game_state.initializeGame(fd, challenger_fd, AIDifficulty::EASY);
    
    // Set player colors: Challenger is Red (goes first), Accepter is Black
    challenger.is_red = true;
    sender.is_red = false;

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
  if (!sender.in_game) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"You are not in a game"});
    return;
  }
  
  // Check if AI game (opponent_fd == -1) or PvP game
  bool is_ai_game = (sender.opponent_fd == -1) && g_game_state.hasGame(fd);
  
  if (!is_ai_game) {
    // PvP game - check opponent exists
    if (sender.opponent_fd < 0) {
      sendMessage(fd, MessageType::ERROR, ErrorPayload{"You are not in a game"});
      return;
    }
    int opp = sender.opponent_fd;
    if (clients.count(opp) == 0) {
      sendMessage(fd, MessageType::ERROR, ErrorPayload{"Opponent disconnected"});
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
  // In AI game: player is always Red (goes first), player_turn indicates player's turn
  // In PvP game: use move count to determine turn (even = Red, odd = Black)
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
                  InvalidMovePayload{"You are playing Red, but trying to move Black piece"});
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
                  InvalidMovePayload{"You are playing Black, but trying to move Red piece"});
      return;
    }
  }
  
  // 3. Validate move legality (physical rules)
  if (!GameStateManager::isValidMoveOnBoard(board, move)) {
    sendMessage(fd, MessageType::INVALID_MOVE,
                InvalidMovePayload{"Illegal move: violates Chinese Chess rules"});
    return;
  }
  
  // 4. Update game state (for both AI and PvP)
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
    std::cout << "[AI] Cleaning up existing game state for player_fd=" << fd << std::endl;
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
  sender.is_red = true; // Player always plays Red (goes first) when playing against AI
  
  g_game_state.initializeGame(fd, ai_fd, difficulty);
  
  // Send GAME_START to player
  GameStartPayload gs;
  gs.opponent = "AI (" + gamemode + ")";
  gs.game_mode = "ai_" + gamemode;
  
  sendMessage(fd, MessageType::GAME_START, gs);
}

static void handleSuggestMove(const ParsedMessage &pm, int fd,
                              map<int, PlayerInfo> &clients) {
  auto &sender = clients[fd];
  
  if (!sender.in_game) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"You are not in a game"});
    return;
  }
  
  if (!g_ai_engine.isReady()) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"AI engine is not available"});
    return;
  }
  
  // Get current game state
  if (!g_game_state.hasGame(fd)) {
    sendMessage(fd, MessageType::ERROR,
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
  MovePayload suggested_move = PikafishEngine::parseUCIMove(ucci_move);
  
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
  
  // CRITICAL FIX: Copy data needed for AI thread to avoid race conditions
  // Get current game state and difficulty BEFORE spawning thread
  // Use position string with move history (more reliable than recalculated FEN)
  string position_str = g_game_state.getPositionString(player_fd);
  if (position_str.empty()) {
    return;
  }
  
  AIDifficulty difficulty = g_game_state.getAIDifficulty(player_fd);
  
  // Spawn worker thread to handle AI thinking (non-blocking)
  std::thread ai_thread([player_fd, position_str, difficulty]() {
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
      MovePayload ai_move = PikafishEngine::parseUCIMove(ucci_move);
      if (ai_move.from.row < 0 || ai_move.from.col < 0 || 
          ai_move.to.row < 0 || ai_move.to.col < 0) {
        std::lock_guard<std::mutex> lock(g_ai_queue_mutex);
        g_ai_message_queue.push({player_fd, MessageType::ERROR,
                                  ErrorPayload{"AI generated invalid move format"}});
        g_ai_queue_cv.notify_one();
        return;
      }
      
      // 3. Check if game still exists (client might have disconnected)
      if (!g_game_state.hasGame(player_fd)) {
        std::cout << "[AI] Game ended while AI thinking, cancelling move" << std::endl;
        return; // Game ended, don't send move
      }
      
      // 4. Update Game State (thread-safe - GameStateManager has mutex)
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
      g_ai_message_queue.push({player_fd, MessageType::ERROR,
                                ErrorPayload{"AI thread error"}});
      g_ai_queue_cv.notify_one();
    }
  });
  
  // Detach thread - it will run independently and clean up when done
  ai_thread.detach();
}
