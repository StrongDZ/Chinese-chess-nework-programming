#include "game/game_controller.h"
#include <chrono>
#include <iomanip>
#include <sstream>

using namespace std;

GameController::GameController(GameService& svc) 
    : service(svc) {}

Json::Value GameController::moveToJson(const Move& move) const {
    Json::Value json;
    json["move_number"] = move.move_number;
    json["player"] = move.player;
    json["from"]["x"] = move.from_x;
    json["from"]["y"] = move.from_y;
    json["to"]["x"] = move.to_x;
    json["to"]["y"] = move.to_y;
    json["piece"] = move.piece;
    json["captured"] = move.captured;
    json["notation"] = move.notation;
    json["time_taken"] = move.time_taken;
    
    if (!move.xfen_after.empty()) {
        json["xfen_after"] = move.xfen_after;
    }
    
    return json;
}

Json::Value GameController::gameToJson(const Game& game, bool includeMoves) const {
    Json::Value json;
    json["game_id"] = game.id;
    json["red_player"] = game.red_player;
    json["black_player"] = game.black_player;
    json["status"] = game.status;
    json["current_turn"] = game.current_turn;
    json["xfen"] = game.xfen;
    json["move_count"] = game.move_count;
    json["time_control"] = game.time_control;
    json["time_limit"] = game.time_limit;
    json["red_time_remaining"] = game.red_time_remaining;
    json["black_time_remaining"] = game.black_time_remaining;
    json["increment"] = game.increment;
    json["rated"] = game.rated;
    
    if (!game.result.empty()) {
        json["result"] = game.result;
    }
    
    if (!game.winner.empty()) {
        json["winner"] = game.winner;
    }
    
    // Format start_time
    auto startTime = chrono::system_clock::to_time_t(game.start_time);
    stringstream ss;
    ss << put_time(localtime(&startTime), "%Y-%m-%d %H:%M:%S");
    json["start_time"] = ss.str();
    
    if (includeMoves) {
        json["moves"] = Json::Value(Json::arrayValue);
        for (const auto& move : game.moves) {
            json["moves"].append(moveToJson(move));
        }
    }
    
    return json;
}

Json::Value GameController::handleCreateGame(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate required fields
        if (!request.isMember("username") || !request.isMember("challenged_username")) {
            response["status"] = "error";
            response["message"] = "Missing required fields: username, challenged_username";
            return response;
        }
        
        string challengerUsername = request["username"].asString();
        string challengedUsername = request["challenged_username"].asString();
        string timeControl = request.get("time_control", "blitz").asString();
        bool rated = request.get("rated", true).asBool();
        
        // 2. Call service
        auto result = service.createGame(challengerUsername, challengedUsername, timeControl, rated);
        
        // 3. Build response
        if (result.success) {
            response["status"] = "success";
            response["message"] = result.message;
            response["game"] = gameToJson(result.game.value(), false);
        } else {
            response["status"] = "error";
            response["message"] = result.message;
        }
        
    } catch (const exception& e) {
        response["status"] = "error";
        response["message"] = string("Exception: ") + e.what();
    }
    
    return response;
}

Json::Value GameController::handleMakeMove(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate required fields
        if (!request.isMember("username") || !request.isMember("game_id") ||
            !request.isMember("from") || !request.isMember("to")) {
            response["status"] = "error";
            response["message"] = "Missing required fields: username, game_id, from, to";
            return response;
        }
        
        if (!request["from"].isMember("x") || !request["from"].isMember("y") ||
            !request["to"].isMember("x") || !request["to"].isMember("y")) {
            response["status"] = "error";
            response["message"] = "from and to must have x,y coordinates";
            return response;
        }
        
        string username = request["username"].asString();
        string gameId = request["game_id"].asString();
        int fromX = request["from"]["x"].asInt();
        int fromY = request["from"]["y"].asInt();
        int toX = request["to"]["x"].asInt();
        int toY = request["to"]["y"].asInt();
        string piece = request.get("piece", "").asString();
        string captured = request.get("captured", "").asString();
        string notation = request.get("notation", "").asString();
        string xfenAfter = request.get("xfen_after", "").asString();
        int timeTaken = request.get("time_taken", 0).asInt();
        
        // 2. Call service
        auto result = service.makeMove(username, gameId, fromX, fromY, toX, toY,
                                        piece, captured, notation, xfenAfter, timeTaken);
        
        // 3. Build response
        if (result.success) {
            response["status"] = "success";
            response["message"] = result.message;
            
            if (result.game) {
                const auto& game = result.game.value();
                response["move"]["from"]["x"] = fromX;
                response["move"]["from"]["y"] = fromY;
                response["move"]["to"]["x"] = toX;
                response["move"]["to"]["y"] = toY;
                response["move"]["move_number"] = game.move_count;
                response["next_turn"] = game.current_turn;
                response["red_time_remaining"] = game.red_time_remaining;
                response["black_time_remaining"] = game.black_time_remaining;
            }
        } else {
            response["status"] = "error";
            response["message"] = result.message;
        }
        
    } catch (const exception& e) {
        response["status"] = "error";
        response["message"] = string("Exception: ") + e.what();
    }
    
    return response;
}

