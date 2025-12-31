"""
Game State Manager for Chinese Chess AI
Manages game states and board positions
"""

import threading
from typing import Optional, List, Dict
from dataclasses import dataclass
from .ai_engine import AIDifficulty, MovePayload, Coord, PikafishEngine


@dataclass
class BoardState:
    """Board state structure"""
    fen: str
    position_string: str
    moves: List[MovePayload]
    difficulty: AIDifficulty
    player_turn: bool
    is_valid: bool


class GameStateManager:
    """Game State Manager for managing multiple AI games"""
    
    @dataclass
    class GameInfo:
        """Game information"""
        player_fd: int
        ai_fd: int
        difficulty: AIDifficulty
        initial_fen: str
        move_history: List[MovePayload]
        player_turn: bool
    
    def __init__(self):
        self.active_games: Dict[int, GameStateManager.GameInfo] = {}
        self.games_lock = threading.Lock()
    
    def initialize_game(self, player_fd: int, ai_fd: int, difficulty: AIDifficulty):
        """Initialize game with starting position"""
        with self.games_lock:
            game = GameStateManager.GameInfo(
                player_fd=player_fd,
                ai_fd=ai_fd,
                difficulty=difficulty,
                initial_fen=self._get_initial_fen(),
                move_history=[],
                player_turn=True  # Player goes first
            )
            self.active_games[player_fd] = game
    
    def apply_move(self, player_fd: int, move: MovePayload) -> bool:
        """Apply move to game state"""
        with self.games_lock:
            if player_fd not in self.active_games:
                print(f"[GameState] Error: No game found for player_fd={player_fd}")
                return False
            
            game = self.active_games[player_fd]
            
            # Validate move coordinates
            if (move.from_pos.row < 0 or move.from_pos.row >= 10 or 
                move.from_pos.col < 0 or move.from_pos.col >= 9 or
                move.to_pos.row < 0 or move.to_pos.row >= 10 or
                move.to_pos.col < 0 or move.to_pos.col >= 9):
                print("[GameState] Error: Invalid move coordinates")
                return False
            
            # Add move to history
            game.move_history.append(move)
            game.player_turn = not game.player_turn
            
            print(f"[GameState] Move applied: ({move.from_pos.row},{move.from_pos.col}) -> "
                  f"({move.to_pos.row},{move.to_pos.col})")
            
            return True
    
    def get_current_fen(self, player_fd: int) -> str:
        """Get current FEN position (for backward compatibility)"""
        return self.get_position_string(player_fd)
    
    def get_position_string(self, player_fd: int) -> str:
        """Get position string for engine (uses move history - more reliable)"""
        with self.games_lock:
            if player_fd not in self.active_games:
                return ""
            
            game = self.active_games[player_fd]
            
            # Build position command using move history
            position_str = f"position fen {game.initial_fen}"
            
            if game.move_history:
                position_str += " moves"
                for move in game.move_history:
                    uci_move = PikafishEngine.move_to_uci(move)
                    position_str += f" {uci_move}"
            
            return position_str
    
    def get_ai_difficulty(self, player_fd: int) -> AIDifficulty:
        """Get AI difficulty for this game"""
        with self.games_lock:
            if player_fd not in self.active_games:
                return AIDifficulty.MEDIUM
            
            return self.active_games[player_fd].difficulty
    
    def has_game(self, player_fd: int) -> bool:
        """Check if game exists"""
        with self.games_lock:
            return player_fd in self.active_games
    
    def end_game(self, player_fd: int):
        """End game"""
        with self.games_lock:
            if player_fd in self.active_games:
                del self.active_games[player_fd]
    
    def get_opponent_fd(self, player_fd: int) -> int:
        """Get opponent FD"""
        with self.games_lock:
            if player_fd not in self.active_games:
                return -1
            
            return self.active_games[player_fd].ai_fd
    
    def get_board_state(self, player_fd: int) -> BoardState:
        """Get board state (for external use)"""
        with self.games_lock:
            state = BoardState(
                fen="",
                position_string="",
                moves=[],
                difficulty=AIDifficulty.MEDIUM,
                player_turn=True,
                is_valid=False
            )
            
            if player_fd not in self.active_games:
                return state
            
            game = self.active_games[player_fd]
            
            state.is_valid = True
            state.fen = game.initial_fen
            state.position_string = self.get_position_string(player_fd)
            state.moves = game.move_history.copy()
            state.difficulty = game.difficulty
            state.player_turn = game.player_turn
            
            return state
    
    def get_current_board_array(self, player_fd: int) -> Optional[List[List[str]]]:
        """Get current board array for a game"""
        with self.games_lock:
            if player_fd not in self.active_games:
                return None
            
            game = self.active_games[player_fd]
            
            # Get position string
            position_str = self.get_position_string(player_fd)
            
            # Convert position string to board array
            board, moves, _ = PikafishEngine.position_string_to_board_array(position_str)
            
            if board is None:
                # Fallback: use initial FEN
                board, _, _, _ = PikafishEngine.fen_to_board_array(game.initial_fen)
                if board is None:
                    return None
                
                # Apply all moves from history
                for move in game.move_history:
                    self.apply_move_to_board_array(board, move)
            
            return board
    
    @staticmethod
    def apply_move_to_board_array(board: List[List[str]], move: MovePayload) -> bool:
        """Apply move to board array manually"""
        # Validate move first
        if not GameStateManager.is_valid_move_on_board(board, move):
            print("[BoardArray] Error: Invalid move")
            return False
        
        # Get piece at source
        piece = board[move.from_pos.row][move.from_pos.col]
        if piece == ' ':
            print("[BoardArray] Error: No piece at source position")
            return False
        
        # Handle capture (if any)
        captured_piece = board[move.to_pos.row][move.to_pos.col]
        if captured_piece != ' ':
            print(f"[BoardArray] Capturing piece: {captured_piece} at "
                  f"({move.to_pos.row},{move.to_pos.col})")
        
        # Apply move
        board[move.from_pos.row][move.from_pos.col] = ' '  # Clear source
        board[move.to_pos.row][move.to_pos.col] = piece   # Place piece at destination
        
        print(f"[BoardArray] Move applied: ({move.from_pos.row},{move.from_pos.col}) -> "
              f"({move.to_pos.row},{move.to_pos.col})")
        print(f"[BoardArray] Piece moved: {piece}")
        
        return True
    
    @staticmethod
    def is_valid_move_on_board(board: List[List[str]], move: MovePayload) -> bool:
        """Validate move on board array"""
        from .board_validator import (
            is_in_palace, is_red_piece, is_black_piece,
            isValidKingMove, isValidAdvisorMove, isValidElephantMove,
            isValidKnightMove, isValidRookMove, isValidCannonMove,
            isValidPawnMove, kings_face_each_other
        )
        
        # Validate coordinates
        if (move.from_pos.row < 0 or move.from_pos.row >= 10 or
            move.from_pos.col < 0 or move.from_pos.col >= 9 or
            move.to_pos.row < 0 or move.to_pos.row >= 10 or
            move.to_pos.col < 0 or move.to_pos.col >= 9):
            return False
        
        # Check if source has a piece
        piece = board[move.from_pos.row][move.from_pos.col]
        if piece == ' ':
            return False  # No piece at source
        
        # Check if destination is different from source
        if (move.from_pos.row == move.to_pos.row and 
            move.from_pos.col == move.to_pos.col):
            return False  # Same position
        
        # Determine piece color
        is_red = is_red_piece(piece)
        is_black = is_black_piece(piece)
        
        if not is_red and not is_black:
            return False  # Invalid piece character
        
        # Check if capturing own piece
        captured = board[move.to_pos.row][move.to_pos.col]
        if captured != ' ':
            captured_is_red = is_red_piece(captured)
            captured_is_black = is_black_piece(captured)
            
            if (is_red and captured_is_red) or (is_black and captured_is_black):
                return False  # Cannot capture own piece
        
        # Validate move based on piece type
        valid_move = False
        
        piece_upper = piece.upper()
        if piece_upper == 'K':
            valid_move = isValidKingMove(board, move.from_pos.row, move.from_pos.col,
                                         move.to_pos.row, move.to_pos.col, is_red)
        elif piece_upper == 'A':
            valid_move = isValidAdvisorMove(board, move.from_pos.row, move.from_pos.col,
                                            move.to_pos.row, move.to_pos.col, is_red)
        elif piece_upper == 'B':
            valid_move = isValidElephantMove(board, move.from_pos.row, move.from_pos.col,
                                             move.to_pos.row, move.to_pos.col, is_red)
        elif piece_upper == 'N':
            valid_move = isValidKnightMove(board, move.from_pos.row, move.from_pos.col,
                                          move.to_pos.row, move.to_pos.col, is_red)
        elif piece_upper == 'R':
            valid_move = isValidRookMove(board, move.from_pos.row, move.from_pos.col,
                                        move.to_pos.row, move.to_pos.col, is_red)
        elif piece_upper == 'C':
            valid_move = isValidCannonMove(board, move.from_pos.row, move.from_pos.col,
                                          move.to_pos.row, move.to_pos.col, is_red)
        elif piece_upper == 'P':
            valid_move = isValidPawnMove(board, move.from_pos.row, move.from_pos.col,
                                       move.to_pos.row, move.to_pos.col, is_red)
        else:
            return False  # Unknown piece type
        
        if not valid_move:
            return False
        
        # Check if move would result in kings facing each other
        # Simulate the move temporarily
        temp_board = [row[:] for row in board]  # Deep copy
        
        # Apply move temporarily
        temp_board[move.to_pos.row][move.to_pos.col] = piece
        temp_board[move.from_pos.row][move.from_pos.col] = ' '
        
        # Check if kings face each other after this move
        if kings_face_each_other(temp_board):
            return False  # Move would result in illegal king position
        
        return True
    
    def _get_initial_fen(self) -> str:
        """Generate initial FEN for Chinese Chess"""
        # Standard starting position for Chinese Chess (Xiangqi)
        return "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"

