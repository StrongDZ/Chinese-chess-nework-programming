#include "auth/auth_repository.h"
#include <bsoncxx/builder/stream/document.hpp>
#include <bsoncxx/json.hpp>
#include <chrono>

using namespace std;
using bsoncxx::builder::stream::document;
using bsoncxx::builder::stream::finalize;
using bsoncxx::builder::stream::open_document;
using bsoncxx::builder::stream::close_document;

AuthRepository::AuthRepository(MongoDBClient& mongo)
    : mongoClient(mongo) {}

// ============================================
// USER OPERATIONS (MongoDB)
// ============================================

optional<User> AuthRepository::findByUsername(const string& username) {
    auto db = mongoClient.getDatabase();
    auto users = db["users"];
    
    auto result = users.find_one(document{} << "username" << username << finalize);
    
    if (!result) {
        return nullopt;
    }
    
    auto view = result->view();
    User user;
    user.username = string(view["username"].get_string().value);
    user.password_hash = string(view["password_hash"].get_string().value);
    user.avatar_id = view["avatar_id"].get_int32().value;
    user.is_online = view["is_online"].get_bool().value;
    user.status = string(view["status"].get_string().value);
    
    return user;
}

bool AuthRepository::usernameExists(const string& username) {
    auto db = mongoClient.getDatabase();
    auto users = db["users"];
    
    auto result = users.find_one(document{} << "username" << username << finalize);
    return result.has_value();
}

string AuthRepository::createUser(const string& username, 
                                  const string& passwordHash, 
                                  int avatarId) {
    auto db = mongoClient.getDatabase();
    auto users = db["users"];
    auto now = chrono::system_clock::now();
    
    auto userDoc = document{}
        << "username" << username
        << "password_hash" << passwordHash
        << "avatar_id" << avatarId
        << "status" << "active"
        << "is_online" << false
        << "created_at" << bsoncxx::types::b_date{now}
        << "last_login" << bsoncxx::types::b_date{now}
        << finalize;
    
    auto result = users.insert_one(userDoc.view());
    
    if (!result) {
        return "";
    }
    
    // Return username instead of ObjectId
    return username;
}

bool AuthRepository::updateAvatar(const string& username, int avatarId) {
    auto db = mongoClient.getDatabase();
    auto users = db["users"];
    
    auto result = users.update_one(
        document{} << "username" << username << finalize,
        document{} << "$set" << open_document
                  << "avatar_id" << avatarId
                  << close_document << finalize
    );
    
    return result && result->matched_count() > 0;
}

bool AuthRepository::updateOnlineStatus(const string& username, bool isOnline) {
    auto db = mongoClient.getDatabase();
    auto users = db["users"];
    
    auto result = users.update_one(
        document{} << "username" << username << finalize,
        document{} << "$set" << open_document
                  << "is_online" << isOnline
                  << close_document << finalize
    );
    
    return result && result->modified_count() > 0;
}

bool AuthRepository::updateLastLogin(const string& username) {
    auto db = mongoClient.getDatabase();
    auto users = db["users"];
    auto now = chrono::system_clock::now();
    
    auto result = users.update_one(
        document{} << "username" << username << finalize,
        document{} << "$set" << open_document
                  << "last_login" << bsoncxx::types::b_date{now}
                  << "is_online" << true
                  << close_document << finalize
    );
    
    return result && result->modified_count() > 0;
}

// ============================================
// PLAYER STATS (MongoDB)
// ============================================

void AuthRepository::createDefaultStats(const string& username) {
    auto db = mongoClient.getDatabase();
    auto stats = db["player_stats"];
    auto now = chrono::system_clock::now();
    
    vector<string> timeControls = {"bullet", "blitz", "classical"};
    int initialRating = 1500;
    
    for (const auto& timeControl : timeControls) {
        auto statDoc = document{}
            << "username" << username
            << "time_control" << timeControl
            << "rating" << initialRating
            << "highest_rating" << initialRating
            << "lowest_rating" << initialRating
            << "rd" << 350.0
            << "volatility" << 0.06
            << "total_games" << 0
            << "wins" << 0
            << "losses" << 0
            << "draws" << 0
            << "win_streak" << 0
            << "longest_win_streak" << 0
            << "total_playtime" << 0
            << "last_game_time" << bsoncxx::types::b_date{now}
            << finalize;
        
        stats.insert_one(statDoc.view());
    }
}
