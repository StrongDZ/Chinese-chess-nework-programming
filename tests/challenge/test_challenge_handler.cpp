#include "../../include/challenge/challenge_handler.h"
#include "../../include/auth/auth_handler.h"
#include <iostream>
#include <json/json.h>

using namespace std;

int main() {
    cout << "=== CHALLENGE HANDLER TEST ===" << endl;
    
    try {
        // 1. Kết nối database
        MongoDBClient mongoClient;
        mongoClient.connect("mongodb+srv://admin:admin@chess.p8k9xpw.mongodb.net/", "chess_server_game");
        
        RedisClient redisClient;
        redisClient.connect("127.0.0.1", 6379);
        
        cout << "✓ MongoDB connected: " << mongoClient.getDatabase().name() << endl;
        cout << "✓ Redis connected: 127.0.0.1:6379" << endl << endl;
        
        ChallengeHandler challengeHandler(mongoClient, redisClient);
        AuthHandler authHandler(mongoClient, redisClient);
        
        // 2. Đăng ký 2 users để test (hoặc skip nếu đã tồn tại)
        cout << "--- Setup: Register 2 users (or skip if exist) ---" << endl;
        
        Json::Value regReq1;
        regReq1["username"] = "challenger1";
        regReq1["email"] = "challenger1@test.com";
        regReq1["password"] = "password123";
        regReq1["display_name"] = "Challenger One";
        regReq1["avatar_id"] = 3;
        
        auto regResp1 = authHandler.handleRegister(regReq1);
        if (regResp1["success"].asBool()) {
            cout << "Challenger1 registered ✓" << endl;
        } else {
            cout << "Challenger1 already exists (skip)" << endl;
        }
        
        Json::Value regReq2;
        regReq2["username"] = "challenger2";
        regReq2["email"] = "challenger2@test.com";
        regReq2["password"] = "password123";
        regReq2["display_name"] = "Challenger Two";
        regReq2["avatar_id"] = 4;
        
        auto regResp2 = authHandler.handleRegister(regReq2);
        if (regResp2["success"].asBool()) {
            cout << "Challenger2 registered ✓" << endl;
        } else {
            cout << "Challenger2 already exists (skip)" << endl;
        }
        cout << endl;
        
        // 3. Login 2 users
        cout << "--- Setup: Login 2 users ---" << endl;
        
        Json::Value loginReq1;
        loginReq1["username"] = "challenger1";
        loginReq1["password"] = "password123";
        auto loginResp1 = authHandler.handleLogin(loginReq1);
        
        if (!loginResp1["success"].asBool()) {
            cerr << "Login failed for challenger1: " << loginResp1["error"].asString() << endl;
            return 1;
        }
        
        string token1 = loginResp1["data"]["token"].asString();
        cout << "Challenger1 token: " << token1 << endl;
        
        Json::Value loginReq2;
        loginReq2["username"] = "challenger2";
        loginReq2["password"] = "password123";
        auto loginResp2 = authHandler.handleLogin(loginReq2);
        
        if (!loginResp2["success"].asBool()) {
            cerr << "Login failed for challenger2: " << loginResp2["error"].asString() << endl;
            return 1;
        }
        
        string token2 = loginResp2["data"]["token"].asString();
        cout << "Challenger2 token: " << token2 << endl << endl;
        
        // 4. Test Create Challenge (public)
        cout << "--- Test Create Public Challenge ---" << endl;
        Json::Value createReq1;
        createReq1["token"] = token1;
        createReq1["time_control"] = "blitz";
        createReq1["rated"] = true;
        
        auto createResp1 = challengeHandler.handleCreateChallenge(createReq1);
        cout << Json::StyledWriter().write(createResp1) << endl;
        string challengeId1 = createResp1["data"]["challenge_id"].asString();
        
        // 5. Test Create Challenge (specific opponent)
        cout << "--- Test Create Challenge for Specific Opponent ---" << endl;
        string player2Id = loginResp2["data"]["user_id"].asString();
        
        Json::Value createReq2;
        createReq2["token"] = token1;
        createReq2["time_control"] = "classical";
        createReq2["rated"] = false;
        createReq2["opponent_id"] = player2Id;
        
        auto createResp2 = challengeHandler.handleCreateChallenge(createReq2);
        cout << Json::StyledWriter().write(createResp2) << endl;
        
        // Fail vì đã có pending challenge
        if (!createResp2["success"].asBool()) {
            cout << "Expected: Cannot create multiple pending challenges ✓" << endl << endl;
        }
        
        // 6. Test List Challenges
        cout << "--- Test List Challenges (All) ---" << endl;
        Json::Value listReq1;
        listReq1["token"] = token2;
        listReq1["filter"] = "all";
        
        auto listResp1 = challengeHandler.handleListChallenges(listReq1);
        cout << Json::StyledWriter().write(listResp1) << endl;
        
        cout << "--- Test List Challenges (For Me) ---" << endl;
        Json::Value listReq2;
        listReq2["token"] = token2;
        listReq2["filter"] = "for_me";
        
        auto listResp2 = challengeHandler.handleListChallenges(listReq2);
        cout << Json::StyledWriter().write(listResp2) << endl;
        
        // 7. Test Accept Challenge
        cout << "--- Test Accept Challenge ---" << endl;
        Json::Value acceptReq;
        acceptReq["token"] = token2;
        acceptReq["challenge_id"] = challengeId1;
        
        auto acceptResp = challengeHandler.handleAcceptChallenge(acceptReq);
        cout << Json::StyledWriter().write(acceptResp) << endl;
        
        // 8. Test Create Another Challenge
        cout << "--- Test Create Another Challenge (after accepting previous) ---" << endl;
        Json::Value createReq3;
        createReq3["token"] = token1;
        createReq3["time_control"] = "bullet";
        createReq3["rated"] = true;
        
        auto createResp3 = challengeHandler.handleCreateChallenge(createReq3);
        cout << Json::StyledWriter().write(createResp3) << endl;
        string challengeId3 = createResp3["data"]["challenge_id"].asString();
        
        // 9. Test Decline Challenge
        cout << "--- Test Decline Challenge ---" << endl;
        Json::Value declineReq;
        declineReq["token"] = token2;
        declineReq["challenge_id"] = challengeId3;
        
        auto declineResp = challengeHandler.handleDeclineChallenge(declineReq);
        cout << Json::StyledWriter().write(declineResp) << endl;
        
        // 10. Test Cancel Challenge
        cout << "--- Test Create & Cancel Challenge ---" << endl;
        Json::Value createReq4;
        createReq4["token"] = token1;
        createReq4["time_control"] = "blitz";
        createReq4["rated"] = false;
        
        auto createResp4 = challengeHandler.handleCreateChallenge(createReq4);
        string challengeId4 = createResp4["data"]["challenge_id"].asString();
        cout << "Created: " << challengeId4 << endl;
        
        Json::Value cancelReq;
        cancelReq["token"] = token1;
        cancelReq["challenge_id"] = challengeId4;
        
        auto cancelResp = challengeHandler.handleCancelChallenge(cancelReq);
        cout << Json::StyledWriter().write(cancelResp) << endl;
        
        cout << "=== TEST COMPLETED ===" << endl;
        
    } catch (const exception& e) {
        cerr << "Test error: " << e.what() << endl;
        return 1;
    }
    
    return 0;
}
