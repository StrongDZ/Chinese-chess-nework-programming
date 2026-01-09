#include "game/game_repository.h"
#include <bsoncxx/builder/basic/array.hpp>
#include <bsoncxx/builder/basic/document.hpp>
#include <bsoncxx/builder/stream/document.hpp>
#include <bsoncxx/json.hpp>
#include <iostream>
#include <random>

using namespace std;
using bsoncxx::builder::basic::kvp;
using bsoncxx::builder::basic::make_document;
using bsoncxx::builder::stream::close_array;
using bsoncxx::builder::stream::close_document;
using bsoncxx::builder::stream::document;
using bsoncxx::builder::stream::finalize;
using bsoncxx::builder::stream::open_array;
using bsoncxx::builder::stream::open_document;

GameRepository::GameRepository(MongoDBClient &mongo) : mongoClient(mongo) {}

// ============ Game Operations ============

string GameRepository::createGame(const Game &game) {
  try {
    cout << "[GameRepository::createGame] Creating game: red="
         << game.red_player << ", black=" << game.black_player
         << ", xfen=" << game.xfen << endl;

    auto db = mongoClient.getDatabase();
    auto games = db["active_games"];
    auto gameDoc = document{}
                   << "red_player" << game.red_player << "black_player"
                   << game.black_player << "status" << game.status
                   << "start_time" << bsoncxx::types::b_date{game.start_time}
                   << "xfen" << game.xfen << "moves" << open_array
                   << close_array << "current_turn" << game.current_turn
                   << "move_count" << bsoncxx::types::b_int32{game.move_count}
                   << "time_control" << game.time_control << "time_limit"
                   << bsoncxx::types::b_int32{game.time_limit}
                   << "red_time_remaining"
                   << bsoncxx::types::b_int32{game.red_time_remaining}
                   << "black_time_remaining"
                   << bsoncxx::types::b_int32{game.black_time_remaining}
                   << "increment" << bsoncxx::types::b_int32{game.increment}
                   << "rated" << bsoncxx::types::b_bool{game.rated} << finalize;

    cout << "[GameRepository::createGame] Inserting document into database..."
         << endl;
    auto result = games.insert_one(gameDoc.view());

    if (!result) {
      cerr << "[GameRepository::createGame] ERROR: insert_one returned null "
              "result"
           << endl;
      return "";
    }

    string gameId = result->inserted_id().get_oid().value.to_string();
    cout << "[GameRepository::createGame] Game created successfully, game_id="
         << gameId << endl;
    return gameId;

  } catch (const exception &e) {
    cerr << "[GameRepository::createGame] EXCEPTION: " << e.what() << endl;
    return "";
  } catch (...) {
    cerr << "[GameRepository::createGame] UNKNOWN EXCEPTION" << endl;
    return "";
  }
}