Json::Value GameController::handleOfferDraw(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate required fields
        if (!request.isMember("username") || !request.isMember("game_id")) {
            response["status"] = "error";
            response["message"] = "Missing required fields: username, game_id";
            return response;
        }
        
        string username = request["username"].asString();
        string gameId = request["game_id"].asString();
        
        // 2. Call service
        auto result = service.offerDraw(username, gameId);
        
        // 3. Build response
        if (result.success) {
            response["status"] = "success";
            response["message"] = result.message;
        } else {
            response["status"] = "error";
            response["message"] = result.message;
        }
        
    } catch (const exception& e) {
        response["status"] = "error";
        response["message"] = string("Exception: ") + e.what();
    }
    
    return response;
}

Json::Value GameController::handleRespondDraw(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate required fields
        if (!request.isMember("username") || !request.isMember("game_id") ||
            !request.isMember("accept")) {
            response["status"] = "error";
            response["message"] = "Missing required fields: username, game_id, accept";
            return response;
        }
        
        string username = request["username"].asString();
        string gameId = request["game_id"].asString();
        bool accept = request["accept"].asBool();
        
        // 2. Call service
        auto result = service.respondDraw(username, gameId, accept);
        
        // 3. Build response
        if (result.success) {
            response["status"] = "success";
            response["message"] = result.message;
            
            if (accept && result.game) {
                response["result"] = result.game->result;
            }
        } else {
            response["status"] = "error";
            response["message"] = result.message;
        }
        
    } catch (const exception& e) {
        response["status"] = "error";
        response["message"] = string("Exception: ") + e.what();
    }
    
    return response;
}

Json::Value GameController::handleResign(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate required fields
        if (!request.isMember("username") || !request.isMember("game_id")) {
            response["status"] = "error";
            response["message"] = "Missing required fields: username, game_id";
            return response;
        }
        
        string username = request["username"].asString();
        string gameId = request["game_id"].asString();
        
        // 2. Call service
        auto result = service.resign(username, gameId);
        
        // 3. Build response
        if (result.success) {
            response["status"] = "success";
            response["message"] = result.message;
            
            if (result.game) {
                response["result"] = result.game->result;
                response["winner"] = result.game->winner;
            }
        } else {
            response["status"] = "error";
            response["message"] = result.message;
        }
        
    } catch (const exception& e) {
        response["status"] = "error";
        response["message"] = string("Exception: ") + e.what();
    }
    
    return response;
}

Json::Value GameController::handleGetGame(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate required fields
        if (!request.isMember("game_id")) {
            response["status"] = "error";
            response["message"] = "Missing required field: game_id";
            return response;
        }
        
        string gameId = request["game_id"].asString();
        
        // 2. Call service
        auto result = service.getGame(gameId);
        
        // 3. Build response
        if (result.success) {
            response["status"] = "success";
            response["game"] = gameToJson(result.game.value(), true);
        } else {
            response["status"] = "error";
            response["message"] = result.message;
        }
        
    } catch (const exception& e) {
        response["status"] = "error";
        response["message"] = string("Exception: ") + e.what();
    }
    
    return response;
}

Json::Value GameController::handleListGames(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate required fields
        if (!request.isMember("username")) {
            response["status"] = "error";
            response["message"] = "Missing required field: username";
            return response;
        }
        
        string username = request["username"].asString();
        string filter = request.get("filter", "active").asString();
        
        // 2. Call service
        auto result = service.listGames(username, filter);
        
        // 3. Build response
        response["status"] = "success";
        response["games"] = Json::Value(Json::arrayValue);
        
        for (const auto& game : result.games) {
            response["games"].append(gameToJson(game, false));
        }
        
        response["count"] = static_cast<int>(result.games.size());
        
    } catch (const exception& e) {
        response["status"] = "error";
        response["message"] = string("Exception: ") + e.what();
    }
    
    return response;
}
