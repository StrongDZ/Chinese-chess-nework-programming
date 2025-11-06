#include "../../include/game/game_handler.h"
#include <mongocxx/client.hpp>
#include <bsoncxx/builder/stream/document.hpp>
#include <bsoncxx/builder/basic/document.hpp>
#include <bsoncxx/json.hpp>
#include <chrono>
#include <cmath>
#include <random>

using namespace std;
using bsoncxx::builder::stream::document;
using bsoncxx::builder::stream::finalize;
using bsoncxx::builder::stream::open_document;
using bsoncxx::builder::stream::close_document;
using bsoncxx::builder::stream::open_array;
using bsoncxx::builder::stream::close_array;
using bsoncxx::builder::basic::make_document;
using bsoncxx::builder::basic::kvp;

GameHandler::GameHandler(MongoDBClient& mongo, RedisClient& redis)
    : mongoClient(mongo), redisClient(redis) {}

// Lấy userId từ token (Redis session)
string GameHandler::getUserIdFromToken(const string& token) {
    return redisClient.getSession(token);
}

// Validate tọa độ cờ tướng (x: 0-8, y: 0-9)
bool GameHandler::isValidCoordinate(int x, int y) {
    return (x >= 0 && x <= 8 && y >= 0 && y <= 9);
}

// Check game đã kết thúc chưa
bool GameHandler::isGameOver(const string& gameId) {
    auto db = mongoClient.getDatabase();
    auto games = db["active_games"];
    
    auto gameDoc = games.find_one(
        document{} << "_id" << bsoncxx::oid(gameId) << finalize
    );
    
    if (!gameDoc) return true;
    
    string status = string(gameDoc->view()["status"].get_string().value);
    return (status == "completed" || status == "abandoned");
}

// Get time limits based on time_control
int GameHandler::getTimeLimitSeconds(const string& timeControl) {
    if (timeControl == "bullet") return 180;      // 3 phút
    if (timeControl == "blitz") return 300;       // 5 phút
    if (timeControl == "classical") return 900;   // 15 phút
    return 300; // Mặc định blitz
}

int GameHandler::getIncrementSeconds(const string& timeControl) {
    if (timeControl == "bullet") return 2;
    if (timeControl == "blitz") return 3;
    if (timeControl == "classical") return 5;
    return 3; // Mặc định
}

// Cập nhật rating sau game (simplified Elo)
void GameHandler::updatePlayerRatings(const string& redId, const string& blackId,
                                    const string& result, const string& timeControl) {
    auto db = mongoClient.getDatabase();
    auto stats = db["player_stats"];
    
    // Lấy rating hiện tại
    auto redStats = stats.find_one(
        document{} 
            << "user_id" << bsoncxx::oid(redId)
            << "time_control" << timeControl
            << finalize
    );
    
    auto blackStats = stats.find_one(
        document{} 
            << "user_id" << bsoncxx::oid(blackId)
            << "time_control" << timeControl
            << finalize
    );
    
    if (!redStats || !blackStats) return;
    
    // Simplified Elo rating update (K-factor = 32)
    int redRating = redStats->view()["rating"].get_int32().value;
    int blackRating = blackStats->view()["rating"].get_int32().value;
    
    double redScore = (result == "red_win") ? 1.0 : (result == "draw") ? 0.5 : 0.0;
    double blackScore = 1.0 - redScore;
    
    double redExpected = 1.0 / (1.0 + pow(10.0, (blackRating - redRating) / 400.0));
    double blackExpected = 1.0 - redExpected;
    
    int redNewRating = redRating + (int)(32 * (redScore - redExpected));
    int blackNewRating = blackRating + (int)(32 * (blackScore - blackExpected));
    
    // Xác định field cần increment
    string redField = (result == "red_win") ? "wins" : (result == "draw") ? "draws" : "losses";
    string blackField = (result == "black_win") ? "wins" : (result == "draw") ? "draws" : "losses";
    
    // Update red stats
    auto redUpdate = document{}
        << "$set" << open_document
            << "rating" << redNewRating
        << close_document
        << "$inc" << open_document
            << "total_games" << 1
            << redField << 1
        << close_document
        << "$max" << open_document
            << "highest_rating" << redNewRating
        << close_document
        << "$min" << open_document
            << "lowest_rating" << redNewRating
        << close_document
        << finalize;
    
    stats.update_one(
        document{} 
            << "user_id" << bsoncxx::oid(redId)
            << "time_control" << timeControl
            << finalize,
        redUpdate.view()
    );
    
    // Update black stats
    auto blackUpdate = document{}
        << "$set" << open_document
            << "rating" << blackNewRating
        << close_document
        << "$inc" << open_document
            << "total_games" << 1
            << blackField << 1
        << close_document
        << "$max" << open_document
            << "highest_rating" << blackNewRating
        << close_document
        << "$min" << open_document
            << "lowest_rating" << blackNewRating
        << close_document
        << finalize;
    
    stats.update_one(
        document{} 
            << "user_id" << bsoncxx::oid(blackId)
            << "time_control" << timeControl
            << finalize,
        blackUpdate.view()
    );
}

