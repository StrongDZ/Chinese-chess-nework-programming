#include "../../include/game/game_handler.h"
#include <mongocxx/client.hpp>
#include <bsoncxx/builder/stream/document.hpp>
#include <bsoncxx/json.hpp>
#include <chrono>
#include <regex>
#include <cmath>

using namespace std;
using bsoncxx::builder::stream::document;
using bsoncxx::builder::stream::finalize;

GameHandler::GameHandler(MongoDBClient& mongo, RedisClient& redis)
    : mongoClient(mongo), redisClient(redis) {}

// Lấy userId từ token
string GameHandler::getUserIdFromToken(const string& token) {
    return redisClient.getSession(token);
}

// Validate move format (e.g., "a1", "b2")
bool GameHandler::isValidMove(const string& from, const string& to) {
    regex pattern("^[a-i][0-9]$");
    return regex_match(from, pattern) && regex_match(to, pattern);
}

// Check game đã kết thúc chưa
bool GameHandler::isGameOver(const string& gameId) {
    auto db = mongoClient.getDatabase();
    auto games = db["games"];
    
    auto gameDoc = games.find_one(
        document{} << "_id" << bsoncxx::oid(gameId) << finalize
    );
    
    if (!gameDoc) return true;
    
    string status = string(gameDoc->view()["status"].get_string().value);
    return status != "ongoing";
}

// Cập nhật rating sau game (simplified Glicko-2)
void GameHandler::updatePlayerRatings(const string& whiteId, const string& blackId,
                                      const string& result, const string& timeControl) {
    auto db = mongoClient.getDatabase();
    auto stats = db["player_stats"];
    
    // Lấy rating hiện tại
    auto whiteStats = stats.find_one(
        document{} 
            << "user_id" << bsoncxx::oid(whiteId)
            << "time_control" << timeControl
            << finalize
    );
    
    auto blackStats = stats.find_one(
        document{} 
            << "user_id" << bsoncxx::oid(blackId)
            << "time_control" << timeControl
            << finalize
    );
    
    if (!whiteStats || !blackStats) return;
    
    // Simplified rating update (K-factor = 32)
    int whiteRating = whiteStats->view()["rating"].get_int32().value;
    int blackRating = blackStats->view()["rating"].get_int32().value;
    
    double whiteScore = (result == "white_win") ? 1.0 : (result == "draw") ? 0.5 : 0.0;
    double blackScore = 1.0 - whiteScore;
    
    double whiteExpected = 1.0 / (1.0 + pow(10.0, (blackRating - whiteRating) / 400.0));
    double blackExpected = 1.0 - whiteExpected;
    
    int whiteNewRating = whiteRating + (int)(32 * (whiteScore - whiteExpected));
    int blackNewRating = blackRating + (int)(32 * (blackScore - blackExpected));
    
    // Xác định field cần increment
    string whiteField = (result == "white_win") ? "wins" : (result == "draw") ? "draws" : "losses";
    string blackField = (result == "black_win") ? "wins" : (result == "draw") ? "draws" : "losses";
    
    // Update white stats
    stats.update_one(
        document{} 
            << "user_id" << bsoncxx::oid(whiteId)
            << "time_control" << timeControl
            << finalize,
        document{} 
            << "$set" << bsoncxx::builder::stream::open_document
                << "rating" << whiteNewRating
            << bsoncxx::builder::stream::close_document
            << "$inc" << bsoncxx::builder::stream::open_document
                << "total_games" << 1
                << whiteField << 1
            << bsoncxx::builder::stream::close_document
            << finalize
    );
    
    // Update black stats
    stats.update_one(
        document{} 
            << "user_id" << bsoncxx::oid(blackId)
            << "time_control" << timeControl
            << finalize,
        document{} 
            << "$set" << bsoncxx::builder::stream::open_document
                << "rating" << blackNewRating
            << bsoncxx::builder::stream::close_document
            << "$inc" << bsoncxx::builder::stream::open_document
                << "total_games" << 1
                << blackField << 1
            << bsoncxx::builder::stream::close_document
            << finalize
    );
}