optional<Game> GameRepository::findById(const string &gameId) {
  try {
    auto db = mongoClient.getDatabase();
    auto games = db["active_games"];

    auto result =
        games.find_one(document{} << "_id" << bsoncxx::oid(gameId) << finalize);

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
        chrono::milliseconds(view["start_time"].get_date().value.count()));

    if (view["result"] && view["result"].type() == bsoncxx::type::k_string) {
      game.result = string(view["result"].get_string().value);
    }

    if (view["winner"] && view["winner"].type() == bsoncxx::type::k_string) {
      game.winner = string(view["winner"].get_string().value);
    }

    if (view["end_time"] && view["end_time"].type() == bsoncxx::type::k_date) {
      game.end_time = chrono::system_clock::time_point(
          chrono::milliseconds(view["end_time"].get_date().value.count()));
    }

    if (view["draw_offered_by"] &&
        view["draw_offered_by"].type() == bsoncxx::type::k_string) {
      game.draw_offered_by = string(view["draw_offered_by"].get_string().value);
    }

    // Parse moves array
    auto movesArray = view["moves"].get_array().value;
    for (auto &&moveDoc : movesArray) {
      Move move;
      move.move_number = moveDoc["move_number"].get_int32().value;
      move.from_x = moveDoc["from_x"].get_int32().value;
      move.from_y = moveDoc["from_y"].get_int32().value;
      move.to_x = moveDoc["to_x"].get_int32().value;
      move.to_y = moveDoc["to_y"].get_int32().value;

      if (moveDoc["player"] &&
          moveDoc["player"].type() == bsoncxx::type::k_string) {
        move.player = string(moveDoc["player"].get_string().value);
      }
      if (moveDoc["piece"] &&
          moveDoc["piece"].type() == bsoncxx::type::k_string) {
        move.piece = string(moveDoc["piece"].get_string().value);
      }
      if (moveDoc["captured"] &&
          moveDoc["captured"].type() == bsoncxx::type::k_string) {
        move.captured = string(moveDoc["captured"].get_string().value);
      }
      if (moveDoc["notation"] &&
          moveDoc["notation"].type() == bsoncxx::type::k_string) {
        move.notation = string(moveDoc["notation"].get_string().value);
      }
      if (moveDoc["xfen_after"] &&
          moveDoc["xfen_after"].type() == bsoncxx::type::k_string) {
        move.xfen_after = string(moveDoc["xfen_after"].get_string().value);
      }
      if (moveDoc["time_taken"]) {
        move.time_taken = moveDoc["time_taken"].get_int32().value;
      }
      if (moveDoc["timestamp"] &&
          moveDoc["timestamp"].type() == bsoncxx::type::k_date) {
        move.timestamp = chrono::system_clock::time_point(chrono::milliseconds(
            moveDoc["timestamp"].get_date().value.count()));
      }

      game.moves.push_back(move);
    }

    return game;

  } catch (const exception &) {
    return nullopt;
  }
}

bool GameRepository::updateAfterMove(const string &gameId, const Move &move,
                                     const string &nextTurn,
                                     int redTimeRemaining,
                                     int blackTimeRemaining,
                                     const string &newXfen) {
  try {
    auto db = mongoClient.getDatabase();
    auto games = db["active_games"];

    auto now = chrono::system_clock::now();

    // Build move document
    auto moveDoc = make_document(
        kvp("move_number", move.move_number), kvp("player", move.player),
        kvp("from_x", move.from_x), kvp("from_y", move.from_y),
        kvp("to_x", move.to_x), kvp("to_y", move.to_y),
        kvp("piece", move.piece), kvp("captured", move.captured),
        kvp("notation", move.notation), kvp("xfen_after", move.xfen_after),
        kvp("timestamp", bsoncxx::types::b_date{now}),
        kvp("time_taken", move.time_taken));

    // Build update
    bsoncxx::builder::basic::document setDoc;
    setDoc.append(kvp("current_turn", nextTurn));
    setDoc.append(kvp("move_count", move.move_number));
    setDoc.append(kvp("red_time_remaining", redTimeRemaining));
    setDoc.append(kvp("black_time_remaining", blackTimeRemaining));

    if (!newXfen.empty()) {
      setDoc.append(kvp("xfen", newXfen));
    }

    auto updateDoc =
        make_document(kvp("$push", make_document(kvp("moves", moveDoc.view()))),
                      kvp("$set", setDoc.view()));

    auto result = games.update_one(document{} << "_id" << bsoncxx::oid(gameId)
                                              << finalize,
                                   updateDoc.view());

    return result && result->matched_count() > 0;

  } catch (const exception &) {
    return false;
  }
}

