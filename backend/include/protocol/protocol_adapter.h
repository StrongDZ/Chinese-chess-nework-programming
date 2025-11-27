#pragma once

#include "../include/auth/auth_handler.h"
#include "../include/challenge/challenge_handler.h"
#include "../include/game/game_handler.h"
#include "../include/player_stat/player_stat_handler.h"
#include "../include/protocol/MessageTypes.h"
#include <json/json.h>

/**
 * ProtocolAdapter - Chuyển đổi giữa Protocol Messages và Backend Handlers
 * 
 * Protocol sử dụng:
 * - RapidJSON + nlohmann::json
 * - Format: "COMMAND {json_payload}"
 * - Coordinates: {row, col}
 * 
 * Handlers sử dụng:
 * - JsonCpp (Json::Value)
 * - Token-based authentication
 * - Coordinates: {x, y}
 */
class ProtocolAdapter {
private:
    AuthHandler& authHandler;
    ChallengeHandler& challengeHandler;
    GameHandler& gameHandler;
    PlayerStatsHandler& statsHandler;
    
    // Map fd -> token (session tracking)
    std::map<int, std::string> fdToToken;
    
    // Convert nlohmann::json to JsonCpp
    Json::Value nlohmannToJsonCpp(const nlohmann::json& nj);
    
    // Convert JsonCpp to nlohmann::json
    nlohmann::json jsonCppToNlohmann(const Json::Value& jv);
    
    // Convert coordinates: {row, col} -> {x, y}
    void convertCoordinates(Json::Value& request);

public:
    ProtocolAdapter(AuthHandler& auth, ChallengeHandler& challenge, 
                   GameHandler& game, PlayerStatsHandler& stats);
    
    // === Authentication ===
    std::pair<MessageType, Payload> handleRegister(int fd, const RegisterPayload& p);
    std::pair<MessageType, Payload> handleLogin(int fd, const LoginPayload& p);
    std::pair<MessageType, Payload> handleLogout(int fd);
    
    // === Challenge ===
    std::pair<MessageType, Payload> handleChallengeRequest(int fd, const ChallengeRequestPayload& p);
    std::pair<MessageType, Payload> handleChallengeResponse(int fd, const ChallengeResponsePayload& p);
    std::pair<MessageType, Payload> handleChallengeCancel(int fd, const std::string& challengeId);
    std::pair<MessageType, Payload> handleListChallenges(int fd);
    
    // === Game ===
    std::pair<MessageType, Payload> handleCreateGame(int fd, const std::string& challengeId);
    std::pair<MessageType, Payload> handleMove(int fd, const MovePayload& p, const std::string& gameId);
    std::pair<MessageType, Payload> handleResign(int fd, const std::string& gameId);
    std::pair<MessageType, Payload> handleDrawRequest(int fd, const std::string& gameId);
    std::pair<MessageType, Payload> handleDrawResponse(int fd, const std::string& gameId, bool accept);
    std::pair<MessageType, Payload> handleGetGame(int fd, const std::string& gameId);
    std::pair<MessageType, Payload> handleListGames(int fd);
    
    // === Stats ===
    std::pair<MessageType, Payload> handleGetStats(int fd, const std::string& timeControl);
    std::pair<MessageType, Payload> handleLeaderboard(int fd, const std::string& timeControl);
    
    // === Utility ===
    void disconnectClient(int fd);
    std::string getToken(int fd);
};

// ========== Implementation ==========

ProtocolAdapter::ProtocolAdapter(AuthHandler& auth, ChallengeHandler& challenge,
                               GameHandler& game, PlayerStatsHandler& stats)
    : authHandler(auth), challengeHandler(challenge), 
      gameHandler(game), statsHandler(stats) {}

Json::Value ProtocolAdapter::nlohmannToJsonCpp(const nlohmann::json& nj) {
    Json::Value result;
    std::string jsonStr = nj.dump();
    Json::CharReaderBuilder builder;
    Json::CharReader* reader = builder.newCharReader();
    std::string errors;
    
    reader->parse(jsonStr.c_str(), jsonStr.c_str() + jsonStr.size(), &result, &errors);
    delete reader;
    
    return result;
}

