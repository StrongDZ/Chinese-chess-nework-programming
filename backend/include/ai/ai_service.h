#ifndef AI_SERVICE_H
#define AI_SERVICE_H

#include <string>
#include <optional>
#include <cstdio>
#include <unistd.h>
#include <sys/types.h>
#include <nlohmann/json.hpp>

/**
 * AIService - Service layer for AI integration
 * 
 * Communicates with Python AI engine via persistent subprocess
 * Provides move prediction for AI games
 * 
 * Difficulty levels:
 * - easy: depth 8, 3 seconds
 * - medium: depth 12, 8 seconds
 * - hard: depth 18, 15 seconds
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
    
    // Persistent engine process
    FILE* aiProcessStdin;   // stdin pipe to Python process
    FILE* aiProcessStdout;  // stdout pipe from Python process
    FILE* aiProcessStderr;  // stderr pipe from Python process (for debugging)
    pid_t aiProcessPid;     // PID of Python process
    
    // Convert difficulty enum to string
    std::string difficultyToString(AIDifficulty difficulty);
    
    // Execute Python AI script and get result (via persistent process)
    std::string executePythonAI(const std::string& xfen, const std::string& difficulty);
    
    // Parse UCI move string to AIMove
    std::optional<AIMove> parseUCIMove(const std::string& uci);
    
    // Cleanup persistent process
    void cleanupProcess();
    
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
