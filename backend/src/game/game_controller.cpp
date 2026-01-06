#include "game/game_controller.h"
#include <chrono>
#include <iomanip>
#include <sstream>

using namespace std;

GameController::GameController(GameService &svc) : service(svc) {}

nlohmann::json GameController::moveToJson(const Move &move) const {
  nlohmann::json json;
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

nlohmann::json GameController::gameToJson(const Game &game,
                                          bool includeMoves) const {
  nlohmann::json json;
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

  if (!game.draw_offered_by.empty()) {
    json["draw_offered_by"] = game.draw_offered_by;
  }

  // Format start_time
  auto startTime = chrono::system_clock::to_time_t(game.start_time);
  stringstream ss;
  ss << put_time(localtime(&startTime), "%Y-%m-%d %H:%M:%S");
  json["start_time"] = ss.str();

  if (includeMoves) {
    json["moves"] = nlohmann::json::array();
    for (const auto &move : game.moves) {
      json["moves"].push_back(moveToJson(move));
    }
  }

  return json;
}

nlohmann::json GameController::handleCreateGame(const nlohmann::json &request) {
  nlohmann::json response;

  try {
    // 1. Validate required fields
    if (!request.contains("username") ||
        !request.contains("challenged_username")) {
      response["status"] = "error";
      response["message"] =
          "Missing required fields: username, challenged_username";
      return response;
    }

    string challengerUsername = request["username"].get<string>();
    string challengedUsername = request["challenged_username"].get<string>();
    string timeControl = request.value("time_control", string("blitz"));
    bool rated = request.value("rated", true);

    // 2. Call service
    auto result = service.createGame(challengerUsername, challengedUsername,
                                     timeControl, rated);

    // 3. Build response
    if (result.success) {
      response["status"] = "success";
      response["message"] = result.message;
      response["game"] = gameToJson(result.game.value(), false);
    } else {
      response["status"] = "error";
      response["message"] = result.message;
    }

  } catch (const exception &e) {
    response["status"] = "error";
    response["message"] = string("Exception: ") + e.what();
  }

  return response;
}

nlohmann::json GameController::handleCreateCustomGame(const nlohmann::json &request) {
  nlohmann::json response;

  try {
    // 1. Validate required fields
    if (!request.contains("red_player") ||
        !request.contains("black_player") ||
        !request.contains("custom_xfen")) {
      response["status"] = "error";
      response["message"] =
          "Missing required fields: red_player, black_player, custom_xfen";
      return response;
    }

    string redPlayer = request["red_player"].get<string>();
    string blackPlayer = request["black_player"].get<string>();
    string customXfen = request["custom_xfen"].get<string>();
    string startingColor = request.value("starting_color", string("red"));
    string timeControl = request.value("time_control", string("blitz"));

    // 2. Call service
    auto result = service.createCustomGame(redPlayer, blackPlayer, 
                                            customXfen, startingColor, timeControl);

    // 3. Build response
    if (result.success) {
      response["status"] = "success";
      response["message"] = result.message;
      response["game"] = gameToJson(result.game.value(), false);
      response["custom_mode"] = true;
      response["starting_color"] = startingColor;
    } else {
      response["status"] = "error";
      response["message"] = result.message;
    }

  } catch (const exception &e) {
    response["status"] = "error";
    response["message"] = string("Exception: ") + e.what();
  }

  return response;
}

nlohmann::json GameController::handleMakeMove(const nlohmann::json &request) {
  nlohmann::json response;

  try {
    // 1. Validate required fields
    if (!request.contains("username") || !request.contains("game_id") ||
        !request.contains("from") || !request.contains("to")) {
      response["status"] = "error";
      response["message"] =
          "Missing required fields: username, game_id, from, to";
      return response;
    }

    if (!request["from"].contains("x") || !request["from"].contains("y") ||
        !request["to"].contains("x") || !request["to"].contains("y")) {
      response["status"] = "error";
      response["message"] = "from and to must have x,y coordinates";
      return response;
    }

    string username = request["username"].get<string>();
    string gameId = request["game_id"].get<string>();
    int fromX = request["from"]["x"].get<int>();
    int fromY = request["from"]["y"].get<int>();
    int toX = request["to"]["x"].get<int>();
    int toY = request["to"]["y"].get<int>();
    string piece = request.value("piece", string(""));
    string captured = request.value("captured", string(""));
    string notation = request.value("notation", string(""));
    string xfenAfter = request.value("xfen_after", string(""));
    int timeTaken = request.value("time_taken", 0);

    // 2. Call service
    auto result =
        service.makeMove(username, gameId, fromX, fromY, toX, toY, piece,
                         captured, notation, xfenAfter, timeTaken);

    // 3. Build response
    if (result.success) {
      response["status"] = "success";
      response["message"] = result.message;

      if (result.game) {
        const auto &game = result.game.value();
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

  } catch (const exception &e) {
    response["status"] = "error";
    response["message"] = string("Exception: ") + e.what();
  }

  return response;
}

// Kết thúc game với result cụ thể (được gọi từ protocol khi checkmate, draw
// agreement, timeout, etc.)
nlohmann::json GameController::handleEndGame(const nlohmann::json &request) {
  nlohmann::json response;

  try {
    // 1. Validate required fields
    if (!request.contains("game_id") || !request.contains("result")) {
      response["status"] = "error";
      response["message"] = "Missing required fields: game_id, result";
      return response;
    }

    string gameId = request["game_id"].get<string>();
    string result = request["result"].get<string>();
    string termination = request.value("termination", string("normal"));

    // 2. Call service
    auto gameResult = service.endGame(gameId, result, termination);

    // 3. Build response
    if (gameResult.success) {
      response["status"] = "success";
      response["message"] = gameResult.message;

      if (gameResult.game) {
        response["result"] = gameResult.game->result;
        response["winner"] = gameResult.game->winner;
      }
    } else {
      response["status"] = "error";
      response["message"] = gameResult.message;
    }

  } catch (const exception &e) {
    response["status"] = "error";
    response["message"] = string("Exception: ") + e.what();
  }

  return response;
}

nlohmann::json GameController::handleResign(const nlohmann::json &request) {
  nlohmann::json response;

  try {
    // 1. Validate required fields
    if (!request.contains("username") || !request.contains("game_id")) {
      response["status"] = "error";
      response["message"] = "Missing required fields: username, game_id";
      return response;
    }

    string username = request["username"].get<string>();
    string gameId = request["game_id"].get<string>();

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

  } catch (const exception &e) {
    response["status"] = "error";
    response["message"] = string("Exception: ") + e.what();
  }

  return response;
}

// ============ Draw Offer Handlers ============

nlohmann::json GameController::handleOfferDraw(const nlohmann::json &request) {
  nlohmann::json response;

  try {
    // 1. Validate required fields
    if (!request.contains("username") || !request.contains("game_id")) {
      response["status"] = "error";
      response["message"] = "Missing required fields: username, game_id";
      return response;
    }

    string username = request["username"].get<string>();
    string gameId = request["game_id"].get<string>();

    // 2. Call service
    auto result = service.offerDraw(username, gameId);

    // 3. Build response
    if (result.success) {
      response["status"] = "success";
      response["message"] = result.message;

      if (result.game) {
        response["draw_offered_by"] = result.game->draw_offered_by;
      }
    } else {
      response["status"] = "error";
      response["message"] = result.message;
    }

  } catch (const exception &e) {
    response["status"] = "error";
    response["message"] = string("Exception: ") + e.what();
  }

  return response;
}

nlohmann::json GameController::handleRespondToDraw(const nlohmann::json &request) {
  nlohmann::json response;

  try {
    // 1. Validate required fields
    if (!request.contains("username") || !request.contains("game_id") || !request.contains("accept")) {
      response["status"] = "error";
      response["message"] = "Missing required fields: username, game_id, accept";
      return response;
    }

    string username = request["username"].get<string>();
    string gameId = request["game_id"].get<string>();
    bool accept = request["accept"].get<bool>();

    // 2. Call service
    auto result = service.respondToDraw(username, gameId, accept);

    // 3. Build response
    if (result.success) {
      response["status"] = "success";
      response["message"] = result.message;

      if (result.game) {
        response["game_status"] = result.game->status;
        if (!result.game->result.empty()) {
          response["result"] = result.game->result;
        }
      }
    } else {
      response["status"] = "error";
      response["message"] = result.message;
    }

  } catch (const exception &e) {
    response["status"] = "error";
    response["message"] = string("Exception: ") + e.what();
  }

  return response;
}

nlohmann::json GameController::handleGetGame(const nlohmann::json &request) {
  nlohmann::json response;

  try {
    // 1. Validate required fields
    if (!request.contains("game_id")) {
      response["status"] = "error";
      response["message"] = "Missing required field: game_id";
      return response;
    }

    string gameId = request["game_id"].get<string>();

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

  } catch (const exception &e) {
    response["status"] = "error";
    response["message"] = string("Exception: ") + e.what();
  }

  return response;
}

nlohmann::json GameController::handleListGames(const nlohmann::json &request) {
  nlohmann::json response;

  try {
    // 1. Validate required fields
    if (!request.contains("username")) {
      response["status"] = "error";
      response["message"] = "Missing required field: username";
      return response;
    }

    string username = request["username"].get<string>();
    string filter = request.value("filter", string("active"));

    // 2. Call service
    auto result = service.listGames(username, filter);

    // 3. Build response
    response["status"] = "success";
    response["games"] = nlohmann::json::array();

    for (const auto &game : result.games) {
      response["games"].push_back(gameToJson(game, false));
    }

    response["count"] = static_cast<int>(result.games.size());

  } catch (const exception &e) {
    response["status"] = "error";
    response["message"] = string("Exception: ") + e.what();
  }

  return response;
}

// ============ Rematch Handlers ============

nlohmann::json GameController::handleRequestRematch(const nlohmann::json &request) {
  nlohmann::json response;

  try {
    // 1. Validate required fields
    if (!request.contains("username") || !request.contains("game_id")) {
      response["status"] = "error";
      response["message"] = "Missing required fields: username, game_id";
      return response;
    }

    string username = request["username"].get<string>();
    string gameId = request["game_id"].get<string>();

    // 2. Call service
    auto result = service.requestRematch(username, gameId);

    // 3. Build response
    if (result.success) {
      response["status"] = "success";
      response["message"] = result.message;
      response["rematch_offered_by"] = username;
    } else {
      response["status"] = "error";
      response["message"] = result.message;
    }

  } catch (const exception &e) {
    response["status"] = "error";
    response["message"] = string("Exception: ") + e.what();
  }

  return response;
}

nlohmann::json GameController::handleRespondToRematch(const nlohmann::json &request) {
  nlohmann::json response;

  try {
    // 1. Validate required fields
    if (!request.contains("username") || !request.contains("game_id") || !request.contains("accept")) {
      response["status"] = "error";
      response["message"] = "Missing required fields: username, game_id, accept";
      return response;
    }

    string username = request["username"].get<string>();
    string gameId = request["game_id"].get<string>();
    bool accept = request["accept"].get<bool>();

    // 2. Call service
    auto result = service.respondToRematch(username, gameId, accept);

    // 3. Build response
    if (result.success) {
      response["status"] = "success";
      response["message"] = result.message;

      // If accepted, include new game info
      if (accept && result.game.has_value()) {
        response["new_game"] = gameToJson(result.game.value(), true);
      }
    } else {
      response["status"] = "error";
      response["message"] = result.message;
    }

  } catch (const exception &e) {
    response["status"] = "error";
    response["message"] = string("Exception: ") + e.what();
  }

  return response;
}

// ============ Game History & Replay Handlers ============

nlohmann::json GameController::handleGetGameHistory(const nlohmann::json &request) {
  nlohmann::json response;

  try {
    // 1. Validate required fields
    if (!request.contains("username")) {
      response["status"] = "error";
      response["message"] = "Missing required field: username";
      return response;
    }

    string username = request["username"].get<string>();
    int limit = request.value("limit", 50);
    int offset = request.value("offset", 0);

    // 2. Call service
    auto result = service.getGameHistory(username, limit, offset);

    // 3. Build response
    if (result.success) {
      response["status"] = "success";
      response["history"] = nlohmann::json::array();

      for (const auto &game : result.archivedGames) {
        nlohmann::json gameJson;
        gameJson["game_id"] = game.id;
        if (!game.original_game_id.empty()) {
          gameJson["original_game_id"] = game.original_game_id;
        }
        gameJson["red_player"] = game.red_player;
        gameJson["black_player"] = game.black_player;
        gameJson["result"] = game.result;
        if (!game.winner.empty()) {
          gameJson["winner"] = game.winner;
        }
        gameJson["time_control"] = game.time_control;
        gameJson["rated"] = game.rated;
        gameJson["move_count"] = game.move_count;
        
        // Format timestamps
        auto startMs = chrono::duration_cast<chrono::milliseconds>(
            game.start_time.time_since_epoch()).count();
        auto endMs = chrono::duration_cast<chrono::milliseconds>(
            game.end_time.time_since_epoch()).count();
        gameJson["start_time"] = startMs;
        gameJson["end_time"] = endMs;
        
        response["history"].push_back(gameJson);
      }

      response["count"] = static_cast<int>(result.archivedGames.size());
    } else {
      response["status"] = "error";
      response["message"] = result.message;
    }

  } catch (const exception &e) {
    response["status"] = "error";
    response["message"] = string("Exception: ") + e.what();
  }

  return response;
}

nlohmann::json GameController::handleGetGameDetails(const nlohmann::json &request) {
  nlohmann::json response;

  try {
    // 1. Validate required fields
    if (!request.contains("game_id")) {
      response["status"] = "error";
      response["message"] = "Missing required field: game_id";
      return response;
    }

    string gameId = request["game_id"].get<string>();

    // 2. Call service
    auto result = service.getGameDetails(gameId);

    // 3. Build response
    if (result.success) {
      response["status"] = "success";
      
      if (result.game.has_value()) {
        // Active game
        response["game_type"] = "active";
        response["game"] = gameToJson(result.game.value(), true);
      } else if (result.archivedGame.has_value()) {
        // Archived game with full moves for replay
        auto& game = result.archivedGame.value();
        nlohmann::json gameJson;
        
        gameJson["game_id"] = game.id;
        if (!game.original_game_id.empty()) {
          gameJson["original_game_id"] = game.original_game_id;
        }
        gameJson["red_player"] = game.red_player;
        gameJson["black_player"] = game.black_player;
        gameJson["result"] = game.result;
        if (!game.winner.empty()) {
          gameJson["winner"] = game.winner;
        }
        gameJson["time_control"] = game.time_control;
        gameJson["time_limit"] = game.time_limit;
        gameJson["increment"] = game.increment;
        gameJson["rated"] = game.rated;
        gameJson["move_count"] = game.move_count;
        
        // Timestamps
        auto startMs = chrono::duration_cast<chrono::milliseconds>(
            game.start_time.time_since_epoch()).count();
        auto endMs = chrono::duration_cast<chrono::milliseconds>(
            game.end_time.time_since_epoch()).count();
        gameJson["start_time"] = startMs;
        gameJson["end_time"] = endMs;
        
        // Full moves array for replay
        gameJson["moves"] = nlohmann::json::array();
        for (const auto& move : game.moves) {
          nlohmann::json moveJson;
          moveJson["move_number"] = move.move_number;
          moveJson["player"] = move.player;
          moveJson["from_x"] = move.from_x;
          moveJson["from_y"] = move.from_y;
          moveJson["to_x"] = move.to_x;
          moveJson["to_y"] = move.to_y;
          if (!move.piece.empty()) {
            moveJson["piece"] = move.piece;
          }
          if (!move.captured.empty()) {
            moveJson["captured"] = move.captured;
          }
          if (!move.notation.empty()) {
            moveJson["notation"] = move.notation;
          }
          if (!move.xfen_after.empty()) {
            moveJson["xfen_after"] = move.xfen_after;
          }
          if (move.time_taken > 0) {
            moveJson["time_taken"] = move.time_taken;
          }
          auto moveMs = chrono::duration_cast<chrono::milliseconds>(
              move.timestamp.time_since_epoch()).count();
          moveJson["timestamp"] = moveMs;
          
          gameJson["moves"].push_back(moveJson);
        }
        
        response["game_type"] = "archived";
        response["game"] = gameJson;
      }
    } else {
      response["status"] = "error";
      response["message"] = result.message;
    }

  } catch (const exception &e) {
    response["status"] = "error";
    response["message"] = string("Exception: ") + e.what();
  }

  return response;
}
