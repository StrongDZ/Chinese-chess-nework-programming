#include "../../include/challenge/challenge_repository.h"
#include <bsoncxx/builder/stream/document.hpp>
#include <bsoncxx/builder/basic/document.hpp>
#include <bsoncxx/json.hpp>

using namespace std;
using bsoncxx::builder::stream::document;
using bsoncxx::builder::stream::finalize;
using bsoncxx::builder::stream::open_document;
using bsoncxx::builder::stream::close_document;
using bsoncxx::builder::stream::open_array;
using bsoncxx::builder::stream::close_array;

ChallengeRepository::ChallengeRepository(MongoDBClient& mongo) 
    : mongoClient(mongo) {}

string ChallengeRepository::create(const Challenge& challenge) {
    try {
        auto db = mongoClient.getDatabase();
        auto challenges = db["challenges"];
        
        auto doc = document{}
            << "challenger_username" << challenge.challenger_username
            << "challenged_username" << challenge.challenged_username
            << "time_control" << challenge.time_control
            << "rated" << challenge.rated
            << "status" << challenge.status
            << "message" << challenge.message
            << "created_at" << bsoncxx::types::b_date{challenge.created_at}
            << "expires_at" << bsoncxx::types::b_date{challenge.expires_at}
            << finalize;
        
        auto result = challenges.insert_one(doc.view());
        
        if (!result) {
            return "";
        }
        
        return result->inserted_id().get_oid().value.to_string();
        
    } catch (const exception&) {
        return "";
    }
}

optional<Challenge> ChallengeRepository::findById(const string& challengeId) {
    try {
        auto db = mongoClient.getDatabase();
        auto challenges = db["challenges"];
        
        auto result = challenges.find_one(
            document{} << "_id" << bsoncxx::oid(challengeId) << finalize
        );
        
        if (!result) {
            return nullopt;
        }
        
        auto view = result->view();
        Challenge challenge;
        challenge.id = view["_id"].get_oid().value.to_string();
        challenge.challenger_username = string(view["challenger_username"].get_string().value);
        challenge.challenged_username = string(view["challenged_username"].get_string().value);
        challenge.time_control = string(view["time_control"].get_string().value);
        challenge.rated = view["rated"].get_bool().value;
        challenge.status = string(view["status"].get_string().value);
        
        if (view["message"] && view["message"].type() == bsoncxx::type::k_string) {
            challenge.message = string(view["message"].get_string().value);
        }
        
        challenge.created_at = chrono::system_clock::time_point(
            chrono::milliseconds(view["created_at"].get_date().value.count())
        );
        challenge.expires_at = chrono::system_clock::time_point(
            chrono::milliseconds(view["expires_at"].get_date().value.count())
        );
        
        if (view["responded_at"] && view["responded_at"].type() == bsoncxx::type::k_date) {
            challenge.responded_at = chrono::system_clock::time_point(
                chrono::milliseconds(view["responded_at"].get_date().value.count())
            );
        }
        
        if (view["game_id"] && view["game_id"].type() == bsoncxx::type::k_oid) {
            challenge.game_id = view["game_id"].get_oid().value.to_string();
        }
        
        return challenge;
        
    } catch (const exception&) {
        return nullopt;
    }
}

optional<Challenge> ChallengeRepository::findPendingBetweenUsers(const string& challenger, 
                                                                  const string& challenged) {
    try {
        auto db = mongoClient.getDatabase();
        auto challenges = db["challenges"];
        
        auto result = challenges.find_one(
            document{} 
                << "challenger_username" << challenger
                << "challenged_username" << challenged
                << "status" << "pending"
                << finalize
        );
        
        if (!result) {
            return nullopt;
        }
        
        // Reuse findById logic by extracting ID
        auto view = result->view();
        string id = view["_id"].get_oid().value.to_string();
        return findById(id);
        
    } catch (const exception&) {
        return nullopt;
    }
}