// TẠO GAME MỚI TỪ CHALLENGE
Json::Value GameHandler::handleCreateGame(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate token và challenge_id
        if (!request.isMember("token") || !request.isMember("challenge_id")) {
            response["status"] = "error";
            response["message"] = "Token and challenge_id required";
            return response;
        }
        
        string token = request["token"].asString();
        string userId = getUserIdFromToken(token);
        
        if (userId.empty()) {
            response["status"] = "error";
            response["message"] = "Invalid or expired token";
            return response;
        }
        
        string challengeId = request["challenge_id"].asString();
        
        // 2. Load challenge từ Redis bằng cách load trực tiếp từ hash
        string challengeKey = "challenge:" + challengeId;
        auto challengeFields = redisClient.hgetall(challengeKey);
        
        if (challengeFields.empty()) {
            response["status"] = "error";
            response["message"] = "Challenge not found or expired";
            return response;
        }
        
        // 3. Check challenge đã accepted
        string status = challengeFields["status"];
        if (status != "accepted") {
            response["status"] = "error";
            response["message"] = "Challenge must be accepted first";
            return response;
        }
        
        // 4. Lấy thông tin players
        string challengerId = challengeFields["challenger_id"];
        string challengedId = challengeFields["challenged_id"];
        string timeControl = challengeFields["time_control"];
        bool rated = (challengeFields["rated"] == "true");
        
        // Random red/black (red đi trước trong cờ tướng)
        random_device rd;
        mt19937 gen(rd());
        uniform_int_distribution<> dis(0, 1);
        
        bool challengerIsRed = (dis(gen) == 0);
        string redId = challengerIsRed ? challengerId : challengedId;
        string blackId = challengerIsRed ? challengedId : challengerId;
        
        // 5. Lấy username của players
        auto db = mongoClient.getDatabase();
        auto users = db["users"];
        
        auto redUser = users.find_one(document{} << "_id" << bsoncxx::oid(redId) << finalize);
        auto blackUser = users.find_one(document{} << "_id" << bsoncxx::oid(blackId) << finalize);
        
        if (!redUser || !blackUser) {
            response["status"] = "error";
            response["message"] = "Player not found";
            return response;
        }
        
        string redPlayerName = string(redUser->view()["username"].get_string().value);
        string blackPlayerName = string(blackUser->view()["username"].get_string().value);
        
        // 6. Tạo game document trong active_games
        auto now = chrono::system_clock::now();
        auto games = db["active_games"];
        
        // XFEN ban đầu cho cờ tướng (Xiangqi starting position)
        string initialXFEN = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1";
        
        int timeLimit = getTimeLimitSeconds(timeControl);
        int increment = getIncrementSeconds(timeControl);
        
        // Create ObjectIDs
        bsoncxx::oid redOid;
        bsoncxx::oid blackOid;
        
        try {
            redOid = bsoncxx::oid(redId);
            blackOid = bsoncxx::oid(blackId);
        } catch (const exception& e) {
            response["status"] = "error";
            response["message"] = string("Invalid player ID format: ") + e.what();
            return response;
        }
        
        auto gameDoc = document{}
            << "red_player_id" << redOid
            << "black_player_id" << blackOid
            << "red_player_name" << redPlayerName
            << "black_player_name" << blackPlayerName
            << "status" << "in_progress"
            << "start_time" << bsoncxx::types::b_date{now}
            << "xfen" << initialXFEN
            << "moves" << open_array << close_array
            << "current_turn" << "red"
            << "move_count" << bsoncxx::types::b_int32{0}
            << "time_control" << timeControl
            << "time_limit" << bsoncxx::types::b_int32{timeLimit}
            << "red_time_remaining" << bsoncxx::types::b_int32{timeLimit}
            << "black_time_remaining" << bsoncxx::types::b_int32{timeLimit}
            << "increment" << bsoncxx::types::b_int32{increment}
            << "rated" << bsoncxx::types::b_bool{rated}
            << finalize;
        
        auto insertResult = games.insert_one(gameDoc.view());
        if (!insertResult) {
            response["status"] = "error";
            response["message"] = "Failed to create game";
            return response;
        }
        
        string gameId = insertResult->inserted_id().get_oid().value.to_string();
        
        // 7. Publish notification to Redis (cho real-time updates)
        Json::Value gameNotification;
        gameNotification["event"] = "game_created";
        gameNotification["game_id"] = gameId;
        gameNotification["red_player_id"] = redId;
        gameNotification["black_player_id"] = blackId;
        
        Json::StreamWriterBuilder writer;
        string notificationStr = Json::writeString(writer, gameNotification);
        redisClient.publish("game:created", notificationStr);
        
        // 8. Return success
        response["status"] = "success";
        response["message"] = "Game created successfully";
        response["game"]["game_id"] = gameId;
        response["game"]["red_player_id"] = redId;
        response["game"]["red_player_name"] = redPlayerName;
        response["game"]["black_player_id"] = blackId;
        response["game"]["black_player_name"] = blackPlayerName;
        response["game"]["time_control"] = timeControl;
        response["game"]["current_turn"] = "red";
        response["game"]["xfen"] = initialXFEN;
        response["game"]["rated"] = rated;
        
    } catch (const exception& e) {
        response["status"] = "error";
        response["message"] = string("Create game error: ") + e.what();
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
            response["status"] = "error";
            response["message"] = "Token, game_id, from {x,y}, to {x,y} required";
            return response;
        }
        
        string token = request["token"].asString();
        string userId = getUserIdFromToken(token);
        
        if (userId.empty()) {
            response["status"] = "error";
            response["message"] = "Invalid or expired token";
            return response;
        }
        
        string gameId = request["game_id"].asString();
        
        // Parse from/to coordinates
        if (!request["from"].isMember("x") || !request["from"].isMember("y") ||
            !request["to"].isMember("x") || !request["to"].isMember("y")) {
            response["status"] = "error";
            response["message"] = "from and to must have x,y coordinates";
            return response;
        }
        
        int fromX = request["from"]["x"].asInt();
        int fromY = request["from"]["y"].asInt();
        int toX = request["to"]["x"].asInt();
        int toY = request["to"]["y"].asInt();
        
        // 2. Validate coordinates
        if (!isValidCoordinate(fromX, fromY) || !isValidCoordinate(toX, toY)) {
            response["status"] = "error";
            response["message"] = "Invalid coordinates (x: 0-8, y: 0-9)";
            return response;
        }
        
        // 3. Tìm game
        auto db = mongoClient.getDatabase();
        auto games = db["active_games"];
        
        auto gameDoc = games.find_one(
            document{} << "_id" << bsoncxx::oid(gameId) << finalize
        );
        
        if (!gameDoc) {
            response["status"] = "error";
            response["message"] = "Game not found";
            return response;
        }
        
        auto game = gameDoc->view();
        
        // 4. Check game đang in_progress
        string status = string(game["status"].get_string().value);
        if (status != "in_progress") {
            response["status"] = "error";
            response["message"] = "Game is not in progress";
            return response;
        }
        
        // 5. Check đúng lượt
        string currentTurn = string(game["current_turn"].get_string().value);
        string redId = game["red_player_id"].get_oid().value.to_string();
        string blackId = game["black_player_id"].get_oid().value.to_string();
        
        bool isRedTurn = (currentTurn == "red");
        string currentPlayerId = isRedTurn ? redId : blackId;
        
        if (currentPlayerId != userId) {
            response["status"] = "error";
            response["message"] = "Not your turn";
            return response;
        }
        
        // 6. Build move object
        int moveCount = game["move_count"].get_int32().value;
        int moveNumber = moveCount + 1;
        
        auto now = chrono::system_clock::now();
        
        // Simplified: không validate rules ở đây, client sẽ validate
        string piece = request.get("piece", "?").asString();
        string captured = request.get("captured", "").asString();
        string notation = request.get("notation", "").asString();
        string xfenAfter = request.get("xfen_after", "").asString();
        int timeTaken = request.get("time_taken", 0).asInt();
        
        // Build move document using make_document (simpler)
        auto moveDoc = make_document(
            kvp("move_number", moveNumber),
            kvp("player_id", bsoncxx::oid(userId)),
            kvp("from_x", fromX),
            kvp("from_y", fromY),
            kvp("to_x", toX),
            kvp("to_y", toY),
            kvp("piece", piece),
            kvp("captured", captured),
            kvp("notation", notation),
            kvp("xfen_after", xfenAfter),
            kvp("timestamp", bsoncxx::types::b_date{now}),
            kvp("time_taken", timeTaken)
        );
        
        // 7. Update game
        string nextTurn = isRedTurn ? "black" : "red";
        
        // Update time remaining
        int redTime = game["red_time_remaining"].get_int32().value;
        int blackTime = game["black_time_remaining"].get_int32().value;
        int increment = game["increment"].get_int32().value;
        
        if (isRedTurn) {
            redTime = max(0, redTime - timeTaken + increment);
        } else {
            blackTime = max(0, blackTime - timeTaken + increment);
        }
        
        // Build update document using make_document
        bsoncxx::document::value updateDoc = (!xfenAfter.empty()) 
            ? make_document(
                kvp("$push", make_document(
                    kvp("moves", moveDoc.view())
                )),
                kvp("$set", make_document(
                    kvp("current_turn", nextTurn),
                    kvp("move_count", moveNumber),
                    kvp("red_time_remaining", redTime),
                    kvp("black_time_remaining", blackTime),
                    kvp("xfen", xfenAfter)
                ))
            )
            : make_document(
                kvp("$push", make_document(
                    kvp("moves", moveDoc.view())
                )),
                kvp("$set", make_document(
                    kvp("current_turn", nextTurn),
                    kvp("move_count", moveNumber),
                    kvp("red_time_remaining", redTime),
                    kvp("black_time_remaining", blackTime)
                ))
            );
        
        games.update_one(
            make_document(kvp("_id", bsoncxx::oid(gameId))),
            updateDoc.view()
        );
        
        // 8. Publish move to Redis (real-time)
        Json::Value moveData;
        moveData["game_id"] = gameId;
        moveData["move_number"] = moveNumber;
        moveData["from"]["x"] = fromX;
        moveData["from"]["y"] = fromY;
        moveData["to"]["x"] = toX;
        moveData["to"]["y"] = toY;
        moveData["player"] = currentTurn;
        moveData["next_turn"] = nextTurn;
        
        Json::StreamWriterBuilder writer;
        string moveStr = Json::writeString(writer, moveData);
        redisClient.publish("game:move:" + gameId, moveStr);
        
        response["status"] = "success";
        response["message"] = "Move executed successfully";
        response["move"]["from"]["x"] = fromX;
        response["move"]["from"]["y"] = fromY;
        response["move"]["to"]["x"] = toX;
        response["move"]["to"]["y"] = toY;
        response["move"]["move_number"] = moveNumber;
        response["next_turn"] = nextTurn;
        response["red_time_remaining"] = redTime;
        response["black_time_remaining"] = blackTime;
        
    } catch (const exception& e) {
        response["status"] = "error";
        response["message"] = string("Make move error: ") + e.what();
    }
    
    return response;
}

