#!/usr/bin/env python3
"""
AI Service for Chinese Chess
Service script that can be called from C++ via subprocess
Accepts JSON input and returns JSON output
"""

import sys
import json
import os

# Add parent directory to path to import python_ai
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from python_ai import PikafishEngine, AIDifficulty, MovePayload, Coord, GameStateManager


class AIService:
    """AI Service singleton"""
    _instance = None
    _engine = None
    _game_state = None
    
    def __new__(cls):
        if cls._instance is None:
            cls._instance = super(AIService, cls).__new__(cls)
            cls._engine = PikafishEngine()
            cls._game_state = GameStateManager()
        return cls._instance
    
    def initialize(self, pikafish_path="pikafish"):
        """Initialize AI engine"""
        return self._engine.initialize(pikafish_path)
    
    def shutdown(self):
        """Shutdown AI engine"""
        self._engine.shutdown()
    
    def is_ready(self):
        """Check if engine is ready"""
        return self._engine.is_ready()
    
    def get_best_move(self, fen_position, difficulty_str):
        """Get best move"""
        difficulty_map = {
            "easy": AIDifficulty.EASY,
            "medium": AIDifficulty.MEDIUM,
            "hard": AIDifficulty.HARD
        }
        difficulty = difficulty_map.get(difficulty_str.lower(), AIDifficulty.MEDIUM)
        return self._engine.get_best_move(fen_position, difficulty)
    
    def suggest_move(self, fen_position):
        """Suggest move"""
        return self._engine.suggest_move(fen_position)
    
    def parse_uci_move(self, uci_move):
        """Parse UCI move to MovePayload"""
        move = PikafishEngine.parse_uci_move(uci_move)
        return {
            "piece": move.piece,
            "from": {"row": move.from_pos.row, "col": move.from_pos.col},
            "to": {"row": move.to_pos.row, "col": move.to_pos.col}
        }
    
    def move_to_uci(self, from_row, from_col, to_row, to_col):
        """Convert move to UCI format"""
        move = MovePayload("", Coord(from_row, from_col), Coord(to_row, to_col))
        return PikafishEngine.move_to_uci(move)
    
    def initialize_game(self, player_fd, ai_fd, difficulty_str):
        """Initialize game"""
        difficulty_map = {
            "easy": AIDifficulty.EASY,
            "medium": AIDifficulty.MEDIUM,
            "hard": AIDifficulty.HARD
        }
        difficulty = difficulty_map.get(difficulty_str.lower(), AIDifficulty.MEDIUM)
        self._game_state.initialize_game(player_fd, ai_fd, difficulty)
        return True
    
    def apply_move(self, player_fd, from_row, from_col, to_row, to_col):
        """Apply move to game state"""
        move = MovePayload("", Coord(from_row, from_col), Coord(to_row, to_col))
        return self._game_state.apply_move(player_fd, move)
    
    def get_position_string(self, player_fd):
        """Get position string"""
        return self._game_state.get_position_string(player_fd)
    
    def get_current_fen(self, player_fd):
        """Get current FEN"""
        return self._game_state.get_current_fen(player_fd)
    
    def get_ai_difficulty(self, player_fd):
        """Get AI difficulty"""
        difficulty = self._game_state.get_ai_difficulty(player_fd)
        return difficulty.value
    
    def has_game(self, player_fd):
        """Check if game exists"""
        return self._game_state.has_game(player_fd)
    
    def end_game(self, player_fd):
        """End game"""
        self._game_state.end_game(player_fd)
        return True
    
    def get_opponent_fd(self, player_fd):
        """Get opponent FD"""
        return self._game_state.get_opponent_fd(player_fd)
    
    def get_current_board_array(self, player_fd):
        """Get current board array for a game"""
        board = self._game_state.get_current_board_array(player_fd)
        return board
    
    def is_valid_move_on_board(self, board_json, from_row, from_col, to_row, to_col):
        """Validate move on board"""
        # Convert JSON board to 2D list
        board = json.loads(board_json) if isinstance(board_json, str) else board_json
        move = MovePayload("", Coord(from_row, from_col), Coord(to_row, to_col))
        return GameStateManager.is_valid_move_on_board(board, move)


