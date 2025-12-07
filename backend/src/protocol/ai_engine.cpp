#include "../../include/protocol/ai_engine.h"
#include <algorithm>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <iostream>
#include <limits.h>
#include <signal.h>
#include <sstream>
#include <sys/types.h>
#include <sys/wait.h>
#include <thread>
#include <unistd.h>
#ifdef __APPLE__
#include <mach-o/dyld.h> // For _NSGetExecutablePath on macOS
#endif

// ===================== Helper Functions ===================== //

// Get directory where executable is located
static std::string getExecutableDirectory() {
  char path[PATH_MAX];
  ssize_t len = 0;

#ifdef __APPLE__
  // macOS
  uint32_t size = sizeof(path);
  if (_NSGetExecutablePath(path, &size) == 0) {
    len = strlen(path);
  }
#elif defined(__linux__)
  // Linux
  len = readlink("/proc/self/exe", path, sizeof(path) - 1);
  if (len != -1) {
    path[len] = '\0';
  }
#else
  // Fallback
  return "";
#endif

  if (len > 0) {
    std::string full_path(path);
    size_t last_slash = full_path.find_last_of("/");
    if (last_slash != std::string::npos) {
      return full_path.substr(0, last_slash + 1);
    }
  }
  return "";
}

// Try to find pikafish in various locations
static std::string findPikafish(const std::string &user_path) {
  // 1. User-specified path (environment variable or parameter)
  if (!user_path.empty() && user_path != "pikafish") {
    // Check if it's a full path
    if (user_path.find('/') != std::string::npos) {
      // Check if file exists and is executable
      if (access(user_path.c_str(), X_OK) == 0) {
        return user_path;
      }
    }
  }

  // 2. Check in executable directory (for macOS - same folder as server binary)
  std::string exe_dir = getExecutableDirectory();
  if (!exe_dir.empty()) {
    std::string local_path = exe_dir + "pikafish";
    if (access(local_path.c_str(), X_OK) == 0) {
      std::cout << "[AI] Found Pikafish in executable directory: " << local_path
                << std::endl;
      return local_path;
    }
  }

  // 3. Check in PATH
  if (user_path == "pikafish" || user_path.empty()) {
    // Try to find in PATH
    const char *path_env = getenv("PATH");
    if (path_env) {
      std::istringstream path_stream(path_env);
      std::string path_entry;
      while (std::getline(path_stream, path_entry, ':')) {
        std::string test_path = path_entry + "/pikafish";
        if (access(test_path.c_str(), X_OK) == 0) {
          return test_path;
        }
      }
    }
  }

  // 4. Return user_path as fallback (will be tried with execlp)
  return user_path;
}

// ===================== PikafishEngine Implementation ===================== //

PikafishEngine::PikafishEngine()
    : engine_stdin_(nullptr), engine_stdout_(nullptr), engine_pid_(-1),
      engine_ready_(false) {}

PikafishEngine::~PikafishEngine() { shutdown(); }

bool PikafishEngine::initialize(const std::string &pikafish_path) {
  std::lock_guard<std::mutex> lock(engine_mutex_);

  if (engine_ready_) {
    std::cout << "[AI] Engine already initialized" << std::endl;
    return true; // Already initialized
  }

  // Try to find Pikafish in various locations
  std::string resolved_path = findPikafish(pikafish_path);

  std::cout << "[AI] Initializing Pikafish engine..." << std::endl;
  std::cout << "[AI] Search path: "
            << (pikafish_path.empty() ? "default" : pikafish_path) << std::endl;
  std::cout << "[AI] Resolved path: " << resolved_path << std::endl;

  // Create pipes for communication
  int stdin_pipe[2], stdout_pipe[2];
  if (pipe(stdin_pipe) < 0 || pipe(stdout_pipe) < 0) {
    std::cerr << "[AI] Error: Failed to create pipes" << std::endl;
    perror("pipe");
    return false;
  }

  // Fork process
  engine_pid_ = fork();
  if (engine_pid_ < 0) {
    std::cerr << "[AI] Error: Failed to fork process" << std::endl;
    perror("fork");
    return false;
  }

  if (engine_pid_ == 0) {
    // Child process: Pikafish
    close(stdin_pipe[1]);  // Close write end
    close(stdout_pipe[0]); // Close read end

    // Redirect stdin/stdout
    dup2(stdin_pipe[0], STDIN_FILENO);
    dup2(stdout_pipe[1], STDOUT_FILENO);

    // Execute Pikafish
    // Use resolved path (which is already a full path if found)
    // Note: resolved_path is in parent's scope, but we can access it before
    // fork
    const char *exec_path = resolved_path.c_str();
    if (resolved_path.find('/') != std::string::npos) {
      // Full path - use execl
      execl(exec_path, exec_path, nullptr);
    } else {
      // Fallback to PATH lookup
      execlp(exec_path, exec_path, nullptr);
    }
    std::cerr << "[AI] Error: Failed to execute Pikafish: " << resolved_path
              << std::endl;
    perror("execlp/execl");
    exit(1);
  }

  // Parent process
  close(stdin_pipe[0]);  // Close read end
  close(stdout_pipe[1]); // Close write end

  // Open file streams
  engine_stdin_ = fdopen(stdin_pipe[1], "w");
  engine_stdout_ = fdopen(stdout_pipe[0], "r");

  if (!engine_stdin_ || !engine_stdout_) {
    std::cerr << "[AI] Error: Failed to open file streams" << std::endl;
    perror("fdopen");
    shutdown();
    return false;
  }

  // Set non-blocking mode for stdout (optional, for timeout)
  int flags = fcntl(fileno(engine_stdout_), F_GETFL);
  fcntl(fileno(engine_stdout_), F_SETFL, flags | O_NONBLOCK);

  // Initialize UCI protocol (Pikafish uses UCI, not UCCI)
  std::this_thread::sleep_for(std::chrono::milliseconds(200));

  std::cout << "[AI] Sending UCI initialization command..." << std::endl;
  if (!sendCommand("uci")) {
    std::cerr << "[AI] Error: Failed to send UCI command" << std::endl;
    shutdown();
    return false;
  }

  std::string response = readResponse(3000);
  std::cout << "[AI] UCI response: " << response << std::endl;

  if (response.find("uciok") != std::string::npos) {
    engine_ready_ = true;
    sendCommand("isready");
    std::string ready_response = readResponse(2000);
    std::cout << "[AI] Ready response: " << ready_response << std::endl;

    if (ready_response.find("readyok") != std::string::npos) {
      std::cout << "[AI] Pikafish engine initialized successfully" << std::endl;
      return true;
    }
  }

  std::cerr << "[AI] Error: Failed to initialize UCI protocol. Response: "
            << response << std::endl;
  shutdown();
  return false;
}

