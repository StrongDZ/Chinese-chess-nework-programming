#include "../../include/database/mongodb_client.h"
#include <bsoncxx/json.hpp>
#include <iostream>

using namespace std;

mongocxx::instance MongoDBClient::instance{};

MongoDBClient::MongoDBClient() 
    : client(mongocxx::uri{}), database(client["test"]) {
}

MongoDBClient::~MongoDBClient() {
}

bool MongoDBClient::connect(const string& connectionString, const string& databaseName) {
    try {
        if (connectionString.empty()) {
            cerr << "MongoDB connection string is empty!" << endl;
            cerr << "Please configure MONGODB_CONNECTION_STRING in .env file" << endl;
            return false;
        }
        
        if (databaseName.empty()) {
            cerr << "MongoDB database name is empty!" << endl;
            cerr << "Please configure MONGODB_DATABASE_NAME in .env file" << endl;
            return false;
        }

        this->connectionString = connectionString;
        this->databaseName = databaseName;

        mongocxx::uri uri(connectionString);
        client = mongocxx::client(uri);
        database = client[databaseName];

        // Test connection with ping
        database.run_command(bsoncxx::from_json(R"({"ping": 1})"));

        cout << "Connected to MongoDB: " << databaseName << endl;
        return true;

    } catch (const exception& e) {
        cerr << "MongoDB connection error: " << e.what() << endl;
        return false;
    }
}

mongocxx::database& MongoDBClient::getDatabase() {
    return database;
}

bool MongoDBClient::isConnected() const {
    return true;  
}
