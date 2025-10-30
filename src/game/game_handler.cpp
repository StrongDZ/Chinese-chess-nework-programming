#include "../../include/game/game_handler.h"
#include <bsoncxx/builder/stream/document.hpp>
#include <bsoncxx/json.hpp>
#include <mongocxx/exception/exception.hpp>
#include <chrono>
#include <regex>

using namespace std;
using bsoncxx::builder::stream::document;
using bsoncxx::builder::stream::finalize;
using bsoncxx::builder::stream::open_document;
using bsoncxx::builder::stream::close_document;
using bsoncxx::builder::stream::open_array;
using bsoncxx::builder::stream::close_array;

GameHandler::GameHandler(MongoDBClient& mongo, RedisClient& redis)
    : mongoClient(mongo), redisClient(redis) {
}

string GameHandler::getInitialXiangqiFEN() {
    // Standard Xiangqi starting position
    return "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1";
}

bool GameHandler::isValidTimeControl(const string& timeControl) {
    return timeControl == "bullet" || timeControl == "blitz" || timeControl == "classical";
}

bool GameHandler::isValidFEN(const string& fen) {
    // Basic FEN validation (simplified)
    return !fen.empty() && fen.length() > 10;
}

bool GameHandler::isValidMove(const Move& move) {
    // Basic validation
    if (move.fromX < 0 || move.fromX > 8 || move.fromY < 0 || move.fromY > 9) return false;
    if (move.toX < 0 || move.toX > 8 || move.toY < 0 || move.toY > 9) return false;
    if (move.piece.empty()) return false;
    return true;
}

string GameHandler::getUserIdFromToken(const string& token) {
    return redisClient.getSession(token);
}

Json::Value GameHandler::handleCreateGame(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate input
        if (!request.isMember("token") || !request.isMember("opponent_username")) {
            response["success"] = false;
            response["error"] = "Missing required fields (token, opponent_username)";
            return response;
        }
        
        string token = request["token"].asString();
        string opponentUsername = request["opponent_username"].asString();
        string timeControl = request.get("time_control", "blitz").asString();
        int timeLimit = request.get("time_limit", 300).asInt(); // Default 5 minutes
        int increment = request.get("increment", 3).asInt(); // Default 3 seconds
        bool rated = request.get("rated", true).asBool();
        
        // 2. Validate time control
        if (!isValidTimeControl(timeControl)) {
            response["success"] = false;
            response["error"] = "Invalid time control (must be bullet, blitz, or classical)";
            return response;
        }
        
        // 3. Get user ID from token
        string userId = getUserIdFromToken(token);
        if (userId.empty()) {
            response["success"] = false;
            response["error"] = "Invalid or expired session";
            return response;
        }
        
        // 4. Get users from database
        auto db = mongoClient.getDatabase();
        auto users = db["users"];
        
        auto userDoc = users.find_one(document{} << "_id" << bsoncxx::oid(userId) << finalize);
        if (!userDoc) {
            response["success"] = false;
            response["error"] = "User not found";
            return response;
        }
        
        auto opponentDoc = users.find_one(document{} << "username" << opponentUsername << finalize);
        if (!opponentDoc) {
            response["success"] = false;
            response["error"] = "Opponent not found";
            return response;
        }
        
        string username = string(userDoc->view()["username"].get_string().value);
        bsoncxx::oid opponentId = opponentDoc->view()["_id"].get_oid().value;
        
        // 5. Create game document
        auto games = db["active_games"];
        auto now = chrono::system_clock::now();
        string initialFen = getInitialXiangqiFEN();
        
        auto gameDoc = document{}
            << "red_player_id" << bsoncxx::oid(userId)
            << "black_player_id" << opponentId
            << "red_player_name" << username
            << "black_player_name" << opponentUsername
            << "status" << "waiting"
            << "winner_id" << bsoncxx::types::b_null()
            << "result" << bsoncxx::types::b_null()
            << "start_time" << bsoncxx::types::b_date{now}
            << "end_time" << bsoncxx::types::b_null()
            << "fen" << initialFen
            << "xfen" << initialFen
            << "moves" << open_array << close_array
            << "current_turn" << "white"
            << "move_count" << 0
            << "time_control" << timeControl
            << "time_limit" << timeLimit
            << "red_time_remaining" << timeLimit
            << "black_time_remaining" << timeLimit
            << "increment" << increment
            << "rated" << rated
            << finalize;
        
        auto insertResult = games.insert_one(gameDoc.view());
        
        if (!insertResult) {
            response["success"] = false;
            response["error"] = "Failed to create game";
            return response;
        }
        
        string gameId = insertResult->inserted_id().get_oid().value.to_string();
        
        // 6. Store game in Redis for quick access
        string gameKey = "game:" + gameId;
        redisClient.set(gameKey, "waiting", 3600); // 1 hour TTL
        
        // 7. Return success
        response["success"] = true;
        response["message"] = "Game created successfully";
        response["data"]["game_id"] = gameId;
        response["data"]["status"] = "waiting";
        response["data"]["time_control"] = timeControl;
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Create game error: ") + e.what();
    }
    
    return response;
}