void PikafishEngine::shutdown() {
  std::lock_guard<std::mutex> lock(engine_mutex_);

  if (!engine_ready_ && engine_pid_ < 0) {
    return;
  }

  std::cout << "[AI] Shutting down Pikafish engine..." << std::endl;

  if (engine_stdin_) {
    sendCommand("quit");
    std::this_thread::sleep_for(std::chrono::milliseconds(100));
    fclose(engine_stdin_);
    engine_stdin_ = nullptr;
  }

  if (engine_stdout_) {
    fclose(engine_stdout_);
    engine_stdout_ = nullptr;
  }

  if (engine_pid_ > 0) {
    // Wait for process to terminate (with timeout)
    int status;
    int wait_result = waitpid(engine_pid_, &status, WNOHANG);
    if (wait_result == 0) {
      // Process still running, kill it
      kill(engine_pid_, SIGTERM);
      std::this_thread::sleep_for(std::chrono::milliseconds(100));
      waitpid(engine_pid_, &status, 0);
    }
    engine_pid_ = -1;
  }

  engine_ready_ = false;
  std::cout << "[AI] Engine shutdown complete" << std::endl;
}

bool PikafishEngine::sendCommand(const std::string &cmd) {
  if (!engine_stdin_) {
    std::cerr << "[AI] Error: Engine stdin not available" << std::endl;
    return false;
  }

  // Note: engine_ready_ check removed here because we need to send "uci" before
  // engine is ready
  std::cout << "[AI] Sending command: " << cmd << std::endl;
  fprintf(engine_stdin_, "%s\n", cmd.c_str());
  fflush(engine_stdin_);
  return true;
}

std::string PikafishEngine::readResponse(int timeout_ms) {
  if (!engine_stdout_) {
    std::cerr << "[AI] Error: Engine stdout not available" << std::endl;
    return "";
  }

  std::string response;
  auto start = std::chrono::steady_clock::now();
  char buffer[4096];
  bool got_data = false;
  bool found_terminator = false;

  // Clear any existing data in buffer
  fflush(engine_stdout_);

  while (true) {
    auto elapsed = std::chrono::steady_clock::now() - start;
    auto elapsed_ms =
        std::chrono::duration_cast<std::chrono::milliseconds>(elapsed).count();

    if (elapsed_ms > timeout_ms) {
      if (!got_data) {
        std::cerr << "[AI] Warning: Timeout waiting for response ("
                  << timeout_ms << "ms)" << std::endl;
      }
      break;
    }

    // Try to read data
    if (fgets(buffer, sizeof(buffer), engine_stdout_)) {
      got_data = true;
      response += buffer;

      // Check for UCI response terminators
      if (response.find("uciok") != std::string::npos ||
          response.find("readyok") != std::string::npos ||
          response.find("bestmove") != std::string::npos ||
          response.find("nobestmove") != std::string::npos) {
        found_terminator = true;
        // Wait a bit more to ensure we got the complete line
        std::this_thread::sleep_for(std::chrono::milliseconds(50));
        // Try to read any remaining data
        while (fgets(buffer, sizeof(buffer), engine_stdout_)) {
          response += buffer;
          // Check again in case there's more
          if (response.find("bestmove") != std::string::npos ||
              response.find("nobestmove") != std::string::npos) {
            break; // Got bestmove, definitely done
          }
        }
        break;
      }
    } else {
      // No data available immediately
      if (got_data) {
        // If we got data but no terminator, wait a bit more
        if (!found_terminator) {
          std::this_thread::sleep_for(std::chrono::milliseconds(100));
          // Try one more read
          if (fgets(buffer, sizeof(buffer), engine_stdout_)) {
            response += buffer;
            if (response.find("uciok") != std::string::npos ||
                response.find("readyok") != std::string::npos ||
                response.find("bestmove") != std::string::npos) {
              found_terminator = true;
              break;
            }
          }
        }
        // If we have data and either found terminator or no more data, break
        if (found_terminator || elapsed_ms > timeout_ms / 2) {
          break;
        }
      }
      // Wait a bit before retrying
      std::this_thread::sleep_for(std::chrono::milliseconds(10));
    }
  }

  // Remove trailing whitespace
  while (!response.empty() &&
         (response.back() == '\n' || response.back() == '\r' ||
          response.back() == ' ' || response.back() == '\t')) {
    response.pop_back();
  }

  return response;
}

void PikafishEngine::getDifficultyParams(AIDifficulty difficulty, int &depth,
                                         int &time_ms) {
  switch (difficulty) {
  case AIDifficulty::EASY:
    depth = 3;
    time_ms = 500;
    break;
  case AIDifficulty::MEDIUM:
    depth = 5;
    time_ms = 1000;
    break;
  case AIDifficulty::HARD:
    depth = 8;
    time_ms = 2000;
    break;
  }
}