// ĐỀ NGHỊ HÒA
Json::Value GameHandler::handleOfferDraw(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate
        if (!request.isMember("token") || !request.isMember("game_id")) {
            response["status"] = "error";
            response["message"] = "Token and game_id required";
            return response;
        }
        
        string token = request["token"].asString();
        string userId = getUserIdFromToken(token);
        
        if (userId.empty()) {
            response["status"] = "error";
            response["message"] = "Invalid or expired token";
            return response;
        }
        
        string gameId = request["game_id"].asString();
        
        // 2. Check game
        auto db = mongoClient.getDatabase();
        auto games = db["active_games"];
        
        auto gameDoc = games.find_one(
            document{} << "_id" << bsoncxx::oid(gameId) << finalize
        );
        
        if (!gameDoc) {
            response["status"] = "error";
            response["message"] = "Game not found";
            return response;
        }
        
        auto game = gameDoc->view();
        string status = string(game["status"].get_string().value);
        
        if (status != "in_progress") {
            response["status"] = "error";
            response["message"] = "Game is not in progress";
            return response;
        }
        
        // 3. Lưu draw offer vào Redis (tạm thời)
        redisClient.set("draw_offer:" + gameId, userId, 300); // TTL 5 phút
        
        // 4. Publish notification
        Json::Value drawOffer;
        drawOffer["game_id"] = gameId;
        drawOffer["from_player_id"] = userId;
        
        Json::StreamWriterBuilder writer;
        string drawOfferStr = Json::writeString(writer, drawOffer);
        redisClient.publish("game:draw_offer:" + gameId, drawOfferStr);
        
        response["status"] = "success";
        response["message"] = "Draw offer sent";
        
    } catch (const exception& e) {
        response["status"] = "error";
        response["message"] = string("Offer draw error: ") + e.what();
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
            response["status"] = "error";
            response["message"] = "Token, game_id, accept required";
            return response;
        }
        
        string token = request["token"].asString();
        string userId = getUserIdFromToken(token);
        
        if (userId.empty()) {
            response["status"] = "error";
            response["message"] = "Invalid or expired token";
            return response;
        }
        
        string gameId = request["game_id"].asString();
        bool accept = request["accept"].asBool();
        
        // 2. Check draw offer tồn tại
        string offererId = redisClient.get("draw_offer:" + gameId);
        
        if (offererId.empty()) {
            response["status"] = "error";
            response["message"] = "No draw offer to respond to";
            return response;
        }
        
        // 3. Check user không phải là người offer
        if (offererId == userId) {
            response["status"] = "error";
            response["message"] = "Cannot accept your own draw offer";
            return response;
        }
        
        // 4. Check game
        auto db = mongoClient.getDatabase();
        auto games = db["active_games"];
        
        auto gameDoc = games.find_one(
            document{} << "_id" << bsoncxx::oid(gameId) << finalize
        );
        
        if (!gameDoc) {
            response["status"] = "error";
            response["message"] = "Game not found";
            return response;
        }
        
        auto game = gameDoc->view();
        
        if (accept) {
            // Chấp nhận hòa - kết thúc game
            auto now = chrono::system_clock::now();
            
            games.update_one(
                document{} << "_id" << bsoncxx::oid(gameId) << finalize,
                document{} << "$set" << open_document
                          << "status" << "completed"
                          << "result" << "draw"
                          << "end_time" << bsoncxx::types::b_date{now}
                          << "winner_id" << bsoncxx::types::b_null{}
                          << close_document
                          << finalize
            );
            
            // Update ratings
            string redId = game["red_player_id"].get_oid().value.to_string();
            string blackId = game["black_player_id"].get_oid().value.to_string();
            string timeControl = string(game["time_control"].get_string().value);
            bool rated = game["rated"].get_bool().value;
            
            if (rated) {
                updatePlayerRatings(redId, blackId, "draw", timeControl);
            }
            
            // Xóa draw offer
            redisClient.del("draw_offer:" + gameId);
            
            response["status"] = "success";
            response["message"] = "Draw accepted - Game ended";
            response["result"] = "draw";
        } else {
            // Từ chối hòa
            redisClient.del("draw_offer:" + gameId);
            
            response["status"] = "success";
            response["message"] = "Draw declined";
        }
        
    } catch (const exception& e) {
        response["status"] = "error";
        response["message"] = string("Respond draw error: ") + e.what();
    }
    
    return response;
}

