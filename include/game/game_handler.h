#ifndef GAME_HANDLER_H
#define GAME_HANDLER_H

#include "../database/mongodb_client.h"
#include "../database/redis_client.h"
#include <json/json.h>
#include <string>
#include <bsoncxx/oid.hpp>

// Move structure
struct Move {
    int moveNumber;
    bsoncxx::oid playerId;
    int fromX, fromY;
    int toX, toY;
    std::string piece;
    std::string captured;
    std::string notation;
    std::string fenAfter;
    int timeTaken;
};

class GameHandler {
private:
    MongoDBClient& mongoClient;
    RedisClient& redisClient;

public:
    GameHandler(MongoDBClient& mongo, RedisClient& redis);
    
    // Create a new game
    Json::Value handleCreateGame(const Json::Value& request);
    
    // Make a move
    Json::Value handleMakeMove(const Json::Value& request);
    
    // End game (resign, draw offer, timeout)
    Json::Value handleEndGame(const Json::Value& request);
    
    // Get game state
    Json::Value handleGetGame(const Json::Value& request);
    
    // Get active games for a player
    Json::Value handleGetActiveGames(const Json::Value& request);
    
    // Accept/reject draw offer
    Json::Value handleDrawOffer(const Json::Value& request);

private:
    // Validate FEN string
    bool isValidFEN(const std::string& fen);
    
    // Validate move (basic validation, full chess logic would be more complex)
    bool isValidMove(const Move& move);
    
    // Archive a completed game
    bool archiveGame(const bsoncxx::oid& gameId, 
                    const std::string& result,
                    const std::string& termination);
    
    // Get user ID from token
    std::string getUserIdFromToken(const std::string& token);
    
    // Calculate initial FEN for Chinese Chess (Xiangqi)
    std::string getInitialXiangqiFEN();
    
    // Validate time control
    bool isValidTimeControl(const std::string& timeControl);
};

#endif // GAME_HANDLER_H