bool GameRepository::endGame(const string &gameId, const string &status,
                             const string &result, const string &winner) {
  try {
    auto db = mongoClient.getDatabase();
    auto games = db["active_games"];
    auto archive = db["game_archive"];
    auto now = chrono::system_clock::now();

    // Load full game for archiving (if available)
    auto gameOpt = findById(gameId);

    // Update active_games status/result/end_time/winner
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
        make_document(kvp("$set", setDoc.view())));

    if (!(updateResult && updateResult->matched_count() > 0)) {
      return false;
    }

    // Archive and clean up active record if we have full game data
    if (gameOpt) {
      const Game &g = gameOpt.value();

      bsoncxx::builder::basic::document archiveDoc;
      archiveDoc.append(kvp("original_game_id", bsoncxx::oid(gameId)));
      archiveDoc.append(kvp("red_player", g.red_player));
      archiveDoc.append(kvp("black_player", g.black_player));
      archiveDoc.append(
          kvp("winner",
              winner.empty()
                  ? bsoncxx::types::bson_value::value{bsoncxx::types::b_null{}}
                  : bsoncxx::types::bson_value::value{winner}));
      archiveDoc.append(kvp("result", result));
      archiveDoc.append(
          kvp("start_time", bsoncxx::types::b_date{g.start_time}));
      archiveDoc.append(kvp("end_time", bsoncxx::types::b_date{now}));
      archiveDoc.append(kvp("initial_xfen", g.xfen));
      archiveDoc.append(kvp("final_xfen", g.xfen));
      archiveDoc.append(
          kvp("move_count", bsoncxx::types::b_int32{g.move_count}));
      archiveDoc.append(kvp("time_control", g.time_control));
      archiveDoc.append(
          kvp("time_limit", bsoncxx::types::b_int32{g.time_limit}));
      archiveDoc.append(kvp("increment", bsoncxx::types::b_int32{g.increment}));

      bsoncxx::builder::basic::array movesArray;
      for (const auto &m : g.moves) {
        bsoncxx::builder::basic::document moveDoc;
        moveDoc.append(
            kvp("move_number", bsoncxx::types::b_int32{m.move_number}));
        moveDoc.append(kvp("player", m.player));

        bsoncxx::builder::basic::document fromDoc;
        fromDoc.append(kvp("x", bsoncxx::types::b_int32{m.from_x}));
        fromDoc.append(kvp("y", bsoncxx::types::b_int32{m.from_y}));
        moveDoc.append(kvp("from", fromDoc.extract()));

        bsoncxx::builder::basic::document toDoc;
        toDoc.append(kvp("x", bsoncxx::types::b_int32{m.to_x}));
        toDoc.append(kvp("y", bsoncxx::types::b_int32{m.to_y}));
        moveDoc.append(kvp("to", toDoc.extract()));

        moveDoc.append(kvp("piece", m.piece));
        moveDoc.append(kvp(
            "captured",
            m.captured.empty()
                ? bsoncxx::types::bson_value::value{bsoncxx::types::b_null{}}
                : bsoncxx::types::bson_value::value{m.captured}));
        moveDoc.append(kvp("notation", m.notation));
        moveDoc.append(kvp("xfen_after", m.xfen_after));
        moveDoc.append(kvp("timestamp", bsoncxx::types::b_date{m.timestamp}));
        moveDoc.append(
            kvp("time_taken", bsoncxx::types::b_int32{m.time_taken}));

        movesArray.append(moveDoc.extract());
      }

      archiveDoc.append(kvp("moves", movesArray.extract()));

      // Insert to game_archive (best-effort)
      try {
        archive.insert_one(archiveDoc.view());
      } catch (const exception &) {
        // If archiving fails, we still keep active update; no throw
      }

      // Remove from active_games to keep only ongoing games
      try {
        games.delete_one(document{} << "_id" << bsoncxx::oid(gameId)
                                    << finalize);
      } catch (const exception &) {
        // best-effort cleanup
      }
    }

    return true;

  } catch (const exception &) {
    return false;
  }
}

