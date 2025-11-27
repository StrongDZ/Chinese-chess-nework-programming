#ifndef FRIEND_HANDLER_H
#define FRIEND_HANDLER_H

#include "../database/mongodb_client.h"
#include <json/json.h>
#include <string>

/**
 * FriendHandler - Xử lý quan hệ bạn bè
 * 
 * Chức năng:
 * - handleSendFriendRequest: Gửi lời mời kết bạn
 * - handleAcceptFriendRequest: Chấp nhận lời mời
 * - handleDeclineFriendRequest: Từ chối lời mời
 * - handleUnfriend: Hủy kết bạn
 * - handleBlockUser: Chặn người dùng
 * - handleUnblockUser: Bỏ chặn
 * - handleListFriends: Danh sách bạn bè
 * - handleListPendingRequests: Danh sách lời mời đang chờ
 * - handleListBlockedUsers: Danh sách người bị chặn
 * - handleSearchFriends: Tìm kiếm bạn bè theo tên
 */
class FriendHandler {
private:
    MongoDBClient& mongoClient;
    
    // Helper: Lấy userId từ username
    std::string getUserIdFromUsername(const std::string& username);
    
    // Helper: Check relationship exists
    bool relationshipExists(const std::string& userId, const std::string& friendId);
    
    // Helper: Get relationship status
    std::string getRelationshipStatus(const std::string& userId, const std::string& friendId);
    
    // Helper: Create bidirectional friendship (khi accept)
    bool createBidirectionalFriendship(const std::string& userId1, const std::string& friendId1,
                                      const std::string& username1, const std::string& username2);

public:
    explicit FriendHandler(MongoDBClient& mongo);
    
    // Gửi lời mời kết bạn
    Json::Value handleSendFriendRequest(const Json::Value& request);
    
    // Chấp nhận lời mời kết bạn
    Json::Value handleAcceptFriendRequest(const Json::Value& request);
    
    // Từ chối lời mời kết bạn
    Json::Value handleDeclineFriendRequest(const Json::Value& request);
    
    // Hủy kết bạn (unfriend)
    Json::Value handleUnfriend(const Json::Value& request);
    
    // Chặn người dùng
    Json::Value handleBlockUser(const Json::Value& request);
    
    // Bỏ chặn người dùng
    Json::Value handleUnblockUser(const Json::Value& request);
    
    // Danh sách bạn bè (status = accepted)
    Json::Value handleListFriends(const Json::Value& request);
    
    // Danh sách lời mời đang chờ (received)
    Json::Value handleListPendingRequests(const Json::Value& request);
    
    // Danh sách lời mời đã gửi (sent)
    Json::Value handleListSentRequests(const Json::Value& request);
    
    // Danh sách người bị chặn
    Json::Value handleListBlockedUsers(const Json::Value& request);
    
    // Tìm kiếm bạn bè theo tên
    Json::Value handleSearchFriends(const Json::Value& request);
};

#endif