// TẠO GAME MỚI
Json::Value GameHandler::handleCreateGame(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate token và challenge_id
        if (!request.isMember("token") || !request.isMember("challenge_id")) {
            response["success"] = false;
            response["error"] = "Token and challenge_id required";
            return response;
        }
        
        string token = request["token"].asString();
        string userId = getUserIdFromToken(token);
        
        if (userId.empty()) {
            response["success"] = false;
            response["error"] = "Invalid or expired token";
            return response;
        }
        
        string challengeId = request["challenge_id"].asString();
        
        // 2. Tìm challenge
        auto db = mongoClient.getDatabase();
        auto challenges = db["challenges"];
        
        auto challengeDoc = challenges.find_one(
            document{} << "_id" << bsoncxx::oid(challengeId) << finalize
        );
        
        if (!challengeDoc) {
            response["success"] = false;
            response["error"] = "Challenge not found";
            return response;
        }
        
        auto challenge = challengeDoc->view();
        
        // 3. Check challenge đã accepted
        string status = string(challenge["status"].get_string().value);
        if (status != "accepted") {
            response["success"] = false;
            response["error"] = "Challenge must be accepted first";
            return response;
        }
        
        // 4. Lấy thông tin players
        string challengerId = challenge["challenger_id"].get_oid().value.to_string();
        string opponentId = challenge["opponent_id"].get_oid().value.to_string();
        string timeControl = string(challenge["time_control"].get_string().value);
        bool rated = challenge["rated"].get_bool().value;
        
        // Random white/black
        bool challengerIsWhite = (rand() % 2) == 0;
        string whiteId = challengerIsWhite ? challengerId : opponentId;
        string blackId = challengerIsWhite ? opponentId : challengerId;
        
        // 5. Tạo game document
        auto now = chrono::system_clock::now();
        auto games = db["games"];
        
        auto gameDoc = document{}
            << "white_player_id" << bsoncxx::oid(whiteId)
            << "black_player_id" << bsoncxx::oid(blackId)
            << "time_control" << timeControl
            << "rated" << rated
            << "status" << "ongoing"
            << "current_turn" << "white"
            << "moves" << bsoncxx::builder::stream::open_array
            << bsoncxx::builder::stream::close_array
            << "fen" << "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"
            << "draw_offer" << bsoncxx::types::b_null{}
            << "created_at" << bsoncxx::types::b_date{now}
            << "started_at" << bsoncxx::types::b_date{now}
            << finalize;
        
        auto insertResult = games.insert_one(gameDoc.view());
        if (!insertResult) {
            response["success"] = false;
            response["error"] = "Failed to create game";
            return response;
        }
        
        string gameId = insertResult->inserted_id().get_oid().value.to_string();
        
        // 6. Return success
        response["success"] = true;
        response["message"] = "Game created successfully";
        response["data"]["game_id"] = gameId;
        response["data"]["white_player_id"] = whiteId;
        response["data"]["black_player_id"] = blackId;
        response["data"]["time_control"] = timeControl;
        response["data"]["current_turn"] = "white";
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Create game error: ") + e.what();
    }
    
    return response;
}