std::string PikafishEngine::getBestMove(const std::string &fen_position,
                                        AIDifficulty difficulty) {
  std::lock_guard<std::mutex> lock(engine_mutex_);

  if (!engine_ready_) {
    std::cerr << "[AI] Error: Engine not ready" << std::endl;
    return "";
  }

  int depth, time_ms;
  getDifficultyParams(difficulty, depth, time_ms);

  std::cout << "[AI] Getting best move (difficulty: "
            << (difficulty == AIDifficulty::EASY     ? "EASY"
                : difficulty == AIDifficulty::MEDIUM ? "MEDIUM"
                                                     : "HARD")
            << ", depth: " << depth << ", time: " << time_ms << "ms)"
            << std::endl;
  std::cout << "[AI] Position: " << fen_position << std::endl;

  // Set position
  // fen_position can be either:
  // 1. "position fen <fen> moves <move1> <move2> ..." (from getPositionString)
  // 2. Just FEN string (backward compatibility)
  std::string position_cmd;
  if (fen_position.find("position ") == 0) {
    // Already a full position command
    position_cmd = fen_position;
  } else {
    // Just FEN string, construct position command
    position_cmd = "position fen " + fen_position;
  }

  if (!sendCommand(position_cmd)) {
    std::cerr << "[AI] Error: Failed to send position command" << std::endl;
    return "";
  }

  // Set search parameters
  std::ostringstream oss;
  oss << "go depth " << depth;
  if (!sendCommand(oss.str())) {
    std::cerr << "[AI] Error: Failed to send go command" << std::endl;
    return "";
  }

  // Read best move with timeout
  std::string response = readResponse(time_ms + 1000);
  std::cout << "[AI] Engine response: " << response << std::endl;

  // Parse response: "bestmove a0a1" or "bestmove a0a1 ponder b1b2"
  size_t bestmove_pos = response.find("bestmove");
  if (bestmove_pos != std::string::npos) {
    std::istringstream iss(response.substr(bestmove_pos));
    std::string token;
    iss >> token; // "bestmove"
    if (iss >> token) {
      std::cout << "[AI] Best move found: " << token << std::endl;
      return token; // Return the move (e.g., "a0a1")
    }
  }

  std::cerr << "[AI] Error: Failed to parse bestmove from response: "
            << response << std::endl;
  return "";
}

std::string PikafishEngine::suggestMove(const std::string &fen_position) {
  // Use HARD difficulty for suggestions
  return getBestMove(fen_position, AIDifficulty::HARD);
}

// Static method implementation
MovePayload PikafishEngine::parseUCIMove(const std::string &uci_move) {
  // UCI format: "a0a1" means from a0 to a1
  // Chinese Chess coordinates: files are a-i (left to right), ranks are 0-9
  // (bottom to top)
  MovePayload move;
  move.from = {-1, -1}; // Initialize as invalid
  move.to = {-1, -1};
  move.piece = "";

  if (uci_move.length() < 4) {
    return move; // Invalid
  }

  // Parse from position
  char from_col_char = uci_move[0];
  char from_row_char = uci_move[1];
  char to_col_char = uci_move[2];
  char to_row_char = uci_move[3];

  if (from_col_char < 'a' || from_col_char > 'i' || to_col_char < 'a' ||
      to_col_char > 'i' || from_row_char < '0' || from_row_char > '9' ||
      to_row_char < '0' || to_row_char > '9') {
    return move; // Invalid characters
  }

  int from_col = from_col_char - 'a'; // a=0, b=1, ..., i=8
  int from_row =
      from_row_char -
      '0'; // UCI rank 0 = row 0 (Đỏ bottom), rank 9 = row 9 (Đen top)
  int to_col = to_col_char - 'a';
  int to_row = to_row_char - '0';

  move.from = {from_row, from_col};
  move.to = {to_row, to_col};
  move.piece = ""; // Will be determined by game state

  return move;
}

// Static method implementation
std::string PikafishEngine::moveToUCI(const MovePayload &move) {
  // Convert MovePayload to UCI format
  // Row 0 = Đỏ (bottom) = UCI rank 0, Row 9 = Đen (top) = UCI rank 9
  char from_col = 'a' + move.from.col;
  char from_row = '0' + move.from.row;
  char to_col = 'a' + move.to.col;
  char to_row = '0' + move.to.row;

  std::string uci;
  uci += from_col;
  uci += from_row;
  uci += to_col;
  uci += to_row;
  return uci;
}

// ===================== Board Array Conversion Functions =====================
// //

std::string PikafishEngine::boardArrayToFEN(const char board[10][9],
                                            char side_to_move, int halfmove,
                                            int fullmove) {
  std::ostringstream fen;

  // Convert board array to FEN format
  // board[0] = Đỏ (bottom) → FEN rank 9, board[9] = Đen (top) → FEN rank 0
  for (int row = 9; row >= 0; row--) {
    if (row < 9)
      fen << '/';

    int empty_count = 0;
    for (int col = 0; col < 9; col++) {
      char piece = board[row][col];

      if (piece == ' ') {
        empty_count++;
      } else {
        // Output accumulated empty squares
        if (empty_count > 0) {
          fen << empty_count;
          empty_count = 0;
        }
        // Output piece
        fen << piece;
      }
    }

    // Output remaining empty squares at end of row
    if (empty_count > 0) {
      fen << empty_count;
    }
  }

  // Add FEN metadata
  fen << ' ' << side_to_move;
  fen << " - - "; // castling and en passant (not used in Chinese Chess)
  fen << halfmove << ' ' << fullmove;

  return fen.str();
}

