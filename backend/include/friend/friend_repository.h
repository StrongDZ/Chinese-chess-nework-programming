#ifndef FRIEND_REPOSITORY_H
#define FRIEND_REPOSITORY_H

#include "database/mongodb_client.h"
#include <string>
#include <vector>
#include <optional>
#include <chrono>

struct FriendRelation {
    std::string id;                             // MongoDB _id as string
    std::string user_name;                      // owner side
    std::string friend_name;                    // counterpart username
    std::string status;                         // pending | accepted | blocked
    std::chrono::system_clock::time_point created_at;
    std::optional<std::chrono::system_clock::time_point> accepted_at;
    std::optional<std::chrono::system_clock::time_point> blocked_at;
    int games_played_together = 0;
};

class FriendRepository {
private:
    MongoDBClient& mongoClient;
    std::optional<FriendRelation> mapDocToRelation(const bsoncxx::document::view& doc);

public:
    explicit FriendRepository(MongoDBClient& mongo);

    bool userExists(const std::string& username);
    std::optional<FriendRelation> findRelation(const std::string& user, const std::string& friendName);
    std::string createRelation(const FriendRelation& relation);
    bool updateStatus(const std::string& user, const std::string& friendName, const std::string& newStatus,
                      bool setAcceptedTime = false, bool setBlockedTime = false);
    bool deleteRelation(const std::string& user, const std::string& friendName, const std::optional<std::string>& statusFilter = std::nullopt);

    // Queries
    std::vector<FriendRelation> findAccepted(const std::string& username);
    std::vector<FriendRelation> findPendingReceived(const std::string& username);
    std::vector<FriendRelation> findPendingSent(const std::string& username);
    std::vector<FriendRelation> findBlocked(const std::string& username);
    std::vector<FriendRelation> searchFriends(const std::string& username, const std::string& searchQuery);
    // Get all friend requests received (pending + accepted)
    std::vector<FriendRelation> findAllReceivedRequests(const std::string& username);
};

#endif