// THỰC HIỆN NƯỚC ĐI
Json::Value GameHandler::handleMakeMove(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate
        if (!request.isMember("token") || !request.isMember("game_id") ||
            !request.isMember("from") || !request.isMember("to")) {
            response["success"] = false;
            response["error"] = "Token, game_id, from, to required";
            return response;
        }
        
        string token = request["token"].asString();
        string userId = getUserIdFromToken(token);
        
        if (userId.empty()) {
            response["success"] = false;
            response["error"] = "Invalid or expired token";
            return response;
        }
        
        string gameId = request["game_id"].asString();
        string from = request["from"].asString();
        string to = request["to"].asString();
        
        // 2. Validate move format
        if (!isValidMove(from, to)) {
            response["success"] = false;
            response["error"] = "Invalid move format (use a1-i9)";
            return response;
        }
        
        // 3. Tìm game
        auto db = mongoClient.getDatabase();
        auto games = db["games"];
        
        auto gameDoc = games.find_one(
            document{} << "_id" << bsoncxx::oid(gameId) << finalize
        );
        
        if (!gameDoc) {
            response["success"] = false;
            response["error"] = "Game not found";
            return response;
        }
        
        auto game = gameDoc->view();
        
        // 4. Check game đang ongoing
        string status = string(game["status"].get_string().value);
        if (status != "ongoing") {
            response["success"] = false;
            response["error"] = "Game is not ongoing";
            return response;
        }
        
        // 5. Check đúng lượt
        string currentTurn = string(game["current_turn"].get_string().value);
        string whiteId = game["white_player_id"].get_oid().value.to_string();
        string blackId = game["black_player_id"].get_oid().value.to_string();
        
        bool isWhiteTurn = (currentTurn == "white");
        string currentPlayerId = isWhiteTurn ? whiteId : blackId;
        
        if (currentPlayerId != userId) {
            response["success"] = false;
            response["error"] = "Not your turn";
            return response;
        }
        
        // 6. Thêm move vào moves array
        string moveStr = from + "-" + to;
        string nextTurn = isWhiteTurn ? "black" : "white";
        
        games.update_one(
            document{} << "_id" << bsoncxx::oid(gameId) << finalize,
            document{} 
                << "$push" << bsoncxx::builder::stream::open_document
                    << "moves" << moveStr
                << bsoncxx::builder::stream::close_document
                << "$set" << bsoncxx::builder::stream::open_document
                    << "current_turn" << nextTurn
                << bsoncxx::builder::stream::close_document
                << finalize
        );
        
        // 7. Lưu move vào Redis (real-time)
        Json::Value moveData;
        moveData["from"] = from;
        moveData["to"] = to;
        moveData["player"] = currentTurn;
        redisClient.addGameMessage(gameId, moveData);
        
        response["success"] = true;
        response["message"] = "Move executed successfully";
        response["data"]["move"] = moveStr;
        response["data"]["next_turn"] = nextTurn;
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Make move error: ") + e.what();
    }
    
    return response;
}

// ĐỀ NGHỊ HÒA
Json::Value GameHandler::handleOfferDraw(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate
        if (!request.isMember("token") || !request.isMember("game_id")) {
            response["success"] = false;
            response["error"] = "Token and game_id required";
            return response;
        }
        
        string token = request["token"].asString();
        string userId = getUserIdFromToken(token);
        
        if (userId.empty()) {
            response["success"] = false;
            response["error"] = "Invalid or expired token";
            return response;
        }
        
        string gameId = request["game_id"].asString();
        
        // 2. Check game
        auto db = mongoClient.getDatabase();
        auto games = db["games"];
        
        auto gameDoc = games.find_one(
            document{} << "_id" << bsoncxx::oid(gameId) << finalize
        );
        
        if (!gameDoc) {
            response["success"] = false;
            response["error"] = "Game not found";
            return response;
        }
        
        auto game = gameDoc->view();
        string status = string(game["status"].get_string().value);
        
        if (status != "ongoing") {
            response["success"] = false;
            response["error"] = "Game is not ongoing";
            return response;
        }
        
        // 3. Update draw_offer
        games.update_one(
            document{} << "_id" << bsoncxx::oid(gameId) << finalize,
            document{} << "$set" << bsoncxx::builder::stream::open_document
                      << "draw_offer" << bsoncxx::oid(userId)
                      << bsoncxx::builder::stream::close_document
                      << finalize
        );
        
        response["success"] = true;
        response["message"] = "Draw offer sent";
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Offer draw error: ") + e.what();
    }
    
    return response;
}

