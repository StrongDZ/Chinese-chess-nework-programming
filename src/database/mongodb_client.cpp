#include "../../include/database/mongodb_client.h"
#include <bsoncxx/builder/stream/document.hpp>
#include <bsoncxx/json.hpp>
#include <iostream>

using namespace std;
using bsoncxx::builder::stream::document;
using bsoncxx::builder::stream::finalize;

mongocxx::instance MongoDBClient::instance{};

MongoDBClient::MongoDBClient() {}

MongoDBClient::~MongoDBClient() {}

bool MongoDBClient::connect(const string& connStr, const string& dbName) {
    try {
        connectionString = connStr;
        databaseName = dbName;
        
        // Tạo URI và client
        mongocxx::uri uri(connStr);
        client = mongocxx::client(uri);
        
        // Lấy database reference
        database = client[dbName];
        
        // Test kết nối bằng ping
        auto admin = client["admin"];
        auto pingCmd = document{} << "ping" << 1 << finalize;
        auto result = admin.run_command(pingCmd.view());
        
        cout << " MongoDB connected: " << dbName << endl;
        return true;
        
    } catch (const exception& e) {
        cerr << " MongoDB connection failed: " << e.what() << endl;
        return false;
    }
}

mongocxx::database& MongoDBClient::getDatabase() {
    return database;
}

bool MongoDBClient::isConnected() const {
    try {
        // Ping để check connection
        auto admin = client["admin"];
        auto pingCmd = document{} << "ping" << 1 << finalize;
        admin.run_command(pingCmd.view());
        return true;
    } catch (...) {
        return false;
    }
}
