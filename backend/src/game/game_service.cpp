#include "game/game_service.h"
#include <algorithm>
#include <cmath>
#include <map>
#include <nlohmann/json.hpp>
#include <random>
#include <sstream>

using namespace std;

GameService::GameService(GameRepository &repo) : repository(repo) {}

// bool GameService::isValidCoordinate(int x, int y) {
//     return (x >= 0 && x <= 8 && y >= 0 && y <= 9);
// }

bool GameService::isGameOver(const string &status) {
  return (status == "completed" || status == "abandoned");
}

int GameService::getTimeLimitSeconds(const string &timeControl) {
  if (timeControl == "blitz")
    return 300; // 5 minutes
  if (timeControl == "classical")
    return 900; // 15 minutes
  return 300;   // Default blitz
}

int GameService::getIncrementSeconds(const string &timeControl) {
  if (timeControl == "blitz")
    return 3;
  if (timeControl == "classical")
    return 5;
  return 3; // Default
}

// ============ XFEN Calculation Helpers ============

// Parse XFEN string to 10x9 board array
// Board: board[y][x] where y=0 is top (black side), y=9 is bottom (red side)
// x=0 is left, x=8 is right
void GameService::parseXfenToBoard(const string &xfen, char board[10][9]) {
  // Initialize board with empty squares
  for (int y = 0; y < 10; y++) {
    for (int x = 0; x < 9; x++) {
      board[y][x] = '.';
    }
  }

  // Extract board part (before first space)
  string boardPart = xfen.substr(0, xfen.find(' '));

  int y = 0;
  int x = 0;

  for (char c : boardPart) {
    if (c == '/') {
      y++;
      x = 0;
    } else if (c >= '1' && c <= '9') {
      x += (c - '0');
    } else {
      if (x < 9 && y < 10) {
        board[y][x] = c;
      }
      x++;
    }
  }
}

// Convert board array to XFEN string
string GameService::boardToXfen(const char board[10][9], const string &turn,
                                int moveCount) {
  stringstream ss;

  for (int y = 0; y < 10; y++) {
    int emptyCount = 0;

    for (int x = 0; x < 9; x++) {
      if (board[y][x] == '.') {
        emptyCount++;
      } else {
        if (emptyCount > 0) {
          ss << emptyCount;
          emptyCount = 0;
        }
        ss << board[y][x];
      }
    }

    if (emptyCount > 0) {
      ss << emptyCount;
    }

    if (y < 9) {
      ss << '/';
    }
  }

  // Add turn indicator
  ss << ' ' << (turn == "red" ? 'w' : 'b');

  // Add placeholders for castling and en passant (not used in Xiangqi)
  ss << " - -";

  // Add half-move clock and full-move number
  ss << " 0 " << ((moveCount / 2) + 1);

  return ss.str();
}

// Validate XFEN format for custom board setup
bool GameService::isValidXfen(const string &xfen) {
  if (xfen.empty())
    return false;

  // Split XFEN into parts
  stringstream ss(xfen);
  string boardPart, turnPart;
  ss >> boardPart >> turnPart;

  if (boardPart.empty())
    return false;

  // Validate turn indicator (w/b or r/b for red/black)
  if (!turnPart.empty() && turnPart != "w" && turnPart != "b" &&
      turnPart != "r" && turnPart != "W" && turnPart != "B" &&
      turnPart != "R") {
    return false;
  }

  // Count rows (should be 10 rows for Xiangqi)
  int rowCount = 1;
  for (char c : boardPart) {
    if (c == '/')
      rowCount++;
  }
  if (rowCount != 10)
    return false;

  // Validate each row
  stringstream rowStream(boardPart);
  string row;
  int rowIndex = 0;

  while (getline(rowStream, row, '/')) {
    if (rowIndex >= 10)
      return false;

    int colCount = 0;
    for (char c : row) {
      if (c >= '1' && c <= '9') {
        colCount += (c - '0');
      } else if (isalpha(c)) {
        // Valid piece characters: r,n,b,a,k,c,p (lowercase=black,
        // uppercase=red)
        char lower = tolower(c);
        if (lower != 'r' && lower != 'n' && lower != 'b' && lower != 'a' &&
            lower != 'k' && lower != 'c' && lower != 'p') {
          return false;
        }
        colCount++;
      } else {
        return false; // Invalid character
      }
    }

    if (colCount != 9)
      return false; // Each row must have exactly 9 columns
    rowIndex++;
  }

  // Must have both kings
  bool hasRedKing = (boardPart.find('K') != string::npos);
  bool hasBlackKing = (boardPart.find('k') != string::npos);

  return hasRedKing && hasBlackKing;
}

