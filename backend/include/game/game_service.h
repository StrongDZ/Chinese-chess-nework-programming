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
    std::vector<Game> games; // For list operations
};

class GameService {
private:
    GameRepository& repository;
    
    // Constants
    static constexpr int DRAW_OFFER_TTL_SECONDS = 300; // 5 minutes
    
    // Initial XFEN for Xiangqi
    static constexpr const char* INITIAL_XFEN = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1";
    
    // Validation helpers
    bool isValidCoordinate(int x, int y);
    bool isGameOver(const std::string& status);
    
    // Time control helpers
    int getTimeLimitSeconds(const std::string& timeControl);
    int getIncrementSeconds(const std::string& timeControl);
    
    // Rating calculation (simplified Elo)
    void calculateAndUpdateRatings(const std::string& redUsername, const std::string& blackUsername,
                                   const std::string& result, const std::string& timeControl);
    
public:
    explicit GameService(GameRepository& repo);
    
    // Tạo game mới từ challenge đã accept
    // Parameters from accepted challenge
    GameResult createGame(const std::string& challengerUsername,
                          const std::string& challengedUsername,
                          const std::string& timeControl = "blitz",
                          bool rated = true);
    
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
    
    // Đề nghị hòa
    GameResult offerDraw(const std::string& username, const std::string& gameId);
    
    // Chấp nhận/từ chối hòa
    GameResult respondDraw(const std::string& username, const std::string& gameId, bool accept);
    
    // Đầu hàng
    GameResult resign(const std::string& username, const std::string& gameId);
    
    // Lấy thông tin game
    GameResult getGame(const std::string& gameId);
    
    // Lấy danh sách game của user
    // filter: "active", "completed", "all"
    GameResult listGames(const std::string& username, const std::string& filter = "active");

    // Tìm đối thủ ngẫu nhiên trong khoảng rating (elo) cho matchmaking nhanh
    GameResult autoMatchAndCreateGame(const std::string& username,
                                      const std::string& timeControl = "blitz",
                                      bool rated = true,
                                      int ratingWindow = 200);
};

#endif
