#include "game/game_service.h"
#include <cmath>
#include <random>
#include <algorithm>

using namespace std;

GameService::GameService(GameRepository& repo) 
    : repository(repo) {}

// bool GameService::isValidCoordinate(int x, int y) {
//     return (x >= 0 && x <= 8 && y >= 0 && y <= 9);
// }

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

GameResult GameService::autoMatchAndCreateGame(const string& username,
                                               const string& timeControl,
                                               bool rated,
                                               int ratingWindow) {
    GameResult result{};
    result.success = false;

    // 1) Verify user exists
    if (!repository.userExists(username)) {
        result.message = "User not found";
        return result;
    }

    // 2) Ask repository for a random opponent within rating window
    auto opponentOpt = repository.findRandomOpponentByElo(username, timeControl, ratingWindow);
    if (!opponentOpt) {
        result.message = "No opponent found within rating window";
        return result;
    }

    // 3) Reuse existing createGame flow (random color assignment inside)
    return createGame(username, opponentOpt.value(), timeControl, rated);
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
    // if (!isValidCoordinate(fromX, fromY) || !isValidCoordinate(toX, toY)) {
    //     result.message = "Invalid coordinates (x: 0-8, y: 0-9)";
    //     return result;
    // }
    
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

// Kết thúc game với result và termination cụ thể
GameResult GameService::endGame(const string& gameId,
                                 const string& result,
                                 const string& termination) {
    GameResult gameResult;
    gameResult.success = false;
    
    // 1. Load game
    auto gameOpt = repository.findById(gameId);
    if (!gameOpt) {
        gameResult.message = "Game not found";
        return gameResult;
    }
    
    Game game = gameOpt.value();
    
    // 2. Check game is still in progress
    if (game.status != "in_progress") {
        gameResult.message = "Game is not in progress";
        return gameResult;
    }
    
    // 3. Determine winner
    string winnerUsername = "";
    if (result == "red_win") {
        winnerUsername = game.red_player;
    } else if (result == "black_win") {
        winnerUsername = game.black_player;
    }
    // else: draw or abandoned - no winner
    
    // 4. End game in repository
    bool ended = repository.endGame(gameId, "completed", result, winnerUsername);
    
    if (!ended) {
        gameResult.message = "Failed to end game";
        return gameResult;
    }
    
    // 5. Update ratings if rated
    if (game.rated) {
        calculateAndUpdateRatings(game.red_player, game.black_player,
                                  result, game.time_control);
    }
    
    game.status = "completed";
    game.result = result;
    game.winner = winnerUsername;
    
    gameResult.success = true;
    gameResult.message = "Game ended: " + termination;
    gameResult.game = game;
    
    return gameResult;
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
    
    // 3. Determine result based on who resigned
    string gameResult;
    
    if (username == game.red_player) {
        gameResult = "black_win";
    } else if (username == game.black_player) {
        gameResult = "red_win";
    } else {
        result.message = "You are not a player in this game";
        return result;
    }
    
    // 4. Call endGame with resignation
    return endGame(gameId, gameResult, "resignation");
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
