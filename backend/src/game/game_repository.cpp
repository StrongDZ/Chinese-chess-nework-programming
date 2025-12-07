#include "game/game_repository.h"
#include <bsoncxx/builder/stream/document.hpp>
#include <bsoncxx/builder/basic/document.hpp>
#include <bsoncxx/json.hpp>
#include <random>

using namespace std;
using bsoncxx::builder::stream::document;
using bsoncxx::builder::stream::finalize;
using bsoncxx::builder::stream::open_document;
using bsoncxx::builder::stream::close_document;
using bsoncxx::builder::stream::open_array;
using bsoncxx::builder::stream::close_array;
using bsoncxx::builder::basic::make_document;
using bsoncxx::builder::basic::kvp;

GameRepository::GameRepository(MongoDBClient& mongo) 
    : mongoClient(mongo) {}

// ============ Game Operations ============

string GameRepository::createGame(const Game& game) {
    try {
        auto db = mongoClient.getDatabase();
        auto games = db["active_games"];
        
        auto gameDoc = document{}
            << "red_player" << game.red_player
            << "black_player" << game.black_player
            << "status" << game.status
            << "start_time" << bsoncxx::types::b_date{game.start_time}
            << "xfen" << game.xfen
            << "moves" << open_array << close_array
            << "current_turn" << game.current_turn
            << "move_count" << bsoncxx::types::b_int32{game.move_count}
            << "time_control" << game.time_control
            << "time_limit" << bsoncxx::types::b_int32{game.time_limit}
            << "red_time_remaining" << bsoncxx::types::b_int32{game.red_time_remaining}
            << "black_time_remaining" << bsoncxx::types::b_int32{game.black_time_remaining}
            << "increment" << bsoncxx::types::b_int32{game.increment}
            << "rated" << bsoncxx::types::b_bool{game.rated}
            << finalize;
        
        auto result = games.insert_one(gameDoc.view());
        
        if (!result) {
            std::cerr << "[DEBUG] insert_one returned empty optional" << std::endl;
            return "";
        }
        
        return result->inserted_id().get_oid().value.to_string();
        
    } catch (const exception& e) {
        std::cerr << "[DEBUG] createGame exception: " << e.what() << std::endl;
        return "";
    }
}

optional<Game> GameRepository::findById(const string& gameId) {
    try {
        auto db = mongoClient.getDatabase();
        auto games = db["active_games"];
        
        auto result = games.find_one(
            document{} << "_id" << bsoncxx::oid(gameId) << finalize
        );
        
        if (!result) {
            return nullopt;
        }
        
        auto view = result->view();
        Game game;
        game.id = view["_id"].get_oid().value.to_string();
        game.red_player = string(view["red_player"].get_string().value);
        game.black_player = string(view["black_player"].get_string().value);
        game.status = string(view["status"].get_string().value);
        game.xfen = string(view["xfen"].get_string().value);
        game.current_turn = string(view["current_turn"].get_string().value);
        game.move_count = view["move_count"].get_int32().value;
        game.time_control = string(view["time_control"].get_string().value);
        game.time_limit = view["time_limit"].get_int32().value;
        game.red_time_remaining = view["red_time_remaining"].get_int32().value;
        game.black_time_remaining = view["black_time_remaining"].get_int32().value;
        game.increment = view["increment"].get_int32().value;
        game.rated = view["rated"].get_bool().value;
        
        game.start_time = chrono::system_clock::time_point(
            chrono::milliseconds(view["start_time"].get_date().value.count())
        );
        
        if (view["result"] && view["result"].type() == bsoncxx::type::k_string) {
            game.result = string(view["result"].get_string().value);
        }
        
        if (view["winner"] && view["winner"].type() == bsoncxx::type::k_string) {
            game.winner = string(view["winner"].get_string().value);
        }
        
        if (view["end_time"] && view["end_time"].type() == bsoncxx::type::k_date) {
            game.end_time = chrono::system_clock::time_point(
                chrono::milliseconds(view["end_time"].get_date().value.count())
            );
        }
        
        // Parse moves array
        auto movesArray = view["moves"].get_array().value;
        for (auto&& moveDoc : movesArray) {
            Move move;
            move.move_number = moveDoc["move_number"].get_int32().value;
            move.from_x = moveDoc["from_x"].get_int32().value;
            move.from_y = moveDoc["from_y"].get_int32().value;
            move.to_x = moveDoc["to_x"].get_int32().value;
            move.to_y = moveDoc["to_y"].get_int32().value;
            
            if (moveDoc["player"] && moveDoc["player"].type() == bsoncxx::type::k_string) {
                move.player = string(moveDoc["player"].get_string().value);
            }
            if (moveDoc["piece"] && moveDoc["piece"].type() == bsoncxx::type::k_string) {
                move.piece = string(moveDoc["piece"].get_string().value);
            }
            if (moveDoc["captured"] && moveDoc["captured"].type() == bsoncxx::type::k_string) {
                move.captured = string(moveDoc["captured"].get_string().value);
            }
            if (moveDoc["notation"] && moveDoc["notation"].type() == bsoncxx::type::k_string) {
                move.notation = string(moveDoc["notation"].get_string().value);
            }
            if (moveDoc["xfen_after"] && moveDoc["xfen_after"].type() == bsoncxx::type::k_string) {
                move.xfen_after = string(moveDoc["xfen_after"].get_string().value);
            }
            if (moveDoc["time_taken"]) {
                move.time_taken = moveDoc["time_taken"].get_int32().value;
            }
            if (moveDoc["timestamp"] && moveDoc["timestamp"].type() == bsoncxx::type::k_date) {
                move.timestamp = chrono::system_clock::time_point(
                    chrono::milliseconds(moveDoc["timestamp"].get_date().value.count())
                );
            }
            
            game.moves.push_back(move);
        }
        
        return game;
        
    } catch (const exception&) {
        return nullopt;
    }
}

