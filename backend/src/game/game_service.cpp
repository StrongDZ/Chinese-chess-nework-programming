#include "game/game_service.h"
#include <cmath>
#include <random>
#include <algorithm>

using namespace std;

GameService::GameService(GameRepository& repo) 
    : repository(repo) {}

bool GameService::isValidCoordinate(int x, int y) {
    return (x >= 0 && x <= 8 && y >= 0 && y <= 9);
}

bool GameService::isGameOver(const string& status) {
    return (status == "completed" || status == "abandoned");
}

int GameService::getTimeLimitSeconds(const string& timeControl) {
    if (timeControl == "bullet") return 180;      // 3 minutes
    if (timeControl == "blitz") return 300;       // 5 minutes
    if (timeControl == "classical") return 900;   // 15 minutes
    return 300; // Default blitz
}

int GameService::getIncrementSeconds(const string& timeControl) {
    if (timeControl == "bullet") return 2;
    if (timeControl == "blitz") return 3;
    if (timeControl == "classical") return 5;
    return 3; // Default
}

void GameService::calculateAndUpdateRatings(const string& redUsername, const string& blackUsername,
                                             const string& result, const string& timeControl) {
    // Get current ratings
    int redRating = repository.getPlayerRating(redUsername, timeControl);
    int blackRating = repository.getPlayerRating(blackUsername, timeControl);
    
    // Calculate scores
    double redScore = (result == "red_win") ? 1.0 : (result == "draw") ? 0.5 : 0.0;
    double blackScore = 1.0 - redScore;
    
    // Elo calculation (K-factor = 32)
    double redExpected = 1.0 / (1.0 + pow(10.0, (blackRating - redRating) / 400.0));
    double blackExpected = 1.0 - redExpected;
    
    int redNewRating = redRating + static_cast<int>(32 * (redScore - redExpected));
    int blackNewRating = blackRating + static_cast<int>(32 * (blackScore - blackExpected));
    
    // Determine result fields
    string redField = (result == "red_win") ? "wins" : (result == "draw") ? "draws" : "losses";
    string blackField = (result == "black_win") ? "wins" : (result == "draw") ? "draws" : "losses";
    
    // Update stats
    repository.updatePlayerStats(redUsername, timeControl, redNewRating, redField);
    repository.updatePlayerStats(blackUsername, timeControl, blackNewRating, blackField);
}

GameResult GameService::createGame(const string& challengerUsername,
                                    const string& challengedUsername,
                                    const string& timeControl,
                                    bool rated) {
    GameResult result;
    result.success = false;
    
    // 1. Validate users exist
    if (!repository.userExists(challengerUsername)) {
        result.message = "Challenger not found";
        return result;
    }
    
    if (!repository.userExists(challengedUsername)) {
        result.message = "Challenged player not found";
        return result;
    }
    
    // 2. Random assign red/black (red goes first in Xiangqi)
    random_device rd;
    mt19937 gen(rd());
    uniform_int_distribution<> dis(0, 1);
    
    bool challengerIsRed = (dis(gen) == 0);
    
    string redPlayer = challengerIsRed ? challengerUsername : challengedUsername;
    string blackPlayer = challengerIsRed ? challengedUsername : challengerUsername;
    
    // 3. Create game
    Game game;
    game.red_player = redPlayer;
    game.black_player = blackPlayer;
    game.status = "in_progress";
    game.start_time = chrono::system_clock::now();
    game.xfen = INITIAL_XFEN;
    game.current_turn = "red";
    game.move_count = 0;
    game.time_control = timeControl;
    game.time_limit = getTimeLimitSeconds(timeControl);
    game.red_time_remaining = game.time_limit;
    game.black_time_remaining = game.time_limit;
    game.increment = getIncrementSeconds(timeControl);
    game.rated = rated;
    
    // 4. Save to database
    string gameId = repository.createGame(game);
    
    if (gameId.empty()) {
        result.message = "Failed to create game";
        return result;
    }
    
    game.id = gameId;
    
    // 5. Success
    result.success = true;
    result.message = "Game created successfully";
    result.game = game;
    
    return result;
}

GameResult GameService::makeMove(const string& username,
                                  const string& gameId,
                                  int fromX, int fromY,
                                  int toX, int toY,
                                  const string& piece,
                                  const string& captured,
                                  const string& notation,
                                  const string& xfenAfter,
                                  int timeTaken) {
    GameResult result;
    result.success = false;
    
    // 1. Validate coordinates
    if (!isValidCoordinate(fromX, fromY) || !isValidCoordinate(toX, toY)) {
        result.message = "Invalid coordinates (x: 0-8, y: 0-9)";
        return result;
    }
    
    // 2. Load game
    auto gameOpt = repository.findById(gameId);
    if (!gameOpt) {
        result.message = "Game not found";
        return result;
    }
    
    Game game = gameOpt.value();
    
    // 3. Check game is in progress
    if (game.status != "in_progress") {
        result.message = "Game is not in progress";
        return result;
    }
    
    // 4. Check it's user's turn
    bool isRedTurn = (game.current_turn == "red");
    string currentPlayerName = isRedTurn ? game.red_player : game.black_player;
    
    if (currentPlayerName != username) {
        result.message = "Not your turn";
        return result;
    }
    
    // 5. Build move
    Move move;
    move.move_number = game.move_count + 1;
    move.player = username;
    move.from_x = fromX;
    move.from_y = fromY;
    move.to_x = toX;
    move.to_y = toY;
    move.piece = piece;
    move.captured = captured;
    move.notation = notation;
    move.xfen_after = xfenAfter;
    move.time_taken = timeTaken;
    move.timestamp = chrono::system_clock::now();
    
    // 6. Calculate new time remaining
    string nextTurn = isRedTurn ? "black" : "red";
    int redTime = game.red_time_remaining;
    int blackTime = game.black_time_remaining;
    
    if (isRedTurn) {
        redTime = max(0, redTime - timeTaken + game.increment);
    } else {
        blackTime = max(0, blackTime - timeTaken + game.increment);
    }
    
    // 7. Update game
    bool updated = repository.updateAfterMove(gameId, move, nextTurn, redTime, blackTime, xfenAfter);
    
    if (!updated) {
        result.message = "Failed to update game";
        return result;
    }
    
    // 8. Reload game for response
    auto updatedGame = repository.findById(gameId);
    if (updatedGame) {
        result.game = updatedGame.value();
    }
    
    result.success = true;
    result.message = "Move executed successfully";
    
    return result;
}

