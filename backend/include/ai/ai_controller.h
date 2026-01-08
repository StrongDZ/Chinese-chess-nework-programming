#ifndef AI_CONTROLLER_H
#define AI_CONTROLLER_H

#include "ai/ai_service.h"
#include "game/game_service.h"
#include <nlohmann/json.hpp>
#include <string>

/**
 * AIController - Controller layer for AI game operations
 * 
 * Handles:
 * - Start AI game (CREATE_AI_GAME)
 * - Get AI move (GET_AI_MOVE)
 * - Make move in AI game (MAKE_AI_MOVE)
 * - Get move suggestion (SUGGEST_MOVE)
 * 
 * API:
 * - handleCreateAIGame: Create a new game against AI
 * - handleGetAIMove: Get AI's response move
 * - handleMakeAIMove: Player makes a move, AI responds
 * - handleSuggestMove: Get AI suggestion for current position
 */

using json = nlohmann::json;

class AIController {
private:
    AIService& aiService;
    GameService& gameService;
    
    // Convert AIDifficulty from string
    AIDifficulty parseDifficulty(const std::string& diff);
    
public:
    AIController(AIService& ai, GameService& game);
    
    /**
     * Create a new AI game
     * 
     * Input:
     * {
     *   "username": "player1",
     *   "difficulty": "easy|medium|hard",
     *   "time_control": "blitz|bullet|classical" (optional),
     *   "player_color": "red|black" (optional, default "red")
     * }
     * 
     * Output:
     * {
     *   "status": "success|error",
     *   "message": "...",
     *   "game": { game object },
     *   "ai_difficulty": "easy|medium|hard"
     * }
     */
    json handleCreateAIGame(const json& request);
    
    /**
     * Get AI's move for current position
     * 
     * Input:
     * {
     *   "game_id": "...",
     *   "xfen": "current position" (optional, uses game state if not provided)
     * }
     * 
     * Output:
     * {
     *   "status": "success|error",
     *   "message": "...",
     *   "move": {
     *     "from_x": 0, "from_y": 0,
     *     "to_x": 1, "to_y": 0,
     *     "uci": "a0a1"
     *   }
     * }
     */
    json handleGetAIMove(const json& request);
    
    /**
     * Player makes a move, then get AI response
     * 
     * Input:
     * {
     *   "game_id": "...",
     *   "username": "player1",
     *   "from_x": 0, "from_y": 0,
     *   "to_x": 1, "to_y": 0,
     *   "piece": "P" (optional),
     *   "notation": "P0-1" (optional)
     * }
     * 
     * Output:
     * {
     *   "status": "success|error",
     *   "message": "...",
     *   "player_move": { move details },
     *   "ai_move": { ai response move },
     *   "game": { updated game state },
     *   "game_over": false,
     *   "result": null
     * }
     */
    json handleMakeAIMove(const json& request);
    
    /**
     * Get AI suggestion for current position (hint)
     * 
     * Input:
     * {
     *   "game_id": "...",
     *   "xfen": "current position" (optional)
     * }
     * 
     * Output:
     * {
     *   "status": "success|error",
     *   "message": "...",
     *   "suggested_move": {
     *     "from_x": 0, "from_y": 0,
     *     "to_x": 1, "to_y": 0,
     *     "uci": "a0a1"
     *   }
     * }
     */
    json handleSuggestMove(const json& request);
    
    /**
     * Resign from AI game
     * 
     * Input:
     * {
     *   "game_id": "...",
     *   "username": "player1"
     * }
     * 
     * Output:
     * {
     *   "status": "success|error",
     *   "message": "...",
     *   "result": "ai_win"
     * }
     */
    json handleResignAIGame(const json& request);
};

#endif // AI_CONTROLLER_H