bool GameRepository::deleteGame(const string &gameId) {
  try {
    auto db = mongoClient.getDatabase();
    auto games = db["active_games"];

    // Xóa game khỏi active_games (không archive, không tính điểm)
    auto deleteResult = games.delete_one(
        document{} << "_id" << bsoncxx::oid(gameId) << finalize);

    if (deleteResult && deleteResult->deleted_count() > 0) {
      cout << "[GameRepository::deleteGame] Deleted game " << gameId
           << " from active_games (not archived, no rating change)" << endl;
      return true;
    } else {
      cout << "[GameRepository::deleteGame] Game " << gameId
           << " not found in active_games" << endl;
      return false;
    }
  } catch (const exception &e) {
    cerr << "[GameRepository::deleteGame] Error: " << e.what() << endl;
    return false;
  }
}

// ============ Draw Offer Operations ============

bool GameRepository::setDrawOffer(const string &gameId,
                                  const string &username) {
  try {
    auto db = mongoClient.getDatabase();
    auto games = db["active_games"];

    auto updateResult = games.update_one(
        document{} << "_id" << bsoncxx::oid(gameId) << finalize,
        make_document(
            kvp("$set", make_document(kvp("draw_offered_by", username)))));

    return updateResult && updateResult->matched_count() > 0;

  } catch (const exception &) {
    return false;
  }
}

bool GameRepository::clearDrawOffer(const string &gameId) {
  try {
    auto db = mongoClient.getDatabase();
    auto games = db["active_games"];

    auto updateResult = games.update_one(
        document{} << "_id" << bsoncxx::oid(gameId) << finalize,
        make_document(
            kvp("$unset", make_document(kvp("draw_offered_by", "")))));

    return updateResult && updateResult->matched_count() > 0;

  } catch (const exception &) {
    return false;
  }
}

// ============ Rematch Operations ============