bool PikafishEngine::FENToBoardArray(const std::string &fen, char board[10][9],
                                     char &side_to_move, int &halfmove,
                                     int &fullmove) {
  // Initialize board to empty
  for (int i = 0; i < 10; i++) {
    for (int j = 0; j < 9; j++) {
      board[i][j] = ' ';
    }
  }

  // Parse FEN string
  std::istringstream fen_stream(fen);
  std::string board_part;

  // Get board part (first part before space)
  if (!(fen_stream >> board_part)) {
    std::cerr << "[AI] Error: Invalid FEN format - missing board" << std::endl;
    return false;
  }

  // Parse remaining parts
  std::string side_str;
  fen_stream >> side_str;
  side_to_move = side_str.empty() ? 'w' : side_str[0];

  std::string dummy;
  fen_stream >> dummy; // castling
  fen_stream >> dummy; // en passant
  fen_stream >> halfmove;
  fen_stream >> fullmove;

  // Default values
  if (halfmove < 0)
    halfmove = 0;
  if (fullmove < 1)
    fullmove = 1;

  // Parse board string (ranks separated by '/')
  std::vector<std::string> ranks;
  std::istringstream board_stream(board_part);
  std::string rank_str;
  while (std::getline(board_stream, rank_str, '/')) {
    ranks.push_back(rank_str);
  }

  if (ranks.size() != 10) {
    std::cerr << "[AI] Error: Invalid FEN - expected 10 ranks, got "
              << ranks.size() << std::endl;
    return false;
  }

  // Fill board array
  // FEN rank 0 = top (Đen) → board[9], FEN rank 9 = bottom (Đỏ) → board[0]
  for (size_t rank_idx = 0; rank_idx < ranks.size(); rank_idx++) {
    int board_row = 9 - rank_idx; // Đảo ngược: FEN rank 0 → board row 9
    int col = 0;
    for (char c : ranks[rank_idx]) {
      if (col >= 9)
        break;

      if (c >= '1' && c <= '9') {
        // Empty squares
        int empty_count = c - '0';
        for (int i = 0; i < empty_count && col < 9; i++) {
          board[board_row][col++] = ' ';
        }
      } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
        // Piece
        board[board_row][col++] = c;
      }
    }
  }

  return true;
}

std::string PikafishEngine::boardArrayToPositionString(
    const char board[10][9], const std::vector<MovePayload> &moves,
    char side_to_move) {
  // Convert board array to FEN
  std::string fen = boardArrayToFEN(board, side_to_move);

  // Build position string
  std::ostringstream oss;
  oss << "position fen " << fen;

  // Add moves if any
  if (!moves.empty()) {
    oss << " moves";
    for (const auto &move : moves) {
      std::string uci_move = moveToUCI(move);
      oss << " " << uci_move;
    }
  }

  return oss.str();
}

bool PikafishEngine::positionStringToBoardArray(const std::string &position_str,
                                                char board[10][9],
                                                std::vector<MovePayload> &moves,
                                                char &side_to_move) {
  // Parse position string
  // Format: "position fen <fen> moves <move1> <move2> ..."

  std::istringstream iss(position_str);
  std::string token;

  // Check for "position"
  if (!(iss >> token) || token != "position") {
    std::cerr << "[AI] Error: Invalid position string - missing 'position'"
              << std::endl;
    return false;
  }

  // Check for "fen"
  if (!(iss >> token) || token != "fen") {
    std::cerr << "[AI] Error: Invalid position string - missing 'fen'"
              << std::endl;
    return false;
  }

  // Read FEN string (until "moves" or end)
  std::string fen;
  std::string fen_part;
  while (iss >> fen_part) {
    if (fen_part == "moves") {
      break; // Found moves section
    }
    if (!fen.empty())
      fen += " ";
    fen += fen_part;
  }

  if (fen.empty()) {
    std::cerr << "[AI] Error: Invalid position string - missing FEN"
              << std::endl;
    return false;
  }

  // Parse FEN to board array
  int halfmove, fullmove;
  if (!FENToBoardArray(fen, board, side_to_move, halfmove, fullmove)) {
    return false;
  }

  // Parse moves if present
  moves.clear();
  std::string uci_move;
  while (iss >> uci_move) {
    MovePayload move = parseUCIMove(uci_move);
    if (move.from.row >= 0 && move.from.col >= 0 && move.to.row >= 0 &&
        move.to.col >= 0) {
      moves.push_back(move);
    } else {
      std::cerr << "[AI] Warning: Invalid UCI move in position string: "
                << uci_move << std::endl;
    }
  }

  return true;
}

// ===================== GameStateManager Implementation =====================
// //

void GameStateManager::initializeGame(int player_fd, int ai_fd,
                                      AIDifficulty difficulty) {
  std::lock_guard<std::mutex> lock(games_mutex_);

  GameInfo game;
  game.player_fd = player_fd;
  game.ai_fd = ai_fd;
  game.difficulty = difficulty;
  game.initial_fen = getInitialFEN();
  game.move_history.clear();
  game.player_turn = true; // Player goes first

  active_games_[player_fd] = game;
}

bool GameStateManager::applyMove(int player_fd, const MovePayload &move) {
  std::lock_guard<std::mutex> lock(games_mutex_);

  auto it = active_games_.find(player_fd);
  if (it == active_games_.end()) {
    std::cerr << "[GameState] Error: No game found for player_fd=" << player_fd
              << std::endl;
    return false;
  }

  auto &game = it->second;

  // Validate move
  if (move.from.row < 0 || move.from.row >= 10 || move.from.col < 0 ||
      move.from.col >= 9 || move.to.row < 0 || move.to.row >= 10 ||
      move.to.col < 0 || move.to.col >= 9) {
    std::cerr << "[GameState] Error: Invalid move coordinates" << std::endl;
    return false;
  }

  // Add move to history (more reliable than recalculating FEN)
  game.move_history.push_back(move);
  game.player_turn = !game.player_turn;

  std::cout << "[GameState] Move applied: (" << move.from.row << ","
            << move.from.col << ") -> (" << move.to.row << "," << move.to.col
            << ")" << std::endl;

  return true;
}