// Calculate new XFEN after a move
string GameService::calculateNewXfen(const string &currentXfen, int fromX,
                                     int fromY, int toX, int toY,
                                     const string &nextTurn) {
  char board[10][9];
  parseXfenToBoard(currentXfen, board);

  // Validate positions
  // NOTE: fromX/toX are COLUMNS (0-8), fromY/toY are ROWS (0-9)
  // board[10][9] = board[row][col] = board[y][x]
  if (fromX < 0 || fromX >= 9 || fromY < 0 || fromY >= 10 || toX < 0 ||
      toX >= 9 || toY < 0 || toY >= 10) {
    cerr << "[calculateNewXfen] Invalid coordinates: from=(col=" << fromX
         << ",row=" << fromY << ") to=(col=" << toX << ",row=" << toY << ")"
         << endl;
    return currentXfen; // Return unchanged if invalid
  }

  // Get the piece at from position
  // board[row][col] = board[fromY][fromX] where fromY=row, fromX=col
  char piece = board[fromY][fromX];

  // Check if there's a piece at destination (capture)
  bool isCapture = (board[toY][toX] != '.');

  if (piece == '.') {
    cerr << "[calculateNewXfen] No piece at from position (col=" << fromX
         << ",row=" << fromY << ")" << endl;
    return currentXfen; // Return unchanged if no piece to move
  }

  // Move the piece (this automatically handles capture by overwriting)
  // board[row][col] = board[toY][toX] where toY=row, toX=col
  board[toY][toX] = piece;
  board[fromY][fromX] = '.';

  // Extract move count from current xfen
  // X-fen format: "... w - - 0 3" (last number is full move count)
  int moveCount = 1;
  size_t lastSpace = currentXfen.rfind(' ');
  if (lastSpace != string::npos) {
    try {
      string lastPart = currentXfen.substr(lastSpace + 1);
      moveCount = stoi(lastPart);
    } catch (...) {
      // If parsing fails, try to extract from second-to-last number
      size_t secondLastSpace = currentXfen.rfind(' ', lastSpace - 1);
      if (secondLastSpace != string::npos) {
        try {
          string secondLastPart = currentXfen.substr(
              secondLastSpace + 1, lastSpace - secondLastSpace - 1);
          // Remove non-digit characters
          secondLastPart.erase(remove_if(secondLastPart.begin(),
                                         secondLastPart.end(),
                                         [](char c) { return !isdigit(c); }),
                               secondLastPart.end());
          if (!secondLastPart.empty()) {
            moveCount = stoi(secondLastPart);
          }
        } catch (...) {
          moveCount = 1;
        }
      }
    }
  }

  // Increment move count (each move increases the full move count)
  // boardToXfen expects: moveCount * 2 for half-moves, but we want full moves
  // So we pass the incremented full move count
  int newMoveCount = moveCount + 1;

  // Generate new XFEN
  string newXfen = boardToXfen(board, nextTurn, newMoveCount * 2);

  if (isCapture) {
    char capturedPiece = board[toY][toX]; // Get before overwrite
    cout << "[calculateNewXfen] Capture detected: piece '" << capturedPiece
         << "' at (col=" << toX << ",row=" << toY << ") was captured" << endl;
  }

  cout << "[calculateNewXfen] Move: (col=" << fromX << ",row=" << fromY
       << ") -> (col=" << toX << ",row=" << toY << "), piece='" << piece
       << "', moveCount: " << moveCount << " -> " << newMoveCount << endl;
  cout << "[calculateNewXfen] Old x-fen: " << currentXfen << endl;
  cout << "[calculateNewXfen] New x-fen: " << newXfen << endl;

  return newXfen;
}