optional<ArchivedGame>
GameRepository::findArchivedGameById(const string &gameId) {
  try {
    auto db = mongoClient.getDatabase();
    auto archive = db["game_archive"];

    // Try to find by _id first
    auto result = archive.find_one(document{} << "_id" << bsoncxx::oid(gameId)
                                              << finalize);

    // If not found, try by original_game_id
    if (!result) {
      result = archive.find_one(document{} << "original_game_id"
                                           << bsoncxx::oid(gameId) << finalize);
    }

    if (!result) {
      return nullopt;
    }

    auto view = result->view();
    ArchivedGame game;
    game.id = view["_id"].get_oid().value.to_string();

    if (view["original_game_id"] &&
        view["original_game_id"].type() == bsoncxx::type::k_oid) {
      game.original_game_id =
          view["original_game_id"].get_oid().value.to_string();
    }

    game.red_player = string(view["red_player"].get_string().value);
    game.black_player = string(view["black_player"].get_string().value);

    if (view["winner"] && view["winner"].type() == bsoncxx::type::k_string) {
      game.winner = string(view["winner"].get_string().value);
    }

    game.result = string(view["result"].get_string().value);
    game.time_control = string(view["time_control"].get_string().value);
    game.time_limit = view["time_limit"].get_int32().value;
    game.increment = view["increment"].get_int32().value;
    game.move_count = view["move_count"].get_int32().value;

    // Check for rated field (might not exist in old records)
    if (view["rated"] && view["rated"].type() == bsoncxx::type::k_bool) {
      game.rated = view["rated"].get_bool().value;
    } else {
      game.rated = true; // Default to rated
    }

    game.start_time = chrono::system_clock::time_point(
        chrono::milliseconds(view["start_time"].get_date().value.count()));
    game.end_time = chrono::system_clock::time_point(
        chrono::milliseconds(view["end_time"].get_date().value.count()));

    if (view["rematch_offered_by"] &&
        view["rematch_offered_by"].type() == bsoncxx::type::k_string) {
      game.rematch_offered_by =
          string(view["rematch_offered_by"].get_string().value);
    }

    // Check rematch_accepted field
    if (view["rematch_accepted"] &&
        view["rematch_accepted"].type() == bsoncxx::type::k_bool) {
      game.rematch_accepted = view["rematch_accepted"].get_bool().value;
    } else {
      game.rematch_accepted = false;
    }

    // Load moves for replay functionality
    if (view["moves"] && view["moves"].type() == bsoncxx::type::k_array) {
      for (auto &&moveDoc : view["moves"].get_array().value) {
        Move move;
        auto mv = moveDoc.get_document().value;

        move.move_number = mv["move_number"].get_int32().value;
        move.player = string(mv["player"].get_string().value);

        // Parse from/to coordinates - support both formats:
        // Old format: from_x, from_y, to_x, to_y (flat)
        // New format: from: {x, y}, to: {x, y} (nested)
        if (mv["from_x"] && mv["from_x"].type() == bsoncxx::type::k_int32) {
          // Old format (flat)
          move.from_x = mv["from_x"].get_int32().value;
          move.from_y = mv["from_y"].get_int32().value;
          move.to_x = mv["to_x"].get_int32().value;
          move.to_y = mv["to_y"].get_int32().value;
        } else if (mv["from"] &&
                   mv["from"].type() == bsoncxx::type::k_document) {
          // New format (nested)
          auto fromDoc = mv["from"].get_document().value;
          auto toDoc = mv["to"].get_document().value;
          move.from_x = fromDoc["x"].get_int32().value;
          move.from_y = fromDoc["y"].get_int32().value;
          move.to_x = toDoc["x"].get_int32().value;
          move.to_y = toDoc["y"].get_int32().value;
        } else {
          // Skip move if format is unknown
          continue;
        }

        if (mv["piece"] && mv["piece"].type() == bsoncxx::type::k_string) {
          move.piece = string(mv["piece"].get_string().value);
        }
        if (mv["captured"] &&
            mv["captured"].type() == bsoncxx::type::k_string) {
          move.captured = string(mv["captured"].get_string().value);
        }
        if (mv["notation"] &&
            mv["notation"].type() == bsoncxx::type::k_string) {
          move.notation = string(mv["notation"].get_string().value);
        }
        if (mv["xfen_after"] &&
            mv["xfen_after"].type() == bsoncxx::type::k_string) {
          move.xfen_after = string(mv["xfen_after"].get_string().value);
        }
        if (mv["timestamp"] &&
            mv["timestamp"].type() == bsoncxx::type::k_date) {
          move.timestamp = chrono::system_clock::time_point(
              chrono::milliseconds(mv["timestamp"].get_date().value.count()));
        }
        if (mv["time_taken"] &&
            mv["time_taken"].type() == bsoncxx::type::k_int32) {
          move.time_taken = mv["time_taken"].get_int32().value;
        }

        game.moves.push_back(move);
      }
    }

    return game;

  } catch (const exception &) {
    return nullopt;
  }
}

vector<ArchivedGame> GameRepository::findGameHistory(const string &username,
                                                     int limit, int offset) {
  vector<ArchivedGame> result;

  try {
    auto db = mongoClient.getDatabase();
    auto archive = db["game_archive"];

    // Find games where user was red or black player, sorted by end_time
    // descending
    auto cursor =
        archive.find(document{} << "$or" << open_array << open_document
                                << "red_player" << username << close_document
                                << open_document << "black_player" << username
                                << close_document << close_array << finalize,
                     mongocxx::options::find{}
                         .sort(document{} << "end_time" << -1 << finalize)
                         .skip(offset)
                         .limit(limit));

    for (auto &&doc : cursor) {
      ArchivedGame game;
      game.id = doc["_id"].get_oid().value.to_string();

      if (doc["original_game_id"] &&
          doc["original_game_id"].type() == bsoncxx::type::k_oid) {
        game.original_game_id =
            doc["original_game_id"].get_oid().value.to_string();
      }

      game.red_player = string(doc["red_player"].get_string().value);
      game.black_player = string(doc["black_player"].get_string().value);

      if (doc["winner"] && doc["winner"].type() == bsoncxx::type::k_string) {
        game.winner = string(doc["winner"].get_string().value);
      }

      game.result = string(doc["result"].get_string().value);
      game.time_control = string(doc["time_control"].get_string().value);
      game.time_limit = doc["time_limit"].get_int32().value;
      game.increment = doc["increment"].get_int32().value;
      game.move_count = doc["move_count"].get_int32().value;

      if (doc["rated"] && doc["rated"].type() == bsoncxx::type::k_bool) {
        game.rated = doc["rated"].get_bool().value;
      } else {
        game.rated = true;
      }

      game.start_time = chrono::system_clock::time_point(
          chrono::milliseconds(doc["start_time"].get_date().value.count()));
      game.end_time = chrono::system_clock::time_point(
          chrono::milliseconds(doc["end_time"].get_date().value.count()));

      // Note: moves not loaded here for performance (use findArchivedGameById
      // for full details)

      result.push_back(game);
    }

  } catch (const exception &) {
    // Return empty on error
  }

  return result;
}