bool GameRepository::updateAfterMove(const string& gameId,
                                      const Move& move,
                                      const string& nextTurn,
                                      int redTimeRemaining,
                                      int blackTimeRemaining,
                                      const string& newXfen) {
    try {
        auto db = mongoClient.getDatabase();
        auto games = db["active_games"];
        
        auto now = chrono::system_clock::now();
        
        // Build move document
        auto moveDoc = make_document(
            kvp("move_number", move.move_number),
            kvp("player", move.player),
            kvp("from_x", move.from_x),
            kvp("from_y", move.from_y),
            kvp("to_x", move.to_x),
            kvp("to_y", move.to_y),
            kvp("piece", move.piece),
            kvp("captured", move.captured),
            kvp("notation", move.notation),
            kvp("xfen_after", move.xfen_after),
            kvp("timestamp", bsoncxx::types::b_date{now}),
            kvp("time_taken", move.time_taken)
        );
        
        // Build update
        bsoncxx::builder::basic::document setDoc;
        setDoc.append(kvp("current_turn", nextTurn));
        setDoc.append(kvp("move_count", move.move_number));
        setDoc.append(kvp("red_time_remaining", redTimeRemaining));
        setDoc.append(kvp("black_time_remaining", blackTimeRemaining));
        
        if (!newXfen.empty()) {
            setDoc.append(kvp("xfen", newXfen));
        }
        
        auto updateDoc = make_document(
            kvp("$push", make_document(kvp("moves", moveDoc.view()))),
            kvp("$set", setDoc.view())
        );
        
        auto result = games.update_one(
            document{} << "_id" << bsoncxx::oid(gameId) << finalize,
            updateDoc.view()
        );
        
        return result && result->matched_count() > 0;
        
    } catch (const exception&) {
        return false;
    }
}

bool GameRepository::endGame(const string& gameId,
                              const string& status,
                              const string& result,
                              const string& winner) {
    try {
        auto db = mongoClient.getDatabase();
        auto games = db["active_games"];
        auto now = chrono::system_clock::now();
        
        bsoncxx::builder::basic::document setDoc;
        setDoc.append(kvp("status", status));
        setDoc.append(kvp("result", result));
        setDoc.append(kvp("end_time", bsoncxx::types::b_date{now}));
        
        if (!winner.empty()) {
            setDoc.append(kvp("winner", winner));
        } else {
            setDoc.append(kvp("winner", bsoncxx::types::b_null{}));
        }
        
        auto updateResult = games.update_one(
            document{} << "_id" << bsoncxx::oid(gameId) << finalize,
            make_document(kvp("$set", setDoc.view()))
        );
        
        return updateResult && updateResult->matched_count() > 0;
        
    } catch (const exception&) {
        return false;
    }
}

vector<Game> GameRepository::findByUser(const string& username,
                                         const string& filter,
                                         int limit) {
    vector<Game> result;
    
    try {
        auto db = mongoClient.getDatabase();
        auto games = db["active_games"];
        
        // Build query - using username in player name fields
        bsoncxx::builder::stream::document queryBuilder;
        queryBuilder << "$or" << open_array
            << open_document << "red_player" << username << close_document
            << open_document << "black_player" << username << close_document
            << close_array;
        
        if (filter == "active") {
            queryBuilder << "status" << "in_progress";
        } else if (filter == "completed") {
            queryBuilder << "status" << "completed";
        }
        
        mongocxx::options::find opts;
        opts.sort(document{} << "start_time" << -1 << finalize);
        opts.limit(limit);
        
        auto cursor = games.find(queryBuilder << finalize, opts);
        
        for (auto&& doc : cursor) {
            Game game;
            game.id = doc["_id"].get_oid().value.to_string();
            game.red_player = string(doc["red_player"].get_string().value);
            game.black_player = string(doc["black_player"].get_string().value);
            game.status = string(doc["status"].get_string().value);
            game.current_turn = string(doc["current_turn"].get_string().value);
            game.move_count = doc["move_count"].get_int32().value;
            game.time_control = string(doc["time_control"].get_string().value);
            
            if (doc["result"] && doc["result"].type() == bsoncxx::type::k_string) {
                game.result = string(doc["result"].get_string().value);
            }
            
            result.push_back(game);
        }
        
    } catch (const exception&) {
        // Return empty vector on error
    }
    
    return result;
}