// Helper: map piece char to human-readable name (uppercase=red,
// lowercase=black)
static string charToPieceName(char c) {
  switch (toupper(c)) {
  case 'K':
    return "King";
  case 'A':
    return "Advisor";
  case 'B':
    return "Elephant";
  case 'N':
    return "Horse";
  case 'R':
    return "Rook";
  case 'C':
    return "Cannon";
  case 'P':
    return "Pawn";
  default:
    return "";
  }
}

void GameService::calculateAndUpdateRatings(const string &redUsername,
                                            const string &blackUsername,
                                            const string &result,
                                            const string &timeControl) {
  // Get current ratings
  int redRating = repository.getPlayerRating(redUsername, timeControl);
  int blackRating = repository.getPlayerRating(blackUsername, timeControl);

  // Calculate scores
  double redScore = (result == "red_win") ? 1.0
                    : (result == "draw")  ? 0.5
                                          : 0.0;
  double blackScore = 1.0 - redScore;

  // Elo calculation (K-factor = 32)
  double redExpected =
      1.0 / (1.0 + pow(10.0, (blackRating - redRating) / 400.0));
  double blackExpected = 1.0 - redExpected;

  int redNewRating =
      redRating + static_cast<int>(32 * (redScore - redExpected));
  int blackNewRating =
      blackRating + static_cast<int>(32 * (blackScore - blackExpected));

  // Determine result fields
  string redField = (result == "red_win") ? "wins"
                    : (result == "draw")  ? "draws"
                                          : "losses";
  string blackField = (result == "black_win") ? "wins"
                      : (result == "draw")    ? "draws"
                                              : "losses";

  // Update stats
  repository.updatePlayerStats(redUsername, timeControl, redNewRating,
                               redField);
  repository.updatePlayerStats(blackUsername, timeControl, blackNewRating,
                               blackField);
}

// Private helper: Create game with specific colors (no random assignment)
GameResult GameService::createGameWithColors(const string &redPlayer,
                                             const string &blackPlayer,
                                             const string &timeControl,
                                             bool rated) {
  GameResult result;
  result.success = false;

  // 1. Validate users exist
  if (!repository.userExists(redPlayer)) {
    result.message = "red player not found";
    return result;
  }

  if (!repository.userExists(blackPlayer)) {
    result.message = "black player not found";
    return result;
  }

  // 2. Create game with specified colors (no randomization)
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

  // 3. Save to database
  string gameId = repository.createGame(game);

  if (gameId.empty()) {
    result.message = "Failed to create game";
    return result;
  }

  game.id = gameId;

  // 4. Success
  result.success = true;
  result.message = "Game created successfully";
  result.game = game;

  return result;
}

