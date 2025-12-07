#pragma once

#include "MessageTypes.h"
#include <memory>
#include <string>
#include <mutex>
#include <vector>
#include <unistd.h>
#include <sys/types.h>

// AI Difficulty levels (keep for compatibility)
enum class AIDifficulty {
  EASY,    // Depth 3, time limit 500ms
  MEDIUM,  // Depth 5, time limit 1000ms
  HARD     // Depth 8, time limit 2000ms
};

// Forward declaration
class GameStateManager;

// Python AI Service Wrapper
// Calls Python AI service via subprocess
class PythonAIWrapper {
  friend class GameStateManager;  // Allow GameStateManager to use persistent connection
  
public:
  PythonAIWrapper();
  ~PythonAIWrapper();

  // Initialize Python AI service
  bool initialize(const std::string &pikafish_path = "pikafish");

  // Shutdown Python AI service
  void shutdown();

  // Check if service is ready
  bool isReady() const;

  // Get best move from current position
  // Returns move in UCI format (e.g., "a0a1")
  std::string getBestMove(const std::string &fen_position, AIDifficulty difficulty);

  // Get move suggestion (same as getBestMove but with HARD difficulty)
  std::string suggestMove(const std::string &fen_position);

  // Convert UCI move format to MovePayload (static helper)
  static MovePayload parseUCIMove(const std::string &uci_move);

  // Convert MovePayload to UCI format (static helper)
  static std::string moveToUCI(const MovePayload &move);

private:
  // Call Python service with JSON command (uses persistent connection)
  std::string callPythonService(const std::string &json_command);

  // Get path to Python AI service script
  std::string getPythonServicePath() const;

  // Convert AIDifficulty to string
  std::string difficultyToString(AIDifficulty difficulty) const;

  // Start persistent Python service process
  bool startPythonService();

  // Stop persistent Python service process
  void stopPythonService();

  mutable std::mutex service_mutex_;
  bool service_ready_;
  std::string python_service_path_;
  
  // Persistent service connection
  pid_t child_pid_;      // Python process PID
  int stdin_fd_;         // Write to Python stdin
  int stdout_fd_;        // Read from Python stdout

// Game State Manager (Python-backed)
class GameStateManager {
public:
  // Initialize game with starting position
  void initializeGame(int player_fd, int ai_fd, AIDifficulty difficulty);

  // Apply move to game state
  bool applyMove(int player_fd, const MovePayload &move);

  // Get current FEN position (for backward compatibility)
  std::string getCurrentFEN(int player_fd) const;

  // Get position string for engine (uses move history - more reliable)
  std::string getPositionString(int player_fd) const;

  // Get position hash (for state comparison - fast)
  // Returns hash of position string (simple string hash, fast comparison)
  std::size_t getPositionHash(int player_fd) const;

  // Get AI difficulty for this game
  AIDifficulty getAIDifficulty(int player_fd) const;

  // Check if game exists
  bool hasGame(int player_fd) const;

  // End game
  void endGame(int player_fd);

  // Get opponent FD
  int getOpponentFD(int player_fd) const;

  // Get board state (for external use)
  struct BoardState {
    std::string fen;
    std::string position_string;
    std::vector<MovePayload> moves;
    AIDifficulty difficulty;
    bool player_turn;
    bool is_valid;
  };
  
  BoardState getBoardState(int player_fd) const;

  // Quick validation (C++ native, fast) - for AI moves only
  // Only checks basic sanity, assumes Pikafish engine already validated legality
  static bool quickValidateMove(const MovePayload &move, const char board[10][9]);

  // Get current board array for a game
  bool getCurrentBoardArray(int player_fd, char board[10][9]) const;

  // Set shared wrapper (called by server.cpp when initializing)
  static void setSharedWrapper(PythonAIWrapper* wrapper);

private:
  // Call Python service for game state operations (uses shared connection from PythonAIWrapper)
  std::string callPythonService(const std::string &json_command) const;

  // Get path to Python AI service script
  std::string getPythonServicePath() const;

  // Convert AIDifficulty to string
  std::string difficultyToString(AIDifficulty difficulty) const;

  mutable std::mutex games_mutex_;
  
  // Shared PythonAIWrapper instance for persistent connection
  static PythonAIWrapper* shared_wrapper_;

