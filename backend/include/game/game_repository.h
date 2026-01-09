#ifndef GAME_REPOSITORY_H
#define GAME_REPOSITORY_H

#include "database/mongodb_client.h"
#include <chrono>
#include <optional>
#include <string>
#include <vector>

/**
 * GameRepository - Data Access Layer cho Game
 *
 * Chịu trách nhiệm:
 * - CRUD operations trên MongoDB collections: active_games, game_archive,
 * player_stats
 * - Không chứa business logic
 *
 * NOTE: Không dùng Redis - MongoDB only
 */

// Move structure
struct Move {
  int move_number;
  std::string player; // Username of player who made move
  int from_x, from_y;
  int to_x, to_y;
  std::string piece;
  std::string captured;
  std::string notation;
  std::string xfen_after;
  std::chrono::system_clock::time_point timestamp;
  int time_taken;
};

// Game model (active games)
struct Game {
  std::string id;           // MongoDB _id
  std::string red_player;   // red player username
  std::string black_player; // black player username
  std::string status;       // in_progress, completed, abandoned
  std::string result;       // red_win, black_win, draw
  std::string winner;       // Winner username (empty if draw/ongoing)
  std::chrono::system_clock::time_point start_time;
  std::optional<std::chrono::system_clock::time_point> end_time;
  std::string xfen;         // Current board state
  std::string current_turn; // red, black
  int move_count;
  std::string time_control; // blitz, classical
  int time_limit;
  int red_time_remaining;
  int black_time_remaining;
  int increment;
  bool rated;
  std::vector<Move> moves;
  std::string
      draw_offered_by; // Username of player who offered draw (empty if none)
};

// Archived game model (completed games)
struct ArchivedGame {
  std::string id;               // MongoDB _id
  std::string original_game_id; // Original game ID from active_games
  std::string red_player;
  std::string black_player;
  std::string winner;
  std::string result; // red_win, black_win, draw
  std::chrono::system_clock::time_point start_time;
  std::chrono::system_clock::time_point end_time;
  std::string time_control;
  int time_limit;
  int increment;
  bool rated;
  int move_count;
  std::vector<Move> moves;
  std::string rematch_offered_by; // Username of player who offered rematch
                                  // (empty if none)
  bool rematch_accepted; // True if rematch was accepted and new game created
};

class GameRepository {
private:
  MongoDBClient &mongoClient;

public:
  explicit GameRepository(MongoDBClient &mongo);

  // ============ Game Operations ============

  // Create new game, returns game_id if success
  std::string createGame(const Game &game);

  // Find game by ID
  std::optional<Game> findById(const std::string &gameId);

  // Update game after move
  bool updateAfterMove(const std::string &gameId, const Move &move,
                       const std::string &nextTurn, int redTimeRemaining,
                       int blackTimeRemaining, const std::string &newXfen = "");

  // End game (complete or abandon)
  bool endGame(const std::string &gameId, const std::string &status,
               const std::string &result, const std::string &winner = "");

  // ============ Draw Offer Operations ============

  // Set draw offer (username who offered)
  bool setDrawOffer(const std::string &gameId, const std::string &username);

  // Clear draw offer (after decline or new move)
  bool clearDrawOffer(const std::string &gameId);

  // Get games by user (username)
  std::vector<Game> findByUser(const std::string &username,
                               const std::string &filter = "all",
                               int limit = 50);

  // ============ Player Stats Operations ============

  // Player stats structure for Glicko-2
  struct PlayerGlickoStats {
    int rating;
    double rd;         // Rating Deviation
    double volatility; // Rating volatility (sigma)
  };

  // Update player ratings after game ends (Glicko-2)
  bool updatePlayerStats(const std::string &username,
                         const std::string &timeControl, int newRating,
                         double newRD, double newVolatility,
                         const std::string &resultField); // wins, losses, draws

  // Get player full Glicko stats (rating, RD, volatility)
  PlayerGlickoStats getPlayerGlickoStats(const std::string &username,
                                         const std::string &timeControl);

  // Get player rating (backward compatibility)
  int getPlayerRating(const std::string &username,
                      const std::string &timeControl);
  // ============ Rematch Operations ============

  // Find archived game by ID
  std::optional<ArchivedGame> findArchivedGameById(const std::string &gameId);

  // Find game history for a user (completed games from archive)
  std::vector<ArchivedGame> findGameHistory(const std::string &username,
                                            int limit = 50, int offset = 0);

  // Set rematch offer on archived game
  bool setRematchOffer(const std::string &gameId, const std::string &username);

  // Clear rematch offer
  bool clearRematchOffer(const std::string &gameId);

  // Set rematch accepted flag
  bool setRematchAccepted(const std::string &gameId);

  // ============ Helper Operations ============

  // Check if user exists
  bool userExists(const std::string &username);
};

#endif