nlohmann::json ProtocolAdapter::jsonCppToNlohmann(const Json::Value& jv) {
    Json::StreamWriterBuilder builder;
    std::string jsonStr = Json::writeString(builder, jv);
    return nlohmann::json::parse(jsonStr);
}

void ProtocolAdapter::convertCoordinates(Json::Value& request) {
    // Convert from {row, col} to {x, y}
    // Protocol: row (0-9 hàng), col (0-8 cột)
    // Backend: x (0-8 cột), y (0-9 hàng)
    if (request.isMember("from") && request["from"].isObject()) {
        if (request["from"].isMember("row") && request["from"].isMember("col")) {
            int x = request["from"]["col"].asInt();
            int y = request["from"]["row"].asInt();
            request["from"]["x"] = x;
            request["from"]["y"] = y;
            request["from"].removeMember("row");
            request["from"].removeMember("col");
        }
    }
    if (request.isMember("to") && request["to"].isObject()) {
        if (request["to"].isMember("row") && request["to"].isMember("col")) {
            int x = request["to"]["col"].asInt();
            int y = request["to"]["row"].asInt();
            request["to"]["x"] = x;
            request["to"]["y"] = y;
            request["to"].removeMember("row");
            request["to"].removeMember("col");
        }
    }
}

// === Authentication ===

std::pair<MessageType, Payload> ProtocolAdapter::handleRegister(int fd, const RegisterPayload& p) {
    Json::Value request;
    request["username"] = p.username;
    request["password"] = p.password;
    request["email"] = p.username + "@chess.local"; // Default email
    
    Json::Value response = authHandler.handleRegister(request);
    
    if (response["status"].asString() == "success") {
        return {MessageType::AUTHENTICATED, EmptyPayload{}};
    } else {
        return {MessageType::ERROR, ErrorPayload{response["message"].asString()}};
    }
}

std::pair<MessageType, Payload> ProtocolAdapter::handleLogin(int fd, const LoginPayload& p) {
    Json::Value request;
    request["username"] = p.username;
    request["password"] = p.password;
    
    Json::Value response = authHandler.handleLogin(request);
    
    if (response["status"].asString() == "success") {
        std::string token = response["token"].asString();
        fdToToken[fd] = token;
        return {MessageType::AUTHENTICATED, EmptyPayload{}};
    } else {
        return {MessageType::ERROR, ErrorPayload{response["message"].asString()}};
    }
}

std::pair<MessageType, Payload> ProtocolAdapter::handleLogout(int fd) {
    auto it = fdToToken.find(fd);
    if (it == fdToToken.end()) {
        return {MessageType::ERROR, ErrorPayload{"Not logged in"}};
    }
    
    Json::Value request;
    request["token"] = it->second;
    
    Json::Value response = authHandler.handleLogout(request);
    fdToToken.erase(it);
    
    if (response["status"].asString() == "success") {
        return {MessageType::INFO, InfoPayload{nlohmann::json{{"logout", "ok"}}}};
    } else {
        return {MessageType::ERROR, ErrorPayload{response["message"].asString()}};
    }
}

// === Challenge ===

std::pair<MessageType, Payload> ProtocolAdapter::handleChallengeRequest(
    int fd, const ChallengeRequestPayload& p) {
    
    auto it = fdToToken.find(fd);
    if (it == fdToToken.end()) {
        return {MessageType::ERROR, ErrorPayload{"Please login first"}};
    }
    
    Json::Value request;
    request["token"] = it->second;
    request["challenged_id"] = p.username; // Needs user_id, not username
    request["time_control"] = "blitz";
    request["rated"] = true;
    
    Json::Value response = challengeHandler.handleCreateChallenge(request);
    
    if (response["status"].asString() == "success") {
        // Notify target user via Redis pub/sub (implement later)
        return {MessageType::INFO, InfoPayload{jsonCppToNlohmann(response["challenge"])}};
    } else {
        return {MessageType::ERROR, ErrorPayload{response["message"].asString()}};
    }
}