std::string GameStateManager::getCurrentFEN(int player_fd) const {
  // For backward compatibility, calculate FEN from move history
  // But prefer using getPositionString() which is more reliable
  return getPositionString(player_fd);
}

std::string GameStateManager::getPositionString(int player_fd) const {
  std::lock_guard<std::mutex> lock(games_mutex_);

  auto it = active_games_.find(player_fd);
  if (it == active_games_.end()) {
    return "";
  }

  const auto &game = it->second;

  // Build position command using move history (more reliable)
  // Format: "position fen <initial_fen> moves <move1> <move2> ..."
  std::ostringstream oss;
  oss << "position fen " << game.initial_fen;

  if (!game.move_history.empty()) {
    oss << " moves";
    for (const auto &move : game.move_history) {
      // Convert MovePayload to UCI format
      std::string uci_move = PikafishEngine::moveToUCI(move);
      oss << " " << uci_move;
    }
  }

  return oss.str();
}

AIDifficulty GameStateManager::getAIDifficulty(int player_fd) const {
  std::lock_guard<std::mutex> lock(games_mutex_);

  auto it = active_games_.find(player_fd);
  if (it == active_games_.end()) {
    return AIDifficulty::MEDIUM;
  }

  return it->second.difficulty;
}

bool GameStateManager::hasGame(int player_fd) const {
  std::lock_guard<std::mutex> lock(games_mutex_);
  return active_games_.find(player_fd) != active_games_.end();
}

void GameStateManager::endGame(int player_fd) {
  std::lock_guard<std::mutex> lock(games_mutex_);
  active_games_.erase(player_fd);
}

int GameStateManager::getOpponentFD(int player_fd) const {
  std::lock_guard<std::mutex> lock(games_mutex_);

  auto it = active_games_.find(player_fd);
  if (it == active_games_.end()) {
    return -1;
  }

  return it->second.ai_fd;
}

GameStateManager::BoardState
GameStateManager::getBoardState(int player_fd) const {
  std::lock_guard<std::mutex> lock(games_mutex_);

  BoardState state;
  state.is_valid = false;

  auto it = active_games_.find(player_fd);
  if (it == active_games_.end()) {
    return state; // Return invalid state
  }

  const auto &game = it->second;

  // Set valid flag
  state.is_valid = true;

  // Get FEN (initial FEN for now, can be enhanced to calculate current FEN)
  state.fen = game.initial_fen;

  // Get position string (includes move history)
  state.position_string = getPositionString(player_fd);

  // Get move history
  state.moves = game.move_history;

  // Get difficulty
  state.difficulty = game.difficulty;

  // Get turn
  state.player_turn = game.player_turn;

  return state;
}

bool GameStateManager::getCurrentBoardArray(int player_fd,
                                            char board[10][9]) const {
  std::lock_guard<std::mutex> lock(games_mutex_);

  auto it = active_games_.find(player_fd);
  if (it == active_games_.end()) {
    return false; // Game doesn't exist
  }

  const auto &game = it->second;

  // Get position string
  std::string position_str = getPositionString(player_fd);

  // Convert position string to board array
  std::vector<MovePayload> moves;
  char side_to_move;
  if (!PikafishEngine::positionStringToBoardArray(position_str, board, moves,
                                                  side_to_move)) {
    // Fallback: use initial FEN if position string parsing fails
    char dummy_side;
    int dummy_halfmove, dummy_fullmove;
    if (!PikafishEngine::FENToBoardArray(game.initial_fen, board, dummy_side,
                                         dummy_halfmove, dummy_fullmove)) {
      return false;
    }

    // Apply all moves from history
    for (const auto &move : game.move_history) {
      applyMoveToBoardArray(board, move);
    }
  }

  return true;
}

// ===================== Board Array Operations ===================== //

