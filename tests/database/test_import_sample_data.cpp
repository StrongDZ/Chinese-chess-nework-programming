#include "../../include/database/mongodb_client.h"
#include "../../include/config/config_loader.h"
#include <bsoncxx/builder/stream/document.hpp>
#include <bsoncxx/json.hpp>
#include <mongocxx/client.hpp>
#include <iostream>
#include <vector>
#include <ctime>

using namespace std;
using bsoncxx::builder::stream::document;
using bsoncxx::builder::stream::finalize;
using bsoncxx::builder::stream::open_document;
using bsoncxx::builder::stream::close_document;
using bsoncxx::builder::stream::open_array;
using bsoncxx::builder::stream::close_array;

// Hàm tạo timestamp hiện tại
bsoncxx::types::b_date getCurrentTimestamp() {
    auto now = std::chrono::system_clock::now();
    return bsoncxx::types::b_date{now};
}

void importUsers(mongocxx::database& db) {
    cout << "\n=== IMPORTING USERS ===" << endl;
    
    auto collection = db["users"];
    
    // Xóa dữ liệu cũ
    try {
        collection.delete_many({});
    } catch (...) {}
    
    vector<bsoncxx::document::value> users;
    
    // User 1: Active player
    users.push_back(
        document{} 
            << "username" << "player1"
            << "email" << "player1@example.com"
            << "password_hash" << "$2a$10$N9qo8uLOickgx2ZMRZoMye1234567890abcdefghijk"
            << "display_name" << "Cao Thủ 1"
            << "created_at" << getCurrentTimestamp()
            << "last_login" << getCurrentTimestamp()
            << "is_online" << true
            << "status" << "active"
            << "avatar_path" << "resources/avatars/avatar_1.jpg"
            << "country" << "VN"
            << finalize
    );
    
    // User 2: Active player
    users.push_back(
        document{} 
            << "username" << "player2"
            << "email" << "player2@example.com"
            << "password_hash" << "$2a$10$N9qo8uLOickgx2ZMRZoMye0987654321zyxwvutsrqp"
            << "display_name" << "Cao Thủ 2"
            << "created_at" << getCurrentTimestamp()
            << "last_login" << getCurrentTimestamp()
            << "is_online" << true
            << "status" << "active"
            << "avatar_path" << "resources/avatars/avatar_2.jpg"
            << "country" << "VN"
            << finalize
    );
    
    // User 3: Inactive player
    users.push_back(
        document{} 
            << "username" << "player3"
            << "email" << "player3@example.com"
            << "password_hash" << "$2a$10$N9qo8uLOickgx2ZMRZoMyeABCDEF1234567890QWERTY"
            << "display_name" << "Người Chơi 3"
            << "created_at" << getCurrentTimestamp()
            << "last_login" << getCurrentTimestamp()
            << "is_online" << false
            << "status" << "inactive"
            << "country" << "US"
            << finalize
    );
    
    // User 4: Beginner
    users.push_back(
        document{} 
            << "username" << "newplayer"
            << "email" << "newplayer@example.com"
            << "password_hash" << "$2a$10$N9qo8uLOickgx2ZMRZoMyeXYZ9876543210LMNOPQRS"
            << "display_name" << "Người Mới"
            << "created_at" << getCurrentTimestamp()
            << "last_login" << getCurrentTimestamp()
            << "is_online" << true
            << "status" << "active"
            << "country" << "CN"
            << finalize
    );
    
    // Insert users
    for (const auto& user : users) {
        try {
            auto result = collection.insert_one(user.view());
            if (result) {
                cout << " Inserted user: " << user.view()["username"].get_string().value << endl;
            }
        } catch (const exception& e) {
            cerr << "Error inserting user: " << e.what() << endl;
        }
    }
    
    cout << "Total users inserted: " << users.size() << endl;
}