std::pair<MessageType, Payload> ProtocolAdapter::handleChallengeResponse(
    int fd, const ChallengeResponsePayload& p) {
    
    auto it = fdToToken.find(fd);
    if (it == fdToToken.end()) {
        return {MessageType::ERROR, ErrorPayload{"Please login first"}};
    }
    
    Json::Value request;
    request["token"] = it->second;
    request["challenge_id"] = p.username; // Actually challenge_id in your implementation
    
    Json::Value response;
    if (p.accept) {
        response = challengeHandler.handleAcceptChallenge(request);
    } else {
        response = challengeHandler.handleDeclineChallenge(request);
    }
    
    if (response["status"].asString() == "success") {
        if (p.accept) {
            // Create game
            auto gameResult = handleCreateGame(fd, request["challenge_id"].asString());
            if (gameResult.first == MessageType::GAME_START) {
                return gameResult;
            }
        }
        return {MessageType::INFO, InfoPayload{jsonCppToNlohmann(response)}};
    } else {
        return {MessageType::ERROR, ErrorPayload{response["message"].asString()}};
    }
}

// === Game ===

std::pair<MessageType, Payload> ProtocolAdapter::handleCreateGame(
    int fd, const std::string& challengeId) {
    
    auto it = fdToToken.find(fd);
    if (it == fdToToken.end()) {
        return {MessageType::ERROR, ErrorPayload{"Please login first"}};
    }
    
    Json::Value request;
    request["token"] = it->second;
    request["challenge_id"] = challengeId;
    
    Json::Value response = gameHandler.handleCreateGame(request);
    
    if (response["status"].asString() == "success") {
        GameStartPayload gsp;
        gsp.opponent = response["game"]["black_player_name"].asString();
        gsp.game_mode = response["game"]["time_control"].asString();
        return {MessageType::GAME_START, gsp};
    } else {
        return {MessageType::ERROR, ErrorPayload{response["message"].asString()}};
    }
}

std::pair<MessageType, Payload> ProtocolAdapter::handleMove(
    int fd, const MovePayload& p, const std::string& gameId) {
    
    auto it = fdToToken.find(fd);
    if (it == fdToToken.end()) {
        return {MessageType::ERROR, ErrorPayload{"Please login first"}};
    }
    
    Json::Value request;
    request["token"] = it->second;
    request["game_id"] = gameId;
    request["from"]["x"] = p.from.col; // col -> x
    request["from"]["y"] = p.from.row; // row -> y
    request["to"]["x"] = p.to.col;
    request["to"]["y"] = p.to.row;
    request["piece"] = p.piece;
    request["notation"] = "";
    request["xfen_after"] = "";
    request["time_taken"] = 0;
    
    Json::Value response = gameHandler.handleMakeMove(request);
    
    if (response["status"].asString() == "success") {
        // Forward move to opponent via existing logic
        return {MessageType::MOVE, p};
    } else {
        return {MessageType::INVALID_MOVE, InvalidMovePayload{response["message"].asString()}};
    }
}

std::pair<MessageType, Payload> ProtocolAdapter::handleResign(
    int fd, const std::string& gameId) {
    
    auto it = fdToToken.find(fd);
    if (it == fdToToken.end()) {
        return {MessageType::ERROR, ErrorPayload{"Please login first"}};
    }
    
    Json::Value request;
    request["token"] = it->second;
    request["game_id"] = gameId;
    
    Json::Value response = gameHandler.handleResign(request);
    
    if (response["status"].asString() == "success") {
        GameEndPayload gep;
        gep.win_side = response["result"].asString();
        return {MessageType::GAME_END, gep};
    } else {
        return {MessageType::ERROR, ErrorPayload{response["message"].asString()}};
    }
}

// === Utility ===

void ProtocolAdapter::disconnectClient(int fd) {
    fdToToken.erase(fd);
}

std::string ProtocolAdapter::getToken(int fd) {
    auto it = fdToToken.find(fd);
    return (it != fdToToken.end()) ? it->second : "";
}
