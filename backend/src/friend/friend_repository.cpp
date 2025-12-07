#include "../../include/friend/friend_repository.h"
#include <bsoncxx/builder/stream/document.hpp>
#include <bsoncxx/builder/stream/helpers.hpp>
#include <bsoncxx/json.hpp>

using namespace std;
using bsoncxx::builder::stream::document;
using bsoncxx::builder::stream::finalize;
using bsoncxx::builder::stream::open_document;
using bsoncxx::builder::stream::close_document;
using bsoncxx::builder::stream::open_array;
using bsoncxx::builder::stream::close_array;

FriendRepository::FriendRepository(MongoDBClient& mongo) : mongoClient(mongo) {}

optional<FriendRelation> FriendRepository::mapDocToRelation(const bsoncxx::document::view& doc) {
    try {
        FriendRelation rel;
        rel.id = doc["_id"].get_oid().value.to_string();
        rel.user_name = string(doc["user_name"].get_string().value);
        rel.friend_name = string(doc["friend_name"].get_string().value);
        rel.status = string(doc["status"].get_string().value);
        rel.created_at = chrono::system_clock::time_point(
            chrono::milliseconds(doc["created_at"].get_date().value.count()));
        if (doc["accepted_at"] && doc["accepted_at"].type() == bsoncxx::type::k_date) {
            rel.accepted_at = chrono::system_clock::time_point(
                chrono::milliseconds(doc["accepted_at"].get_date().value.count()));
        }
        if (doc["blocked_at"] && doc["blocked_at"].type() == bsoncxx::type::k_date) {
            rel.blocked_at = chrono::system_clock::time_point(
                chrono::milliseconds(doc["blocked_at"].get_date().value.count()));
        }
        if (doc["games_played_together"] && doc["games_played_together"].type() == bsoncxx::type::k_int32) {
            rel.games_played_together = doc["games_played_together"].get_int32().value;
        }
        return rel;
    } catch (const exception&) {
        return nullopt;
    }
}

bool FriendRepository::userExists(const string& username) {
    auto db = mongoClient.getDatabase();
    auto users = db["users"];
    auto res = users.find_one(document{} << "username" << username << finalize);
    return res.has_value();
}

optional<FriendRelation> FriendRepository::findRelation(const string& user, const string& friendName) {
    try {
        auto db = mongoClient.getDatabase();
        auto friends = db["friends"];
        auto res = friends.find_one(document{} << "user_name" << user << "friend_name" << friendName << finalize);
        if (!res) return nullopt;
        return mapDocToRelation(res->view());
    } catch (const exception&) {
        return nullopt;
    }
}

string FriendRepository::createRelation(const FriendRelation& relation) {
    try {
        auto db = mongoClient.getDatabase();
        auto friends = db["friends"];
        bsoncxx::builder::stream::document doc;
        doc << "user_name" << relation.user_name
            << "friend_name" << relation.friend_name
            << "status" << relation.status
            << "created_at" << bsoncxx::types::b_date{relation.created_at}
            << "games_played_together" << relation.games_played_together;
        if (relation.accepted_at) {
            doc << "accepted_at" << bsoncxx::types::b_date{relation.accepted_at.value()};
        }
        if (relation.blocked_at) {
            doc << "blocked_at" << bsoncxx::types::b_date{relation.blocked_at.value()};
        }
        auto result = friends.insert_one(doc << finalize);
        if (!result) return "";
        return result->inserted_id().get_oid().value.to_string();
    } catch (const exception&) {
        return "";
    }
}