void importPlayerStats(mongocxx::database& db) {
    cout << "\n=== IMPORTING PLAYER STATS ===" << endl;
    
    auto users_collection = db["users"];
    auto stats_collection = db["player_stats"];
    
    // Xóa dữ liệu cũ
    try {
        stats_collection.delete_many({});
    } catch (...) {}
    
    // Lấy tất cả users
    auto cursor = users_collection.find({});
    
    int count = 0;
    for (auto&& doc : cursor) {
        auto username = doc["username"].get_string().value;
        auto user_id = doc["_id"].get_oid().value;
        
        // Tạo stats cho từng time_control
        vector<string> time_controls = {"bullet", "blitz", "classical"};
        
        for (const auto& tc : time_controls) {
            int base_rating = 1500;
            if (tc == "bullet") base_rating = 1400;
            else if (tc == "blitz") base_rating = 1500;
            else base_rating = 1600;
            
            auto stats = document{}
                << "user_id" << user_id
                << "time_control" << tc
                << "total_games" << 50
                << "wins" << 25
                << "losses" << 20
                << "draws" << 5
                << "rating" << base_rating
                << "highest_rating" << base_rating + 100
                << "lowest_rating" << base_rating - 100
                << "win_streak" << 3
                << "longest_win_streak" << 7
                << "total_playtime" << 90000 // 25 hours in seconds
                << "last_game_time" << getCurrentTimestamp()
                << "rd" << 30.5 // Rating Deviation
                << "volatility" << 0.06
                << finalize;
            
            try {
                auto result = stats_collection.insert_one(stats.view());
                if (result) {
                    count++;
                }
            } catch (const exception& e) {
                cerr << "Error inserting stats: " << e.what() << endl;
            }
        }
        
        cout << " Inserted stats for: " << username << " (3 time controls)" << endl;
    }
    
    cout << "Total player stats inserted: " << count << endl;
}

