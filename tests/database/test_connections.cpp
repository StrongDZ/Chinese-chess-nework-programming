// Test kết nối MongoDB + Redis
#include "../../include/database/mongodb_client.h"
#include "../../include/database/redis_client.h"
#include <iostream>

using namespace std;

int main() {
    cout << "=== DATABASE CONNECTION TEST ===" << endl;
    
    // 1. Test MongoDB
    MongoDBClient mongoClient;
    bool mongoOk = mongoClient.connect(
        "mongodb+srv://admin:admin@chess.p8k9xpw.mongodb.net/chess_server_game?retryWrites=true&w=majority",
        "chess_server_game"
    );
    
    if (mongoOk) {
        cout << "MongoDB:  Connected" << endl;
        
        // Test lấy database
        auto db = mongoClient.getDatabase();
        cout << "Database name: " << db.name() << endl;
    } else {
        cout << "MongoDB:  Failed" << endl;
    }
    
    cout << endl;
    
    // 2. Test Redis
    RedisClient redisClient;
    bool redisOk = redisClient.connect("127.0.0.1", 6379);
    
    if (redisOk) {
        cout << "Redis:  Connected" << endl;
        
        // Test ping
        if (redisClient.ping()) {
            cout << "Redis ping: PONG" << endl;
        }
        
        // Test session
        redisClient.saveSession("test_token_123", "user_abc", 60);
        string userId = redisClient.getSession("test_token_123");
        cout << "Session test: " << (userId == "user_abc" ? " OK" : " FAILED") << endl;
        redisClient.deleteSession("test_token_123");
        
    } else {
        cout << "Redis:  Failed" << endl;
    }
    
    cout << "\n=== TEST COMPLETED ===" << endl;
    return 0;
}
