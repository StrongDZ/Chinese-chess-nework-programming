#include "../../include/friend/friend_handler.h"
#include <mongocxx/client.hpp>
#include <bsoncxx/builder/basic/document.hpp>
#include <bsoncxx/json.hpp>
#include <chrono>

using namespace std;
using bsoncxx::builder::basic::make_document;
using bsoncxx::builder::basic::kvp;

FriendHandler::FriendHandler(MongoDBClient& mongo)
    : mongoClient(mongo) {}

// Helper: Lấy userId từ username
string FriendHandler::getUserIdFromUsername(const string& username) {
    auto db = mongoClient.getDatabase();
    auto users = db["users"];
    
    auto user = users.find_one(make_document(kvp("username", username)));
    
    if (!user) return "";
    
    return user->view()["_id"].get_oid().value.to_string();
}

// Helper: Check relationship exists
bool FriendHandler::relationshipExists(const string& userId, const string& friendId) {
    auto db = mongoClient.getDatabase();
    auto friends = db["friends"];
    
    auto doc = friends.find_one(
        make_document(
            kvp("user_id", bsoncxx::oid(userId)),
            kvp("friend_id", bsoncxx::oid(friendId))
        )
    );
    
    return doc.has_value();
}

// Helper: Get relationship status
string FriendHandler::getRelationshipStatus(const string& userId, const string& friendId) {
    auto db = mongoClient.getDatabase();
    auto friends = db["friends"];
    
    auto doc = friends.find_one(
        make_document(
            kvp("user_id", bsoncxx::oid(userId)),
            kvp("friend_id", bsoncxx::oid(friendId))
        )
    );
    
    if (!doc) return "none";
    
    return string(doc->view()["status"].get_string().value);
}

// Helper: Create bidirectional friendship
bool FriendHandler::createBidirectionalFriendship(
    const string& userId1, const string& friendId1,
    const string& username1, const string& username2) {
    
    auto db = mongoClient.getDatabase();
    auto friends = db["friends"];
    auto now = chrono::system_clock::now();
    
    // Document: user1 → user2
        auto doc1 = make_document(
            kvp("user_id", bsoncxx::oid(userId1)),
            kvp("friend_id", bsoncxx::oid(friendId1)),
            kvp("status", "accepted"),
            kvp("created_at", bsoncxx::types::b_date{now}),
            kvp("accepted_at", bsoncxx::types::b_date{now}),
            kvp("blocked_at", bsoncxx::types::b_null{}),
            kvp("user_name", username1),
            kvp("friend_name", username2),
            kvp("games_played_together", 0)
        );    // Document: user2 → user1 (reverse)
        auto doc2 = make_document(
            kvp("user_id", bsoncxx::oid(friendId1)),
            kvp("friend_id", bsoncxx::oid(userId1)),
            kvp("status", "accepted"),
            kvp("created_at", bsoncxx::types::b_date{now}),
            kvp("accepted_at", bsoncxx::types::b_date{now}),
            kvp("blocked_at", bsoncxx::types::b_null{}),
            kvp("user_name", username2),
            kvp("friend_name", username1),
            kvp("games_played_together", 0)
        );    try {
        friends.insert_one(doc1.view());
        friends.insert_one(doc2.view());
        return true;
    } catch (const exception& e) {
        return false;
    }
}