void importActiveGames(mongocxx::database& db) {
    cout << "\n=== IMPORTING ACTIVE GAMES ===" << endl;
    
    auto users_collection = db["users"];
    auto games_collection = db["active_games"];
    
    // Xóa dữ liệu cũ
    try {
        games_collection.delete_many({});
    } catch (...) {}
    
    // Lấy users
    auto cursor = users_collection.find({});
    vector<bsoncxx::document::view> user_docs;
    for (auto&& doc : cursor) {
        user_docs.push_back(bsoncxx::document::view(doc));
        if (user_docs.size() >= 4) break;
    }
    
    if (user_docs.size() < 2) {
        cout << "Not enough users to create games!" << endl;
        return;
    }
    
    // Game 1: player1 (red) vs player2 (black) - in progress
    // Note: Schema uses red/black for Chinese chess (Xiangqi), not white/black
    auto game1 = document{}
        << "red_player_id" << user_docs[0]["_id"].get_oid().value
        << "black_player_id" << user_docs[1]["_id"].get_oid().value
        << "red_player_name" << user_docs[0]["username"].get_string().value
        << "black_player_name" << user_docs[1]["username"].get_string().value
        << "status" << "in_progress"
        << "winner_id" << bsoncxx::types::b_null()
        << "result" << bsoncxx::types::b_null()
        << "start_time" << getCurrentTimestamp()
        << "end_time" << bsoncxx::types::b_null()
        << "fen" << "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"
        << "xfen" << "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"
        << "moves" << open_array
            << open_document
                << "move_number" << 1
                << "player_id" << user_docs[0]["_id"].get_oid().value
                << "from" << open_document
                    << "x" << 1
                    << "y" << 0
                << close_document
                << "to" << open_document
                    << "x" << 2
                    << "y" << 2
                << close_document
                << "piece" << "horse"
                << "captured" << bsoncxx::types::b_null()
                << "notation" << "Nb1-c3"
                << "fen_after" << "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR b - - 1 1"
                << "timestamp" << getCurrentTimestamp()
                << "time_taken" << 5
            << close_document
            << open_document
                << "move_number" << 2
                << "player_id" << user_docs[1]["_id"].get_oid().value
                << "from" << open_document
                    << "x" << 1
                    << "y" << 9
                << close_document
                << "to" << open_document
                    << "x" << 2
                    << "y" << 7
                << close_document
                << "piece" << "horse"
                << "captured" << bsoncxx::types::b_null()
                << "notation" << "Nb8-c6"
                << "fen_after" << "r1bakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 2 2"
                << "timestamp" << getCurrentTimestamp()
                << "time_taken" << 8
            << close_document
        << close_array
        << "current_turn" << "white"
        << "move_count" << 2
        << "time_control" << "blitz"
        << "time_limit" << 300 // 5 minutes
        << "red_time_remaining" << 280
        << "black_time_remaining" << 275
        << "increment" << 3
        << "rated" << true
        << finalize;
    
    try {
        games_collection.insert_one(game1.view());
        cout << " Inserted game: " << user_docs[0]["username"].get_string().value 
             << " vs " << user_docs[1]["username"].get_string().value << " (in progress)" << endl;
    } catch (const exception& e) {
        cerr << "Error inserting game 1: " << e.what() << endl;
    }
    
    // Game 2: player3 vs player4 - waiting
    if (user_docs.size() >= 4) {
        auto game2 = document{}
            << "red_player_id" << user_docs[2]["_id"].get_oid().value
            << "black_player_id" << user_docs[3]["_id"].get_oid().value
            << "red_player_name" << user_docs[2]["username"].get_string().value
            << "black_player_name" << user_docs[3]["username"].get_string().value
            << "status" << "waiting"
            << "winner_id" << bsoncxx::types::b_null()
            << "result" << bsoncxx::types::b_null()
            << "start_time" << getCurrentTimestamp()
            << "end_time" << bsoncxx::types::b_null()
            << "fen" << "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"
            << "xfen" << "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"
            << "moves" << open_array << close_array
            << "current_turn" << "white"
            << "move_count" << 0
            << "time_control" << "classical"
            << "time_limit" << 1800 // 30 minutes
            << "red_time_remaining" << 1800
            << "black_time_remaining" << 1800
            << "increment" << 30
            << "rated" << true
            << finalize;
        
        try {
            games_collection.insert_one(game2.view());
            cout << " Inserted game: " << user_docs[2]["username"].get_string().value 
                 << " vs " << user_docs[3]["username"].get_string().value << " (waiting)" << endl;
        } catch (const exception& e) {
            cerr << "Error inserting game 2: " << e.what() << endl;
        }
    }
    
    cout << "Total active games inserted: 2" << endl;
}

