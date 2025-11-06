#include "../../include/game/game_handler.h"
#include "../../include/auth/auth_handler.h"
#include "../../include/challenge/challenge_handler.h"
#include <iostream>
#include <json/json.h>

using namespace std;

int main() {
    cout << "=== GAME HANDLER TEST ===" << endl;
    
    try {
        // 1. Kết nối database
        MongoDBClient mongoClient;
        mongoClient.connect("mongodb+srv://admin:admin@chess.p8k9xpw.mongodb.net/", "chess_server_game");
        
        RedisClient redisClient;
        redisClient.connect("127.0.0.1", 6379);
        
        cout << "✓ MongoDB connected: " << mongoClient.getDatabase().name() << endl;
        cout << "✓ Redis connected: 127.0.0.1:6379" << endl << endl;
        
        GameHandler gameHandler(mongoClient, redisClient);
        AuthHandler authHandler(mongoClient, redisClient);
        ChallengeHandler challengeHandler(mongoClient, redisClient);
        
        // 2. Setup: Tạo 2 users
        cout << "--- Setup: Create 2 players ---" << endl;
        
        Json::Value reg1;
        reg1["username"] = "gameplayer1";
        reg1["email"] = "gameplayer1@test.com";
        reg1["password"] = "password123";
        reg1["display_name"] = "Game Player 1";
        reg1["avatar_id"] = 1;
        
        auto regResp1 = authHandler.handleRegister(reg1);
        if (regResp1["success"].asBool()) {
            cout << "Player1 registered ✓" << endl;
        } else {
            cout << "Player1 already exists" << endl;
        }
        
        Json::Value reg2;
        reg2["username"] = "gameplayer2";
        reg2["email"] = "gameplayer2@test.com";
        reg2["password"] = "password123";
        reg2["display_name"] = "Game Player 2";
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
        login1["username"] = "gameplayer1";
        login1["password"] = "password123";
        auto loginResp1 = authHandler.handleLogin(login1);
        string token1 = loginResp1["data"]["token"].asString();
        string player1Id = loginResp1["data"]["user_id"].asString();
        cout << "Player1 logged in: " << token1.substr(0, 16) << "..." << endl;
        
        Json::Value login2;
        login2["username"] = "gameplayer2";
        login2["password"] = "password123";
        auto loginResp2 = authHandler.handleLogin(login2);
        string token2 = loginResp2["data"]["token"].asString();
        string player2Id = loginResp2["data"]["user_id"].asString();
        cout << "Player2 logged in: " << token2.substr(0, 16) << "..." << endl << endl;
        
        // 4. Tạo challenge
        cout << "--- Test: Create Challenge ---" << endl;
        Json::Value createChallenge;
        createChallenge["token"] = token1;
        createChallenge["time_control"] = "blitz";
        createChallenge["rated"] = true;
        createChallenge["opponent_id"] = player2Id;
        
        auto challengeResp = challengeHandler.handleCreateChallenge(createChallenge);
        cout << Json::StyledWriter().write(challengeResp) << endl;
        string challengeId = challengeResp["data"]["challenge_id"].asString();
        
        // 5. Accept challenge
        cout << "--- Test: Accept Challenge ---" << endl;
        Json::Value acceptChallenge;
        acceptChallenge["token"] = token2;
        acceptChallenge["challenge_id"] = challengeId;
        
        auto acceptResp = challengeHandler.handleAcceptChallenge(acceptChallenge);
        cout << Json::StyledWriter().write(acceptResp) << endl;
        
        // 6. Tạo game từ challenge
        cout << "--- Test: Create Game ---" << endl;
        Json::Value createGame;
        createGame["token"] = token1;
        createGame["challenge_id"] = challengeId;
        
        auto gameResp = gameHandler.handleCreateGame(createGame);
        cout << Json::StyledWriter().write(gameResp) << endl;
        string gameId = gameResp["data"]["game_id"].asString();
        string whiteId = gameResp["data"]["white_player_id"].asString();
        string blackId = gameResp["data"]["black_player_id"].asString();
        
        // Xác định token của white và black
        string whiteToken = (whiteId == player1Id) ? token1 : token2;
        string blackToken = (blackId == player1Id) ? token1 : token2;
        
        // 7. Test Get Game
        cout << "--- Test: Get Game Info ---" << endl;
        Json::Value getGame;
        getGame["game_id"] = gameId;
        
        auto getGameResp = gameHandler.handleGetGame(getGame);
        cout << Json::StyledWriter().write(getGameResp) << endl;
        
        // 8. Test Make Move (white's turn)
        cout << "--- Test: Make Move (White) ---" << endl;
        Json::Value move1;
        move1["token"] = whiteToken;
        move1["game_id"] = gameId;
        move1["from"] = "b0";
        move1["to"] = "c2";
        
        auto moveResp1 = gameHandler.handleMakeMove(move1);
        cout << Json::StyledWriter().write(moveResp1) << endl;
        
        // 9. Test Make Move (black's turn)
        cout << "--- Test: Make Move (Black) ---" << endl;
        Json::Value move2;
        move2["token"] = blackToken;
        move2["game_id"] = gameId;
        move2["from"] = "b9";
        move2["to"] = "c7";
        
        auto moveResp2 = gameHandler.handleMakeMove(move2);
        cout << Json::StyledWriter().write(moveResp2) << endl;
        
        // 10. Test Offer Draw
        cout << "--- Test: Offer Draw ---" << endl;
        Json::Value offerDraw;
        offerDraw["token"] = whiteToken;
        offerDraw["game_id"] = gameId;
        
        auto drawResp = gameHandler.handleOfferDraw(offerDraw);
        cout << Json::StyledWriter().write(drawResp) << endl;
        
        // 11. Test Decline Draw
        cout << "--- Test: Decline Draw ---" << endl;
        Json::Value respondDraw;
        respondDraw["token"] = blackToken;
        respondDraw["game_id"] = gameId;
        respondDraw["accept"] = false;
        
        auto declineResp = gameHandler.handleRespondDraw(respondDraw);
        cout << Json::StyledWriter().write(declineResp) << endl;
        
        // 12. Test List Games
        cout << "--- Test: List Games ---" << endl;
        Json::Value listGames;
        listGames["token"] = token1;
        
        auto listResp = gameHandler.handleListGames(listGames);
        cout << Json::StyledWriter().write(listResp) << endl;
        
        // 13. Test Resign
        cout << "--- Test: Resign ---" << endl;
        Json::Value resign;
        resign["token"] = whiteToken;
        resign["game_id"] = gameId;
        
        auto resignResp = gameHandler.handleResign(resign);
        cout << Json::StyledWriter().write(resignResp) << endl;
        
        cout << "=== TEST COMPLETED ===" << endl;
        
    } catch (const exception& e) {
        cerr << "Test error: " << e.what() << endl;
        return 1;
    }
    
    return 0;
}