def main():
    """Main function - handles JSON commands from stdin (persistent service)"""
    service = AIService()
    
    # Persistent service loop - read commands continuously
    while True:
        try:
            line = sys.stdin.readline()
            if not line:  # EOF (C++ closed pipe)
                break
            
            line = line.strip()
            if not line:  # Empty line, skip
                continue
            
            command = json.loads(line)
            cmd = command.get("command")
            params = command.get("params", {})
            
            result = {"success": False, "data": None, "error": None}
            
            try:
                if cmd == "initialize":
                    pikafish_path = params.get("pikafish_path", "pikafish")
                    result["success"] = service.initialize(pikafish_path)
                    result["data"] = {"ready": service.is_ready()}
                
                elif cmd == "shutdown":
                    service.shutdown()
                    result["success"] = True
                    # After shutdown, exit the loop
                    print(json.dumps(result))
                    sys.stdout.flush()
                    break
                
                elif cmd == "is_ready":
                    result["success"] = True
                    result["data"] = {"ready": service.is_ready()}
                
                elif cmd == "get_best_move":
                    fen_position = params.get("fen_position")
                    difficulty = params.get("difficulty", "medium")
                    move = service.get_best_move(fen_position, difficulty)
                    result["success"] = bool(move)
                    result["data"] = {"move": move}
                
                elif cmd == "suggest_move":
                    fen_position = params.get("fen_position")
                    move = service.suggest_move(fen_position)
                    result["success"] = bool(move)
                    result["data"] = {"move": move}
                
                elif cmd == "parse_uci_move":
                    uci_move = params.get("uci_move")
                    move_data = service.parse_uci_move(uci_move)
                    result["success"] = True
                    result["data"] = move_data
                
                elif cmd == "move_to_uci":
                    from_row = params.get("from_row")
                    from_col = params.get("from_col")
                    to_row = params.get("to_row")
                    to_col = params.get("to_col")
                    uci = service.move_to_uci(from_row, from_col, to_row, to_col)
                    result["success"] = True
                    result["data"] = {"uci": uci}
                
                elif cmd == "initialize_game":
                    player_fd = params.get("player_fd")
                    ai_fd = params.get("ai_fd", -1)
                    difficulty = params.get("difficulty", "medium")
                    result["success"] = service.initialize_game(player_fd, ai_fd, difficulty)
                
                elif cmd == "apply_move":
                    player_fd = params.get("player_fd")
                    from_row = params.get("from_row")
                    from_col = params.get("from_col")
                    to_row = params.get("to_row")
                    to_col = params.get("to_col")
                    result["success"] = service.apply_move(player_fd, from_row, from_col, to_row, to_col)
                
                elif cmd == "get_position_string":
                    player_fd = params.get("player_fd")
                    position = service.get_position_string(player_fd)
                    result["success"] = bool(position)
                    result["data"] = {"position": position}
                
                elif cmd == "get_current_fen":
                    player_fd = params.get("player_fd")
                    fen = service.get_current_fen(player_fd)
                    result["success"] = bool(fen)
                    result["data"] = {"fen": fen}
                
                elif cmd == "get_ai_difficulty":
                    player_fd = params.get("player_fd")
                    difficulty = service.get_ai_difficulty(player_fd)
                    result["success"] = True
                    result["data"] = {"difficulty": difficulty}
                
                elif cmd == "has_game":
                    player_fd = params.get("player_fd")
                    has = service.has_game(player_fd)
                    result["success"] = True
                    result["data"] = {"has_game": has}
                
                elif cmd == "end_game":
                    player_fd = params.get("player_fd")
                    result["success"] = service.end_game(player_fd)
                
                elif cmd == "get_opponent_fd":
                    player_fd = params.get("player_fd")
                    opp_fd = service.get_opponent_fd(player_fd)
                    result["success"] = True
                    result["data"] = {"opponent_fd": opp_fd}
                
                elif cmd == "get_current_board_array":
                    player_fd = params.get("player_fd")
                    board = service.get_current_board_array(player_fd)
                    if board:
                        result["success"] = True
                        result["data"] = {"board": board}
                    else:
                        result["success"] = False
                        result["error"] = "Failed to get board array"
                
                elif cmd == "is_valid_move_on_board":
                    board_json = params.get("board")
                    from_row = params.get("from_row")
                    from_col = params.get("from_col")
                    to_row = params.get("to_row")
                    to_col = params.get("to_col")
                    is_valid = service.is_valid_move_on_board(board_json, from_row, from_col, to_row, to_col)
                    result["success"] = True
                    result["data"] = {"is_valid": is_valid}
                
                else:
                    result["error"] = f"Unknown command: {cmd}"
            
            except Exception as e:
                result["error"] = str(e)
            
            # Write result to stdout
            print(json.dumps(result))
            sys.stdout.flush()
        
        except json.JSONDecodeError as e:
            error_result = {"success": False, "data": None, "error": f"Invalid JSON: {e}"}
            print(json.dumps(error_result))
            sys.stdout.flush()
        except Exception as e:
            error_result = {"success": False, "data": None, "error": str(e)}
            print(json.dumps(error_result))
            sys.stdout.flush()


if __name__ == "__main__":
    main()