void importGameArchive(mongocxx::database& db) {
    cout << "\n=== IMPORTING GAME ARCHIVE ===" << endl;
    
    auto users_collection = db["users"];
    auto archive_collection = db["game_archive"];
    
    // Xóa dữ liệu cũ
    try {
        archive_collection.delete_many({});
    } catch (...) {}
    
    // Lấy users
    auto cursor = users_collection.find({});
    vector<bsoncxx::document::view> user_docs;
    for (auto&& doc : cursor) {
        user_docs.push_back(bsoncxx::document::view(doc));
        if (user_docs.size() >= 4) break;
    }
    
    if (user_docs.size() < 2) {
        cout << "Not enough users to create archive!" << endl;
        return;
    }
    
    // Archived Game 1: white won by checkmate
    auto original_game_id = bsoncxx::oid();
    auto archive1 = document{}
        << "original_game_id" << original_game_id
        << "white_player_id" << user_docs[0]["_id"].get_oid().value
        << "black_player_id" << user_docs[1]["_id"].get_oid().value
        << "white_player_name" << user_docs[0]["username"].get_string().value
        << "black_player_name" << user_docs[1]["username"].get_string().value
        << "white_rating_before" << 1500
        << "black_rating_before" << 1450
        << "white_rating_after" << 1515
        << "black_rating_after" << 1435
        << "winner_id" << user_docs[0]["_id"].get_oid().value
        << "result" << "white_win"
        << "termination" << "checkmate"
        << "start_time" << getCurrentTimestamp()
        << "end_time" << getCurrentTimestamp()
        << "initial_fen" << "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"
        << "final_fen" << "rnbak4/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAK4 b - - 45 23"
        << "moves" << open_array
            << open_document
                << "move_number" << 1
                << "player_id" << user_docs[0]["_id"].get_oid().value
                << "from" << open_document
                    << "x" << 1
                    << "y" << 0
                << close_document
                << "to" << open_document
                    << "x" << 2
                    << "y" << 2
                << close_document
                << "piece" << "horse"
                << "captured" << bsoncxx::types::b_null()
                << "notation" << "Nb1-c3"
                << "fen_after" << "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR b - - 1 1"
                << "timestamp" << getCurrentTimestamp()
                << "time_taken" << 5
            << close_document
        << close_array
        << "pgn" << "1. Nb1-c3 Nb8-c6 2. Cc1-e2 ... 23. Checkmate"
        << "move_count" << 45
        << "time_control" << "blitz"
        << "time_limit" << 300
        << "increment" << 3
        << "average_move_time" << 6.5
        << "longest_think_time" << 45
        << "archived_at" << getCurrentTimestamp()
        << finalize;
    
    try {
        archive_collection.insert_one(archive1.view());
        cout << " Inserted archived game: white won by checkmate" << endl;
    } catch (const exception& e) {
        cerr << "Error inserting archive 1: " << e.what() << endl;
    }
    
    // Archived Game 2: draw by stalemate
    if (user_docs.size() >= 3) {
        auto original_game_id2 = bsoncxx::oid();
        auto archive2 = document{}
            << "original_game_id" << original_game_id2
            << "white_player_id" << user_docs[1]["_id"].get_oid().value
            << "black_player_id" << user_docs[2]["_id"].get_oid().value
            << "white_player_name" << user_docs[1]["username"].get_string().value
            << "black_player_name" << user_docs[2]["username"].get_string().value
            << "white_rating_before" << 1435
            << "black_rating_before" << 1400
            << "white_rating_after" << 1435
            << "black_rating_after" << 1400
            << "winner_id" << bsoncxx::types::b_null()
            << "result" << "draw"
            << "termination" << "stalemate"
            << "start_time" << getCurrentTimestamp()
            << "end_time" << getCurrentTimestamp()
            << "initial_fen" << "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"
            << "final_fen" << "4k4/9/9/9/9/9/9/9/9/4K4 b - - 60 30"
            << "moves" << open_array << close_array // Abbreviated
            << "pgn" << "1. ... 30. Stalemate"
            << "move_count" << 60
            << "time_control" << "classical"
            << "time_limit" << 1800
            << "increment" << 30
            << "average_move_time" << 28.5
            << "longest_think_time" << 180
            << "archived_at" << getCurrentTimestamp()
            << finalize;
        
        try {
            archive_collection.insert_one(archive2.view());
            cout << " Inserted archived game: draw by stalemate" << endl;
        } catch (const exception& e) {
            cerr << "Error inserting archive 2: " << e.what() << endl;
        }
    }
    
    // Archived Game 3: black won by resignation
    if (user_docs.size() >= 4) {
        auto original_game_id3 = bsoncxx::oid();
        auto archive3 = document{}
            << "original_game_id" << original_game_id3
            << "white_player_id" << user_docs[2]["_id"].get_oid().value
            << "black_player_id" << user_docs[3]["_id"].get_oid().value
            << "white_player_name" << user_docs[2]["username"].get_string().value
            << "black_player_name" << user_docs[3]["username"].get_string().value
            << "white_rating_before" << 1400
            << "black_rating_before" << 1200
            << "white_rating_after" << 1388
            << "black_rating_after" << 1212
            << "winner_id" << user_docs[3]["_id"].get_oid().value
            << "result" << "black_win"
            << "termination" << "resignation"
            << "start_time" << getCurrentTimestamp()
            << "end_time" << getCurrentTimestamp()
            << "initial_fen" << "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"
            << "final_fen" << "rnbak4/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAK4 w - - 32 16"
            << "moves" << open_array << close_array // Abbreviated
            << "pgn" << "1. ... 16. White resigned"
            << "move_count" << 32
            << "time_control" << "bullet"
            << "time_limit" << 60
            << "increment" << 0
            << "average_move_time" << 1.8
            << "longest_think_time" << 8
            << "archived_at" << getCurrentTimestamp()
            << finalize;
        
        try {
            archive_collection.insert_one(archive3.view());
            cout << " Inserted archived game: black won by resignation" << endl;
        } catch (const exception& e) {
            cerr << "Error inserting archive 3: " << e.what() << endl;
        }
    }
    
    cout << "Total archived games inserted: 3" << endl;
}