Json::Value GameHandler::handleMakeMove(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate input
        if (!request.isMember("token") || !request.isMember("game_id") || !request.isMember("move")) {
            response["success"] = false;
            response["error"] = "Missing required fields (token, game_id, move)";
            return response;
        }
        
        string token = request["token"].asString();
        string gameId = request["game_id"].asString();
        auto moveJson = request["move"];
        
        // 2. Get user ID
        string userId = getUserIdFromToken(token);
        if (userId.empty()) {
            response["success"] = false;
            response["error"] = "Invalid or expired session";
            return response;
        }
        
        // 3. Parse move
        Move move;
        move.playerId = bsoncxx::oid(userId);
        move.fromX = moveJson.get("from_x", -1).asInt();
        move.fromY = moveJson.get("from_y", -1).asInt();
        move.toX = moveJson.get("to_x", -1).asInt();
        move.toY = moveJson.get("to_y", -1).asInt();
        move.piece = moveJson.get("piece", "").asString();
        move.captured = moveJson.get("captured", "").asString();
        move.notation = moveJson.get("notation", "").asString();
        move.fenAfter = moveJson.get("fen_after", "").asString();
        move.timeTaken = moveJson.get("time_taken", 0).asInt();
        
        // 4. Validate move
        if (!isValidMove(move)) {
            response["success"] = false;
            response["error"] = "Invalid move";
            return response;
        }
        
        // 5. Get game from database
        auto db = mongoClient.getDatabase();
        auto games = db["active_games"];
        
        auto gameDoc = games.find_one(document{} << "_id" << bsoncxx::oid(gameId) << finalize);
        if (!gameDoc) {
            response["success"] = false;
            response["error"] = "Game not found";
            return response;
        }
        
        auto gameView = gameDoc->view();
        string status = string(gameView["status"].get_string().value);
        
        if (status != "in_progress" && status != "waiting") {
            response["success"] = false;
            response["error"] = "Game is not active";
            return response;
        }
        
        // 6. Verify it's player's turn
        bsoncxx::oid redPlayerId = gameView["red_player_id"].get_oid().value;
        bsoncxx::oid blackPlayerId = gameView["black_player_id"].get_oid().value;
        string currentTurn = string(gameView["current_turn"].get_string().value);
        
        bool isRedPlayer = (move.playerId == redPlayerId);
        bool isBlackPlayer = (move.playerId == blackPlayerId);
        
        if (!isRedPlayer && !isBlackPlayer) {
            response["success"] = false;
            response["error"] = "You are not a player in this game";
            return response;
        }
        
        if ((currentTurn == "white" && !isRedPlayer) || (currentTurn == "black" && !isBlackPlayer)) {
            response["success"] = false;
            response["error"] = "Not your turn";
            return response;
        }
        
        // 7. Update game state
        int moveCount = gameView["move_count"].get_int32().value;
        move.moveNumber = moveCount + 1;
        
        auto now = chrono::system_clock::now();
        
        // Build move document
        auto moveDoc = document{}
            << "move_number" << move.moveNumber
            << "player_id" << move.playerId
            << "from" << open_document
                << "x" << move.fromX
                << "y" << move.fromY
            << close_document
            << "to" << open_document
                << "x" << move.toX
                << "y" << move.toY
            << close_document
            << "piece" << move.piece
            << "captured" << (move.captured.empty() ? bsoncxx::types::b_null() : bsoncxx::types::b_string{move.captured})
            << "notation" << move.notation
            << "fen_after" << move.fenAfter
            << "timestamp" << bsoncxx::types::b_date{now}
            << "time_taken" << move.timeTaken
            << finalize;
        
        // Update game
        string nextTurn = (currentTurn == "white") ? "black" : "white";
        string newStatus = (status == "waiting") ? "in_progress" : status;
        
        auto update = document{}
            << "$push" << open_document
                << "moves" << moveDoc.view()
            << close_document
            << "$set" << open_document
                << "current_turn" << nextTurn
                << "move_count" << move.moveNumber
                << "fen" << move.fenAfter
                << "xfen" << move.fenAfter
                << "status" << newStatus
            << close_document
            << "$inc" << open_document
                << (isRedPlayer ? "red_time_remaining" : "black_time_remaining") << -move.timeTaken
            << close_document
            << finalize;
        
        auto filter = document{} << "_id" << bsoncxx::oid(gameId) << finalize;
        auto result = games.update_one(filter.view(), update.view());
        
        if (!result || result->modified_count() == 0) {
            response["success"] = false;
            response["error"] = "Failed to update game";
            return response;
        }
        
        // 8. Return success
        response["success"] = true;
        response["message"] = "Move made successfully";
        response["data"]["move_number"] = move.moveNumber;
        response["data"]["next_turn"] = nextTurn;
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Make move error: ") + e.what();
    }
    
    return response;
}