// Helper functions for Chinese Chess validation
namespace {
// Check if position is in red palace (bottom, rows 0-2, cols 3-5)
bool isInRedPalace(int row, int col) {
  return row >= 0 && row <= 2 && col >= 3 && col <= 5;
}

// Check if position is in black palace (top, rows 7-9, cols 3-5)
bool isInBlackPalace(int row, int col) {
  return row >= 7 && row <= 9 && col >= 3 && col <= 5;
}

// Check if position is in palace (either red or black)
bool isInPalace(int row, int col) {
  return isInRedPalace(row, col) || isInBlackPalace(row, col);
}

// Check if red piece has crossed the river (row > 4)
bool redCrossedRiver(int row) { return row > 4; }

// Check if black piece has crossed the river (row < 5)
bool blackCrossedRiver(int row) { return row < 5; }

// Count pieces between two positions (horizontal or vertical line)
int countPiecesBetween(const char board[10][9], int from_row, int from_col,
                       int to_row, int to_col) {
  int count = 0;

  if (from_row == to_row) {
    // Horizontal line
    int start_col = std::min(from_col, to_col);
    int end_col = std::max(from_col, to_col);
    for (int col = start_col + 1; col < end_col; col++) {
      if (board[from_row][col] != ' ') {
        count++;
      }
    }
  } else if (from_col == to_col) {
    // Vertical line
    int start_row = std::min(from_row, to_row);
    int end_row = std::max(from_row, to_row);
    for (int row = start_row + 1; row < end_row; row++) {
      if (board[row][from_col] != ' ') {
        count++;
      }
    }
  }

  return count;
}

// Check if kings face each other directly (illegal position)
bool kingsFaceEachOther(const char board[10][9]) {
  // Find red king (K) and black king (k)
  int red_king_row = -1, red_king_col = -1;
  int black_king_row = -1, black_king_col = -1;

  for (int row = 0; row < 10; row++) {
    for (int col = 0; col < 9; col++) {
      if (board[row][col] == 'K') {
        red_king_row = row;
        red_king_col = col;
      } else if (board[row][col] == 'k') {
        black_king_row = row;
        black_king_col = col;
      }
    }
  }

  // If both kings found and in same column
  if (red_king_row >= 0 && black_king_row >= 0 &&
      red_king_col == black_king_col) {
    // Check if no pieces between them
    int start_row = std::min(red_king_row, black_king_row);
    int end_row = std::max(red_king_row, black_king_row);
    for (int row = start_row + 1; row < end_row; row++) {
      if (board[row][red_king_col] != ' ') {
        return false; // Piece blocking, kings don't face each other
      }
    }
    return true; // Kings face each other directly
  }

  return false;
}

// Validate King (K/k) move
bool isValidKingMove(const char board[10][9], int from_row, int from_col,
                     int to_row, int to_col, bool is_red) {
  // King must stay in palace
  if (!isInPalace(to_row, to_col)) {
    return false;
  }

  // King can only move 1 square horizontally or vertically
  int row_diff = std::abs(to_row - from_row);
  int col_diff = std::abs(to_col - from_col);

  if ((row_diff == 1 && col_diff == 0) || (row_diff == 0 && col_diff == 1)) {
    return true;
  }

  return false;
}

// Validate Advisor (A/a) move
bool isValidAdvisorMove(const char board[10][9], int from_row, int from_col,
                        int to_row, int to_col, bool is_red) {
  // Advisor must stay in palace
  if (!isInPalace(to_row, to_col)) {
    return false;
  }

  // Advisor can only move 1 square diagonally
  int row_diff = std::abs(to_row - from_row);
  int col_diff = std::abs(to_col - from_col);

  if (row_diff == 1 && col_diff == 1) {
    return true;
  }

  return false;
}

// Validate Elephant (B/b) move
bool isValidElephantMove(const char board[10][9], int from_row, int from_col,
                         int to_row, int to_col, bool is_red) {
  // Elephant cannot cross the river
  if (is_red && to_row > 4) {
    return false; // Red elephant cannot cross river
  }
  if (!is_red && to_row < 5) {
    return false; // Black elephant cannot cross river
  }

  // Elephant moves 2 squares diagonally
  int row_diff = std::abs(to_row - from_row);
  int col_diff = std::abs(to_col - from_col);

  if (row_diff == 2 && col_diff == 2) {
    // Check if there's a piece blocking the diagonal path
    int mid_row = (from_row + to_row) / 2;
    int mid_col = (from_col + to_col) / 2;

    if (board[mid_row][mid_col] != ' ') {
      return false; // Blocked
    }

    return true;
  }

  return false;
}

// Validate Knight (N/n) move
bool isValidKnightMove(const char board[10][9], int from_row, int from_col,
                       int to_row, int to_col, bool is_red) {
  // Knight moves in L-shape: 2 squares in one direction, then 1 square
  // perpendicular
  int row_diff = std::abs(to_row - from_row);
  int col_diff = std::abs(to_col - from_col);

  // Must be L-shape: (2,1) or (1,2)
  if (!((row_diff == 2 && col_diff == 1) || (row_diff == 1 && col_diff == 2))) {
    return false;
  }

  // Check for blocking piece (horse leg)
  int block_row, block_col;
  if (row_diff == 2) {
    // Moving 2 rows, block is 1 row away
    block_row = from_row + (to_row > from_row ? 1 : -1);
    block_col = from_col;
  } else {
    // Moving 2 cols, block is 1 col away
    block_row = from_row;
    block_col = from_col + (to_col > from_col ? 1 : -1);
  }

  if (board[block_row][block_col] != ' ') {
    return false; // Blocked by piece (horse leg blocked)
  }

  return true;
}

// Validate Rook (R/r) move
bool isValidRookMove(const char board[10][9], int from_row, int from_col,
                     int to_row, int to_col, bool is_red) {
  // Rook moves horizontally or vertically
  if (from_row != to_row && from_col != to_col) {
    return false; // Not horizontal or vertical
  }

  // Check if path is clear
  int pieces_between =
      countPiecesBetween(board, from_row, from_col, to_row, to_col);
  if (pieces_between > 0) {
    return false; // Path blocked
  }

  return true;
}

// Validate Cannon (C/c) move
bool isValidCannonMove(const char board[10][9], int from_row, int from_col,
                       int to_row, int to_col, bool is_red) {
  // Cannon moves horizontally or vertically
  if (from_row != to_row && from_col != to_col) {
    return false; // Not horizontal or vertical
  }

  char target_piece = board[to_row][to_col];
  int pieces_between =
      countPiecesBetween(board, from_row, from_col, to_row, to_col);

  if (target_piece == ' ') {
    // Moving to empty square: path must be clear
    return pieces_between == 0;
  } else {
    // Capturing: must have exactly one piece between (screen)
    return pieces_between == 1;
  }
}

// Validate Pawn (P/p) move
bool isValidPawnMove(const char board[10][9], int from_row, int from_col,
                     int to_row, int to_col, bool is_red) {
  int row_diff = to_row - from_row;
  int col_diff = std::abs(to_col - from_col);

  if (is_red) {
    // Red pawn (P) starts at bottom (row 0-4), moves forward (toward black) =
    // row increases Forward means moving toward opponent (black at top), so row
    // increases
    if (redCrossedRiver(from_row)) {
      // After crossing river (row > 4): can move forward or sideways
      if (row_diff == 1 && col_diff == 0) {
        return true; // Forward (row increases)
      }
      if (row_diff == 0 && col_diff == 1) {
        return true; // Sideways
      }
    } else {
      // Before crossing river (row <= 4): can only move forward
      if (row_diff == 1 && col_diff == 0) {
        return true; // Forward (row increases)
      }
    }
  } else {
    // Black pawn (p) starts at top (row 5-9), moves forward (toward red) = row
    // decreases Forward means moving toward opponent (red at bottom), so row
    // decreases
    if (blackCrossedRiver(from_row)) {
      // After crossing river (row < 5): can move forward or sideways
      if (row_diff == -1 && col_diff == 0) {
        return true; // Forward (row decreases)
      }
      if (row_diff == 0 && col_diff == 1) {
        return true; // Sideways
      }
    } else {
      // Before crossing river (row >= 5): can only move forward
      if (row_diff == -1 && col_diff == 0) {
        return true; // Forward (row decreases)
      }
    }
  }

  return false;
}
} // namespace