// ĐẦU HÀNG
Json::Value GameHandler::handleResign(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate
        if (!request.isMember("token") || !request.isMember("game_id")) {
            response["status"] = "error";
            response["message"] = "Token and game_id required";
            return response;
        }
        
        string token = request["token"].asString();
        string userId = getUserIdFromToken(token);
        
        if (userId.empty()) {
            response["status"] = "error";
            response["message"] = "Invalid or expired token";
            return response;
        }
        
        string gameId = request["game_id"].asString();
        
        // 2. Check game
        auto db = mongoClient.getDatabase();
        auto games = db["active_games"];
        
        auto gameDoc = games.find_one(
            document{} << "_id" << bsoncxx::oid(gameId) << finalize
        );
        
        if (!gameDoc) {
            response["status"] = "error";
            response["message"] = "Game not found";
            return response;
        }
        
        auto game = gameDoc->view();
        string status = string(game["status"].get_string().value);
        
        if (status != "in_progress") {
            response["status"] = "error";
            response["message"] = "Game is not in progress";
            return response;
        }
        
        string redId = game["red_player_id"].get_oid().value.to_string();
        string blackId = game["black_player_id"].get_oid().value.to_string();
        
        // Xác định kết quả
        string result;
        string winnerId;
        
        if (userId == redId) {
            result = "black_win";
            winnerId = blackId;
        } else if (userId == blackId) {
            result = "red_win";
            winnerId = redId;
        } else {
            response["status"] = "error";
            response["message"] = "You are not a player in this game";
            return response;
        }
        
        // 3. Kết thúc game
        auto now = chrono::system_clock::now();
        games.update_one(
            document{} << "_id" << bsoncxx::oid(gameId) << finalize,
            document{} << "$set" << open_document
                      << "status" << "completed"
                      << "result" << result
                      << "winner_id" << bsoncxx::oid(winnerId)
                      << "end_time" << bsoncxx::types::b_date{now}
                      << close_document
                      << finalize
        );
        
        // 4. Update ratings
        string timeControl = string(game["time_control"].get_string().value);
        bool rated = game["rated"].get_bool().value;
        
        if (rated) {
            updatePlayerRatings(redId, blackId, result, timeControl);
        }
        
        // 5. Publish notification
        Json::Value resignData;
        resignData["game_id"] = gameId;
        resignData["player_id"] = userId;
        resignData["result"] = result;
        
        Json::StreamWriterBuilder writer;
        string resignStr = Json::writeString(writer, resignData);
        redisClient.publish("game:resign:" + gameId, resignStr);
        
        response["status"] = "success";
        response["message"] = "Resigned successfully";
        response["result"] = result;
        response["winner_id"] = winnerId;
        
    } catch (const exception& e) {
        response["status"] = "error";
        response["message"] = string("Resign error: ") + e.what();
    }
    
    return response;
}