GameResult GameService::createGame(const string &challengerUsername,
                                   const string &challengedUsername,
                                   const string &timeControl, bool rated) {
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
  string blackPlayer =
      challengerIsRed ? challengedUsername : challengerUsername;

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

GameResult GameService::createCustomGame(const string &redPlayer,
                                         const string &blackPlayer,
                                         const string &customXfen,
                                         const string &startingColor,
                                         const string &timeControl,
                                         int timeLimit) {
  GameResult result;
  result.success = false;

  // 1. Validate users exist (skip validation for AI users - username starts
  // with "AI_")
  bool redPlayerIsAI = (redPlayer.find("AI_") == 0);
  bool blackPlayerIsAI = (blackPlayer.find("AI_") == 0);

  if (!redPlayerIsAI && !repository.userExists(redPlayer)) {
    result.message = "red player not found";
    return result;
  }

  if (!blackPlayerIsAI && !repository.userExists(blackPlayer)) {
    result.message = "black player not found";
    return result;
  }

  // 2. Validate custom XFEN
  if (!isValidXfen(customXfen)) {
    result.message = "Invalid XFEN format for custom board setup";
    return result;
  }

  // 3. Validate starting color
  string turn = startingColor;
  if (turn != "red" && turn != "black") {
    turn = "red"; // Default to red if invalid
  }

  // 4. Create game with custom position
  Game game;
  game.red_player = redPlayer;
  game.black_player = blackPlayer;
  game.status = "in_progress";
  game.start_time = chrono::system_clock::now();
  game.xfen = customXfen;   // Use custom XFEN instead of INITIAL_XFEN
  game.current_turn = turn; // Use specified starting color
  game.move_count = 0;
  game.time_control = timeControl;
  // Use provided timeLimit if > 0, otherwise use default from timeControl
  if (timeLimit > 0) {
    game.time_limit = timeLimit;
  } else if (timeControl == "custom") {
    game.time_limit = 0; // Default to unlimited for custom mode
  } else {
    game.time_limit = getTimeLimitSeconds(timeControl);
  }
  game.red_time_remaining = game.time_limit;
  game.black_time_remaining = game.time_limit;
  game.increment = getIncrementSeconds(timeControl);
  game.rated = false; // Custom games are always unrated

  // 5. Save to database
  cout << "[createCustomGame] Saving game to database: red=" << redPlayer
       << ", black=" << blackPlayer << ", xfen=" << customXfen << endl;
  string gameId = repository.createGame(game);

  if (gameId.empty()) {
    cerr << "[createCustomGame] ERROR: repository.createGame returned empty "
            "gameId"
         << endl;
    result.message = "Failed to create custom game";
    return result;
  }

  cout << "[createCustomGame] Game saved successfully, game_id=" << gameId
       << endl;

  game.id = gameId;

  // 6. Success
  result.success = true;
  result.message = "Custom game created successfully";
  result.game = game;

  return result;
}

GameResult GameService::makeMove(const string &username, const string &gameId,
                                 int fromX, int fromY, int toX, int toY,
                                 const string &piece, const string &captured,
                                 const string &notation,
                                 const string &xfenAfter, int timeTaken) {
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

  // Current board to detect captured piece
  char board[10][9];
  parseXfenToBoard(game.xfen, board);
  char capturedChar = board[toY][toX];
  string detectedCaptured =
      (capturedChar != '.') ? charToPieceName(capturedChar) : "";

  // 3. Check game is in progress
  if (game.status != "in_progress") {
    result.message = "Game is not in progress";
    return result;
  }

  // 4. Check it's user's turn
  bool isRedTurn = (game.current_turn == "red");
  string currentPlayerName = isRedTurn ? game.red_player : game.black_player;

  // For AI games, check if current player is AI (starts with "AI_")
  // In AI game, we need to allow both AI and human to move when it's their turn
  bool isAIGame =
      (game.red_player.find("AI_") == 0 || game.black_player.find("AI_") == 0);

  if (isAIGame) {
    // In AI game: check if current player matches the username
    // - If current player is AI and username is AI → allow
    // - If current player is human and username is human → allow
    // - Otherwise → reject
    bool currentPlayerIsAI = (currentPlayerName.find("AI_") == 0);
    bool usernameIsAI = (username.find("AI_") == 0);

    if (currentPlayerIsAI != usernameIsAI) {
      // Mismatch: current player is AI but username is human, or vice versa
      result.message = "Not your turn";
      return result;
    }

    // Both are AI or both are human - check if they match
    if (currentPlayerName != username) {
      result.message = "Not your turn";
      return result;
    }
  } else {
    // Normal PvP game: current player must match username
    if (currentPlayerName != username) {
      result.message = "Not your turn";
      return result;
    }
  }

  // 5. Calculate next turn
  string nextTurn = isRedTurn ? "black" : "red";

  // 6. Calculate new XFEN after this move
  // If xfenAfter is provided by client, use it; otherwise calculate it
  string calculatedXfen = xfenAfter;
  if (calculatedXfen.empty()) {
    cout << "[makeMove] Calculating new x-fen from current: " << game.xfen
         << endl;
    cout << "[makeMove] Move: (" << fromX << "," << fromY << ") -> (" << toX
         << "," << toY << ")" << endl;
    calculatedXfen =
        calculateNewXfen(game.xfen, fromX, fromY, toX, toY, nextTurn);
    cout << "[makeMove] Calculated x-fen: " << calculatedXfen << endl;
  } else {
    cout << "[makeMove] Using client-provided x-fen: " << calculatedXfen
         << endl;
  }

  // 7. Build move
  Move move;
  move.move_number = game.move_count + 1;
  move.player = username;
  move.from_x = fromX;
  move.from_y = fromY;
  move.to_x = toX;
  move.to_y = toY;
  move.piece = piece;
  // If client didn't provide captured, use detectedCaptured
  move.captured = captured.empty() ? detectedCaptured : captured;
  move.notation = notation;
  move.xfen_after = calculatedXfen; // Use calculated XFEN
  move.time_taken = timeTaken;
  move.timestamp = chrono::system_clock::now();

  // 8. Calculate new time remaining
  int redTime = game.red_time_remaining;
  int blackTime = game.black_time_remaining;

  if (isRedTurn) {
    redTime = max(0, redTime - timeTaken + game.increment);
  } else {
    blackTime = max(0, blackTime - timeTaken + game.increment);
  }

  // 9. Update game with new XFEN
  cout << "[makeMove] Updating database with new x-fen: " << calculatedXfen
       << endl;
  bool updated = repository.updateAfterMove(gameId, move, nextTurn, redTime,
                                            blackTime, calculatedXfen);

  if (!updated) {
    cerr << "[makeMove] ERROR: Failed to update game in database" << endl;
    result.message = "Failed to update game";
    return result;
  }
  cout << "[makeMove] Database updated successfully" << endl;

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
GameResult GameService::endGame(const string &gameId, const string &result,
                                const string &termination) {
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
    calculateAndUpdateRatings(game.red_player, game.black_player, result,
                              game.time_control);
  }

  game.status = "completed";
  game.result = result;
  game.winner = winnerUsername;

  gameResult.success = true;
  gameResult.message = "Game ended: " + termination;
  gameResult.game = game;

  return gameResult;
}

GameResult GameService::resign(const string &username, const string &gameId) {
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

// ============ Draw Offer Operations ============

GameResult GameService::offerDraw(const string &username,
                                  const string &gameId) {
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

  // 3. Check user is a player in this game
  if (username != game.red_player && username != game.black_player) {
    result.message = "You are not a player in this game";
    return result;
  }

  // 4. Check if there's already a pending draw offer
  if (!game.draw_offered_by.empty()) {
    if (game.draw_offered_by == username) {
      result.message = "You have already offered a draw";
      return result;
    } else {
      result.message = "There is already a pending draw offer from opponent";
      return result;
    }
  }

  // 5. Set draw offer
  bool updated = repository.setDrawOffer(gameId, username);

  if (!updated) {
    result.message = "Failed to offer draw";
    return result;
  }

  // 6. Reload game for response
  auto updatedGame = repository.findById(gameId);
  if (updatedGame) {
    result.game = updatedGame.value();
  }

  result.success = true;
  result.message = "Draw offered successfully";

  return result;
}

GameResult GameService::respondToDraw(const string &username,
                                      const string &gameId, bool accept) {
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

  // 3. Check user is a player in this game
  if (username != game.red_player && username != game.black_player) {
    result.message = "You are not a player in this game";
    return result;
  }

  // 4. Check there's a pending draw offer
  if (game.draw_offered_by.empty()) {
    result.message = "No pending draw offer";
    return result;
  }

  // 5. Check user is the one who should respond (not the one who offered)
  if (game.draw_offered_by == username) {
    result.message = "You cannot respond to your own draw offer";
    return result;
  }

  // 6. Process response
  if (accept) {
    // Accept draw - end game with draw result
    return endGame(gameId, "draw", "draw_agreement");
  } else {
    // Decline draw - clear the offer
    bool cleared = repository.clearDrawOffer(gameId);

    if (!cleared) {
      result.message = "Failed to decline draw";
      return result;
    }

    // Reload game for response
    auto updatedGame = repository.findById(gameId);
    if (updatedGame) {
      result.game = updatedGame.value();
    }

    result.success = true;
    result.message = "Draw declined";

    return result;
  }
}

// ============ Rematch Operations ============

GameResult GameService::requestRematch(const string &username,
                                       const string &gameId) {
  GameResult result;
  result.success = false;

  // 1. Load archived game
  auto archivedOpt = repository.findArchivedGameById(gameId);
  if (!archivedOpt) {
    result.message = "Archived game not found";
    return result;
  }

  ArchivedGame archivedGame = archivedOpt.value();

  // 2. Check user was a player in this game
  if (username != archivedGame.red_player &&
      username != archivedGame.black_player) {
    result.message = "You are not a player in this game";
    return result;
  }

  // 3. Check if rematch was already accepted
  if (archivedGame.rematch_accepted) {
    result.message = "Rematch has already been accepted for this game";
    return result;
  }

  // 4. Check if there's already a pending rematch offer
  if (!archivedGame.rematch_offered_by.empty()) {
    if (archivedGame.rematch_offered_by == username) {
      result.message = "You have already requested a rematch";
      return result;
    } else {
      result.message =
          "There is already a pending rematch request from opponent";
      return result;
    }
  }

  // 5. Set rematch offer
  bool updated = repository.setRematchOffer(gameId, username);

  if (!updated) {
    result.message = "Failed to request rematch";
    return result;
  }

  result.success = true;
  result.message = "Rematch requested successfully";

  return result;
}

GameResult GameService::respondToRematch(const string &username,
                                         const string &gameId, bool accept) {
  GameResult result;
  result.success = false;

  // 1. Load archived game
  auto archivedOpt = repository.findArchivedGameById(gameId);
  if (!archivedOpt) {
    result.message = "Archived game not found";
    return result;
  }

  ArchivedGame archivedGame = archivedOpt.value();

  // 2. Check user was a player in this game
  if (username != archivedGame.red_player &&
      username != archivedGame.black_player) {
    result.message = "You are not a player in this game";
    return result;
  }

  // 3. Check there's a pending rematch offer
  if (archivedGame.rematch_offered_by.empty()) {
    result.message = "No pending rematch request";
    return result;
  }

  // 4. Check user is the one who should respond (not the one who offered)
  if (archivedGame.rematch_offered_by == username) {
    result.message = "You cannot respond to your own rematch request";
    return result;
  }

  // 5. Process response
  if (accept) {
    // Accept rematch - create new game with swapped colors
    // The player who was red becomes black and vice versa
    string newRedPlayer = archivedGame.black_player;
    string newBlackPlayer = archivedGame.red_player;

    // Create new game with specified colors (no random assignment)
    auto createResult =
        createGameWithColors(newRedPlayer, newBlackPlayer,
                             archivedGame.time_control, archivedGame.rated);

    if (!createResult.success) {
      result.message = "Failed to create rematch game: " + createResult.message;
      return result;
    }

    // Mark rematch as accepted (prevents future rematch requests on this game)
    repository.setRematchAccepted(gameId);

    result.success = true;
    result.message = "Rematch accepted, new game created";
    result.game = createResult.game;

    return result;
  } else {
    // Decline rematch - clear the offer
    bool cleared = repository.clearRematchOffer(gameId);

    if (!cleared) {
      result.message = "Failed to decline rematch";
      return result;
    }

    result.success = true;
    result.message = "Rematch declined";

    return result;
  }
}

GameResult GameService::getGame(const string &gameId) {
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

GameResult GameService::listGames(const string &username,
                                  const string &filter) {
  GameResult result;
  result.success = true;
  result.message = "Games retrieved successfully";
  result.games = repository.findByUser(username, filter);

  return result;
}

// ============ Game History & Replay Operations ============

GameResult GameService::getGameHistory(const string &username, int limit,
                                       int offset) {
  GameResult result;
  result.success = false;

  // Validate user exists
  if (!repository.userExists(username)) {
    result.message = "User not found";
    return result;
  }

  // Validate pagination params
  if (limit <= 0)
    limit = 50;
  if (limit > 100)
    limit = 100; // Cap max limit
  if (offset < 0)
    offset = 0;

  // Get game history from archive
  result.archivedGames = repository.findGameHistory(username, limit, offset);
  result.success = true;
  result.message = "Game history retrieved successfully";

  return result;
}

GameResult GameService::getGameDetails(const string &gameId) {
  GameResult result;
  result.success = false;

  // First try to find in active games
  auto activeOpt = repository.findById(gameId);
  if (activeOpt) {
    result.success = true;
    result.message = "Active game details retrieved";
    result.game = activeOpt.value();
    return result;
  }

  // If not active, try archived games (includes full moves for replay)
  auto archivedOpt = repository.findArchivedGameById(gameId);
  if (archivedOpt) {
    result.success = true;
    result.message = "Archived game details retrieved";
    result.archivedGame = archivedOpt.value();
    return result;
  }

  result.message = "Game not found";
  return result;
}

// Convert custom board setup (JSON map) to XFEN string
// customBoardSetup: JSON object with keys "row_col" -> "color_pieceType"
// Example: {"0_0": "red_Rook", "9_0": "black_Rook"}
string
GameService::customBoardSetupToXfen(const nlohmann::json &customBoardSetup,
                                    const string &startingColor) {
  // Initialize empty board
  char board[10][9];
  for (int y = 0; y < 10; y++) {
    for (int x = 0; x < 9; x++) {
      board[y][x] = '.';
    }
  }

  // Map piece types to XFEN characters
  map<string, char> pieceMap = {// Red pieces (uppercase)
                                {"red_King", 'K'},
                                {"red_Advisor", 'A'},
                                {"red_Elephant", 'B'},
                                {"red_Horse", 'N'},
                                {"red_Rook", 'R'},
                                {"red_Cannon", 'C'},
                                {"red_Pawn", 'P'},
                                // Black pieces (lowercase)
                                {"black_King", 'k'},
                                {"black_Advisor", 'a'},
                                {"black_Elephant", 'b'},
                                {"black_Horse", 'n'},
                                {"black_Rook", 'r'},
                                {"black_Cannon", 'c'},
                                {"black_Pawn", 'p'}};

  // Parse custom board setup
  if (customBoardSetup.is_object()) {
    cout << "[customBoardSetupToXfen] Parsing " << customBoardSetup.size()
         << " pieces" << endl;
    for (auto &[key, value] : customBoardSetup.items()) {
      if (value.is_string()) {
        string posKey = key;
        string pieceInfo = value.get<string>();

        // Parse position: "row_col"
        size_t underscorePos = posKey.find('_');
        if (underscorePos != string::npos) {
          try {
            int row = stoi(posKey.substr(0, underscorePos));
            int col = stoi(posKey.substr(underscorePos + 1));

            // Validate coordinates
            if (row >= 0 && row < 10 && col >= 0 && col < 9) {
              // Get piece character
              if (pieceMap.find(pieceInfo) != pieceMap.end()) {
                board[row][col] = pieceMap[pieceInfo];
                cout << "[customBoardSetupToXfen] Placed " << pieceInfo
                     << " at (" << row << "," << col << ")" << endl;
              } else {
                cerr << "[customBoardSetupToXfen] WARNING: Unknown piece type: "
                     << pieceInfo << endl;
              }
            } else {
              cerr << "[customBoardSetupToXfen] WARNING: Invalid coordinates: ("
                   << row << "," << col << ")" << endl;
            }
          } catch (const exception &e) {
            cerr << "[customBoardSetupToXfen] ERROR parsing position " << posKey
                 << ": " << e.what() << endl;
            continue;
          }
        }
      }
    }
  } else {
    cerr << "[customBoardSetupToXfen] ERROR: customBoardSetup is not an object"
         << endl;
  }

  // Convert to XFEN
  string xfen = boardToXfen(board, startingColor, 1);
  cout << "[customBoardSetupToXfen] Generated XFEN: " << xfen << endl;
  return xfen;
}