bool GameStateManager::isValidMoveOnBoard(const char board[10][9],
                                          const MovePayload &move) {
  // Validate coordinates
  if (move.from.row < 0 || move.from.row >= 10 || move.from.col < 0 ||
      move.from.col >= 9 || move.to.row < 0 || move.to.row >= 10 ||
      move.to.col < 0 || move.to.col >= 9) {
    return false;
  }

  // Check if source has a piece
  char piece = board[move.from.row][move.from.col];
  if (piece == ' ') {
    return false; // No piece at source
  }

  // Check if destination is different from source
  if (move.from.row == move.to.row && move.from.col == move.to.col) {
    return false; // Same position
  }

  // Determine piece color
  bool is_red = (piece >= 'A' && piece <= 'Z');
  bool is_black = (piece >= 'a' && piece <= 'z');

  if (!is_red && !is_black) {
    return false; // Invalid piece character
  }

  // Check if capturing own piece
  char captured = board[move.to.row][move.to.col];
  if (captured != ' ') {
    bool captured_is_red = (captured >= 'A' && captured <= 'Z');
    bool captured_is_black = (captured >= 'a' && captured <= 'z');

    if ((is_red && captured_is_red) || (is_black && captured_is_black)) {
      return false; // Cannot capture own piece
    }
  }

  // Validate move based on piece type
  bool valid_move = false;

  switch (piece) {
  case 'K': // Red King
  case 'k': // Black King
    valid_move = isValidKingMove(board, move.from.row, move.from.col,
                                 move.to.row, move.to.col, is_red);
    break;

  case 'A': // Red Advisor
  case 'a': // Black Advisor
    valid_move = isValidAdvisorMove(board, move.from.row, move.from.col,
                                    move.to.row, move.to.col, is_red);
    break;

  case 'B': // Red Elephant
  case 'b': // Black Elephant
    valid_move = isValidElephantMove(board, move.from.row, move.from.col,
                                     move.to.row, move.to.col, is_red);
    break;

  case 'N': // Red Knight
  case 'n': // Black Knight
    valid_move = isValidKnightMove(board, move.from.row, move.from.col,
                                   move.to.row, move.to.col, is_red);
    break;

  case 'R': // Red Rook
  case 'r': // Black Rook
    valid_move = isValidRookMove(board, move.from.row, move.from.col,
                                 move.to.row, move.to.col, is_red);
    break;

  case 'C': // Red Cannon
  case 'c': // Black Cannon
    valid_move = isValidCannonMove(board, move.from.row, move.from.col,
                                   move.to.row, move.to.col, is_red);
    break;

  case 'P': // Red Pawn
  case 'p': // Black Pawn
    valid_move = isValidPawnMove(board, move.from.row, move.from.col,
                                 move.to.row, move.to.col, is_red);
    break;

  default:
    return false; // Unknown piece type
  }

  if (!valid_move) {
    return false;
  }

  // After validating the move, check if it would result in kings facing each
  // other We need to simulate the move temporarily
  char temp_board[10][9];
  for (int i = 0; i < 10; i++) {
    for (int j = 0; j < 9; j++) {
      temp_board[i][j] = board[i][j];
    }
  }

  // Apply move temporarily
  temp_board[move.to.row][move.to.col] = piece;
  temp_board[move.from.row][move.from.col] = ' ';

  // Check if kings face each other after this move
  if (kingsFaceEachOther(temp_board)) {
    return false; // Move would result in illegal king position
  }

  return true;
}

bool GameStateManager::applyMoveToBoardArray(char board[10][9],
                                             const MovePayload &move) {
  // Validate move first
  if (!isValidMoveOnBoard(board, move)) {
    std::cerr << "[BoardArray] Error: Invalid move" << std::endl;
    return false;
  }

  // Get piece at source
  char piece = board[move.from.row][move.from.col];
  if (piece == ' ') {
    std::cerr << "[BoardArray] Error: No piece at source position" << std::endl;
    return false;
  }

  // Handle capture (if any)
  char captured_piece = board[move.to.row][move.to.col];
  if (captured_piece != ' ') {
    std::cout << "[BoardArray] Capturing piece: " << captured_piece << " at ("
              << move.to.row << "," << move.to.col << ")" << std::endl;
  }

  // Apply move: move piece from source to destination
  board[move.from.row][move.from.col] = ' '; // Clear source
  board[move.to.row][move.to.col] = piece;   // Place piece at destination

  std::cout << "[BoardArray] Move applied: (" << move.from.row << ","
            << move.from.col << ") -> (" << move.to.row << "," << move.to.col
            << ")" << std::endl;
  std::cout << "[BoardArray] Piece moved: " << piece << std::endl;

  return true;
}

std::string GameStateManager::getInitialFEN() const {
  // Standard starting position for Chinese Chess (Xiangqi)
  // Format: "fen <board> <side> <castling> <enpassant> <halfmove> <fullmove>"
  // For Chinese Chess, we use a simplified format
  // Board representation: r=rook, n=knight, b=bishop, a=advisor, k=king,
  // c=cannon, p=pawn Uppercase = red (bottom), lowercase = black (top)

  // Standard starting position
  return "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 "
         "1";
}