bool FriendRepository::updateStatus(const string& user, const string& friendName, const string& newStatus,
                                    bool setAcceptedTime, bool setBlockedTime) {
    try {
        auto db = mongoClient.getDatabase();
        auto friends = db["friends"];
        auto now = chrono::system_clock::now();

        bsoncxx::builder::basic::document setDoc;
        setDoc.append(bsoncxx::builder::basic::kvp("status", newStatus));
        if (setAcceptedTime) {
            setDoc.append(bsoncxx::builder::basic::kvp("accepted_at", bsoncxx::types::b_date{now}));
        }
        if (setBlockedTime) {
            setDoc.append(bsoncxx::builder::basic::kvp("blocked_at", bsoncxx::types::b_date{now}));
        }

        bsoncxx::builder::basic::document updateDoc;
        updateDoc.append(bsoncxx::builder::basic::kvp("$set", setDoc));

        auto res = friends.update_one(document{} << "user_name" << user << "friend_name" << friendName << finalize,
                                       updateDoc.view());
        return res && res->matched_count() > 0;
    } catch (const exception&) {
        return false;
    }
}

bool FriendRepository::deleteRelation(const string& user, const string& friendName, const optional<string>& statusFilter) {
    try {
        auto db = mongoClient.getDatabase();
        auto friends = db["friends"];
        bsoncxx::builder::stream::document query;
        query << "user_name" << user << "friend_name" << friendName;
        if (statusFilter) {
            query << "status" << statusFilter.value();
        }
        auto res = friends.delete_one(query << finalize);
        return res && res->deleted_count() > 0;
    } catch (const exception&) {
        return false;
    }
}

vector<FriendRelation> FriendRepository::findAccepted(const string& username) {
    vector<FriendRelation> out;
    try {
        auto db = mongoClient.getDatabase();
        auto friends = db["friends"];
        auto cursor = friends.find(document{} << "user_name" << username << "status" << "accepted" << finalize);
        for (auto&& doc : cursor) {
            auto rel = mapDocToRelation(doc);
            if (rel) out.push_back(rel.value());
        }
    } catch (const exception&) {}
    return out;
}

vector<FriendRelation> FriendRepository::findPendingReceived(const string& username) {
    vector<FriendRelation> out;
    try {
        auto db = mongoClient.getDatabase();
        auto friends = db["friends"];
        auto cursor = friends.find(document{} << "friend_name" << username << "status" << "pending" << finalize);
        for (auto&& doc : cursor) {
            auto rel = mapDocToRelation(doc);
            if (rel) out.push_back(rel.value());
        }
    } catch (const exception&) {}
    return out;
}

vector<FriendRelation> FriendRepository::findPendingSent(const string& username) {
    vector<FriendRelation> out;
    try {
        auto db = mongoClient.getDatabase();
        auto friends = db["friends"];
        auto cursor = friends.find(document{} << "user_name" << username << "status" << "pending" << finalize);
        for (auto&& doc : cursor) {
            auto rel = mapDocToRelation(doc);
            if (rel) out.push_back(rel.value());
        }
    } catch (const exception&) {}
    return out;
}

vector<FriendRelation> FriendRepository::findBlocked(const string& username) {
    vector<FriendRelation> out;
    try {
        auto db = mongoClient.getDatabase();
        auto friends = db["friends"];
        auto cursor = friends.find(document{} << "user_name" << username << "status" << "blocked" << finalize);
        for (auto&& doc : cursor) {
            auto rel = mapDocToRelation(doc);
            if (rel) out.push_back(rel.value());
        }
    } catch (const exception&) {}
    return out;
}

vector<FriendRelation> FriendRepository::searchFriends(const string& username, const string& searchQuery) {
    vector<FriendRelation> out;
    try {
        auto db = mongoClient.getDatabase();
        auto friends = db["friends"];
        auto cursor = friends.find(document{}
            << "user_name" << username
            << "status" << "accepted"
            << "friend_name" << open_document << "$regex" << searchQuery << "$options" << "i" << close_document
            << finalize);
        for (auto&& doc : cursor) {
            auto rel = mapDocToRelation(doc);
            if (rel) out.push_back(rel.value());
        }
    } catch (const exception&) {}
    return out;
}