bool ChallengeRepository::updateStatus(const string& challengeId, 
                                        const string& newStatus,
                                        const optional<string>& gameId) {
    try {
        auto db = mongoClient.getDatabase();
        auto challenges = db["challenges"];
        auto now = chrono::system_clock::now();
        
        bsoncxx::builder::basic::document updateDoc;
        bsoncxx::builder::basic::document setDoc;
        
        setDoc.append(bsoncxx::builder::basic::kvp("status", newStatus));
        setDoc.append(bsoncxx::builder::basic::kvp("responded_at", bsoncxx::types::b_date{now}));
        
        if (gameId.has_value()) {
            setDoc.append(bsoncxx::builder::basic::kvp("game_id", bsoncxx::oid(gameId.value())));
        }
        
        updateDoc.append(bsoncxx::builder::basic::kvp("$set", setDoc));
        
        auto result = challenges.update_one(
            document{} << "_id" << bsoncxx::oid(challengeId) << finalize,
            updateDoc.view()
        );
        
        return result && result->matched_count() > 0;
        
    } catch (const exception&) {
        return false;
    }
}

vector<Challenge> ChallengeRepository::findByUser(const string& username, 
                                                   const string& filter,
                                                   int limit) {
    vector<Challenge> result;
    
    try {
        auto db = mongoClient.getDatabase();
        auto challenges = db["challenges"];
        
        bsoncxx::builder::stream::document queryBuilder;
        
        if (filter == "sent") {
            queryBuilder << "challenger_username" << username;
        } else if (filter == "received") {
            queryBuilder << "challenged_username" << username;
        } else if (filter == "pending") {
            queryBuilder << "$or" << open_array
                << open_document << "challenger_username" << username << close_document
                << open_document << "challenged_username" << username << close_document
                << close_array
                << "status" << "pending";
        } else { // "all"
            queryBuilder << "$or" << open_array
                << open_document << "challenger_username" << username << close_document
                << open_document << "challenged_username" << username << close_document
                << close_array;
        }
        
        mongocxx::options::find opts;
        opts.sort(document{} << "created_at" << -1 << finalize);
        opts.limit(limit);
        
        auto cursor = challenges.find(queryBuilder << finalize, opts);
        
        for (auto&& doc : cursor) {
            Challenge challenge;
            challenge.id = doc["_id"].get_oid().value.to_string();
            challenge.challenger_username = string(doc["challenger_username"].get_string().value);
            challenge.challenged_username = string(doc["challenged_username"].get_string().value);
            challenge.time_control = string(doc["time_control"].get_string().value);
            challenge.rated = doc["rated"].get_bool().value;
            challenge.status = string(doc["status"].get_string().value);
            
            if (doc["message"] && doc["message"].type() == bsoncxx::type::k_string) {
                challenge.message = string(doc["message"].get_string().value);
            }
            
            challenge.created_at = chrono::system_clock::time_point(
                chrono::milliseconds(doc["created_at"].get_date().value.count())
            );
            challenge.expires_at = chrono::system_clock::time_point(
                chrono::milliseconds(doc["expires_at"].get_date().value.count())
            );
            
            if (doc["game_id"] && doc["game_id"].type() == bsoncxx::type::k_oid) {
                challenge.game_id = doc["game_id"].get_oid().value.to_string();
            }
            
            result.push_back(challenge);
        }
        
    } catch (const exception&) {
        // Return empty vector on error
    }
    
    return result;
}

bool ChallengeRepository::userExists(const string& username) {
    auto db = mongoClient.getDatabase();
    auto users = db["users"];
    
    auto result = users.find_one(document{} << "username" << username << finalize);
    return result.has_value();
}

int ChallengeRepository::deleteExpired() {
    try {
        auto db = mongoClient.getDatabase();
        auto challenges = db["challenges"];
        auto now = chrono::system_clock::now();
        
        auto result = challenges.delete_many(
            document{} 
                << "status" << "pending"
                << "expires_at" << open_document 
                    << "$lt" << bsoncxx::types::b_date{now} 
                << close_document 
                << finalize
        );
        
        if (!result) {
            return 0;
        }
        
        return static_cast<int>(result->deleted_count());
        
    } catch (const exception&) {
        return 0;
    }
}