Json::Value GameHandler::handleEndGame(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate input
        if (!request.isMember("token") || !request.isMember("game_id") || !request.isMember("result")) {
            response["success"] = false;
            response["error"] = "Missing required fields (token, game_id, result)";
            return response;
        }
        
        string token = request["token"].asString();
        string gameId = request["game_id"].asString();
        string result = request["result"].asString();
        string termination = request.get("termination", "resignation").asString();
        
        // 2. Get user ID
        string userId = getUserIdFromToken(token);
        if (userId.empty()) {
            response["success"] = false;
            response["error"] = "Invalid or expired session";
            return response;
        }
        
        // 3. Archive game
        if (!archiveGame(bsoncxx::oid(gameId), result, termination)) {
            response["success"] = false;
            response["error"] = "Failed to archive game";
            return response;
        }
        
        // 4. Return success
        response["success"] = true;
        response["message"] = "Game ended successfully";
        response["data"]["result"] = result;
        response["data"]["termination"] = termination;
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("End game error: ") + e.what();
    }
    
    return response;
}

bool GameHandler::archiveGame(const bsoncxx::oid& gameId,
                               const string& result,
                               const string& termination) {
    try {
        auto db = mongoClient.getDatabase();
        auto activeGames = db["active_games"];
        auto gameArchive = db["game_archive"];
        
        // Get game
        auto gameDoc = activeGames.find_one(document{} << "_id" << gameId << finalize);
        if (!gameDoc) return false;
        
        auto gameView = gameDoc->view();
        
        // Determine winner
        bsoncxx::oid winnerId;
        bool hasWinner = false;
        
        if (result == "white_win") {
            winnerId = gameView["red_player_id"].get_oid().value;
            hasWinner = true;
        } else if (result == "black_win") {
            winnerId = gameView["black_player_id"].get_oid().value;
            hasWinner = true;
        }
        
        // Create archive document
        auto now = chrono::system_clock::now();
        
        auto archiveDocBuilder = document{}
            << "original_game_id" << gameId
            << "white_player_id" << gameView["red_player_id"].get_oid().value
            << "black_player_id" << gameView["black_player_id"].get_oid().value
            << "white_player_name" << gameView["red_player_name"].get_string().value
            << "black_player_name" << gameView["black_player_name"].get_string().value
            << "white_rating_before" << 1500 // TODO: Get from player_stats
            << "black_rating_before" << 1500
            << "white_rating_after" << 1500
            << "black_rating_after" << 1500;
        
        if (hasWinner) {
            archiveDocBuilder << "winner_id" << winnerId;
        } else {
            archiveDocBuilder << "winner_id" << bsoncxx::types::b_null();
        }
        
        archiveDocBuilder
            << "result" << result
            << "termination" << termination
            << "start_time" << gameView["start_time"].get_date()
            << "end_time" << bsoncxx::types::b_date{now}
            << "initial_fen" << getInitialXiangqiFEN()
            << "final_fen" << gameView["xfen"].get_string().value
            << "moves" << gameView["moves"].get_array().value
            << "pgn" << "Game record"
            << "move_count" << gameView["move_count"].get_int32().value
            << "time_control" << gameView["time_control"].get_string().value
            << "time_limit" << gameView["time_limit"].get_int32().value
            << "increment" << gameView["increment"].get_int32().value
            << "average_move_time" << 10.0
            << "longest_think_time" << 60
            << "archived_at" << bsoncxx::types::b_date{now}
            << finalize;
        
        // Insert to archive
        gameArchive.insert_one(archiveDocBuilder.view());
        
        // Delete from active games
        activeGames.delete_one(document{} << "_id" << gameId << finalize);
        
        return true;
        
    } catch (const exception& e) {
        cerr << "Archive game error: " << e.what() << endl;
        return false;
    }
}

