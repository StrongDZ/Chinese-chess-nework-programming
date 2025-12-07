#include "../../include/protocol/python_ai_wrapper.h"
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <iostream>
#include <sstream>
#include <functional>  // For std::hash
#include <unistd.h>
#include <sys/wait.h>
#include <signal.h>
#include <nlohmann/json.hpp>
#ifdef __APPLE__
#include <mach-o/dyld.h>
#endif

// ===================== Helper Functions ===================== //

static std::string getExecutableDirectory() {
  char path[1024];
  ssize_t len = 0;

#ifdef __APPLE__
  uint32_t size = sizeof(path);
  if (_NSGetExecutablePath(path, &size) == 0) {
    len = strlen(path);
  }
#elif defined(__linux__)
  len = readlink("/proc/self/exe", path, sizeof(path) - 1);
  if (len != -1) {
    path[len] = '\0';
  }
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

static std::string findPythonService() {
  // Try to find python_ai/ai_service.py relative to executable
  std::string exe_dir = getExecutableDirectory();
  if (!exe_dir.empty()) {
    std::string service_path = exe_dir + "../python_ai/ai_service.py";
    std::ifstream file(service_path);
    if (file.good()) {
      file.close();
      return service_path;
    }
  }

  // Try current directory
  std::string service_path = "python_ai/ai_service.py";
  std::ifstream file(service_path);
  if (file.good()) {
    file.close();
    return service_path;
  }

  // Try absolute path from source
  service_path = "backend/python_ai/ai_service.py";
  file.open(service_path);
  if (file.good()) {
    file.close();
    return service_path;
  }

  return "python_ai/ai_service.py"; // Default fallback
}

// ===================== PythonAIWrapper Implementation ===================== //

PythonAIWrapper::PythonAIWrapper() 
  : service_ready_(false), child_pid_(-1), stdin_fd_(-1), stdout_fd_(-1) {
  python_service_path_ = findPythonService();
}

PythonAIWrapper::~PythonAIWrapper() {
  shutdown();
}

std::string PythonAIWrapper::getPythonServicePath() const {
  return python_service_path_;
}

std::string PythonAIWrapper::difficultyToString(AIDifficulty difficulty) const {
  switch (difficulty) {
    case AIDifficulty::EASY:
      return "easy";
    case AIDifficulty::MEDIUM:
      return "medium";
    case AIDifficulty::HARD:
      return "hard";
    default:
      return "medium";
  }
}

bool PythonAIWrapper::startPythonService() {
  if (child_pid_ != -1) {
    // Service already started
    return true;
  }

  // Create pipes for stdin and stdout
  int stdin_pipe[2], stdout_pipe[2];
  if (pipe(stdin_pipe) < 0 || pipe(stdout_pipe) < 0) {
    std::cerr << "[PythonAI] Error: Failed to create pipes" << std::endl;
    return false;
  }

  // Fork process
  pid_t pid = fork();
  if (pid < 0) {
    std::cerr << "[PythonAI] Error: Failed to fork process" << std::endl;
    close(stdin_pipe[0]);
    close(stdin_pipe[1]);
    close(stdout_pipe[0]);
    close(stdout_pipe[1]);
    return false;
  }

  if (pid == 0) {
    // Child process: Python service
    close(stdin_pipe[1]);  // Close write end of stdin pipe
    close(stdout_pipe[0]); // Close read end of stdout pipe

    // Redirect stdin and stdout
    dup2(stdin_pipe[0], STDIN_FILENO);
    dup2(stdout_pipe[1], STDOUT_FILENO);
    close(stdin_pipe[0]);
    close(stdout_pipe[1]);

    // Execute Python service
    std::string python_cmd = "python3";
    execlp(python_cmd.c_str(), python_cmd.c_str(), python_service_path_.c_str(), nullptr);
    
    std::cerr << "[PythonAI] Error: Failed to execute Python service" << std::endl;
    exit(1);
  }

  // Parent process
  close(stdin_pipe[0]);  // Close read end of stdin pipe
  close(stdout_pipe[1]); // Close write end of stdout pipe

  // Store persistent connection
  child_pid_ = pid;
  stdin_fd_ = stdin_pipe[1];
  stdout_fd_ = stdout_pipe[0];

  std::cout << "[PythonAI] Started persistent Python service (PID: " << child_pid_ << ")" << std::endl;
  return true;
}

void PythonAIWrapper::stopPythonService() {
  if (child_pid_ == -1) {
    return;
  }

  // Close pipes
  if (stdin_fd_ != -1) {
    close(stdin_fd_);
    stdin_fd_ = -1;
  }
  if (stdout_fd_ != -1) {
    close(stdout_fd_);
    stdout_fd_ = -1;
  }

  // Kill child process
  if (child_pid_ > 0) {
    kill(child_pid_, SIGTERM);
    int status;
    waitpid(child_pid_, &status, 0);
    child_pid_ = -1;
  }

  std::cout << "[PythonAI] Stopped Python service" << std::endl;
}

std::string PythonAIWrapper::callPythonService(const std::string &json_command) {
  std::lock_guard<std::mutex> lock(service_mutex_);

  if (child_pid_ == -1 || stdin_fd_ == -1 || stdout_fd_ == -1) {
    std::cerr << "[PythonAI] Error: Service not started" << std::endl;
    return "";
  }

  // Check if process is still alive
  if (kill(child_pid_, 0) != 0) {
    std::cerr << "[PythonAI] Error: Python process died" << std::endl;
    stopPythonService();
    return "";
  }

  // Write command to Python's stdin
  std::string command_line = json_command + "\n";
  ssize_t written = write(stdin_fd_, command_line.c_str(), command_line.length());
  if (written < 0) {
    std::cerr << "[PythonAI] Error: Failed to write to Python stdin" << std::endl;
    return "";
  }

  // Read response from Python's stdout (read until newline)
  std::string response;
  char buffer[4096];
  ssize_t n;
  
  // Read line by line until we get a complete JSON response
  while ((n = read(stdout_fd_, buffer, sizeof(buffer) - 1)) > 0) {
    buffer[n] = '\0';
    response += buffer;
    
    // Check if we have a complete JSON line (ends with newline)
    if (response.find('\n') != std::string::npos) {
      // Extract the first line (JSON response)
      size_t newline_pos = response.find('\n');
      response = response.substr(0, newline_pos);
      break;
    }
  }

  if (n < 0) {
    std::cerr << "[PythonAI] Error: Failed to read from Python stdout" << std::endl;
    return "";
  }

  return response;
}

bool PythonAIWrapper::initialize(const std::string &pikafish_path) {
  std::lock_guard<std::mutex> lock(service_mutex_);

  if (service_ready_) {
    std::cout << "[PythonAI] Service already initialized" << std::endl;
    return true;
  }

  std::cout << "[PythonAI] Initializing Python AI service..." << std::endl;
  std::cout << "[PythonAI] Service path: " << python_service_path_ << std::endl;

  // Start persistent Python service process
  if (!startPythonService()) {
    std::cerr << "[PythonAI] Error: Failed to start Python service" << std::endl;
    return false;
  }

  // Send initialize command
  nlohmann::json command;
  command["command"] = "initialize";
  command["params"]["pikafish_path"] = pikafish_path;

  std::string response = callPythonService(command.dump());
  
  try {
    nlohmann::json result = nlohmann::json::parse(response);
    if (result.contains("success") && result["success"]) {
      service_ready_ = true;
      std::cout << "[PythonAI] Service initialized successfully" << std::endl;
      return true;
    } else {
      std::cerr << "[PythonAI] Error: " << result.value("error", "Unknown error") << std::endl;
      stopPythonService();
      return false;
    }
  } catch (const std::exception &e) {
    std::cerr << "[PythonAI] Error parsing response: " << e.what() << std::endl;
    stopPythonService();
    return false;
  }
}

void PythonAIWrapper::shutdown() {
  std::lock_guard<std::mutex> lock(service_mutex_);

  if (!service_ready_ && child_pid_ == -1) {
    return;
  }

  std::cout << "[PythonAI] Shutting down Python AI service..." << std::endl;

  // Send shutdown command if service is ready
  if (service_ready_ && child_pid_ != -1) {
    nlohmann::json command;
    command["command"] = "shutdown";
    callPythonService(command.dump());
  }

  service_ready_ = false;
  
  // Stop Python process
  stopPythonService();

  std::cout << "[PythonAI] Service shutdown complete" << std::endl;
}

bool PythonAIWrapper::isReady() const {
  std::lock_guard<std::mutex> lock(service_mutex_);
  return service_ready_;
}

std::string PythonAIWrapper::getBestMove(const std::string &fen_position, AIDifficulty difficulty) {
  std::lock_guard<std::mutex> lock(service_mutex_);

  if (!service_ready_) {
    std::cerr << "[PythonAI] Error: Service not ready" << std::endl;
    return "";
  }

  nlohmann::json command;
  command["command"] = "get_best_move";
  command["params"]["fen_position"] = fen_position;
  command["params"]["difficulty"] = difficultyToString(difficulty);

  std::string response = callPythonService(command.dump());

  try {
    nlohmann::json result = nlohmann::json::parse(response);
    if (result.contains("success") && result["success"]) {
      if (result.contains("data") && result["data"].contains("move")) {
        return result["data"]["move"].get<std::string>();
      }
    }
    std::cerr << "[PythonAI] Error: " << result.value("error", "Unknown error") << std::endl;
  } catch (const std::exception &e) {
    std::cerr << "[PythonAI] Error parsing response: " << e.what() << std::endl;
  }

  return "";
}

std::string PythonAIWrapper::suggestMove(const std::string &fen_position) {
  std::lock_guard<std::mutex> lock(service_mutex_);

  if (!service_ready_) {
    std::cerr << "[PythonAI] Error: Service not ready" << std::endl;
    return "";
  }

  nlohmann::json command;
  command["command"] = "suggest_move";
  command["params"]["fen_position"] = fen_position;

  std::string response = callPythonService(command.dump());

  try {
    nlohmann::json result = nlohmann::json::parse(response);
    if (result.contains("success") && result["success"]) {
      if (result.contains("data") && result["data"].contains("move")) {
        return result["data"]["move"].get<std::string>();
      }
    }
    std::cerr << "[PythonAI] Error: " << result.value("error", "Unknown error") << std::endl;
  } catch (const std::exception &e) {
    std::cerr << "[PythonAI] Error parsing response: " << e.what() << std::endl;
  }

  return "";
}

MovePayload PythonAIWrapper::parseUCIMove(const std::string &uci_move) {
  MovePayload move;
  move.from = {-1, -1};
  move.to = {-1, -1};
  move.piece = "";

  if (uci_move.length() < 4) {
    return move;
  }

  char from_col_char = uci_move[0];
  char from_row_char = uci_move[1];
  char to_col_char = uci_move[2];
  char to_row_char = uci_move[3];

  if (from_col_char < 'a' || from_col_char > 'i' || to_col_char < 'a' ||
      to_col_char > 'i' || from_row_char < '0' || from_row_char > '9' ||
      to_row_char < '0' || to_row_char > '9') {
    return move;
  }

  int from_col = from_col_char - 'a';
  int from_row = from_row_char - '0';
  int to_col = to_col_char - 'a';
  int to_row = to_row_char - '0';

  move.from = {from_row, from_col};
  move.to = {to_row, to_col};

  return move;
}

std::string PythonAIWrapper::moveToUCI(const MovePayload &move) {
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

// ===================== GameStateManager Implementation ===================== //

// Static member initialization
PythonAIWrapper* GameStateManager::shared_wrapper_ = nullptr;

void GameStateManager::setSharedWrapper(PythonAIWrapper* wrapper) {
  shared_wrapper_ = wrapper;
}

std::string GameStateManager::getPythonServicePath() const {
  return findPythonService();
}

std::string GameStateManager::difficultyToString(AIDifficulty difficulty) const {
  switch (difficulty) {
    case AIDifficulty::EASY:
      return "easy";
    case AIDifficulty::MEDIUM:
      return "medium";
    case AIDifficulty::HARD:
      return "hard";
    default:
      return "medium";
  }
}

std::string GameStateManager::callPythonService(const std::string &json_command) const {
  std::lock_guard<std::mutex> lock(games_mutex_);

  // Use shared PythonAIWrapper connection if available
  if (shared_wrapper_ != nullptr) {
    return shared_wrapper_->callPythonService(json_command);
  }

  // Fallback: create temporary connection (should not happen in production)
  // Create pipes for stdin and stdout
  int stdin_pipe[2], stdout_pipe[2];
  if (pipe(stdin_pipe) < 0 || pipe(stdout_pipe) < 0) {
    return "";
  }

  std::string service_path = getPythonServicePath();
  pid_t pid = fork();
  
  if (pid < 0) {
    close(stdin_pipe[0]);
    close(stdin_pipe[1]);
    close(stdout_pipe[0]);
    close(stdout_pipe[1]);
    return "";
  }

  if (pid == 0) {
    // Child process
    close(stdin_pipe[1]);
    close(stdout_pipe[0]);
    dup2(stdin_pipe[0], STDIN_FILENO);
    dup2(stdout_pipe[1], STDOUT_FILENO);
    close(stdin_pipe[0]);
    close(stdout_pipe[1]);
    execlp("python3", "python3", service_path.c_str(), nullptr);
    exit(1);
  }

  // Parent process
  close(stdin_pipe[0]);
  close(stdout_pipe[1]);

  // Write command
  write(stdin_pipe[1], json_command.c_str(), json_command.length());
  write(stdin_pipe[1], "\n", 1);
  close(stdin_pipe[1]);

  // Read response
  std::string response;
  char buffer[4096];
  ssize_t n;
  while ((n = read(stdout_pipe[0], buffer, sizeof(buffer) - 1)) > 0) {
    buffer[n] = '\0';
    response += buffer;
  }
  close(stdout_pipe[0]);

  int status;
  waitpid(pid, &status, 0);

  return response;
}

void GameStateManager::initializeGame(int player_fd, int ai_fd, AIDifficulty difficulty) {
  nlohmann::json command;
  command["command"] = "initialize_game";
  command["params"]["player_fd"] = player_fd;
  command["params"]["ai_fd"] = ai_fd;
  command["params"]["difficulty"] = difficultyToString(difficulty);

  callPythonService(command.dump());
}

bool GameStateManager::applyMove(int player_fd, const MovePayload &move) {
  nlohmann::json command;
  command["command"] = "apply_move";
  command["params"]["player_fd"] = player_fd;
  command["params"]["from_row"] = move.from.row;
  command["params"]["from_col"] = move.from.col;
  command["params"]["to_row"] = move.to.row;
  command["params"]["to_col"] = move.to.col;

  std::string response = callPythonService(command.dump());
  
  try {
    nlohmann::json result = nlohmann::json::parse(response);
    return result.value("success", false);
  } catch (...) {
    return false;
  }
}

std::string GameStateManager::getCurrentFEN(int player_fd) const {
  return getPositionString(player_fd);
}

std::size_t GameStateManager::getPositionHash(int player_fd) const {
  // Get position string and compute hash
  // Using std::hash for fast comparison (64-bit on most systems)
  std::string position = getPositionString(player_fd);
  if (position.empty()) {
    return 0; // Invalid state
  }
  
  // Use std::hash for fast hash computation
  // This is a simple but effective hash for state comparison
  return std::hash<std::string>{}(position);
}

std::string GameStateManager::getPositionString(int player_fd) const {
  nlohmann::json command;
  command["command"] = "get_position_string";
  command["params"]["player_fd"] = player_fd;

  std::string response = callPythonService(command.dump());
  
  try {
    nlohmann::json result = nlohmann::json::parse(response);
    if (result.contains("data") && result["data"].contains("position")) {
      return result["data"]["position"].get<std::string>();
    }
  } catch (...) {
  }
  
  return "";
}

std::size_t GameStateManager::getPositionHash(int player_fd) const {
  // Get position string and compute hash
  // Using std::hash for fast comparison (64-bit on most systems)
  std::string position = getPositionString(player_fd);
  if (position.empty()) {
    return 0;
  }
  
  // Use std::hash for fast hash computation
  return std::hash<std::string>{}(position);
}

AIDifficulty GameStateManager::getAIDifficulty(int player_fd) const {
  nlohmann::json command;
  command["command"] = "get_ai_difficulty";
  command["params"]["player_fd"] = player_fd;

  std::string response = callPythonService(command.dump());
  
  try {
    nlohmann::json result = nlohmann::json::parse(response);
    if (result.contains("data") && result["data"].contains("difficulty")) {
      std::string diff_str = result["data"]["difficulty"].get<std::string>();
      if (diff_str == "easy") return AIDifficulty::EASY;
      if (diff_str == "hard") return AIDifficulty::HARD;
      return AIDifficulty::MEDIUM;
    }
  } catch (...) {
  }
  
  return AIDifficulty::MEDIUM;
}

bool GameStateManager::hasGame(int player_fd) const {
  nlohmann::json command;
  command["command"] = "has_game";
  command["params"]["player_fd"] = player_fd;

  std::string response = callPythonService(command.dump());
  
  try {
    nlohmann::json result = nlohmann::json::parse(response);
    if (result.contains("data") && result["data"].contains("has_game")) {
      return result["data"]["has_game"].get<bool>();
    }
  } catch (...) {
  }
  
  return false;
}

void GameStateManager::endGame(int player_fd) {
  nlohmann::json command;
  command["command"] = "end_game";
  command["params"]["player_fd"] = player_fd;

  callPythonService(command.dump());
}

int GameStateManager::getOpponentFD(int player_fd) const {
  nlohmann::json command;
  command["command"] = "get_opponent_fd";
  command["params"]["player_fd"] = player_fd;

  std::string response = callPythonService(command.dump());
  
  try {
    nlohmann::json result = nlohmann::json::parse(response);
    if (result.contains("data") && result["data"].contains("opponent_fd")) {
      return result["data"]["opponent_fd"].get<int>();
    }
  } catch (...) {
  }
  
  return -1;
}

GameStateManager::BoardState GameStateManager::getBoardState(int player_fd) const {
  BoardState state;
  state.is_valid = false;
  state.difficulty = AIDifficulty::MEDIUM;
  state.player_turn = true;

  if (!hasGame(player_fd)) {
    return state;
  }

  state.is_valid = true;
  state.fen = getCurrentFEN(player_fd);
  state.position_string = getPositionString(player_fd);
  state.difficulty = getAIDifficulty(player_fd);

  return state;
}

// REMOVED: isValidMoveOnBoard() - No longer used
// Move validation is now done on frontend before sending
// This function was deprecated and not called from anywhere

bool GameStateManager::quickValidateMove(const MovePayload &move, const char board[10][9]) {
  // Lightweight C++ native validation - Optimistic Validation approach
  // Pikafish engine already validates legal moves, this is just a critical safety check
  
  // 1. Check coordinates in range (prevent array out of bounds - CRITICAL)
  if (move.from.row < 0 || move.from.row >= 10 || move.from.col < 0 || move.from.col >= 9 ||
      move.to.row < 0 || move.to.row >= 10 || move.to.col < 0 || move.to.col >= 9) {
    return false;
  }
  
  // 2. Check piece exists at source (critical - prevent memory errors)
  char piece = board[move.from.row][move.from.col];
  if (piece == ' ') {
    return false;
  }
  
  // 3. Check destination is different from source (logical check)
  if (move.from.row == move.to.row && move.from.col == move.to.col) {
    return false;
  }
  
  // 4. Check piece color is valid (basic rule check)
  bool is_red_piece = (piece >= 'A' && piece <= 'Z');
  bool is_black_piece = (piece >= 'a' && piece <= 'z');
  
  if (!is_red_piece && !is_black_piece) {
    return false; // Invalid piece character
  }
  
  // Basic sanity check passed
  // Note: Full validation (piece rules, path blocking, palace, river, etc.) is done by Pikafish engine
  // This lightweight check only prevents critical errors (out of bounds, missing piece, invalid piece)
  return true;
}

// REMOVED: applyMoveToBoardArray() - No longer used
// Move validation is now done on frontend before sending
// This function was not called from anywhere

bool GameStateManager::getCurrentBoardArray(int player_fd, char board[10][9]) const {
  nlohmann::json command;
  command["command"] = "get_current_board_array";
  command["params"]["player_fd"] = player_fd;

  std::string response = callPythonService(command.dump());
  
  try {
    nlohmann::json result = nlohmann::json::parse(response);
    if (result.contains("success") && result["success"]) {
      if (result.contains("data") && result["data"].contains("board")) {
        nlohmann::json board_json = result["data"]["board"];
        if (board_json.is_array() && board_json.size() == 10) {
          for (int i = 0; i < 10; i++) {
            if (board_json[i].is_array() && board_json[i].size() == 9) {
              for (int j = 0; j < 9; j++) {
                std::string piece = board_json[i][j].get<std::string>();
                board[i][j] = piece.empty() ? ' ' : piece[0];
              }
            } else {
              return false;
            }
          }
          return true;
        }
      }
    }
  } catch (...) {
  }
  
  return false;
}

