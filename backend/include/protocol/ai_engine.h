#pragma once

#include "MessageTypes.h"
#include <memory>
#include <string>
#include <vector>
#include <mutex>
#include <thread>
#include <condition_variable>
#include <atomic>

// Forward declaration
struct GameState;

// AI Difficulty levels
enum class AIDifficulty {
  EASY,    // Depth 3, time limit 500ms
  MEDIUM,  // Depth 5, time limit 1000ms
  HARD     // Depth 8, time limit 2000ms
};

// Pikafish Engine wrapper
class PikafishEngine {
public:
  PikafishEngine();
  ~PikafishEngine();

  // Initialize engine (start Pikafish process)
  bool initialize(const std::string &pikafish_path = "pikafish");

  // Shutdown engine
  void shutdown();

  // Get best move from current position
  // Returns move in UCCI format (e.g., "a0a1")
  std::string getBestMove(const std::string &fen_position, AIDifficulty difficulty);

  // Get move suggestion (same as getBestMove but with HARD difficulty)
  std::string suggestMove(const std::string &fen_position);

  // Convert UCI move format to MovePayload (static helper)
  static MovePayload parseUCIMove(const std::string &uci_move);

  // Convert MovePayload to UCI format (static helper)
  static std::string moveToUCI(const MovePayload &move);

  // Check if engine is ready
  bool isReady() const { return engine_ready_; }

private:
  // Send command to Pikafish
  bool sendCommand(const std::string &cmd);

  // Read response from Pikafish
  std::string readResponse(int timeout_ms = 5000);

  // Convert difficulty to depth and time
  void getDifficultyParams(AIDifficulty difficulty, int &depth, int &time_ms);

  // Process handle for Pikafish
  FILE *engine_stdin_;
  FILE *engine_stdout_;
  int engine_pid_;
  std::atomic<bool> engine_ready_;
  std::mutex engine_mutex_;
};

// Game State Manager
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

  // Get AI difficulty for this game
  AIDifficulty getAIDifficulty(int player_fd) const;

  // Check if game exists
  bool hasGame(int player_fd) const;

  // End game
  void endGame(int player_fd);

  // Get opponent FD
  int getOpponentFD(int player_fd) const;

private:
  struct GameInfo {
    int player_fd;
    int ai_fd;
    AIDifficulty difficulty;
    std::string initial_fen;  // Starting position FEN
    std::vector<MovePayload> move_history;  // All moves played (more reliable than recalculating FEN)
    bool player_turn;  // true if player's turn, false if AI's turn
  };

  std::map<int, GameInfo> active_games_;
  mutable std::mutex games_mutex_;

  // Generate initial FEN for Chinese Chess
  std::string getInitialFEN() const;

  // Update FEN after move
  std::string updateFEN(const std::string &current_fen, const MovePayload &move);
};