// CHẤP NHẬN/TỪ CHỐI HÒA
Json::Value GameHandler::handleRespondDraw(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate
        if (!request.isMember("token") || !request.isMember("game_id") ||
            !request.isMember("accept")) {
            response["success"] = false;
            response["error"] = "Token, game_id, accept required";
            return response;
        }
        
        string token = request["token"].asString();
        string userId = getUserIdFromToken(token);
        
        if (userId.empty()) {
            response["success"] = false;
            response["error"] = "Invalid or expired token";
            return response;
        }
        
        string gameId = request["game_id"].asString();
        bool accept = request["accept"].asBool();
        
        // 2. Check game
        auto db = mongoClient.getDatabase();
        auto games = db["games"];
        
        auto gameDoc = games.find_one(
            document{} << "_id" << bsoncxx::oid(gameId) << finalize
        );
        
        if (!gameDoc) {
            response["success"] = false;
            response["error"] = "Game not found";
            return response;
        }
        
        auto game = gameDoc->view();
        
        if (!game["draw_offer"]) {
            response["success"] = false;
            response["error"] = "No draw offer to respond to";
            return response;
        }
        
        if (accept) {
            // Kết thúc game với kết quả hòa
            auto now = chrono::system_clock::now();
            games.update_one(
                document{} << "_id" << bsoncxx::oid(gameId) << finalize,
                document{} << "$set" << bsoncxx::builder::stream::open_document
                          << "status" << "draw"
                          << "result" << "draw"
                          << "ended_at" << bsoncxx::types::b_date{now}
                          << bsoncxx::builder::stream::close_document
                          << finalize
            );
            
            // Update ratings
            string whiteId = game["white_player_id"].get_oid().value.to_string();
            string blackId = game["black_player_id"].get_oid().value.to_string();
            string timeControl = string(game["time_control"].get_string().value);
            
            updatePlayerRatings(whiteId, blackId, "draw", timeControl);
            
            response["success"] = true;
            response["message"] = "Draw accepted - Game ended";
        } else {
            // Từ chối hòa
            games.update_one(
                document{} << "_id" << bsoncxx::oid(gameId) << finalize,
                document{} << "$set" << bsoncxx::builder::stream::open_document
                          << "draw_offer" << bsoncxx::types::b_null{}
                          << bsoncxx::builder::stream::close_document
                          << finalize
            );
            
            response["success"] = true;
            response["message"] = "Draw declined";
        }
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Respond draw error: ") + e.what();
    }
    
    return response;
}

// ĐẦU HÀNG
Json::Value GameHandler::handleResign(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate
        if (!request.isMember("token") || !request.isMember("game_id")) {
            response["success"] = false;
            response["error"] = "Token and game_id required";
            return response;
        }
        
        string token = request["token"].asString();
        string userId = getUserIdFromToken(token);
        
        if (userId.empty()) {
            response["success"] = false;
            response["error"] = "Invalid or expired token";
            return response;
        }
        
        string gameId = request["game_id"].asString();
        
        // 2. Check game
        auto db = mongoClient.getDatabase();
        auto games = db["games"];
        
        auto gameDoc = games.find_one(
            document{} << "_id" << bsoncxx::oid(gameId) << finalize
        );
        
        if (!gameDoc) {
            response["success"] = false;
            response["error"] = "Game not found";
            return response;
        }
        
        auto game = gameDoc->view();
        string status = string(game["status"].get_string().value);
        
        if (status != "ongoing") {
            response["success"] = false;
            response["error"] = "Game is not ongoing";
            return response;
        }
        
        string whiteId = game["white_player_id"].get_oid().value.to_string();
        string blackId = game["black_player_id"].get_oid().value.to_string();
        
        // Xác định kết quả
        string result = (userId == whiteId) ? "black_win" : "white_win";
        
        // 3. Kết thúc game
        auto now = chrono::system_clock::now();
        games.update_one(
            document{} << "_id" << bsoncxx::oid(gameId) << finalize,
            document{} << "$set" << bsoncxx::builder::stream::open_document
                      << "status" << "completed"
                      << "result" << result
                      << "ended_at" << bsoncxx::types::b_date{now}
                      << bsoncxx::builder::stream::close_document
                      << finalize
        );
        
        // 4. Update ratings
        string timeControl = string(game["time_control"].get_string().value);
        updatePlayerRatings(whiteId, blackId, result, timeControl);
        
        response["success"] = true;
        response["message"] = "Resigned successfully";
        response["data"]["result"] = result;
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Resign error: ") + e.what();
    }
    
    return response;
}

