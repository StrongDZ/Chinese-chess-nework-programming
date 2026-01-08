#ifndef AI_SERVICE_H
#define AI_SERVICE_H

#include <string>
#include <optional>
#include <nlohmann/json.hpp>

/**
 * AIService - Service layer for AI integration
 * 
 * Communicates with Python AI engine via subprocess/HTTP
 * Provides move prediction for AI games
 * 
 * Difficulty levels:
 * - easy: depth 3, quick responses
 * - medium: depth 5, balanced play
 * - hard: depth 8, strongest play
 */

// AI Move result
struct AIMove {
    int fromX;  // Row (0-9)
    int fromY;  // Col (0-8)
    int toX;
    int toY;
    std::string uci;  // UCI format: "a0a1"
};

// AI Result struct
struct AIResult {
    bool success;
    std::string message;
    std::optional<AIMove> move;
};

enum class AIDifficulty {
    EASY,
    MEDIUM,
    HARD
};

class AIService {
private:
    std::string pythonPath;
    std::string aiScriptPath;
    bool initialized;
    
    // Convert difficulty enum to string
    std::string difficultyToString(AIDifficulty difficulty);
    
    // Execute Python AI script and get result
    std::string executePythonAI(const std::string& xfen, const std::string& difficulty);
    
    // Parse UCI move string to AIMove
    std::optional<AIMove> parseUCIMove(const std::string& uci);
    
public:
    AIService();
    ~AIService();
    
    // Initialize AI service (find Python, verify AI script exists)
    bool initialize(const std::string& pythonPath = "python3",
                    const std::string& aiScriptDir = "");
    
    // Check if AI is ready
    bool isReady() const;
    
    // Predict best move for given position
    // xfen: Current board position in XFEN format
    // difficulty: easy/medium/hard
    AIResult predictMove(const std::string& xfen, AIDifficulty difficulty);
    
    // Predict move with move history (for more accurate analysis)
    // initialXfen: Starting position
    // moves: List of moves in UCI format ["a0a1", "b9b8", ...]
    AIResult predictMoveWithHistory(const std::string& initialXfen,
                                    const std::vector<std::string>& moves,
                                    AIDifficulty difficulty);
    
    // Get suggested move (always uses HARD difficulty)
    AIResult suggestMove(const std::string& xfen);
    
    // Convert board array to XFEN
    static std::string boardToXfen(const std::string board[10][9], const std::string& turn);
    
    // Convert coordinates to UCI move format
    static std::string toUCI(int fromX, int fromY, int toX, int toY);
    
    // Convert UCI move to coordinates
    static std::optional<AIMove> fromUCI(const std::string& uci);
};

#endif // AI_SERVICE_H
