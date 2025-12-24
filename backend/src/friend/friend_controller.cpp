#include "friend/friend_controller.h"

using namespace std;

FriendController::FriendController(FriendService &svc) : service(svc) {}

nlohmann::json
FriendController::handleSendFriendRequest(const nlohmann::json &request) {
  nlohmann::json resp;
  if (!request.contains("username") || !request.contains("friend_username")) {
    resp["status"] = "error";
    resp["message"] = "Missing required fields";
    return resp;
  }
  auto result =
      service.sendFriendRequest(request["username"].get<string>(),
                                request["friend_username"].get<string>());
  resp["status"] = result.success ? "success" : "error";
  resp["message"] = result.message;
  if (result.success) {
    resp["friend_username"] = request["friend_username"].get<string>();
  }
  return resp;
}

nlohmann::json
FriendController::handleAcceptFriendRequest(const nlohmann::json &request) {
  nlohmann::json resp;
  if (!request.contains("username") || !request.contains("friend_username")) {
    resp["status"] = "error";
    resp["message"] = "Missing required fields";
    return resp;
  }
  auto result =
      service.acceptFriendRequest(request["username"].get<string>(),
                                  request["friend_username"].get<string>());
  resp["status"] = result.success ? "success" : "error";
  resp["message"] = result.message;
  if (result.success) {
    resp["friend_username"] = request["friend_username"].get<string>();
  }
  return resp;
}

nlohmann::json
FriendController::handleDeclineFriendRequest(const nlohmann::json &request) {
  nlohmann::json resp;
  if (!request.contains("username") || !request.contains("friend_username")) {
    resp["status"] = "error";
    resp["message"] = "Missing required fields";
    return resp;
  }
  auto result =
      service.declineFriendRequest(request["username"].get<string>(),
                                   request["friend_username"].get<string>());
  resp["status"] = result.success ? "success" : "error";
  resp["message"] = result.message;
  return resp;
}

nlohmann::json FriendController::handleUnfriend(const nlohmann::json &request) {
  nlohmann::json resp;
  if (!request.contains("username") || !request.contains("friend_username")) {
    resp["status"] = "error";
    resp["message"] = "Missing required fields";
    return resp;
  }
  auto result = service.unfriend(request["username"].get<string>(),
                                 request["friend_username"].get<string>());
  resp["status"] = result.success ? "success" : "error";
  resp["message"] = result.message;
  return resp;
}

nlohmann::json
FriendController::handleBlockUser(const nlohmann::json &request) {
  nlohmann::json resp;
  if (!request.contains("username") || !request.contains("blocked_username")) {
    resp["status"] = "error";
    resp["message"] = "Missing required fields";
    return resp;
  }
  auto result = service.blockUser(request["username"].get<string>(),
                                  request["blocked_username"].get<string>());
  resp["status"] = result.success ? "success" : "error";
  resp["message"] = result.message;
  return resp;
}

nlohmann::json
FriendController::handleUnblockUser(const nlohmann::json &request) {
  nlohmann::json resp;
  if (!request.contains("username") || !request.contains("blocked_username")) {
    resp["status"] = "error";
    resp["message"] = "Missing required fields";
    return resp;
  }
  auto result = service.unblockUser(request["username"].get<string>(),
                                    request["blocked_username"].get<string>());
  resp["status"] = result.success ? "success" : "error";
  resp["message"] = result.message;
  return resp;
}

nlohmann::json
FriendController::handleListFriends(const nlohmann::json &request) {
  nlohmann::json resp;
  if (!request.contains("username")) {
    resp["status"] = "error";
    resp["message"] = "Missing username";
    return resp;
  }
  auto result = service.listFriends(request["username"].get<string>());
  resp["status"] = result.success ? "success" : "error";
  resp["message"] = result.message;
  nlohmann::json arr = nlohmann::json::array();
  for (const auto &rel : result.relations) {
    nlohmann::json item;
    item["friend_username"] = rel.friend_name;
    item["games_played_together"] = rel.games_played_together;
    arr.push_back(item);
  }
  resp["friends"] = arr;
  resp["count"] = static_cast<int>(arr.size());
  return resp;
}

nlohmann::json
FriendController::handleListPendingRequests(const nlohmann::json &request) {
  nlohmann::json resp;
  if (!request.contains("username")) {
    resp["status"] = "error";
    resp["message"] = "Missing username";
    return resp;
  }
  auto result = service.listPendingReceived(request["username"].get<string>());
  resp["status"] = result.success ? "success" : "error";
  resp["message"] = result.message;
  nlohmann::json arr = nlohmann::json::array();
  for (const auto &rel : result.relations) {
    nlohmann::json item;
    item["from_username"] = rel.user_name;
    arr.push_back(item);
  }
  resp["requests"] = arr;
  resp["count"] = static_cast<int>(arr.size());
  return resp;
}

nlohmann::json
FriendController::handleListSentRequests(const nlohmann::json &request) {
  nlohmann::json resp;
  if (!request.contains("username")) {
    resp["status"] = "error";
    resp["message"] = "Missing username";
    return resp;
  }
  auto result = service.listPendingSent(request["username"].get<string>());
  resp["status"] = result.success ? "success" : "error";
  resp["message"] = result.message;
  nlohmann::json arr = nlohmann::json::array();
  for (const auto &rel : result.relations) {
    nlohmann::json item;
    item["to_username"] = rel.friend_name;
    arr.push_back(item);
  }
  resp["requests"] = arr;
  resp["count"] = static_cast<int>(arr.size());
  return resp;
}

nlohmann::json
FriendController::handleListBlockedUsers(const nlohmann::json &request) {
  nlohmann::json resp;
  if (!request.contains("username")) {
    resp["status"] = "error";
    resp["message"] = "Missing username";
    return resp;
  }
  auto result = service.listBlocked(request["username"].get<string>());
  resp["status"] = result.success ? "success" : "error";
  resp["message"] = result.message;
  nlohmann::json arr = nlohmann::json::array();
  for (const auto &rel : result.relations) {
    nlohmann::json item;
    item["blocked_username"] = rel.friend_name;
    arr.push_back(item);
  }
  resp["blocked_users"] = arr;
  resp["count"] = static_cast<int>(arr.size());
  return resp;
}

nlohmann::json
FriendController::handleSearchFriends(const nlohmann::json &request) {
  nlohmann::json resp;
  if (!request.contains("username") || !request.contains("search_query")) {
    resp["status"] = "error";
    resp["message"] = "Missing required fields";
    return resp;
  }
  auto result = service.searchFriends(request["username"].get<string>(),
                                      request["search_query"].get<string>());
  resp["status"] = result.success ? "success" : "error";
  resp["message"] = result.message;
  nlohmann::json arr = nlohmann::json::array();
  for (const auto &rel : result.relations) {
    nlohmann::json item;
    item["friend_username"] = rel.friend_name;
    arr.push_back(item);
  }
  resp["results"] = arr;
  resp["count"] = static_cast<int>(arr.size());
  return resp;
}