Json::Value GameHandler::handleGetGame(const Json::Value& request) {
    Json::Value response;
    
    try {
        if (!request.isMember("game_id")) {
            response["success"] = false;
            response["error"] = "Missing game_id";
            return response;
        }
        
        string gameId = request["game_id"].asString();
        
        auto db = mongoClient.getDatabase();
        auto games = db["active_games"];
        
        auto gameDoc = games.find_one(document{} << "_id" << bsoncxx::oid(gameId) << finalize);
        
        if (!gameDoc) {
            response["success"] = false;
            response["error"] = "Game not found";
            return response;
        }
        
        // Convert BSON to JSON
        string jsonString = bsoncxx::to_json(gameDoc->view());
        
        response["success"] = true;
        response["data"] = jsonString;
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Get game error: ") + e.what();
    }
    
    return response;
}

Json::Value GameHandler::handleGetActiveGames(const Json::Value& request) {
    Json::Value response;
    
    try {
        if (!request.isMember("token")) {
            response["success"] = false;
            response["error"] = "Missing token";
            return response;
        }
        
        string token = request["token"].asString();
        string userId = getUserIdFromToken(token);
        
        if (userId.empty()) {
            response["success"] = false;
            response["error"] = "Invalid or expired session";
            return response;
        }
        
        auto db = mongoClient.getDatabase();
        auto games = db["active_games"];
        
        // Find games where user is either red or black player
        auto filter = document{}
            << "$or" << open_array
                << open_document
                    << "red_player_id" << bsoncxx::oid(userId)
                << close_document
                << open_document
                    << "black_player_id" << bsoncxx::oid(userId)
                << close_document
            << close_array
            << finalize;
        
        auto cursor = games.find(filter.view());
        
        response["success"] = true;
        response["data"] = Json::Value(Json::arrayValue);
        
        for (auto&& doc : cursor) {
            Json::Value game;
            game["game_id"] = doc["_id"].get_oid().value.to_string();
            game["status"] = string(doc["status"].get_string().value);
            game["time_control"] = string(doc["time_control"].get_string().value);
            response["data"].append(game);
        }
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Get active games error: ") + e.what();
    }
    
    return response;
}

Json::Value GameHandler::handleDrawOffer(const Json::Value& request) {
    Json::Value response;
    
    try {
        response["success"] = true;
        response["message"] = "Draw offer functionality not yet implemented";
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Draw offer error: ") + e.what();
    }
    
    return response;
}
