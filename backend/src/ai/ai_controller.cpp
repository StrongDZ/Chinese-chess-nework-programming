#include "../../include/ai/ai_controller.h"
#include <iostream>

using namespace std;
using json = nlohmann::json;

AIController::AIController(AIService& ai, GameService& game)
    : aiService(ai), gameService(game) {}

AIDifficulty AIController::parseDifficulty(const string& diff) {
    if (diff == "easy") return AIDifficulty::EASY;
    if (diff == "hard") return AIDifficulty::HARD;
    return AIDifficulty::MEDIUM;  // Default
}

json AIController::handleCreateAIGame(const json& request) {
    json response;
    
    // Validate required fields
    if (!request.contains("username") || !request["username"].is_string()) {
        response["status"] = "error";
        response["message"] = "Missing required field: username";
        return response;
    }
    
    string username = request["username"].get<string>();
    string difficulty = request.value("difficulty", "medium");
    string timeControl = request.value("time_control", "blitz");
    string playerColor = request.value("player_color", "red");
    
    // Validate difficulty
    if (difficulty != "easy" && difficulty != "medium" && difficulty != "hard") {
        response["status"] = "error";
        response["message"] = "Invalid difficulty. Use: easy, medium, hard";
        return response;
    }
    
    // Validate player color
    if (playerColor != "red" && playerColor != "black") {
        response["status"] = "error";
        response["message"] = "Invalid player_color. Use: red, black";
        return response;
    }
    
    // Check AI service availability
    if (!aiService.isReady()) {
        response["status"] = "error";
        response["message"] = "AI service is not available";
        return response;
    }
    
    // Create game - AI is always the opponent
    // Use a special username for AI: "AI_<difficulty>"
    string aiUsername = "AI_" + difficulty;
    
    GameResult result;
    if (playerColor == "red") {
        // Player is red, AI is black
        result = gameService.createGame(username, aiUsername, timeControl, false);  // AI games are unrated
    } else {
        // Player is black, AI is red
        result = gameService.createGame(aiUsername, username, timeControl, false);
    }
    
    if (!result.success) {
        response["status"] = "error";
        response["message"] = result.message;
        return response;
    }
    
    response["status"] = "success";
    response["message"] = "AI game created";
    response["ai_difficulty"] = difficulty;
    response["player_color"] = playerColor;
    
    // Convert game to JSON
    if (result.game.has_value()) {
        json gameJson;
        gameJson["game_id"] = result.game->id;
        gameJson["red_player"] = result.game->red_player;
        gameJson["black_player"] = result.game->black_player;
        gameJson["status"] = result.game->status;
        gameJson["current_turn"] = result.game->current_turn;
        gameJson["xfen"] = result.game->xfen;
        gameJson["time_control"] = result.game->time_control;
        gameJson["rated"] = result.game->rated;
        gameJson["is_ai_game"] = true;
        response["game"] = gameJson;
    }
    
    // If player is black, AI (red) moves first
    if (playerColor == "black" && result.game.has_value()) {
        auto aiMoveResult = aiService.predictMove(result.game->xfen, parseDifficulty(difficulty));
        if (aiMoveResult.success && aiMoveResult.move.has_value()) {
            // Make AI's first move
            auto moveResult = gameService.makeMove(
                aiUsername,
                result.game->id,
                aiMoveResult.move->fromX,
                aiMoveResult.move->fromY,
                aiMoveResult.move->toX,
                aiMoveResult.move->toY,
                "", "", "", "", 0
            );
            
            if (moveResult.success && moveResult.game.has_value()) {
                // Update response with AI's first move
                json aiMoveJson;
                aiMoveJson["from_x"] = aiMoveResult.move->fromX;
                aiMoveJson["from_y"] = aiMoveResult.move->fromY;
                aiMoveJson["to_x"] = aiMoveResult.move->toX;
                aiMoveJson["to_y"] = aiMoveResult.move->toY;
                aiMoveJson["uci"] = aiMoveResult.move->uci;
                response["ai_first_move"] = aiMoveJson;
                
                // Update game state in response
                json updatedGame;
                updatedGame["game_id"] = moveResult.game->id;
                updatedGame["red_player"] = moveResult.game->red_player;
                updatedGame["black_player"] = moveResult.game->black_player;
                updatedGame["status"] = moveResult.game->status;
                updatedGame["current_turn"] = moveResult.game->current_turn;
                updatedGame["xfen"] = moveResult.game->xfen;
                updatedGame["move_count"] = moveResult.game->move_count;
                response["game"] = updatedGame;
            }
        }
    }
    
    return response;
}

