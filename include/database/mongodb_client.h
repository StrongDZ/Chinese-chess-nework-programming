#ifndef MONGODB_CLIENT_H
#define MONGODB_CLIENT_H

#include <mongocxx/client.hpp>
#include <mongocxx/instance.hpp>
#include <mongocxx/uri.hpp>
#include <mongocxx/database.hpp>
#include <string>

class MongoDBClient {
private:
    static mongocxx::instance instance;
    mongocxx::client client;
    mongocxx::database database;
    std::string connectionString;
    std::string databaseName;

public:
    MongoDBClient();
    ~MongoDBClient();
    
    bool connect(const std::string& configPath);
    mongocxx::database& getDatabase();
    bool isConnected() const;
};

#endif
