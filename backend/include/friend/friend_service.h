#ifndef FRIEND_SERVICE_H
#define FRIEND_SERVICE_H

#include "friend_repository.h"
#include <string>
#include <vector>
#include <optional>

struct FriendResult {
    bool success;
    std::string message;
    std::optional<FriendRelation> relation;
    std::vector<FriendRelation> relations;
};

class FriendService {
private:
    FriendRepository& repository;
    bool isValidUsername(const std::string& username);

public:
    explicit FriendService(FriendRepository& repo);

    FriendResult sendFriendRequest(const std::string& username, const std::string& friendUsername);
    FriendResult acceptFriendRequest(const std::string& username, const std::string& friendUsername);
    FriendResult declineFriendRequest(const std::string& username, const std::string& friendUsername);
    FriendResult unfriend(const std::string& username, const std::string& friendUsername);
    FriendResult blockUser(const std::string& username, const std::string& blockedUsername);
    FriendResult unblockUser(const std::string& username, const std::string& blockedUsername);
    FriendResult listFriends(const std::string& username);
    FriendResult listPendingReceived(const std::string& username);
    FriendResult listPendingSent(const std::string& username);
    FriendResult listBlocked(const std::string& username);
    FriendResult searchFriends(const std::string& username, const std::string& searchQuery);
};

#endif
