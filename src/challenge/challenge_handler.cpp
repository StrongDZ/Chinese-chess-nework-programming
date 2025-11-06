#include "../../include/challenge/challenge_handler.h"
#include <mongocxx/client.hpp>
#include <bsoncxx/builder/stream/document.hpp>
#include <bsoncxx/json.hpp>
#include <chrono>
#include <vector>

using namespace std;
using bsoncxx::builder::stream::document;
using bsoncxx::builder::stream::finalize;

ChallengeHandler::ChallengeHandler(MongoDBClient& mongo, RedisClient& redis)
    : mongoClient(mongo), redisClient(redis) {}

// Lấy userId từ token
string ChallengeHandler::getUserIdFromToken(const string& token) {
    return redisClient.getSession(token);
}

// Validate time_control (bullet, blitz, classical)
bool ChallengeHandler::isValidTimeControl(const string& timeControl) {
    return timeControl == "bullet" || timeControl == "blitz" || timeControl == "classical";
}

// Validate rated flag
bool ChallengeHandler::isValidRated(bool rated) {
    return true; // Luôn hợp lệ
}

// TẠO THÁCH ĐẤU MỚI
Json::Value ChallengeHandler::handleCreateChallenge(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate token
        if (!request.isMember("token")) {
            response["success"] = false;
            response["error"] = "Token required";
            return response;
        }
        
        string token = request["token"].asString();
        string challengerId = getUserIdFromToken(token);
        
        if (challengerId.empty()) {
            response["success"] = false;
            response["error"] = "Invalid or expired token";
            return response;
        }
        
        // 2. Lấy thông tin thách đấu
        if (!request.isMember("time_control")) {
            response["success"] = false;
            response["error"] = "time_control required";
            return response;
        }
        
        string timeControl = request["time_control"].asString();
        bool isRated = request.get("rated", true).asBool();
        
        // Optional: opponent_id (nếu challenge specific user)
        string opponentId = request.get("opponent_id", "").asString();
        
        // 3. Validate
        if (!isValidTimeControl(timeControl)) {
            response["success"] = false;
            response["error"] = "Invalid time_control (must be bullet/blitz/classical)";
            return response;
        }
        
        // 4. Check nếu challenger đã có challenge pending
        auto db = mongoClient.getDatabase();
        auto challenges = db["challenges"];
        
        auto existingChallenge = challenges.find_one(
            document{} 
                << "challenger_id" << bsoncxx::oid(challengerId)
                << "status" << "pending"
                << finalize
        );
        
        if (existingChallenge) {
            response["success"] = false;
            response["error"] = "You already have a pending challenge";
            return response;
        }
        
        // 5. Nếu có opponent_id, check user tồn tại
        if (!opponentId.empty()) {
            auto users = db["users"];
            auto opponent = users.find_one(
                document{} << "_id" << bsoncxx::oid(opponentId) << finalize
            );
            
            if (!opponent) {
                response["success"] = false;
                response["error"] = "Opponent not found";
                return response;
            }
            
            // Check opponent không phải chính mình
            if (opponentId == challengerId) {
                response["success"] = false;
                response["error"] = "Cannot challenge yourself";
                return response;
            }
        }
        
        // 6. Tạo challenge document
        auto now = chrono::system_clock::now();
        document challengeBuilder{};
        
        challengeBuilder
            << "challenger_id" << bsoncxx::oid(challengerId)
            << "time_control" << timeControl
            << "rated" << isRated
            << "status" << "pending"
            << "created_at" << bsoncxx::types::b_date{now};
        
        if (!opponentId.empty()) {
            challengeBuilder << "opponent_id" << bsoncxx::oid(opponentId);
        }
        
        auto challengeDoc = challengeBuilder << finalize;
        
        // 7. Insert vào database
        auto insertResult = challenges.insert_one(challengeDoc.view());
        if (!insertResult) {
            response["success"] = false;
            response["error"] = "Failed to create challenge";
            return response;
        }
        
        string challengeId = insertResult->inserted_id().get_oid().value.to_string();
        
        // 9. Return success
        response["success"] = true;
        response["message"] = "Challenge created successfully";
        response["data"]["challenge_id"] = challengeId;
        response["data"]["time_control"] = timeControl;
        response["data"]["rated"] = isRated;
        if (!opponentId.empty()) {
            response["data"]["opponent_id"] = opponentId;
        }
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Create challenge error: ") + e.what();
    }
    
    return response;
}

// HỦY THÁCH ĐẤU
Json::Value ChallengeHandler::handleCancelChallenge(const Json::Value& request) {
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
        
        // 3. Check quyền hủy (chỉ challenger mới hủy được)
        string challengerId = challenge["challenger_id"].get_oid().value.to_string();
        if (challengerId != userId) {
            response["success"] = false;
            response["error"] = "Only challenger can cancel the challenge";
            return response;
        }
        
        // 4. Check status
        string status = string(challenge["status"].get_string().value);
        if (status != "pending") {
            response["success"] = false;
            response["error"] = "Can only cancel pending challenges";
            return response;
        }
        
        // 5. Update status = cancelled
        auto result = challenges.update_one(
            document{} << "_id" << bsoncxx::oid(challengeId) << finalize,
            document{} << "$set" << bsoncxx::builder::stream::open_document
                      << "status" << "cancelled"
                      << bsoncxx::builder::stream::close_document
                      << finalize
        );
        
        if (!result || result->modified_count() == 0) {
            response["success"] = false;
            response["error"] = "Failed to cancel challenge";
            return response;
        }
        
        response["success"] = true;
        response["message"] = "Challenge cancelled successfully";
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Cancel challenge error: ") + e.what();
    }
    
    return response;
}

