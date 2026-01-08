#include "../../include/ai/ai_controller.h"
#include <iostream>

using namespace std;
using json = nlohmann::json;

AIController::AIController(AIService &ai, GameService &game)
    : aiService(ai), gameService(game) {}

AIDifficulty AIController::parseDifficulty(const string &diff) {
  if (diff == "easy")
    return AIDifficulty::EASY;
  if (diff == "hard")
    return AIDifficulty::HARD;
  return AIDifficulty::MEDIUM; // Default
}

json AIController::handleCreateAIGame(const json &request) {
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
  int time_limit = request.value("time_limit", 0);

  // Validate difficulty
  if (difficulty != "easy" && difficulty != "medium" && difficulty != "hard") {
    response["status"] = "error";
    response["message"] = "Invalid difficulty. Use: easy, medium, hard";
    return response;
  }

  // Calculate game_timer based on time_control
  int game_timer = 0;
  if (timeControl == "classical") {
    game_timer = 0;
  } else if (timeControl == "blitz") {
    game_timer = 600;
  } else if (timeControl == "custom" && time_limit > 0) {
    game_timer = time_limit; // Use time_limit for custom mode
  } else {
    game_timer = 600; // Default to blitz
  }

  // Check AI service availability
  if (!aiService.isReady()) {
    response["status"] = "error";
    response["message"] = "AI service is not available";
    return response;
  }

  // Create game - AI is always black, player is always red
  // Use a special username for AI: "AI_<difficulty>"
  string aiUsername = "AI_" + difficulty;

  // Player is always red, AI is always black
  GameResult result =
      gameService.createGameWithColors(username, aiUsername, timeControl,
                                       false); // AI games are unrated

  if (!result.success) {
    response["status"] = "error";
    response["message"] = result.message;
    return response;
  }

  response["status"] = "success";
  response["message"] = "AI game created";
  response["ai_difficulty"] = difficulty;
  response["player_color"] = "red"; // Player is always red, AI is always black
  response["time_limit"] = time_limit;
  response["game_timer"] = game_timer;

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
    gameJson["time_limit"] = result.game->time_limit;
    gameJson["rated"] = result.game->rated;
    gameJson["is_ai_game"] = true;
    response["game"] = gameJson;
  }

  // Player (red) always moves first, AI (black) moves second
  // No need to make AI's first move here

  return response;
}

json AIController::handleGetAIMove(const json &request) {
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

  const auto &game = gameResult.game.value();

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

  // Convert to MovePayload format (piece, from Coord, to Coord)
  json moveJson;
  moveJson["piece"] = ""; // Piece will be determined by game logic from xfen
  moveJson["from"]["row"] = aiResult.move->fromX;
  moveJson["from"]["col"] = aiResult.move->fromY;
  moveJson["to"]["row"] = aiResult.move->toX;
  moveJson["to"]["col"] = aiResult.move->toY;
  moveJson["uci"] = aiResult.move->uci; // Keep UCI for reference
  response["move"] = moveJson;

  return response;
}

json AIController::handleMakeAIMove(const json &request) {
  json response;

  // Validate required fields
  if (!request.contains("game_id") || !request.contains("username") ||
      !request.contains("from_x") || !request.contains("from_y") ||
      !request.contains("to_x") || !request.contains("to_y")) {
    response["status"] = "error";
    response["message"] = "Missing required fields: game_id, username, from_x, "
                          "from_y, to_x, to_y";
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

  const auto &game = gameResult.game.value();

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
      username, gameId, fromX, fromY, toX, toY, piece, "", notation, "", 0);

  if (!playerMoveResult.success) {
    response["status"] = "error";
    response["message"] = playerMoveResult.message;
    return response;
  }

  // Add player move to response (MovePayload format)
  json playerMoveJson;
  playerMoveJson["piece"] = piece.empty() ? "" : piece;
  playerMoveJson["from"]["row"] = fromX;
  playerMoveJson["from"]["col"] = fromY;
  playerMoveJson["to"]["row"] = toX;
  playerMoveJson["to"]["col"] = toY;
  playerMoveJson["uci"] =
      AIService::toUCI(fromX, fromY, toX, toY); // Keep UCI for reference
  response["player_move"] = playerMoveJson;

  // Check if game ended after player's move
  if (!playerMoveResult.game.has_value()) {
    response["status"] = "success";
    response["message"] = "Player move made, game ended";
    response["game_over"] = true;
    return response;
  }

  const auto &updatedGame = playerMoveResult.game.value();

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
    response["message"] =
        "AI failed to calculate move: " + aiMoveResult.message;
    return response;
  }

  // 3. Make AI's move
  auto aiGameResult = gameService.makeMove(
      aiPlayer, gameId, aiMoveResult.move->fromX, aiMoveResult.move->fromY,
      aiMoveResult.move->toX, aiMoveResult.move->toY, "", "", "", "", 0);

  if (!aiGameResult.success) {
    response["status"] = "error";
    response["message"] = "AI move failed: " + aiGameResult.message;
    return response;
  }

  // Build response
  response["status"] = "success";
  response["message"] = "Moves made successfully";

  // Convert AI move to MovePayload format
  json aiMoveJson;
  aiMoveJson["piece"] = ""; // Piece will be determined by game logic from xfen
  aiMoveJson["from"]["row"] = aiMoveResult.move->fromX;
  aiMoveJson["from"]["col"] = aiMoveResult.move->fromY;
  aiMoveJson["to"]["row"] = aiMoveResult.move->toX;
  aiMoveJson["to"]["col"] = aiMoveResult.move->toY;
  aiMoveJson["uci"] = aiMoveResult.move->uci; // Keep UCI for reference
  response["ai_move"] = aiMoveJson;

  if (aiGameResult.game.has_value()) {
    const auto &finalGame = aiGameResult.game.value();

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

json AIController::handleSuggestMove(const json &request) {
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

  // Convert to MovePayload format
  json moveJson;
  moveJson["piece"] = ""; // Piece will be determined by game logic from xfen
  moveJson["from"]["row"] = aiResult.move->fromX;
  moveJson["from"]["col"] = aiResult.move->fromY;
  moveJson["to"]["row"] = aiResult.move->toX;
  moveJson["to"]["col"] = aiResult.move->toY;
  moveJson["uci"] = aiResult.move->uci; // Keep UCI for reference
  response["suggested_move"] = moveJson;

  return response;
}

json AIController::handleResignAIGame(const json &request) {
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