// 1. Send Friend Request
Json::Value FriendHandler::handleSendFriendRequest(const Json::Value& request) {
    Json::Value response;
    
    try {
        if (!request.isMember("username") || !request.isMember("friend_username")) {
            response["status"] = "error";
            response["message"] = "Missing required fields";
            return response;
        }
        
        string username = request["username"].asString();
        string friendUsername = request["friend_username"].asString();
        
        if (username == friendUsername) {
            response["status"] = "error";
            response["message"] = "Cannot send friend request to yourself";
            return response;
        }
        
        // Get user IDs
        string userId = getUserIdFromUsername(username);
        string friendId = getUserIdFromUsername(friendUsername);
        
        if (userId.empty()) {
            response["status"] = "error";
            response["message"] = "User not found";
            return response;
        }
        
        if (friendId.empty()) {
            response["status"] = "error";
            response["message"] = "Friend not found";
            return response;
        }
        
        // Check existing relationship
        if (relationshipExists(userId, friendId)) {
            string status = getRelationshipStatus(userId, friendId);
            response["status"] = "error";
            response["message"] = "Relationship already exists with status: " + status;
            return response;
        }
        
        // Check if friend has blocked user
        if (getRelationshipStatus(friendId, userId) == "blocked") {
            response["status"] = "error";
            response["message"] = "Cannot send request";
            return response;
        }
        
        // Create friend request
        auto db = mongoClient.getDatabase();
        auto friends = db["friends"];
        auto now = chrono::system_clock::now();
        
            auto doc = make_document(
            kvp("user_id", bsoncxx::oid(userId)),
            kvp("friend_id", bsoncxx::oid(friendId)),
            kvp("status", "pending"),
            kvp("created_at", bsoncxx::types::b_date{now}),
            kvp("accepted_at", bsoncxx::types::b_null{}),
            kvp("blocked_at", bsoncxx::types::b_null{}),
            kvp("user_name", username),
            kvp("friend_name", friendUsername),
            kvp("games_played_together", 0)
        );        friends.insert_one(doc.view());
        
        response["status"] = "success";
        response["message"] = "Friend request sent";
        response["friend_username"] = friendUsername;
        
    } catch (const exception& e) {
        response["status"] = "error";
        response["message"] = string("Error: ") + e.what();
    }
    
    return response;
}

// 2. Accept Friend Request
Json::Value FriendHandler::handleAcceptFriendRequest(const Json::Value& request) {
    Json::Value response;
    
    try {
        if (!request.isMember("username") || !request.isMember("friend_username")) {
            response["status"] = "error";
            response["message"] = "Missing required fields";
            return response;
        }
        
        string username = request["username"].asString();
        string friendUsername = request["friend_username"].asString();
        
        string userId = getUserIdFromUsername(username);
        string friendId = getUserIdFromUsername(friendUsername);
        
        if (userId.empty() || friendId.empty()) {
            response["status"] = "error";
            response["message"] = "User not found";
            return response;
        }
        
        // Check pending request exists (friend sent to user)
        auto db = mongoClient.getDatabase();
        auto friends = db["friends"];
        
        auto doc = friends.find_one(
            make_document(
                kvp("user_id", bsoncxx::oid(friendId)),
                kvp("friend_id", bsoncxx::oid(userId)),
                kvp("status", "pending")
            )
        );
        
        if (!doc) {
            response["status"] = "error";
            response["message"] = "Friend request not found";
            return response;
        }
        
        auto now = chrono::system_clock::now();
        
        // Update existing request to accepted
        friends.update_one(
            make_document(
                kvp("user_id", bsoncxx::oid(friendId)),
                kvp("friend_id", bsoncxx::oid(userId))
            ),
            make_document(
                kvp("$set", make_document(
                    kvp("status", "accepted"),
                    kvp("accepted_at", bsoncxx::types::b_date{now})
                ))
            )
        );
        
        // Create reverse relationship (bidirectional)
            auto reverseDoc = make_document(
            kvp("user_id", bsoncxx::oid(userId)),
            kvp("friend_id", bsoncxx::oid(friendId)),
            kvp("status", "accepted"),
            kvp("created_at", bsoncxx::types::b_date{now}),
            kvp("accepted_at", bsoncxx::types::b_date{now}),
            kvp("blocked_at", bsoncxx::types::b_null{}),
            kvp("user_name", username),
            kvp("friend_name", friendUsername),
            kvp("games_played_together", 0)
        );        friends.insert_one(reverseDoc.view());
        
        response["status"] = "success";
        response["message"] = "Friend request accepted";
        response["friend_username"] = friendUsername;
        
    } catch (const exception& e) {
        response["status"] = "error";
        response["message"] = string("Error: ") + e.what();
    }
    
    return response;
}

