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

  // ========== Board Array Conversion Functions (for Database) ==========
  
  // Convert 2D board array to FEN string
  // board[10][9]: ' ' = empty, 'r'/'R' = rook, 'n'/'N' = knight, etc.
  // side_to_move: 'w' = red/white, 'b' = black
  static std::string boardArrayToFEN(const char board[10][9], char side_to_move = 'w', 
                                      int halfmove = 0, int fullmove = 1);
  
  // Convert FEN string to 2D board array
  // Returns true if successful, false if invalid FEN
  // board[10][9] will be filled with pieces (' ' = empty, 'r'/'R' = rook, etc.)
  static bool FENToBoardArray(const std::string &fen, char board[10][9], 
                               char &side_to_move, int &halfmove, int &fullmove);
  
  // Convert 2D board array + move history to Position String (UCI format)
  // board[10][9]: Current board state
  // moves: Vector of moves played (in MovePayload format)
  // Returns: "position fen <fen> moves <move1> <move2> ..."
  static std::string boardArrayToPositionString(const char board[10][9], 
                                                 const std::vector<MovePayload> &moves = {},
                                                 char side_to_move = 'w');
  
  // Convert Position String (UCI) to 2D board array
  // position_str: "position fen <fen> moves <move1> <move2> ..."
  // board[10][9]: Output board array
  // moves: Output move history (optional)
  // Returns true if successful
  static bool positionStringToBoardArray(const std::string &position_str, 
                                          char board[10][9],
                                          std::vector<MovePayload> &moves,
                                          char &side_to_move);

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

  // Get board state (for external use - e.g., validation, game logic)
  // Returns a struct containing FEN, move history, and other game info
  struct BoardState {
    std::string fen;                    // Current FEN string
    std::string position_string;       // Position string for engine
    std::vector<MovePayload> moves;     // Move history
    AIDifficulty difficulty;           // AI difficulty level
    bool player_turn;                  // true if player's turn
    bool is_valid;                     // false if game doesn't exist
  };
  
  BoardState getBoardState(int player_fd) const;

  // ========== Board Array Operations (for Database Integration) ==========
  
  // Apply move to board array manually (for network programming requirement)
  // This function implements move logic manually instead of using engine
  // Returns true if move was applied successfully
  static bool applyMoveToBoardArray(char board[10][9], const MovePayload &move);
  
  // Validate move on board array
  // Checks if move is legal (piece exists, destination valid, etc.)
  static bool isValidMoveOnBoard(const char board[10][9], const MovePayload &move);
  
  // Get current board array for a game
  // Fills board[10][9] with current position
  // Returns true if successful, false if game doesn't exist
  bool getCurrentBoardArray(int player_fd, char board[10][9]) const;

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

