#include "challenge/challenge_controller.h"
#include <chrono>
#include <iomanip>
#include <sstream>

using namespace std;

ChallengeController::ChallengeController(ChallengeService& svc) 
    : service(svc) {}

Json::Value ChallengeController::challengeToJson(const Challenge& challenge) const {
    Json::Value json;
    json["challenge_id"] = challenge.id;
    json["challenger_username"] = challenge.challenger_username;
    json["challenged_username"] = challenge.challenged_username;
    json["time_control"] = challenge.time_control;
    json["rated"] = challenge.rated;
    json["status"] = challenge.status;
    json["message"] = challenge.message;
    
    // Format created_at as ISO string
    auto createdTime = chrono::system_clock::to_time_t(challenge.created_at);
    stringstream ss;
    ss << put_time(localtime(&createdTime), "%Y-%m-%d %H:%M:%S");
    json["created_at"] = ss.str();
    
    if (challenge.game_id.has_value()) {
        json["game_id"] = challenge.game_id.value();
    }
    
    return json;
}

Json::Value ChallengeController::handleCreateChallenge(const Json::Value& request) {
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
        string message = request.get("message", "").asString();
        
        // 2. Call service
        auto result = service.createChallenge(challengerUsername, challengedUsername, 
                                               timeControl, rated, message);
        
        // 3. Build response
        if (result.success) {
            response["status"] = "success";
            response["message"] = result.message;
            response["challenge"] = challengeToJson(result.challenge.value());
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

Json::Value ChallengeController::handleCancelChallenge(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate required fields
        if (!request.isMember("username") || !request.isMember("challenge_id")) {
            response["status"] = "error";
            response["message"] = "Missing required fields: username, challenge_id";
            return response;
        }
        
        string username = request["username"].asString();
        string challengeId = request["challenge_id"].asString();
        
        // 2. Call service
        auto result = service.cancelChallenge(username, challengeId);
        
        // 3. Build response
        if (result.success) {
            response["status"] = "success";
            response["message"] = result.message;
            response["challenge"] = challengeToJson(result.challenge.value());
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

Json::Value ChallengeController::handleAcceptChallenge(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate required fields
        if (!request.isMember("username") || !request.isMember("challenge_id")) {
            response["status"] = "error";
            response["message"] = "Missing required fields: username, challenge_id";
            return response;
        }
        
        string username = request["username"].asString();
        string challengeId = request["challenge_id"].asString();
        
        // 2. Call service
        auto result = service.acceptChallenge(username, challengeId);
        
        // 3. Build response
        if (result.success) {
            response["status"] = "success";
            response["message"] = result.message;
            response["challenge"] = challengeToJson(result.challenge.value());
            response["next_step"] = "Create game session using this challenge data";
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

Json::Value ChallengeController::handleDeclineChallenge(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate required fields
        if (!request.isMember("username") || !request.isMember("challenge_id")) {
            response["status"] = "error";
            response["message"] = "Missing required fields: username, challenge_id";
            return response;
        }
        
        string username = request["username"].asString();
        string challengeId = request["challenge_id"].asString();
        
        // 2. Call service
        auto result = service.declineChallenge(username, challengeId);
        
        // 3. Build response
        if (result.success) {
            response["status"] = "success";
            response["message"] = result.message;
            response["challenge"] = challengeToJson(result.challenge.value());
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

Json::Value ChallengeController::handleListChallenges(const Json::Value& request) {
    Json::Value response;
    
    try {
        // 1. Validate required fields
        if (!request.isMember("username")) {
            response["status"] = "error";
            response["message"] = "Missing required field: username";
            return response;
        }
        
        string username = request["username"].asString();
        string filter = request.get("filter", "all").asString();
        
        // 2. Call service
        auto result = service.listChallenges(username, filter);
        
        // 3. Build response
        response["status"] = "success";
        response["message"] = result.message;
        
        Json::Value challengesList(Json::arrayValue);
        for (const auto& challenge : result.challenges) {
            challengesList.append(challengeToJson(challenge));
        }
        
        response["challenges"] = challengesList;
        response["count"] = static_cast<int>(result.challenges.size());
        
    } catch (const exception& e) {
        response["status"] = "error";
        response["message"] = string("Exception: ") + e.what();
    }
    
    return response;
}