// 3. Decline Friend Request
Json::Value FriendHandler::handleDeclineFriendRequest(const Json::Value& request) {
    Json::Value response;
    
    try {
        if (!request.isMember("username") || !request.isMember("friend_username")) {
            response["status"] = "error";
            response["message"] = "Missing required fields";
            return response;
        }
        
        string username = request["username"].asString();
        string friendUsername = request["friend_username"].asString();
        
        string userId = getUserIdFromUsername(username);
        string friendId = getUserIdFromUsername(friendUsername);
        
        if (userId.empty() || friendId.empty()) {
            response["status"] = "error";
            response["message"] = "User not found";
            return response;
        }
        
        // Delete pending request
        auto db = mongoClient.getDatabase();
        auto friends = db["friends"];
        
        auto result = friends.delete_one(
            make_document(
                kvp("user_id", bsoncxx::oid(friendId)),
                kvp("friend_id", bsoncxx::oid(userId)),
                kvp("status", "pending")
            )
        );
        
        if (result && result->deleted_count() > 0) {
            response["status"] = "success";
            response["message"] = "Friend request declined";
        } else {
            response["status"] = "error";
            response["message"] = "Friend request not found";
        }
        
    } catch (const exception& e) {
        response["status"] = "error";
        response["message"] = string("Error: ") + e.what();
    }
    
    return response;
}

// 4. Unfriend
Json::Value FriendHandler::handleUnfriend(const Json::Value& request) {
    Json::Value response;
    
    try {
        if (!request.isMember("username") || !request.isMember("friend_username")) {
            response["status"] = "error";
            response["message"] = "Missing required fields";
            return response;
        }
        
        string username = request["username"].asString();
        string friendUsername = request["friend_username"].asString();
        
        string userId = getUserIdFromUsername(username);
        string friendId = getUserIdFromUsername(friendUsername);
        
        if (userId.empty() || friendId.empty()) {
            response["status"] = "error";
            response["message"] = "User not found";
            return response;
        }
        
        auto db = mongoClient.getDatabase();
        auto friends = db["friends"];
        
        // Delete both directions
        friends.delete_one(
            make_document(
                kvp("user_id", bsoncxx::oid(userId)),
                kvp("friend_id", bsoncxx::oid(friendId))
            )
        );
        
        friends.delete_one(
            make_document(
                kvp("user_id", bsoncxx::oid(friendId)),
                kvp("friend_id", bsoncxx::oid(userId))
            )
        );
        
        response["status"] = "success";
        response["message"] = "Unfriended successfully";
        
    } catch (const exception& e) {
        response["status"] = "error";
        response["message"] = string("Error: ") + e.what();
    }
    
    return response;
}

// 5. Block User
Json::Value FriendHandler::handleBlockUser(const Json::Value& request) {
    Json::Value response;
    
    try {
        if (!request.isMember("username") || !request.isMember("blocked_username")) {
            response["status"] = "error";
            response["message"] = "Missing required fields";
            return response;
        }
        
        string username = request["username"].asString();
        string blockedUsername = request["blocked_username"].asString();
        
        string userId = getUserIdFromUsername(username);
        string blockedId = getUserIdFromUsername(blockedUsername);
        
        if (userId.empty() || blockedId.empty()) {
            response["status"] = "error";
            response["message"] = "User not found";
            return response;
        }
        
        auto db = mongoClient.getDatabase();
        auto friends = db["friends"];
        auto now = chrono::system_clock::now();
        
        // Delete any existing relationship
        friends.delete_one(
            make_document(
                kvp("user_id", bsoncxx::oid(userId)),
                kvp("friend_id", bsoncxx::oid(blockedId))
            )
        );
        
        // Create block relationship
            auto doc = make_document(
            kvp("user_id", bsoncxx::oid(userId)),
            kvp("friend_id", bsoncxx::oid(blockedId)),
            kvp("status", "blocked"),
            kvp("created_at", bsoncxx::types::b_date{now}),
            kvp("accepted_at", bsoncxx::types::b_null{}),
            kvp("blocked_at", bsoncxx::types::b_date{now}),
            kvp("user_name", username),
            kvp("friend_name", blockedUsername),
            kvp("games_played_together", 0)
        );        friends.insert_one(doc.view());
        
        response["status"] = "success";
        response["message"] = "User blocked";
        
    } catch (const exception& e) {
        response["status"] = "error";
        response["message"] = string("Error: ") + e.what();
    }
    
    return response;
}