json AIController::handleGetAIMove(const json& request) {
    json response;
    
    // Validate required fields
    if (!request.contains("game_id") || !request["game_id"].is_string()) {
        response["status"] = "error";
        response["message"] = "Missing required field: game_id";
        return response;
    }
    
    string gameId = request["game_id"].get<string>();
    
    // Get current game state
    auto gameResult = gameService.getGame(gameId);
    if (!gameResult.success || !gameResult.game.has_value()) {
        response["status"] = "error";
        response["message"] = "Game not found";
        return response;
    }
    
    const auto& game = gameResult.game.value();
    
    // Determine AI difficulty from opponent name
    AIDifficulty difficulty = AIDifficulty::MEDIUM;
    string aiPlayer = "";
    
    if (game.red_player.find("AI_") == 0) {
        aiPlayer = game.red_player;
    } else if (game.black_player.find("AI_") == 0) {
        aiPlayer = game.black_player;
    } else {
        response["status"] = "error";
        response["message"] = "Not an AI game";
        return response;
    }
    
    if (aiPlayer.find("easy") != string::npos) {
        difficulty = AIDifficulty::EASY;
    } else if (aiPlayer.find("hard") != string::npos) {
        difficulty = AIDifficulty::HARD;
    }
    
    // Use provided XFEN or game's current XFEN
    string xfen = request.value("xfen", game.xfen);
    
    // Get AI move
    auto aiResult = aiService.predictMove(xfen, difficulty);
    
    if (!aiResult.success || !aiResult.move.has_value()) {
        response["status"] = "error";
        response["message"] = aiResult.message;
        return response;
    }
    
    response["status"] = "success";
    response["message"] = "AI move calculated";
    
    json moveJson;
    moveJson["from_x"] = aiResult.move->fromX;
    moveJson["from_y"] = aiResult.move->fromY;
    moveJson["to_x"] = aiResult.move->toX;
    moveJson["to_y"] = aiResult.move->toY;
    moveJson["uci"] = aiResult.move->uci;
    response["move"] = moveJson;
    
    return response;
}

json AIController::handleMakeAIMove(const json& request) {
    json response;
    
    // Validate required fields
    if (!request.contains("game_id") || !request.contains("username") ||
        !request.contains("from_x") || !request.contains("from_y") ||
        !request.contains("to_x") || !request.contains("to_y")) {
        response["status"] = "error";
        response["message"] = "Missing required fields: game_id, username, from_x, from_y, to_x, to_y";
        return response;
    }
    
    string gameId = request["game_id"].get<string>();
    string username = request["username"].get<string>();
    int fromX = request["from_x"].get<int>();
    int fromY = request["from_y"].get<int>();
    int toX = request["to_x"].get<int>();
    int toY = request["to_y"].get<int>();
    
    string piece = request.value("piece", "");
    string notation = request.value("notation", "");
    
    // Get current game to verify it's an AI game
    auto gameResult = gameService.getGame(gameId);
    if (!gameResult.success || !gameResult.game.has_value()) {
        response["status"] = "error";
        response["message"] = "Game not found";
        return response;
    }
    
    const auto& game = gameResult.game.value();
    
    // Determine AI player and difficulty
    string aiPlayer = "";
    AIDifficulty difficulty = AIDifficulty::MEDIUM;
    
    if (game.red_player.find("AI_") == 0) {
        aiPlayer = game.red_player;
    } else if (game.black_player.find("AI_") == 0) {
        aiPlayer = game.black_player;
    } else {
        response["status"] = "error";
        response["message"] = "Not an AI game";
        return response;
    }
    
    if (aiPlayer.find("easy") != string::npos) {
        difficulty = AIDifficulty::EASY;
    } else if (aiPlayer.find("hard") != string::npos) {
        difficulty = AIDifficulty::HARD;
    }
    
    // 1. Make player's move
    auto playerMoveResult = gameService.makeMove(
        username, gameId, fromX, fromY, toX, toY, piece, "", notation, "", 0
    );
    
    if (!playerMoveResult.success) {
        response["status"] = "error";
        response["message"] = playerMoveResult.message;
        return response;
    }
    
    // Add player move to response
    json playerMoveJson;
    playerMoveJson["from_x"] = fromX;
    playerMoveJson["from_y"] = fromY;
    playerMoveJson["to_x"] = toX;
    playerMoveJson["to_y"] = toY;
    playerMoveJson["uci"] = AIService::toUCI(fromX, fromY, toX, toY);
    response["player_move"] = playerMoveJson;
    
    // Check if game ended after player's move
    if (!playerMoveResult.game.has_value()) {
        response["status"] = "success";
        response["message"] = "Player move made, game ended";
        response["game_over"] = true;
        return response;
    }
    
    const auto& updatedGame = playerMoveResult.game.value();
    
    if (updatedGame.status != "in_progress") {
        response["status"] = "success";
        response["message"] = "Game over";
        response["game_over"] = true;
        response["result"] = updatedGame.result;
        
        json gameJson;
        gameJson["game_id"] = updatedGame.id;
        gameJson["status"] = updatedGame.status;
        gameJson["result"] = updatedGame.result;
        gameJson["xfen"] = updatedGame.xfen;
        response["game"] = gameJson;
        
        return response;
    }
    
    // 2. Get AI's response move
    auto aiMoveResult = aiService.predictMove(updatedGame.xfen, difficulty);
    
    if (!aiMoveResult.success || !aiMoveResult.move.has_value()) {
        response["status"] = "error";
        response["message"] = "AI failed to calculate move: " + aiMoveResult.message;
        return response;
    }
    
    // 3. Make AI's move
    auto aiGameResult = gameService.makeMove(
        aiPlayer,
        gameId,
        aiMoveResult.move->fromX,
        aiMoveResult.move->fromY,
        aiMoveResult.move->toX,
        aiMoveResult.move->toY,
        "", "", "", "", 0
    );
    
    if (!aiGameResult.success) {
        response["status"] = "error";
        response["message"] = "AI move failed: " + aiGameResult.message;
        return response;
    }
    
    // Build response
    response["status"] = "success";
    response["message"] = "Moves made successfully";
    
    json aiMoveJson;
    aiMoveJson["from_x"] = aiMoveResult.move->fromX;
    aiMoveJson["from_y"] = aiMoveResult.move->fromY;
    aiMoveJson["to_x"] = aiMoveResult.move->toX;
    aiMoveJson["to_y"] = aiMoveResult.move->toY;
    aiMoveJson["uci"] = aiMoveResult.move->uci;
    response["ai_move"] = aiMoveJson;
    
    if (aiGameResult.game.has_value()) {
        const auto& finalGame = aiGameResult.game.value();
        
        json gameJson;
        gameJson["game_id"] = finalGame.id;
        gameJson["status"] = finalGame.status;
        gameJson["current_turn"] = finalGame.current_turn;
        gameJson["xfen"] = finalGame.xfen;
        gameJson["move_count"] = finalGame.move_count;
        response["game"] = gameJson;
        
        response["game_over"] = (finalGame.status != "in_progress");
        if (finalGame.status != "in_progress") {
            response["result"] = finalGame.result;
        }
    }
    
    return response;
}

