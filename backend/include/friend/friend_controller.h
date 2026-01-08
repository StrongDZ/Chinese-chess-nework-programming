#ifndef FRIEND_CONTROLLER_H
#define FRIEND_CONTROLLER_H

#include "friend_service.h"
#include <nlohmann/json.hpp>

class FriendController {
private:
  FriendService &service;

public:
  explicit FriendController(FriendService &svc);

  nlohmann::json handleSendFriendRequest(const nlohmann::json &request);
  nlohmann::json handleAcceptFriendRequest(const nlohmann::json &request);
  nlohmann::json handleDeclineFriendRequest(const nlohmann::json &request);
  nlohmann::json handleUnfriend(const nlohmann::json &request);
  nlohmann::json handleBlockUser(const nlohmann::json &request);
  nlohmann::json handleUnblockUser(const nlohmann::json &request);
  nlohmann::json handleListFriends(const nlohmann::json &request);
  nlohmann::json handleListPendingRequests(const nlohmann::json &request);
  nlohmann::json handleListSentRequests(const nlohmann::json &request);
  nlohmann::json handleListBlockedUsers(const nlohmann::json &request);
  nlohmann::json handleSearchFriends(const nlohmann::json &request);
  nlohmann::json handleListAllReceivedRequests(const nlohmann::json &request);
};

#endif