// 6. Unblock User
Json::Value FriendHandler::handleUnblockUser(const Json::Value& request) {
    Json::Value response;
    
    try {
        if (!request.isMember("username") || !request.isMember("blocked_username")) {
            response["status"] = "error";
            response["message"] = "Missing required fields";
            return response;
        }
        
        string username = request["username"].asString();
        string blockedUsername = request["blocked_username"].asString();
        
        string userId = getUserIdFromUsername(username);
        string blockedId = getUserIdFromUsername(blockedUsername);
        
        if (userId.empty() || blockedId.empty()) {
            response["status"] = "error";
            response["message"] = "User not found";
            return response;
        }
        
        auto db = mongoClient.getDatabase();
        auto friends = db["friends"];
        
        auto result = friends.delete_one(
            make_document(
                kvp("user_id", bsoncxx::oid(userId)),
                kvp("friend_id", bsoncxx::oid(blockedId)),
                kvp("status", "blocked")
            )
        );
        
        if (result && result->deleted_count() > 0) {
            response["status"] = "success";
            response["message"] = "User unblocked";
        } else {
            response["status"] = "error";
            response["message"] = "Block not found";
        }
        
    } catch (const exception& e) {
        response["status"] = "error";
        response["message"] = string("Error: ") + e.what();
    }
    
    return response;
}

// 7. List Friends
Json::Value FriendHandler::handleListFriends(const Json::Value& request) {
    Json::Value response;
    
    try {
        if (!request.isMember("username")) {
            response["status"] = "error";
            response["message"] = "Missing username";
            return response;
        }
        
        string username = request["username"].asString();
        string userId = getUserIdFromUsername(username);
        
        if (userId.empty()) {
            response["status"] = "error";
            response["message"] = "User not found";
            return response;
        }
        
        auto db = mongoClient.getDatabase();
        auto friends = db["friends"];
        
        auto cursor = friends.find(
            make_document(
                kvp("user_id", bsoncxx::oid(userId)),
                kvp("status", "accepted")
            )
        );
        
        Json::Value friendsList(Json::arrayValue);
        for (auto&& doc : cursor) {
            Json::Value friendData;
            friendData["friend_username"] = string(doc["friend_name"].get_string().value);
            friendData["friend_id"] = doc["friend_id"].get_oid().value.to_string();
                friendData["games_played_together"] = doc["games_played_together"].get_int32().value;
            
            friendsList.append(friendData);
        }
        
        response["status"] = "success";
        response["friends"] = friendsList;
        response["count"] = static_cast<int>(friendsList.size());
        
    } catch (const exception& e) {
        response["status"] = "error";
        response["message"] = string("Error: ") + e.what();
    }
    
    return response;
}

// 8. List Pending Requests (Received)
Json::Value FriendHandler::handleListPendingRequests(const Json::Value& request) {
    Json::Value response;
    
    try {
        if (!request.isMember("username")) {
            response["status"] = "error";
            response["message"] = "Missing username";
            return response;
        }
        
        string username = request["username"].asString();
        string userId = getUserIdFromUsername(username);
        
        if (userId.empty()) {
            response["status"] = "error";
            response["message"] = "User not found";
            return response;
        }
        
        auto db = mongoClient.getDatabase();
        auto friends = db["friends"];
        
        // Requests sent TO this user (friend_id = userId)
        auto cursor = friends.find(
            make_document(
                kvp("friend_id", bsoncxx::oid(userId)),
                kvp("status", "pending")
            )
        );
        
        Json::Value requestsList(Json::arrayValue);
        for (auto&& doc : cursor) {
                Json::Value reqData;
            reqData["from_username"] = string(doc["user_name"].get_string().value);
            reqData["from_user_id"] = doc["user_id"].get_oid().value.to_string();
            requestsList.append(reqData);
        }
        
        response["status"] = "success";
        response["requests"] = requestsList;
        response["count"] = static_cast<int>(requestsList.size());
        
    } catch (const exception& e) {
        response["status"] = "error";
        response["message"] = string("Error: ") + e.what();
    }
    
    return response;
}