json AIController::handleSuggestMove(const json& request) {
    json response;
    
    // Can provide either game_id or direct xfen
    string xfen = "";
    
    if (request.contains("xfen") && request["xfen"].is_string()) {
        xfen = request["xfen"].get<string>();
    } else if (request.contains("game_id") && request["game_id"].is_string()) {
        string gameId = request["game_id"].get<string>();
        auto gameResult = gameService.getGame(gameId);
        if (!gameResult.success || !gameResult.game.has_value()) {
            response["status"] = "error";
            response["message"] = "Game not found";
            return response;
        }
        xfen = gameResult.game->xfen;
    } else {
        response["status"] = "error";
        response["message"] = "Provide either game_id or xfen";
        return response;
    }
    
    // Get suggestion (uses HARD difficulty)
    auto aiResult = aiService.suggestMove(xfen);
    
    if (!aiResult.success || !aiResult.move.has_value()) {
        response["status"] = "error";
        response["message"] = aiResult.message;
        return response;
    }
    
    response["status"] = "success";
    response["message"] = "Move suggestion calculated";
    
    json moveJson;
    moveJson["from_x"] = aiResult.move->fromX;
    moveJson["from_y"] = aiResult.move->fromY;
    moveJson["to_x"] = aiResult.move->toX;
    moveJson["to_y"] = aiResult.move->toY;
    moveJson["uci"] = aiResult.move->uci;
    response["suggested_move"] = moveJson;
    
    return response;
}

json AIController::handleResignAIGame(const json& request) {
    json response;
    
    if (!request.contains("game_id") || !request.contains("username")) {
        response["status"] = "error";
        response["message"] = "Missing required fields: game_id, username";
        return response;
    }
    
    string gameId = request["game_id"].get<string>();
    string username = request["username"].get<string>();
    
    // Use existing resign functionality
    auto resignResult = gameService.resign(username, gameId);
    
    if (!resignResult.success) {
        response["status"] = "error";
        response["message"] = resignResult.message;
        return response;
    }
    
    response["status"] = "success";
    response["message"] = "Resigned from AI game";
    response["result"] = "ai_win";
    
    return response;
}
