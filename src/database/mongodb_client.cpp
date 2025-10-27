#include "../../include/database/mongodb_client.h"
#include <bsoncxx/json.hpp>
#include <fstream>
#include <iostream>
#include <json/json.h>

using namespace std;

mongocxx::instance MongoDBClient::instance{};

MongoDBClient::MongoDBClient() 
    : client(mongocxx::uri{}), database(client["test"]) {
}

MongoDBClient::~MongoDBClient() {
}

bool MongoDBClient::connect(const string& configPath) {
    try {
        ifstream configFile(configPath);
        if (!configFile.is_open()) {
            cerr << "Failed to open config: " << configPath << endl;
            return false;
        }

        Json::Value config;
        Json::CharReaderBuilder reader;
        string errors;
        
        if (!Json::parseFromStream(reader, configFile, &config, &errors)) {
            cerr << "JSON parse error: " << errors << endl;
            return false;
        }

        connectionString = config["mongodb"]["connection_string"].asString();
        databaseName = config["mongodb"]["database_name"].asString();

        mongocxx::uri uri(connectionString);
        client = mongocxx::client(uri);
        database = client[databaseName];

        database.run_command(bsoncxx::from_json(R"({"ping": 1})"));

        cout << "Connected to MongoDB: " << databaseName << endl;
        return true;

    } catch (const exception& e) {
        cerr << "MongoDB error: " << e.what() << endl;
        return false;
    }
}

mongocxx::database& MongoDBClient::getDatabase() {
    return database;
}

bool MongoDBClient::isConnected() const {
    return true;  
}