void displayCollectionStats(mongocxx::database& db) {
    cout << "\n=== COLLECTION STATISTICS ===" << endl;
    
    vector<string> collections = {"users", "player_stats", "active_games", "game_archive"};
    
    for (const auto& coll_name : collections) {
        auto collection = db[coll_name];
        auto count = collection.count_documents({});
        cout << "Collection '" << coll_name << "': " << count << " documents" << endl;
    }
}

void displaySampleDocuments(mongocxx::database& db) {
    cout << "\n=== SAMPLE DOCUMENTS ===" << endl;
    
    // Show one user
    cout << "\n--- Sample User ---" << endl;
    auto users = db["users"];
    auto user_doc = users.find_one({});
    if (user_doc) {
        cout << bsoncxx::to_json(user_doc->view()) << endl;
    }
    
    // Show one player stat
    cout << "\n--- Sample Player Stat ---" << endl;
    auto stats = db["player_stats"];
    auto stat_doc = stats.find_one({});
    if (stat_doc) {
        cout << bsoncxx::to_json(stat_doc->view()) << endl;
    }
    
    // Show one active game
    cout << "\n--- Sample Active Game ---" << endl;
    auto games = db["active_games"];
    auto game_doc = games.find_one({});
    if (game_doc) {
        cout << bsoncxx::to_json(game_doc->view()) << endl;
    }
    
    // Show one archived game
    cout << "\n--- Sample Archived Game ---" << endl;
    auto archive = db["game_archive"];
    auto archive_doc = archive.find_one({});
    if (archive_doc) {
        cout << bsoncxx::to_json(archive_doc->view()) << endl;
    }
}

int main() {
    cout << "==================================================" << endl;
    cout << "   MongoDB Sample Data Import Tool" << endl;
    cout << "==================================================" << endl;
    
    // Load configuration
    ConfigLoader config;
    if (!config.load(".env")) {
        cerr << "Failed to load .env file!" << endl;
        return 1;
    }
    
    string mongoUri = config.getString("MONGODB_CONNECTION_STRING");
    string dbName = config.getString("MONGODB_DATABASE_NAME");
    
    cout << "MongoDB Database: " << dbName << endl;
    
    // Connect to MongoDB
    MongoDBClient mongoClient;
    if (!mongoClient.connect(mongoUri, dbName)) {
        cerr << "Failed to connect to MongoDB!" << endl;
        return 1;
    }
    
    auto db = mongoClient.getDatabase();
    
    // Import data into all collections
    importUsers(db);
    importPlayerStats(db);
    importActiveGames(db);
    importGameArchive(db);
    
    // Display statistics
    displayCollectionStats(db);
    
    // Display sample documents
    displaySampleDocuments(db);
    
    cout << "\n==================================================" << endl;
    cout << "    Sample Data Import Completed!" << endl;
    cout << "==================================================" << endl;
    cout << "\nNow you can:" << endl;
    cout << "1. Connect to MongoDB Compass/Atlas" << endl;
    cout << "2. View the collections: users, player_stats, active_games, game_archive" << endl;
    cout << "3. Use Schema tab to see inferred schema" << endl;
    cout << "4. Export schema for documentation" << endl;
    
    return 0;
}
