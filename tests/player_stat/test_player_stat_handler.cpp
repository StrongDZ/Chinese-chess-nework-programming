#include "../../include/player_stat/player_stat_handler.h"
#include "../../include/auth/auth_handler.h"
#include "../../include/challenge/challenge_handler.h"
#include "../../include/game/game_handler.h"
#include <iostream>
#include <json/json.h>

using namespace std;

int main() {
    cout << "=== PLAYER STATS HANDLER TEST ===" << endl;
    
    try {
        // 1. Kết nối database
        MongoDBClient mongoClient;
        mongoClient.connect("mongodb+srv://admin:admin@chess.p8k9xpw.mongodb.net/", "chess_server_game");
        
        RedisClient redisClient;
        redisClient.connect("127.0.0.1", 6379);
        
        cout << "✓ MongoDB connected: " << mongoClient.getDatabase().name() << endl;
        cout << "✓ Redis connected: 127.0.0.1:6379" << endl << endl;
        
        PlayerStatsHandler statsHandler(mongoClient, redisClient);
        AuthHandler authHandler(mongoClient, redisClient);
        ChallengeHandler challengeHandler(mongoClient, redisClient);
        GameHandler gameHandler(mongoClient, redisClient);
        
        // 2. Setup: Tạo 2 players
        cout << "--- Setup: Create 2 players ---" << endl;
        
        Json::Value reg1;
        reg1["username"] = "statsplayer1";
        reg1["email"] = "statsplayer1@test.com";
        reg1["password"] = "password123";
        reg1["display_name"] = "Stats Player 1";
        reg1["avatar_id"] = 1;
        
        auto regResp1 = authHandler.handleRegister(reg1);
        if (regResp1["success"].asBool()) {
            cout << "Player1 registered ✓" << endl;
        } else {
            cout << "Player1 already exists" << endl;
        }
        
        Json::Value reg2;
        reg2["username"] = "statsplayer2";
        reg2["email"] = "statsplayer2@test.com";
        reg2["password"] = "password123";
        reg2["display_name"] = "Stats Player 2";
        reg2["avatar_id"] = 2;
        
        auto regResp2 = authHandler.handleRegister(reg2);
        if (regResp2["success"].asBool()) {
            cout << "Player2 registered ✓" << endl;
        } else {
            cout << "Player2 already exists" << endl;
        }
        cout << endl;
        
        // 3. Login
        cout << "--- Setup: Login players ---" << endl;
        
        Json::Value login1;
        login1["username"] = "statsplayer1";
        login1["password"] = "password123";
        auto loginResp1 = authHandler.handleLogin(login1);
        string token1 = loginResp1["data"]["token"].asString();
        string player1Id = loginResp1["data"]["user_id"].asString();
        cout << "Player1 ID: " << player1Id << endl;
        
        Json::Value login2;
        login2["username"] = "statsplayer2";
        login2["password"] = "password123";
        auto loginResp2 = authHandler.handleLogin(login2);
        string token2 = loginResp2["data"]["token"].asString();
        string player2Id = loginResp2["data"]["user_id"].asString();
        cout << "Player2 ID: " << player2Id << endl << endl;
        
        // 4. Test Get Stats (before any games)
        cout << "--- Test: Get Initial Stats ---" << endl;
        Json::Value getStats1;
        getStats1["user_id"] = player1Id;
        getStats1["time_control"] = "all";
        
        auto statsResp1 = statsHandler.handleGetStats(getStats1);
        cout << Json::StyledWriter().write(statsResp1) << endl;
        
        // 5. Tạo và chơi game
        cout << "--- Setup: Create and play a game ---" << endl;
        
        // Create challenge
        Json::Value createChallenge;
        createChallenge["token"] = token1;
        createChallenge["time_control"] = "blitz";
        createChallenge["rated"] = true;
        createChallenge["opponent_id"] = player2Id;
        
        auto challengeResp = challengeHandler.handleCreateChallenge(createChallenge);
        string challengeId = challengeResp["data"]["challenge_id"].asString();
        
        // Accept challenge
        Json::Value acceptChallenge;
        acceptChallenge["token"] = token2;
        acceptChallenge["challenge_id"] = challengeId;
        challengeHandler.handleAcceptChallenge(acceptChallenge);
        
        // Create game
        Json::Value createGame;
        createGame["token"] = token1;
        createGame["challenge_id"] = challengeId;
        
        auto gameResp = gameHandler.handleCreateGame(createGame);
        string gameId = gameResp["data"]["game_id"].asString();
        string whiteId = gameResp["data"]["white_player_id"].asString();
        
        cout << "Game created: " << gameId << endl;
        cout << "White player: " << whiteId << endl << endl;
        
        // White resigns (black wins)
        string whiteToken = (whiteId == player1Id) ? token1 : token2;
        Json::Value resign;
        resign["token"] = whiteToken;
        resign["game_id"] = gameId;
        gameHandler.handleResign(resign);
        
        cout << "White resigned (Black wins)" << endl << endl;
        
        // 6. Test Update Stats (Glicko-2)
        cout << "--- Test: Update Stats (Glicko-2) ---" << endl;
        Json::Value updateStats;
        updateStats["game_id"] = gameId;
        
        auto updateResp = statsHandler.handleUpdateStats(updateStats);
        cout << Json::StyledWriter().write(updateResp) << endl;
        
        // 7. Test Get Stats After Game
        cout << "--- Test: Get Stats After Game ---" << endl;
        Json::Value getStatsAfter;
        getStatsAfter["user_id"] = player1Id;
        getStatsAfter["time_control"] = "blitz";
        
        auto statsAfterResp = statsHandler.handleGetStats(getStatsAfter);
        cout << "Player1 stats:" << endl;
        cout << Json::StyledWriter().write(statsAfterResp) << endl;
        
        Json::Value getStatsAfter2;
        getStatsAfter2["user_id"] = player2Id;
        getStatsAfter2["time_control"] = "blitz";
        
        auto statsAfterResp2 = statsHandler.handleGetStats(getStatsAfter2);
        cout << "Player2 stats:" << endl;
        cout << Json::StyledWriter().write(statsAfterResp2) << endl;
        
        // 8. Test Get Leaderboard
        cout << "--- Test: Get Leaderboard ---" << endl;
        Json::Value getLeaderboard;
        getLeaderboard["time_control"] = "blitz";
        getLeaderboard["limit"] = 10;
        
        auto leaderboardResp = statsHandler.handleGetLeaderboard(getLeaderboard);
        cout << Json::StyledWriter().write(leaderboardResp) << endl;
        
        // 9. Test Get Game History
        cout << "--- Test: Get Game History ---" << endl;
        Json::Value getHistory;
        getHistory["token"] = token1;
        getHistory["limit"] = 5;
        
        auto historyResp = statsHandler.handleGetGameHistory(getHistory);
        cout << Json::StyledWriter().write(historyResp) << endl;
        
        cout << "=== TEST COMPLETED ===" << endl;
        
    } catch (const exception& e) {
        cerr << "Test error: " << e.what() << endl;
        return 1;
    }
    
    return 0;
}
