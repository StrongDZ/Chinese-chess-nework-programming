#ifndef GAME_SERVICE_H
#define GAME_SERVICE_H

#include "game_repository.h"
#include <string>
#include <optional>

/**
 * GameService - Business Logic Layer cho Game
 * 
 * Chịu trách nhiệm:
 * - Validation (coordinates, turns, game state)
 * - Business rules (rating calculation, time control)
 * - Sử dụng GameRepository để truy cập database
 * 
 * NOTE: Sử dụng username thay vì token (protocol layer mapping fd -> username)
 */

// Result struct cho service operations
struct GameResult {
    bool success;
    std::string message;
    std::optional<Game> game;
    std::vector<Game> games;                   // For list active games
    std::optional<ArchivedGame> archivedGame;  // For game details (replay)
    std::vector<ArchivedGame> archivedGames;   // For game history
};

class GameService {
private:
    GameRepository& repository;
    
    // Initial XFEN for Xiangqi
    static constexpr const char* INITIAL_XFEN = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1";
    
    // Validation helpers
    // bool isValidCoordinate(int x, int y);
    bool isGameOver(const std::string& status);
    
    // Time control helpers
    int getTimeLimitSeconds(const std::string& timeControl);
    int getIncrementSeconds(const std::string& timeControl);
    
    // Rating calculation (simplified Elo)
    void calculateAndUpdateRatings(const std::string& redUsername, const std::string& blackUsername,
                                   const std::string& result, const std::string& timeControl);
    
    // XFEN calculation helper
    // Tính toán XFEN mới sau khi thực hiện nước đi
    std::string calculateNewXfen(const std::string& currentXfen,
                                  int fromX, int fromY,
                                  int toX, int toY,
                                  const std::string& nextTurn);
    
    // Parse XFEN to 2D board array
    void parseXfenToBoard(const std::string& xfen, char board[10][9]);
    
    // Convert board array to XFEN
    std::string boardToXfen(const char board[10][9], const std::string& turn, int moveCount);
    
    // Create game with specific colors (no random assignment) - used for rematch
    GameResult createGameWithColors(const std::string& redPlayer,
                                    const std::string& blackPlayer,
                                    const std::string& timeControl,
                                    bool rated);
    
    // Validate XFEN format for custom board setup
    bool isValidXfen(const std::string& xfen);
    
public:
    explicit GameService(GameRepository& repo);
    
    // Tạo game mới từ challenge đã accept
    // Parameters from accepted challenge
    GameResult createGame(const std::string& challengerUsername,
                          const std::string& challengedUsername,
                          const std::string& timeControl = "blitz",
                          bool rated = true);
    
    // Tạo game với custom board setup (custom initial XFEN)
    // customXfen: Custom initial position in XFEN format
    // startingColor: "red" or "black" - who moves first
    // Note: Custom games are unrated by default
    GameResult createCustomGame(const std::string& redPlayer,
                                const std::string& blackPlayer,
                                const std::string& customXfen,
                                const std::string& startingColor = "red",
                                const std::string& timeControl = "blitz");
    
    // Thực hiện nước đi
    GameResult makeMove(const std::string& username,
                        const std::string& gameId,
                        int fromX, int fromY,
                        int toX, int toY,
                        const std::string& piece = "",
                        const std::string& captured = "",
                        const std::string& notation = "",
                        const std::string& xfenAfter = "",
                        int timeTaken = 0);
    
    // Kết thúc game với result cụ thể (dùng cho draw agreement từ protocol, checkmate, etc.)
    // result: "red_win", "black_win", "draw", "abandoned"
    // termination: "checkmate", "resignation", "timeout", "draw_agreement", etc.
    GameResult endGame(const std::string& gameId,
                       const std::string& result,
                       const std::string& termination = "normal");
    
    // Đầu hàng (wrapper gọi endGame với result thích hợp)
    GameResult resign(const std::string& username, const std::string& gameId);
    
    // ============ Draw Offer Operations ============
    
    // Đề nghị hòa - lưu trạng thái pending draw
    GameResult offerDraw(const std::string& username, const std::string& gameId);
    
    // Phản hồi đề nghị hòa
    // accept: true = chấp nhận hòa, false = từ chối
    GameResult respondToDraw(const std::string& username, const std::string& gameId, bool accept);
    
    // ============ Rematch Operations ============
    
    // Yêu cầu rematch sau khi game kết thúc
    GameResult requestRematch(const std::string& username, const std::string& gameId);
    
    // Phản hồi yêu cầu rematch
    // accept: true = chấp nhận rematch (tạo game mới), false = từ chối
    GameResult respondToRematch(const std::string& username, const std::string& gameId, bool accept);
    
    // Lấy thông tin game
    GameResult getGame(const std::string& gameId);
    
    // Lấy danh sách game của user
    // filter: "active", "completed", "all"
    GameResult listGames(const std::string& username, const std::string& filter = "active");
    
    // ============ Game History & Replay Operations ============
    
    // Lấy lịch sử game đã hoàn thành của user (từ game_archive)
    // Dùng cho GET_GAME_HISTORY handler
    GameResult getGameHistory(const std::string& username, int limit = 50, int offset = 0);
    
    // Lấy chi tiết game với đầy đủ moves để replay
    // Dùng cho GET_GAME_DETAILS handler
    GameResult getGameDetails(const std::string& gameId);

    // Tìm đối thủ ngẫu nhiên trong khoảng rating (elo) cho matchmaking nhanh
    GameResult autoMatchAndCreateGame(const std::string& username,
                                      const std::string& timeControl = "blitz",
                                      bool rated = true,
                                      int ratingWindow = 200);
};

#endif
