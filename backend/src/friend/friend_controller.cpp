#include "../../include/friend/friend_controller.h"

using namespace std;

FriendController::FriendController(FriendService& svc) : service(svc) {}

Json::Value FriendController::handleSendFriendRequest(const Json::Value& request) {
    Json::Value resp;
    if (!request.isMember("username") || !request.isMember("friend_username")) {
        resp["status"] = "error";
        resp["message"] = "Missing required fields";
        return resp;
    }
    auto result = service.sendFriendRequest(request["username"].asString(), request["friend_username"].asString());
    resp["status"] = result.success ? "success" : "error";
    resp["message"] = result.message;
    if (result.success) {
        resp["friend_username"] = request["friend_username"].asString();
    }
    return resp;
}

Json::Value FriendController::handleAcceptFriendRequest(const Json::Value& request) {
    Json::Value resp;
    if (!request.isMember("username") || !request.isMember("friend_username")) {
        resp["status"] = "error";
        resp["message"] = "Missing required fields";
        return resp;
    }
    auto result = service.acceptFriendRequest(request["username"].asString(), request["friend_username"].asString());
    resp["status"] = result.success ? "success" : "error";
    resp["message"] = result.message;
    if (result.success) {
        resp["friend_username"] = request["friend_username"].asString();
    }
    return resp;
}

Json::Value FriendController::handleDeclineFriendRequest(const Json::Value& request) {
    Json::Value resp;
    if (!request.isMember("username") || !request.isMember("friend_username")) {
        resp["status"] = "error";
        resp["message"] = "Missing required fields";
        return resp;
    }
    auto result = service.declineFriendRequest(request["username"].asString(), request["friend_username"].asString());
    resp["status"] = result.success ? "success" : "error";
    resp["message"] = result.message;
    return resp;
}

Json::Value FriendController::handleUnfriend(const Json::Value& request) {
    Json::Value resp;
    if (!request.isMember("username") || !request.isMember("friend_username")) {
        resp["status"] = "error";
        resp["message"] = "Missing required fields";
        return resp;
    }
    auto result = service.unfriend(request["username"].asString(), request["friend_username"].asString());
    resp["status"] = result.success ? "success" : "error";
    resp["message"] = result.message;
    return resp;
}

Json::Value FriendController::handleBlockUser(const Json::Value& request) {
    Json::Value resp;
    if (!request.isMember("username") || !request.isMember("blocked_username")) {
        resp["status"] = "error";
        resp["message"] = "Missing required fields";
        return resp;
    }
    auto result = service.blockUser(request["username"].asString(), request["blocked_username"].asString());
    resp["status"] = result.success ? "success" : "error";
    resp["message"] = result.message;
    return resp;
}

Json::Value FriendController::handleUnblockUser(const Json::Value& request) {
    Json::Value resp;
    if (!request.isMember("username") || !request.isMember("blocked_username")) {
        resp["status"] = "error";
        resp["message"] = "Missing required fields";
        return resp;
    }
    auto result = service.unblockUser(request["username"].asString(), request["blocked_username"].asString());
    resp["status"] = result.success ? "success" : "error";
    resp["message"] = result.message;
    return resp;
}

Json::Value FriendController::handleListFriends(const Json::Value& request) {
    Json::Value resp;
    if (!request.isMember("username")) {
        resp["status"] = "error";
        resp["message"] = "Missing username";
        return resp;
    }
    auto result = service.listFriends(request["username"].asString());
    resp["status"] = result.success ? "success" : "error";
    resp["message"] = result.message;
    Json::Value arr(Json::arrayValue);
    for (const auto& rel : result.relations) {
        Json::Value item;
        item["friend_username"] = rel.friend_name;
        item["games_played_together"] = rel.games_played_together;
        arr.append(item);
    }
    resp["friends"] = arr;
    resp["count"] = static_cast<int>(arr.size());
    return resp;
}

Json::Value FriendController::handleListPendingRequests(const Json::Value& request) {
    Json::Value resp;
    if (!request.isMember("username")) {
        resp["status"] = "error";
        resp["message"] = "Missing username";
        return resp;
    }
    auto result = service.listPendingReceived(request["username"].asString());
    resp["status"] = result.success ? "success" : "error";
    resp["message"] = result.message;
    Json::Value arr(Json::arrayValue);
    for (const auto& rel : result.relations) {
        Json::Value item;
        item["from_username"] = rel.user_name;
        arr.append(item);
    }
    resp["requests"] = arr;
    resp["count"] = static_cast<int>(arr.size());
    return resp;
}

Json::Value FriendController::handleListSentRequests(const Json::Value& request) {
    Json::Value resp;
    if (!request.isMember("username")) {
        resp["status"] = "error";
        resp["message"] = "Missing username";
        return resp;
    }
    auto result = service.listPendingSent(request["username"].asString());
    resp["status"] = result.success ? "success" : "error";
    resp["message"] = result.message;
    Json::Value arr(Json::arrayValue);
    for (const auto& rel : result.relations) {
        Json::Value item;
        item["to_username"] = rel.friend_name;
        arr.append(item);
    }
    resp["requests"] = arr;
    resp["count"] = static_cast<int>(arr.size());
    return resp;
}

Json::Value FriendController::handleListBlockedUsers(const Json::Value& request) {
    Json::Value resp;
    if (!request.isMember("username")) {
        resp["status"] = "error";
        resp["message"] = "Missing username";
        return resp;
    }
    auto result = service.listBlocked(request["username"].asString());
    resp["status"] = result.success ? "success" : "error";
    resp["message"] = result.message;
    Json::Value arr(Json::arrayValue);
    for (const auto& rel : result.relations) {
        Json::Value item;
        item["blocked_username"] = rel.friend_name;
        arr.append(item);
    }
    resp["blocked_users"] = arr;
    resp["count"] = static_cast<int>(arr.size());
    return resp;
}

Json::Value FriendController::handleSearchFriends(const Json::Value& request) {
    Json::Value resp;
    if (!request.isMember("username") || !request.isMember("search_query")) {
        resp["status"] = "error";
        resp["message"] = "Missing required fields";
        return resp;
    }
    auto result = service.searchFriends(request["username"].asString(), request["search_query"].asString());
    resp["status"] = result.success ? "success" : "error";
    resp["message"] = result.message;
    Json::Value arr(Json::arrayValue);
    for (const auto& rel : result.relations) {
        Json::Value item;
        item["friend_username"] = rel.friend_name;
        arr.append(item);
    }
    resp["results"] = arr;
    resp["count"] = static_cast<int>(arr.size());
    return resp;
}