bool GameRepository::setRematchOffer(const string &gameId,
                                     const string &username) {
  try {
    auto db = mongoClient.getDatabase();
    auto archive = db["game_archive"];

    // Try to update by _id first
    auto updateResult = archive.update_one(
        document{} << "_id" << bsoncxx::oid(gameId) << finalize,
        make_document(
            kvp("$set", make_document(kvp("rematch_offered_by", username)))));

    if (updateResult && updateResult->matched_count() > 0) {
      return true;
    }

    // If not found, try by original_game_id
    updateResult = archive.update_one(
        document{} << "original_game_id" << bsoncxx::oid(gameId) << finalize,
        make_document(
            kvp("$set", make_document(kvp("rematch_offered_by", username)))));

    return updateResult && updateResult->matched_count() > 0;

  } catch (const exception &) {
    return false;
  }
}

bool GameRepository::clearRematchOffer(const string &gameId) {
  try {
    auto db = mongoClient.getDatabase();
    auto archive = db["game_archive"];

    // Try to update by _id first
    auto updateResult = archive.update_one(
        document{} << "_id" << bsoncxx::oid(gameId) << finalize,
        make_document(
            kvp("$unset", make_document(kvp("rematch_offered_by", "")))));

    if (updateResult && updateResult->matched_count() > 0) {
      return true;
    }

    // If not found, try by original_game_id
    updateResult = archive.update_one(
        document{} << "original_game_id" << bsoncxx::oid(gameId) << finalize,
        make_document(
            kvp("$unset", make_document(kvp("rematch_offered_by", "")))));

    return updateResult && updateResult->matched_count() > 0;

  } catch (const exception &) {
    return false;
  }
}

bool GameRepository::setRematchAccepted(const string &gameId) {
  try {
    auto db = mongoClient.getDatabase();
    auto archive = db["game_archive"];

    // Try to update by _id first
    auto updateResult = archive.update_one(
        document{} << "_id" << bsoncxx::oid(gameId) << finalize,
        make_document(kvp("$set", make_document(kvp("rematch_accepted", true),
                                                kvp("rematch_offered_by",
                                                    "") // Clear the offer
                                                ))));

    if (updateResult && updateResult->matched_count() > 0) {
      return true;
    }

    // If not found, try by original_game_id
    updateResult = archive.update_one(
        document{} << "original_game_id" << bsoncxx::oid(gameId) << finalize,
        make_document(
            kvp("$set", make_document(kvp("rematch_accepted", true),
                                      kvp("rematch_offered_by", "")))));

    return updateResult && updateResult->matched_count() > 0;

  } catch (const exception &) {
    return false;
  }
}

