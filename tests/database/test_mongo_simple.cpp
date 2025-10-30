#include "../../include/database/mongodb_client.h"
#include "../../include/config/config_loader.h"
#include <bsoncxx/json.hpp>
#include <iostream>

int main() {
    std::cout << "========================================" << std::endl;
    std::cout << "  MongoDB Connection Test (Simple)" << std::endl;
    std::cout << "========================================\n" << std::endl;
    
    // Load configuration from .env
    ConfigLoader config;
    if (!config.load(".env")) {
        std::cerr << " Failed to load .env file" << std::endl;
        return 1;
    }
    
    std::string connectionString = config.getString("MONGODB_CONNECTION_STRING");
    std::string databaseName = config.getString("MONGODB_DATABASE_NAME");
    
    MongoDBClient client;
    
    if (client.connect(connectionString, databaseName)) {
        std::cout << " MongoDB connected successfully!" << std::endl;
        
        // Test ping
        if (client.isConnected()) {
            std::cout << " MongoDB is responding to pings" << std::endl;
        }
        
        // List collections
        auto db = client.getDatabase();
        auto cursor = db.list_collections();
        
        std::cout << "\n Collections in database:" << std::endl;
        for (auto&& doc : cursor) {
            std::cout << "   - " << bsoncxx::to_json(doc) << std::endl;
        }
        
        std::cout << "\n Test completed successfully!" << std::endl;
        return 0;
    } else {
        std::cerr << " Failed to connect to MongoDB" << std::endl;
        return 1;
    }
}
