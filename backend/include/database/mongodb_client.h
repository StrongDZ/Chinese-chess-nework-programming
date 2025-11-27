#ifndef MONGODB_CLIENT_H
#define MONGODB_CLIENT_H

#include <mongocxx/client.hpp>
#include <mongocxx/instance.hpp>
#include <mongocxx/uri.hpp>
#include <mongocxx/database.hpp>
#include <string>

// Quản lý kết nối MongoDB
class MongoDBClient {
private:
    static mongocxx::instance instance;  // Singleton instance (khởi tạo 1 lần)
    mongocxx::client client;
    mongocxx::database database;
    std::string connectionString;
    std::string databaseName;

public:
    MongoDBClient();
    ~MongoDBClient();
    
    // Kết nối tới MongoDB Atlas/Local
    bool connect(const std::string& connectionString, const std::string& databaseName);
    
    // Lấy database handle để thao tác collections
    mongocxx::database& getDatabase();
    
    // Kiểm tra kết nối
    bool isConnected() const;
};

#endif