vector<Game> GameRepository::findByUser(const string &username,
                                        const string &filter, int limit) {
  vector<Game> result;

  try {
    auto db = mongoClient.getDatabase();
    auto games = db["active_games"];

    // Build query - using username in player name fields
    bsoncxx::builder::stream::document queryBuilder;
    queryBuilder << "$or" << open_array << open_document << "red_player"
                 << username << close_document << open_document
                 << "black_player" << username << close_document << close_array;

    if (filter == "active") {
      queryBuilder << "status" << "in_progress";
    } else if (filter == "completed") {
      queryBuilder << "status" << "completed";
    }

    mongocxx::options::find opts;
    opts.sort(document{} << "start_time" << -1 << finalize);
    opts.limit(limit);

    auto cursor = games.find(queryBuilder << finalize, opts);

    for (auto &&doc : cursor) {
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

  } catch (const exception &) {
    // Return empty vector on error
  }

  return result;
}

// ============ Player Stats Operations ============

bool GameRepository::updatePlayerStats(const string &username,
                                       const string &timeControl, int newRating,
                                       double newRD, double newVolatility,
                                       const string &resultField) {
  try {
    auto db = mongoClient.getDatabase();
    auto stats = db["player_stats"];

    // Use upsert to create record if it doesn't exist
    // Now also update RD and volatility (Glicko-2)
    auto updateDoc = document{}
<<<<<<< HEAD
                     << "$set" << open_document 
                       << "rating" << newRating
                       << "rd" << newRD
                       << "volatility" << newVolatility
                       << "username" << username
                       << "time_control" << timeControl
                     << close_document 
                     << "$inc" << open_document
                       << "total_games" << 1 << resultField << 1 
                     << close_document
                     << "$max" << open_document << "highest_rating" << newRating
                     << close_document 
                     << "$min" << open_document << "lowest_rating" << newRating
                     << close_document
                     << "$setOnInsert" << open_document
                       << "win_streak" << 0
                       << "longest_win_streak" << 0
                     << close_document
                     << finalize;
=======
                     << "$set" << open_document << "rating" << newRating
                     << "username" << username << "time_control" << timeControl
                     << close_document << "$inc" << open_document
                     << "total_games" << 1 << resultField << 1 << close_document
                     << "$max" << open_document << "highest_rating" << newRating
                     << close_document << "$min" << open_document
                     << "lowest_rating" << newRating << close_document
                     << "$setOnInsert" << open_document << "rd" << 350.0
                     << "volatility" << 0.06 << "win_streak" << 0
                     << "longest_win_streak" << 0 << close_document << finalize;
>>>>>>> origin/main

    mongocxx::options::update options;
    options.upsert(true); // Create if not exists

    auto result =
        stats.update_one(document{} << "username" << username << "time_control"
                                    << timeControl << finalize,
                         updateDoc.view(), options);

    return result && (result->matched_count() > 0 || result->upserted_id());

  } catch (const exception &e) {
    cerr << "[updatePlayerStats] Error: " << e.what() << endl;
    return false;
  }
}

GameRepository::PlayerGlickoStats GameRepository::getPlayerGlickoStats(
    const string &username, const string &timeControl) {
  PlayerGlickoStats stats;
  // Default values for new players (Glicko-2 defaults)
  stats.rating = 1500;
  stats.rd = 350.0;
  stats.volatility = 0.06;

  try {
    auto db = mongoClient.getDatabase();
    auto statsCol = db["player_stats"];

    auto result =
        statsCol.find_one(document{} << "username" << username << "time_control"
                                     << timeControl << finalize);

    if (result) {
      auto view = result->view();
      if (view["rating"]) {
        stats.rating = view["rating"].get_int32().value;
      }
      if (view["rd"]) {
        stats.rd = view["rd"].get_double().value;
      }
      if (view["volatility"]) {
        stats.volatility = view["volatility"].get_double().value;
      }
    }
  } catch (const exception &e) {
    cerr << "[getPlayerGlickoStats] Error: " << e.what() << endl;
  }

  return stats;
}

int GameRepository::getPlayerRating(const string &username,
                                    const string &timeControl) {
  return getPlayerGlickoStats(username, timeControl).rating;
}

// ============ Helper Operations ============

bool GameRepository::userExists(const string &username) {
  auto db = mongoClient.getDatabase();
  auto users = db["users"];

  auto result =
      users.find_one(document{} << "username" << username << finalize);
  return result.has_value();
}