GameResult GameService::offerDraw(const string& username, const string& gameId) {
    GameResult result;
    result.success = false;
    
    // 1. Load game
    auto gameOpt = repository.findById(gameId);
    if (!gameOpt) {
        result.message = "Game not found";
        return result;
    }
    
    Game game = gameOpt.value();
    
    // 2. Check game is in progress
    if (game.status != "in_progress") {
        result.message = "Game is not in progress";
        return result;
    }
    
    // 3. Check user is a player
    if (game.red_player != username && game.black_player != username) {
        result.message = "You are not a player in this game";
        return result;
    }
    
    // 4. Create draw offer
    DrawOffer offer;
    offer.game_id = gameId;
    offer.from_player = username;
    offer.created_at = chrono::system_clock::now();
    offer.expires_at = offer.created_at + chrono::seconds(DRAW_OFFER_TTL_SECONDS);
    
    bool created = repository.createDrawOffer(offer);
    
    if (!created) {
        result.message = "Failed to create draw offer";
        return result;
    }
    
    result.success = true;
    result.message = "Draw offer sent";
    result.game = game;
    
    return result;
}

GameResult GameService::respondDraw(const string& username, const string& gameId, bool accept) {
    GameResult result;
    result.success = false;
    
    // 1. Check draw offer exists
    auto offerOpt = repository.getDrawOffer(gameId);
    if (!offerOpt) {
        result.message = "No draw offer to respond to";
        return result;
    }
    
    DrawOffer offer = offerOpt.value();
    
    // 2. Check user is not the one who offered
    if (offer.from_player == username) {
        result.message = "Cannot respond to your own draw offer";
        return result;
    }
    
    // 3. Load game
    auto gameOpt = repository.findById(gameId);
    if (!gameOpt) {
        result.message = "Game not found";
        return result;
    }
    
    Game game = gameOpt.value();
    
    // 4. Check user is a player
    if (game.red_player != username && game.black_player != username) {
        result.message = "You are not a player in this game";
        return result;
    }
    
    // 5. Delete draw offer
    repository.deleteDrawOffer(gameId);
    
    if (accept) {
        // 6. End game as draw
        repository.endGame(gameId, "completed", "draw", "");
        
        // 7. Update ratings if rated
        if (game.rated) {
            calculateAndUpdateRatings(game.red_player, game.black_player, 
                                      "draw", game.time_control);
        }
        
        game.status = "completed";
        game.result = "draw";
        
        result.success = true;
        result.message = "Draw accepted - Game ended";
        result.game = game;
    } else {
        result.success = true;
        result.message = "Draw declined";
        result.game = game;
    }
    
    return result;
}

GameResult GameService::resign(const string& username, const string& gameId) {
    GameResult result;
    result.success = false;
    
    // 1. Load game
    auto gameOpt = repository.findById(gameId);
    if (!gameOpt) {
        result.message = "Game not found";
        return result;
    }
    
    Game game = gameOpt.value();
    
    // 2. Check game is in progress
    if (game.status != "in_progress") {
        result.message = "Game is not in progress";
        return result;
    }
    
    // 3. Determine result
    string gameResult;
    string winnerUsername;
    
    if (username == game.red_player) {
        gameResult = "black_win";
        winnerUsername = game.black_player;
    } else if (username == game.black_player) {
        gameResult = "red_win";
        winnerUsername = game.red_player;
    } else {
        result.message = "You are not a player in this game";
        return result;
    }
    
    // 4. End game
    bool ended = repository.endGame(gameId, "completed", gameResult, winnerUsername);
    
    if (!ended) {
        result.message = "Failed to end game";
        return result;
    }
    
    // 5. Update ratings if rated
    if (game.rated) {
        calculateAndUpdateRatings(game.red_player, game.black_player,
                                  gameResult, game.time_control);
    }
    
    game.status = "completed";
    game.result = gameResult;
    game.winner = winnerUsername;
    
    result.success = true;
    result.message = "Resigned successfully";
    result.game = game;
    
    return result;
}

GameResult GameService::getGame(const string& gameId) {
    GameResult result;
    result.success = false;
    
    auto gameOpt = repository.findById(gameId);
    
    if (!gameOpt) {
        result.message = "Game not found";
        return result;
    }
    
    result.success = true;
    result.message = "Game retrieved successfully";
    result.game = gameOpt.value();
    
    return result;
}

GameResult GameService::listGames(const string& username, const string& filter) {
    GameResult result;
    result.success = true;
    result.message = "Games retrieved successfully";
    result.games = repository.findByUser(username, filter);
    
    return result;
}
