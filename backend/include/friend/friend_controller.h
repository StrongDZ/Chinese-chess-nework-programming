#ifndef FRIEND_CONTROLLER_H
#define FRIEND_CONTROLLER_H

#include "friend_service.h"
#include <json/json.h>

class FriendController {
private:
    FriendService& service;

public:
    explicit FriendController(FriendService& svc);

    Json::Value handleSendFriendRequest(const Json::Value& request);
    Json::Value handleAcceptFriendRequest(const Json::Value& request);
    Json::Value handleDeclineFriendRequest(const Json::Value& request);
    Json::Value handleUnfriend(const Json::Value& request);
    Json::Value handleBlockUser(const Json::Value& request);
    Json::Value handleUnblockUser(const Json::Value& request);
    Json::Value handleListFriends(const Json::Value& request);
    Json::Value handleListPendingRequests(const Json::Value& request);
    Json::Value handleListSentRequests(const Json::Value& request);
    Json::Value handleListBlockedUsers(const Json::Value& request);
    Json::Value handleSearchFriends(const Json::Value& request);
};

#endif