// ============ Draw Offer Operations ============

bool GameRepository::createDrawOffer(const DrawOffer& offer) {
    try {
        auto db = mongoClient.getDatabase();
        auto drawOffers = db["draw_offers"];
        
        // Delete any existing offer for this game first
        drawOffers.delete_many(document{} << "game_id" << offer.game_id << finalize);
        
        auto doc = document{}
            << "game_id" << offer.game_id
            << "from_player" << offer.from_player
            << "created_at" << bsoncxx::types::b_date{offer.created_at}
            << "expires_at" << bsoncxx::types::b_date{offer.expires_at}
            << finalize;
        
        auto result = drawOffers.insert_one(doc.view());
        return result.has_value();
        
    } catch (const exception&) {
        return false;
    }
}

optional<DrawOffer> GameRepository::getDrawOffer(const string& gameId) {
    try {
        auto db = mongoClient.getDatabase();
        auto drawOffers = db["draw_offers"];
        auto now = chrono::system_clock::now();
        
        auto result = drawOffers.find_one(
            document{} 
                << "game_id" << gameId
                << "expires_at" << open_document 
                    << "$gt" << bsoncxx::types::b_date{now} 
                << close_document
                << finalize
        );
        
        if (!result) {
            return nullopt;
        }
        
        auto view = result->view();
        DrawOffer offer;
        offer.game_id = string(view["game_id"].get_string().value);
        offer.from_player = string(view["from_player"].get_string().value);
        offer.created_at = chrono::system_clock::time_point(
            chrono::milliseconds(view["created_at"].get_date().value.count())
        );
        offer.expires_at = chrono::system_clock::time_point(
            chrono::milliseconds(view["expires_at"].get_date().value.count())
        );
        
        return offer;
        
    } catch (const exception&) {
        return nullopt;
    }
}

bool GameRepository::deleteDrawOffer(const string& gameId) {
    try {
        auto db = mongoClient.getDatabase();
        auto drawOffers = db["draw_offers"];
        
        drawOffers.delete_many(document{} << "game_id" << gameId << finalize);
        return true;
        
    } catch (const exception&) {
        return false;
    }
}

// ============ Player Stats Operations ============

bool GameRepository::updatePlayerStats(const string& username,
                                        const string& timeControl,
                                        int newRating,
                                        const string& resultField) {
    try {
        auto db = mongoClient.getDatabase();
        auto stats = db["player_stats"];
        
        auto updateDoc = document{}
            << "$set" << open_document
                << "rating" << newRating
            << close_document
            << "$inc" << open_document
                << "total_games" << 1
                << resultField << 1
            << close_document
            << "$max" << open_document
                << "highest_rating" << newRating
            << close_document
            << "$min" << open_document
                << "lowest_rating" << newRating
            << close_document
            << finalize;
        
        auto result = stats.update_one(
            document{} 
                << "username" << username
                << "time_control" << timeControl
                << finalize,
            updateDoc.view()
        );
        
        return result && result->matched_count() > 0;
        
    } catch (const exception&) {
        return false;
    }
}

int GameRepository::getPlayerRating(const string& username, const string& timeControl) {
    try {
        auto db = mongoClient.getDatabase();
        auto stats = db["player_stats"];
        
        auto result = stats.find_one(
            document{} 
                << "username" << username
                << "time_control" << timeControl
                << finalize
        );
        
        if (!result) {
            return 1200; // Default rating
        }
        
        return result->view()["rating"].get_int32().value;
        
    } catch (const exception&) {
        return 1200;
    }
}

optional<string> GameRepository::findRandomOpponentByElo(const string& username,
                                                         const string& timeControl,
                                                         int ratingWindow) {
    try {
        auto db = mongoClient.getDatabase();
        auto stats = db["player_stats"];

        const int playerRating = getPlayerRating(username, timeControl);
        const int minRating = max(0, playerRating - ratingWindow);
        const int maxRating = playerRating + ratingWindow;

        auto filter = make_document(
            kvp("username", make_document(kvp("$ne", username))),
            kvp("time_control", timeControl),
            kvp("rating", make_document(kvp("$gte", minRating), kvp("$lte", maxRating)))
        );

        std::vector<std::string> candidates;
        for (auto&& doc : stats.find(filter.view())) {
            if (doc["username"] && doc["username"].type() == bsoncxx::type::k_string) {
                candidates.emplace_back(doc["username"].get_string().value);
            }
        }

        if (candidates.empty()) {
            return nullopt;
        }

        std::random_device rd;
        std::mt19937 gen(rd());
        std::uniform_int_distribution<size_t> dist(0, candidates.size() - 1);
        return candidates[dist(gen)];

    } catch (const std::exception&) {
        return nullopt;
    }
}

// ============ Helper Operations ============

bool GameRepository::userExists(const string& username) {
    auto db = mongoClient.getDatabase();
    auto users = db["users"];
    
    auto result = users.find_one(document{} << "username" << username << finalize);
    return result.has_value();
}