std::string GameStateManager::updateFEN(const std::string &current_fen,
                                        const MovePayload &move) {
  // Parse FEN string
  // Format: "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - -
  // 0 1" Parts: [board] [side] [castling] [enpassant] [halfmove] [fullmove]

  std::istringstream fen_stream(current_fen);
  std::string board_part, side, castling, enpassant;
  int halfmove = 0, fullmove = 1;

  // Parse board part (first part before space)
  if (!(fen_stream >> board_part)) {
    std::cerr << "Error: Invalid FEN format - missing board" << std::endl;
    return current_fen;
  }

  // Parse remaining parts
  fen_stream >> side;
  fen_stream >> castling;
  fen_stream >> enpassant;
  fen_stream >> halfmove;
  fen_stream >> fullmove;

  // Default values if not present
  if (side.empty())
    side = "w";
  if (castling.empty())
    castling = "-";
  if (enpassant.empty())
    enpassant = "-";

  // Parse board into 10x9 array
  // Board representation: 10 rows (ranks), 9 columns (files)
  // Row 0 = top (black side), Row 9 = bottom (red side)
  char board[10][9];
  for (int i = 0; i < 10; i++) {
    for (int j = 0; j < 9; j++) {
      board[i][j] = ' '; // Empty
    }
  }

  // Parse board string (ranks separated by '/')
  std::vector<std::string> ranks;
  std::istringstream board_stream(board_part);
  std::string rank_str;
  while (std::getline(board_stream, rank_str, '/')) {
    ranks.push_back(rank_str);
  }

  if (ranks.size() != 10) {
    std::cerr << "Error: Invalid FEN - expected 10 ranks, got " << ranks.size()
              << std::endl;
    return current_fen;
  }

  // Fill board array
  for (size_t rank_idx = 0; rank_idx < ranks.size(); rank_idx++) {
    int col = 0;
    for (char c : ranks[rank_idx]) {
      if (col >= 9)
        break;

      if (c >= '1' && c <= '9') {
        // Empty squares
        int empty_count = c - '0';
        for (int i = 0; i < empty_count && col < 9; i++) {
          board[rank_idx][col++] = ' ';
        }
      } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
        // Piece
        board[rank_idx][col++] = c;
      }
    }
  }

  // Apply move
  // Move coordinates: from (row, col) to (row, col)
  // Note: FEN uses rank 0 = top, but our system may use different convention
  // Assuming move.from.row and move.from.col are in 0-9, 0-8 range
  int from_row = move.from.row;
  int from_col = move.from.col;
  int to_row = move.to.row;
  int to_col = move.to.col;

  // Validate coordinates
  if (from_row < 0 || from_row >= 10 || from_col < 0 || from_col >= 9 ||
      to_row < 0 || to_row >= 10 || to_col < 0 || to_col >= 9) {
    std::cerr << "[GameState] Error: Invalid move coordinates (" << from_row
              << "," << from_col << ") -> (" << to_row << "," << to_col << ")"
              << std::endl;
    return current_fen;
  }

  // Validate that from and to are different
  if (from_row == to_row && from_col == to_col) {
    std::cerr << "[GameState] Error: Move from and to positions are the same"
              << std::endl;
    return current_fen;
  }

  // Get piece at from position
  char piece = board[from_row][from_col];
  if (piece == ' ') {
    std::cerr << "[GameState] Error: No piece at source position (" << from_row
              << "," << from_col << ")" << std::endl;
    return current_fen;
  }

  // Validate side to move matches piece color
  bool is_red_piece = (piece >= 'A' && piece <= 'Z');
  bool is_black_piece = (piece >= 'a' && piece <= 'z');
  if (side == "w" && !is_red_piece) {
    std::cerr << "[GameState] Warning: Red to move but piece is not red"
              << std::endl;
  } else if (side == "b" && !is_black_piece) {
    std::cerr << "[GameState] Warning: Black to move but piece is not black"
              << std::endl;
  }

  // Check if capturing own piece (shouldn't happen in valid moves)
  char captured_piece = board[to_row][to_col];
  if (captured_piece != ' ') {
    bool captured_is_red = (captured_piece >= 'A' && captured_piece <= 'Z');
    bool captured_is_black = (captured_piece >= 'a' && captured_piece <= 'z');
    if ((is_red_piece && captured_is_red) ||
        (is_black_piece && captured_is_black)) {
      std::cerr << "[GameState] Warning: Attempting to capture own piece"
                << std::endl;
    }
  }

  // Move piece (capture if needed)
  board[to_row][to_col] = piece;
  board[from_row][from_col] = ' ';

  // Toggle side to move
  std::string new_side = (side == "w") ? "b" : "w";

  // Increment move counters
  int new_halfmove = halfmove + 1;
  int new_fullmove = fullmove;
  if (new_side == "w") {
    new_fullmove = fullmove + 1;
  }

  // Regenerate FEN string
  std::ostringstream new_fen;

  // Reconstruct board string
  for (int rank = 0; rank < 10; rank++) {
    if (rank > 0)
      new_fen << "/";

    int empty_count = 0;
    for (int file = 0; file < 9; file++) {
      char c = board[rank][file];
      if (c == ' ') {
        empty_count++;
      } else {
        if (empty_count > 0) {
          new_fen << empty_count;
          empty_count = 0;
        }
        new_fen << c;
      }
    }
    if (empty_count > 0) {
      new_fen << empty_count;
    }
  }

  // Add remaining FEN parts
  new_fen << " " << new_side;
  new_fen << " " << castling;
  new_fen << " " << enpassant;
  new_fen << " " << new_halfmove;
  new_fen << " " << new_fullmove;

  return new_fen.str();
}
