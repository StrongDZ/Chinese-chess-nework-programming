#include "../../include/friend/friend_service.h"
#include <regex>
#include <chrono>

using namespace std;

FriendService::FriendService(FriendRepository& repo) : repository(repo) {}

bool FriendService::isValidUsername(const string& username) {
    if (username.length() < 3 || username.length() > 20) return false;
    regex pattern("^[a-zA-Z0-9_]+$");
    return regex_match(username, pattern);
}

FriendResult FriendService::sendFriendRequest(const string& username, const string& friendUsername) {
    FriendResult res{false, "", nullopt, {}};

    if (!isValidUsername(username) || !isValidUsername(friendUsername)) {
        res.message = "Invalid username format";
        return res;
    }
    if (username == friendUsername) {
        res.message = "Cannot send friend request to yourself";
        return res;
    }
    if (!repository.userExists(username)) {
        res.message = "User not found";
        return res;
    }
    if (!repository.userExists(friendUsername)) {
        res.message = "Friend not found";
        return res;
    }

    // existing relation in either direction
    auto relForward = repository.findRelation(username, friendUsername);
    auto relBackward = repository.findRelation(friendUsername, username);
    if (relForward || relBackward) {
        string status = relForward ? relForward->status : relBackward->status;
        res.message = "Relationship already exists with status: " + status;
        return res;
    }

    // cannot send if blocked by target
    if (relBackward && relBackward->status == "blocked") {
        res.message = "Cannot send request";
        return res;
    }

    FriendRelation rel{};
    rel.user_name = username;
    rel.friend_name = friendUsername;
    rel.status = "pending";
    rel.created_at = chrono::system_clock::now();
    rel.games_played_together = 0;

    string id = repository.createRelation(rel);
    if (id.empty()) {
        res.message = "Failed to create friend request";
        return res;
    }

    rel.id = id;
    res.success = true;
    res.message = "Friend request sent";
    res.relation = rel;
    return res;
}

FriendResult FriendService::acceptFriendRequest(const string& username, const string& friendUsername) {
    FriendResult res{false, "", nullopt, {}};

    auto pending = repository.findRelation(friendUsername, username);
    if (!pending || pending->status != "pending") {
        res.message = "Friend request not found";
        return res;
    }

    // update pending to accepted
    if (!repository.updateStatus(friendUsername, username, "accepted", true, false)) {
        res.message = "Failed to accept friend request";
        return res;
    }

    // ensure reverse accepted entry
    FriendRelation reverse{};
    reverse.user_name = username;
    reverse.friend_name = friendUsername;
    reverse.status = "accepted";
    reverse.created_at = chrono::system_clock::now();
    reverse.accepted_at = reverse.created_at;
    reverse.games_played_together = 0;
    repository.createRelation(reverse);

    auto accepted = repository.findRelation(friendUsername, username);
    res.success = true;
    res.message = "Friend request accepted";
    res.relation = accepted.value_or(pending.value());
    return res;
}

FriendResult FriendService::declineFriendRequest(const string& username, const string& friendUsername) {
    FriendResult res{false, "", nullopt, {}};

    bool deleted = repository.deleteRelation(friendUsername, username, string("pending"));
    if (!deleted) {
        res.message = "Friend request not found";
        return res;
    }

    res.success = true;
    res.message = "Friend request declined";
    return res;
}

FriendResult FriendService::unfriend(const string& username, const string& friendUsername) {
    FriendResult res{false, "", nullopt, {}};

    bool del1 = repository.deleteRelation(username, friendUsername, string("accepted"));
    bool del2 = repository.deleteRelation(friendUsername, username, string("accepted"));

    if (!del1 && !del2) {
        res.message = "Friendship not found";
        return res;
    }

    res.success = true;
    res.message = "Unfriended successfully";
    return res;
}

FriendResult FriendService::blockUser(const string& username, const string& blockedUsername) {
    FriendResult res{false, "", nullopt, {}};

    if (username == blockedUsername) {
        res.message = "Cannot block yourself";
        return res;
    }

    // remove any relation both directions
    repository.deleteRelation(username, blockedUsername, nullopt);
    repository.deleteRelation(blockedUsername, username, nullopt);

    FriendRelation rel{};
    rel.user_name = username;
    rel.friend_name = blockedUsername;
    rel.status = "blocked";
    rel.created_at = chrono::system_clock::now();
    rel.blocked_at = rel.created_at;
    rel.games_played_together = 0;

    string id = repository.createRelation(rel);
    if (id.empty()) {
        res.message = "Failed to block user";
        return res;
    }

    rel.id = id;
    res.success = true;
    res.message = "User blocked";
    res.relation = rel;
    return res;
}

FriendResult FriendService::unblockUser(const string& username, const string& blockedUsername) {
    FriendResult res{false, "", nullopt, {}};

    bool deleted = repository.deleteRelation(username, blockedUsername, string("blocked"));
    if (!deleted) {
        res.message = "Block not found";
        return res;
    }

    res.success = true;
    res.message = "User unblocked";
    return res;
}

FriendResult FriendService::listFriends(const string& username) {
    FriendResult res{true, "Friends retrieved", nullopt, {}};
    res.relations = repository.findAccepted(username);
    return res;
}

FriendResult FriendService::listPendingReceived(const string& username) {
    FriendResult res{true, "Pending requests retrieved", nullopt, {}};
    res.relations = repository.findPendingReceived(username);
    return res;
}

FriendResult FriendService::listPendingSent(const string& username) {
    FriendResult res{true, "Sent requests retrieved", nullopt, {}};
    res.relations = repository.findPendingSent(username);
    return res;
}

FriendResult FriendService::listBlocked(const string& username) {
    FriendResult res{true, "Blocked users retrieved", nullopt, {}};
    res.relations = repository.findBlocked(username);
    return res;
}

FriendResult FriendService::searchFriends(const string& username, const string& searchQuery) {
    FriendResult res{true, "Search results", nullopt, {}};
    res.relations = repository.searchFriends(username, searchQuery);
    return res;
}

