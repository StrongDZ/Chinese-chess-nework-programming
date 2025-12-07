#ifndef GAME_REPOSITORY_H
#define GAME_REPOSITORY_H

#include "database/mongodb_client.h"
#include <string>
#include <vector>
#include <optional>
#include <chrono>

/**
 * GameRepository - Data Access Layer cho Game
 * 
 * Chịu trách nhiệm:
 * - CRUD operations trên MongoDB collections: active_games, game_archive, player_stats
 * - Không chứa business logic
 * 
 * NOTE: Không dùng Redis - MongoDB only
 */

// Move structure
struct Move {
    int move_number;
    std::string player;           // Username of player who made move
    int from_x, from_y;
    int to_x, to_y;
    std::string piece;
    std::string captured;
    std::string notation;
    std::string xfen_after;
    std::chrono::system_clock::time_point timestamp;
    int time_taken;
};

// Game model
struct Game {
    std::string id;               // MongoDB _id
    std::string red_player;       // Red player username
    std::string black_player;     // Black player username
    std::string status;           // in_progress, completed, abandoned
    std::string result;           // red_win, black_win, draw
    std::string winner;           // Winner username (empty if draw/ongoing)
    std::chrono::system_clock::time_point start_time;
    std::optional<std::chrono::system_clock::time_point> end_time;
    std::string xfen;             // Current board state
    std::string current_turn;     // red, black
    int move_count;
    std::string time_control;     // bullet, blitz, classical
    int time_limit;
    int red_time_remaining;
    int black_time_remaining;
    int increment;
    bool rated;
    std::vector<Move> moves;
};

// Draw offer model (stored in MongoDB instead of Redis)
struct DrawOffer {
    std::string game_id;
    std::string from_player;      // Username of player who offered draw
    std::chrono::system_clock::time_point created_at;
    std::chrono::system_clock::time_point expires_at;
};

class GameRepository {
private:
    MongoDBClient& mongoClient;

public:
    explicit GameRepository(MongoDBClient& mongo);
    
    // ============ Game Operations ============
    
    // Create new game, returns game_id if success
    std::string createGame(const Game& game);
    
    // Find game by ID
    std::optional<Game> findById(const std::string& gameId);
    
    // Update game after move
    bool updateAfterMove(const std::string& gameId, 
                         const Move& move,
                         const std::string& nextTurn,
                         int redTimeRemaining,
                         int blackTimeRemaining,
                         const std::string& newXfen = "");
    
    // End game (complete or abandon)
    bool endGame(const std::string& gameId,
                 const std::string& status,
                 const std::string& result,
                 const std::string& winner = "");
    
    // Get games by user (username)
    std::vector<Game> findByUser(const std::string& username,
                                  const std::string& filter = "all",
                                  int limit = 50);
    
    // ============ Draw Offer Operations (MongoDB instead of Redis) ============
    
    // Create draw offer
    bool createDrawOffer(const DrawOffer& offer);
    
    // Get active draw offer for game
    std::optional<DrawOffer> getDrawOffer(const std::string& gameId);
    
    // Delete draw offer
    bool deleteDrawOffer(const std::string& gameId);
    
    // ============ Player Stats Operations ============
    
    // Update player ratings after game ends
    bool updatePlayerStats(const std::string& username,
                           const std::string& timeControl,
                           int newRating,
                           const std::string& resultField); // wins, losses, draws
    
    // Get player rating
    int getPlayerRating(const std::string& username, const std::string& timeControl);
    
    // ============ Helper Operations ============
    
    // Check if user exists
    bool userExists(const std::string& username);
};

#endif