// LẤY THÔNG TIN GAME
Json::Value GameHandler::handleGetGame(const Json::Value& request) {
    Json::Value response;
    
    try {
        if (!request.isMember("game_id")) {
            response["status"] = "error";
            response["message"] = "game_id required";
            return response;
        }
        
        string gameId = request["game_id"].asString();
        
        auto db = mongoClient.getDatabase();
        auto games = db["active_games"];
        
        auto gameDoc = games.find_one(
            document{} << "_id" << bsoncxx::oid(gameId) << finalize
        );
        
        if (!gameDoc) {
            response["status"] = "error";
            response["message"] = "Game not found";
            return response;
        }
        
        auto game = gameDoc->view();
        
        response["status"] = "success";
        response["game"]["game_id"] = gameId;
        response["game"]["red_player_id"] = game["red_player_id"].get_oid().value.to_string();
        response["game"]["black_player_id"] = game["black_player_id"].get_oid().value.to_string();
        response["game"]["red_player_name"] = string(game["red_player_name"].get_string().value);
        response["game"]["black_player_name"] = string(game["black_player_name"].get_string().value);
        response["game"]["time_control"] = string(game["time_control"].get_string().value);
        response["game"]["status"] = string(game["status"].get_string().value);
        response["game"]["current_turn"] = string(game["current_turn"].get_string().value);
        response["game"]["xfen"] = string(game["xfen"].get_string().value);
        response["game"]["move_count"] = game["move_count"].get_int32().value;
        response["game"]["red_time_remaining"] = game["red_time_remaining"].get_int32().value;
        response["game"]["black_time_remaining"] = game["black_time_remaining"].get_int32().value;
        response["game"]["rated"] = game["rated"].get_bool().value;
        
        // Moves
        auto movesArray = game["moves"].get_array().value;
        response["game"]["moves"] = Json::Value(Json::arrayValue);
        
        for (auto&& moveDoc : movesArray) {
            Json::Value move;
            move["move_number"] = moveDoc["move_number"].get_int32().value;
            move["from"]["x"] = moveDoc["from"].get_document().value["x"].get_int32().value;
            move["from"]["y"] = moveDoc["from"].get_document().value["y"].get_int32().value;
            move["to"]["x"] = moveDoc["to"].get_document().value["x"].get_int32().value;
            move["to"]["y"] = moveDoc["to"].get_document().value["y"].get_int32().value;
            
            if (moveDoc["piece"].type() != bsoncxx::type::k_null) {
                move["piece"] = string(moveDoc["piece"].get_string().value);
            }
            
            response["game"]["moves"].append(move);
        }
        
    } catch (const exception& e) {
        response["status"] = "error";
        response["message"] = string("Get game error: ") + e.what();
    }
    
    return response;
}