// LẤY THÔNG TIN GAME
Json::Value GameHandler::handleGetGame(const Json::Value& request) {
    Json::Value response;
    
    try {
        if (!request.isMember("game_id")) {
            response["success"] = false;
            response["error"] = "game_id required";
            return response;
        }
        
        string gameId = request["game_id"].asString();
        
        auto db = mongoClient.getDatabase();
        auto games = db["games"];
        
        auto gameDoc = games.find_one(
            document{} << "_id" << bsoncxx::oid(gameId) << finalize
        );
        
        if (!gameDoc) {
            response["success"] = false;
            response["error"] = "Game not found";
            return response;
        }
        
        auto game = gameDoc->view();
        
        response["success"] = true;
        response["data"]["game_id"] = gameId;
        response["data"]["white_player_id"] = game["white_player_id"].get_oid().value.to_string();
        response["data"]["black_player_id"] = game["black_player_id"].get_oid().value.to_string();
        response["data"]["time_control"] = string(game["time_control"].get_string().value);
        response["data"]["status"] = string(game["status"].get_string().value);
        response["data"]["current_turn"] = string(game["current_turn"].get_string().value);
        
        // Moves
        auto movesArray = game["moves"].get_array().value;
        response["data"]["moves"] = Json::Value(Json::arrayValue);
        for (auto&& move : movesArray) {
            response["data"]["moves"].append(string(move.get_string().value));
        }
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Get game error: ") + e.what();
    }
    
    return response;
}

// LẤY DANH SÁCH GAME
Json::Value GameHandler::handleListGames(const Json::Value& request) {
    Json::Value response;
    
    try {
        if (!request.isMember("token")) {
            response["success"] = false;
            response["error"] = "Token required";
            return response;
        }
        
        string token = request["token"].asString();
        string userId = getUserIdFromToken(token);
        
        if (userId.empty()) {
            response["success"] = false;
            response["error"] = "Invalid or expired token";
            return response;
        }
        
        auto db = mongoClient.getDatabase();
        auto games = db["games"];
        
        // Tìm games của user
        auto cursor = games.find(
            document{} 
                << "$or" << bsoncxx::builder::stream::open_array
                    << bsoncxx::builder::stream::open_document
                        << "white_player_id" << bsoncxx::oid(userId)
                    << bsoncxx::builder::stream::close_document
                    << bsoncxx::builder::stream::open_document
                        << "black_player_id" << bsoncxx::oid(userId)
                    << bsoncxx::builder::stream::close_document
                << bsoncxx::builder::stream::close_array
                << finalize
        );
        
        response["success"] = true;
        response["data"] = Json::Value(Json::arrayValue);
        
        for (auto&& doc : cursor) {
            Json::Value gameJson;
            gameJson["game_id"] = doc["_id"].get_oid().value.to_string();
            gameJson["white_player_id"] = doc["white_player_id"].get_oid().value.to_string();
            gameJson["black_player_id"] = doc["black_player_id"].get_oid().value.to_string();
            gameJson["time_control"] = string(doc["time_control"].get_string().value);
            gameJson["status"] = string(doc["status"].get_string().value);
            
            response["data"].append(gameJson);
        }
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("List games error: ") + e.what();
    }
    
    return response;
}