// 9. List Sent Requests
Json::Value FriendHandler::handleListSentRequests(const Json::Value& request) {
    Json::Value response;
    
    try {
        if (!request.isMember("username")) {
            response["status"] = "error";
            response["message"] = "Missing username";
            return response;
        }
        
        string username = request["username"].asString();
        string userId = getUserIdFromUsername(username);
        
        if (userId.empty()) {
            response["status"] = "error";
            response["message"] = "User not found";
            return response;
        }
        
        auto db = mongoClient.getDatabase();
        auto friends = db["friends"];
        
        // Requests sent BY this user
        auto cursor = friends.find(
            make_document(
                kvp("user_id", bsoncxx::oid(userId)),
                kvp("status", "pending")
            )
        );
        
        Json::Value requestsList(Json::arrayValue);
        for (auto&& doc : cursor) {
                Json::Value reqData;
            reqData["to_username"] = string(doc["friend_name"].get_string().value);
            reqData["to_user_id"] = doc["friend_id"].get_oid().value.to_string();
            requestsList.append(reqData);
        }
        
        response["status"] = "success";
        response["requests"] = requestsList;
        response["count"] = static_cast<int>(requestsList.size());
        
    } catch (const exception& e) {
        response["status"] = "error";
        response["message"] = string("Error: ") + e.what();
    }
    
    return response;
}

// 10. List Blocked Users
Json::Value FriendHandler::handleListBlockedUsers(const Json::Value& request) {
    Json::Value response;
    
    try {
        if (!request.isMember("username")) {
            response["status"] = "error";
            response["message"] = "Missing username";
            return response;
        }
        
        string username = request["username"].asString();
        string userId = getUserIdFromUsername(username);
        
        if (userId.empty()) {
            response["status"] = "error";
            response["message"] = "User not found";
            return response;
        }
        
        auto db = mongoClient.getDatabase();
        auto friends = db["friends"];
        
        auto cursor = friends.find(
            make_document(
                kvp("user_id", bsoncxx::oid(userId)),
                kvp("status", "blocked")
            )
        );
        
        Json::Value blockedList(Json::arrayValue);
        for (auto&& doc : cursor) {
                Json::Value blockedData;
            blockedData["blocked_username"] = string(doc["friend_name"].get_string().value);
            blockedData["blocked_user_id"] = doc["friend_id"].get_oid().value.to_string();
            blockedList.append(blockedData);
        }
        
        response["status"] = "success";
        response["blocked_users"] = blockedList;
        response["count"] = static_cast<int>(blockedList.size());
        
    } catch (const exception& e) {
        response["status"] = "error";
        response["message"] = string("Error: ") + e.what();
    }
    
    return response;
}

// 11. Search Friends
Json::Value FriendHandler::handleSearchFriends(const Json::Value& request) {
    Json::Value response;
    
    try {
        if (!request.isMember("username") || !request.isMember("search_query")) {
            response["status"] = "error";
            response["message"] = "Missing required fields";
            return response;
        }
        
        string username = request["username"].asString();
        string searchQuery = request["search_query"].asString();
        
        string userId = getUserIdFromUsername(username);
        
        if (userId.empty()) {
            response["status"] = "error";
            response["message"] = "User not found";
            return response;
        }
        
        auto db = mongoClient.getDatabase();
        auto friends = db["friends"];
        
        // Search in friend names using regex
        auto cursor = friends.find(
            make_document(
                kvp("user_id", bsoncxx::oid(userId)),
                kvp("status", "accepted"),
                kvp("friend_name", make_document(
                    kvp("$regex", searchQuery),
                    kvp("$options", "i")
                ))
            )
        );
        
        Json::Value resultsList(Json::arrayValue);
        for (auto&& doc : cursor) {
            Json::Value friendData;
            friendData["friend_username"] = string(doc["friend_name"].get_string().value);
            friendData["friend_id"] = doc["friend_id"].get_oid().value.to_string();
            resultsList.append(friendData);
        }
        
        response["status"] = "success";
        response["results"] = resultsList;
        response["count"] = static_cast<int>(resultsList.size());
        
    } catch (const exception& e) {
        response["status"] = "error";
        response["message"] = string("Error: ") + e.what();
    }
    
    return response;
}