// CHẤP NHẬN THÁCH ĐẤU
Json::Value ChallengeHandler::handleAcceptChallenge(const Json::Value& request) {
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
        
        // 3. Check status
        string status = string(challenge["status"].get_string().value);
        if (status != "pending") {
            response["success"] = false;
            response["error"] = "Challenge is no longer pending";
            return response;
        }
        
        // 4. Check không phải challenger
        string challengerId = challenge["challenger_id"].get_oid().value.to_string();
        if (challengerId == userId) {
            response["success"] = false;
            response["error"] = "Cannot accept your own challenge";
            return response;
        }
        
        // 5. Nếu có opponent_id, check phải đúng người
        if (challenge["opponent_id"]) {
            string opponentId = challenge["opponent_id"].get_oid().value.to_string();
            if (opponentId != userId) {
                response["success"] = false;
                response["error"] = "This challenge is not for you";
                return response;
            }
        }
        
        // 6. Update status = accepted, thêm opponent_id
        auto now = chrono::system_clock::now();
        auto result = challenges.update_one(
            document{} << "_id" << bsoncxx::oid(challengeId) << finalize,
            document{} << "$set" << bsoncxx::builder::stream::open_document
                      << "status" << "accepted"
                      << "opponent_id" << bsoncxx::oid(userId)
                      << "accepted_at" << bsoncxx::types::b_date{now}
                      << bsoncxx::builder::stream::close_document
                      << finalize
        );
        
        if (!result || result->modified_count() == 0) {
            response["success"] = false;
            response["error"] = "Failed to accept challenge";
            return response;
        }
        
        // 7. Return success (game sẽ được tạo ở GameHandler)
        response["success"] = true;
        response["message"] = "Challenge accepted successfully";
        response["data"]["challenge_id"] = challengeId;
        response["data"]["challenger_id"] = challengerId;
        response["data"]["opponent_id"] = userId;
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Accept challenge error: ") + e.what();
    }
    
    return response;
}

// TỪ CHỐI THÁCH ĐẤU
Json::Value ChallengeHandler::handleDeclineChallenge(const Json::Value& request) {
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
        
        // 3. Check status
        string status = string(challenge["status"].get_string().value);
        if (status != "pending") {
            response["success"] = false;
            response["error"] = "Challenge is no longer pending";
            return response;
        }
        
        // 4. Check không phải challenger
        string challengerId = challenge["challenger_id"].get_oid().value.to_string();
        if (challengerId == userId) {
            response["success"] = false;
            response["error"] = "Use cancel instead of decline for your own challenge";
            return response;
        }
        
        // 5. Update status = declined
        auto result = challenges.update_one(
            document{} << "_id" << bsoncxx::oid(challengeId) << finalize,
            document{} << "$set" << bsoncxx::builder::stream::open_document
                      << "status" << "declined"
                      << bsoncxx::builder::stream::close_document
                      << finalize
        );
        
        if (!result || result->modified_count() == 0) {
            response["success"] = false;
            response["error"] = "Failed to decline challenge";
            return response;
        }
        
        response["success"] = true;
        response["message"] = "Challenge declined successfully";
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("Decline challenge error: ") + e.what();
    }
    
    return response;
}

// LẤY DANH SÁCH THÁCH ĐẤU
Json::Value ChallengeHandler::handleListChallenges(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate token
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
        
        // 2. Xác định filter
        string filter = request.get("filter", "all").asString(); // all, my, for_me
        
        auto db = mongoClient.getDatabase();
        auto challenges = db["challenges"];
        
        // 3. Build query
        document queryBuilder{};
        queryBuilder << "status" << "pending";
        
        if (filter == "my") {
            // Challenges tôi tạo
            queryBuilder << "challenger_id" << bsoncxx::oid(userId);
        } else if (filter == "for_me") {
            // Challenges dành cho tôi (hoặc public)
            queryBuilder << "$or" << bsoncxx::builder::stream::open_array
                        << bsoncxx::builder::stream::open_document
                            << "opponent_id" << bsoncxx::oid(userId)
                        << bsoncxx::builder::stream::close_document
                        << bsoncxx::builder::stream::open_document
                            << "opponent_id" << bsoncxx::builder::stream::open_document
                                << "$exists" << false
                            << bsoncxx::builder::stream::close_document
                        << bsoncxx::builder::stream::close_document
                        << bsoncxx::builder::stream::close_array;
        }
        
        auto query = queryBuilder << finalize;
        
        // 4. Query database
        auto cursor = challenges.find(query.view());
        
        response["success"] = true;
        response["data"] = Json::Value(Json::arrayValue);
        
        // 5. Build response
        for (auto&& doc : cursor) {
            Json::Value challengeJson;
            challengeJson["challenge_id"] = doc["_id"].get_oid().value.to_string();
            challengeJson["challenger_id"] = doc["challenger_id"].get_oid().value.to_string();
            challengeJson["time_control"] = string(doc["time_control"].get_string().value);
            challengeJson["rated"] = doc["rated"].get_bool().value;
            challengeJson["status"] = string(doc["status"].get_string().value);
            
            if (doc["opponent_id"]) {
                challengeJson["opponent_id"] = doc["opponent_id"].get_oid().value.to_string();
            }
            
            response["data"].append(challengeJson);
        }
        
    } catch (const exception& e) {
        response["success"] = false;
        response["error"] = string("List challenges error: ") + e.what();
    }
    
    return response;
}