// LẤY DANH SÁCH GAME
Json::Value GameHandler::handleListGames(const Json::Value& request) {
    Json::Value response;
    
    try {
        if (!request.isMember("token")) {
            response["status"] = "error";
            response["message"] = "Token required";
            return response;
        }
        
        string token = request["token"].asString();
        string userId = getUserIdFromToken(token);
        
        if (userId.empty()) {
            response["status"] = "error";
            response["message"] = "Invalid or expired token";
            return response;
        }
        
        auto db = mongoClient.getDatabase();
        auto games = db["active_games"];
        
        // Filter option: "active", "completed", "all"
        string filter = request.get("filter", "active").asString();
        
        document queryBuilder{};
        queryBuilder
            << "$or" << open_array
                << open_document
                    << "red_player_id" << bsoncxx::oid(userId)
                << close_document
                << open_document
                    << "black_player_id" << bsoncxx::oid(userId)
                << close_document
            << close_array;
        
        if (filter == "active") {
            queryBuilder << "status" << "in_progress";
        } else if (filter == "completed") {
            queryBuilder << "status" << "completed";
        }
        
        auto query = queryBuilder << finalize;
        auto cursor = games.find(query.view());
        
        response["status"] = "success";
        response["games"] = Json::Value(Json::arrayValue);
        
        for (auto&& doc : cursor) {
            Json::Value gameJson;
            gameJson["game_id"] = doc["_id"].get_oid().value.to_string();
            gameJson["red_player_id"] = doc["red_player_id"].get_oid().value.to_string();
            gameJson["black_player_id"] = doc["black_player_id"].get_oid().value.to_string();
            gameJson["red_player_name"] = string(doc["red_player_name"].get_string().value);
            gameJson["black_player_name"] = string(doc["black_player_name"].get_string().value);
            gameJson["time_control"] = string(doc["time_control"].get_string().value);
            gameJson["status"] = string(doc["status"].get_string().value);
            gameJson["current_turn"] = string(doc["current_turn"].get_string().value);
            gameJson["move_count"] = doc["move_count"].get_int32().value;
            
            // Check if result field exists and is not null
            auto resultElement = doc["result"];
            if (resultElement && resultElement.type() != bsoncxx::type::k_null) {
                gameJson["result"] = string(resultElement.get_string().value);
            }
            
            response["games"].append(gameJson);
        }
        
        response["count"] = static_cast<int>(response["games"].size());
        
    } catch (const exception& e) {
        response["status"] = "error";
        response["message"] = string("List games error: ") + e.what();
    }
    
    return response;
}
