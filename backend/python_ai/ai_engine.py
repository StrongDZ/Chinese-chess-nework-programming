"""
AI Engine for Chinese Chess - Python Implementation
Wraps Pikafish UCI engine for move generation
"""

import subprocess
import threading
import time
import os
import sys
from enum import Enum
from typing import Optional, List, Tuple, Dict
from dataclasses import dataclass


class AIDifficulty(Enum):
    """AI Difficulty levels"""
    EASY = "easy"    # Depth 3, time limit 500ms
    MEDIUM = "medium"  # Depth 5, time limit 1000ms
    HARD = "hard"     # Depth 8, time limit 2000ms


@dataclass
class Coord:
    """Coordinate on the board"""
    row: int
    col: int


@dataclass
class MovePayload:
    """Move payload structure"""
    piece: str
    from_pos: Coord
    to_pos: Coord


class PikafishEngine:
    """Pikafish Engine wrapper for Chinese Chess"""
    
    def __init__(self):
        self.engine_process: Optional[subprocess.Popen] = None
        self.engine_stdin: Optional[subprocess.PIPE] = None
        self.engine_stdout: Optional[subprocess.PIPE] = None
        self.engine_ready = False
        self.engine_lock = threading.Lock()
    
    def _find_pikafish(self, user_path: str = "pikafish") -> str:
        """Try to find pikafish in various locations"""
        # 1. User-specified path
        if user_path and user_path != "pikafish":
            if '/' in user_path:
                if os.access(user_path, os.X_OK):
                    return user_path
        
        # 2. Check in executable directory
        if getattr(sys, 'frozen', False):
            # Running as compiled executable
            exe_dir = os.path.dirname(sys.executable)
        else:
            # Running as script
            exe_dir = os.path.dirname(os.path.abspath(__file__))
        
        local_path = os.path.join(exe_dir, "pikafish")
        if os.access(local_path, os.X_OK):
            print(f"[AI] Found Pikafish in executable directory: {local_path}")
            return local_path
        
        # 3. Check in PATH
        if user_path == "pikafish" or not user_path:
            path_env = os.environ.get("PATH", "")
            for path_entry in path_env.split(os.pathsep):
                test_path = os.path.join(path_entry, "pikafish")
                if os.access(test_path, os.X_OK):
                    return test_path
        
        # 4. Return user_path as fallback
        return user_path
    
    def initialize(self, pikafish_path: str = "pikafish") -> bool:
        """Initialize engine (start Pikafish process)"""
        with self.engine_lock:
            if self.engine_ready:
                print("[AI] Engine already initialized")
                return True
            
            resolved_path = self._find_pikafish(pikafish_path)
            print(f"[AI] Initializing Pikafish engine...")
            print(f"[AI] Search path: {pikafish_path if pikafish_path else 'default'}")
            print(f"[AI] Resolved path: {resolved_path}")
            
            try:
                # Start Pikafish process
                self.engine_process = subprocess.Popen(
                    [resolved_path],
                    stdin=subprocess.PIPE,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True,
                    bufsize=1
                )
                
                self.engine_stdin = self.engine_process.stdin
                self.engine_stdout = self.engine_process.stdout
                
                # Wait a bit for process to start
                time.sleep(0.2)
                
                # Initialize UCI protocol
                print("[AI] Sending UCI initialization command...")
                if not self._send_command("uci"):
                    print("[AI] Error: Failed to send UCI command")
                    self.shutdown()
                    return False
                
                response = self._read_response(3000)
                print(f"[AI] UCI response: {response}")
                
                if "uciok" in response:
                    self._send_command("isready")
                    ready_response = self._read_response(2000)
                    print(f"[AI] Ready response: {ready_response}")
                    
                    if "readyok" in ready_response:
                        self.engine_ready = True
                        print("[AI] Pikafish engine initialized successfully")
                        return True
                
                print(f"[AI] Error: Failed to initialize UCI protocol. Response: {response}")
                self.shutdown()
                return False
                
            except Exception as e:
                print(f"[AI] Error initializing engine: {e}")
                self.shutdown()
                return False
    
    def shutdown(self):
        """Shutdown engine"""
        with self.engine_lock:
            if not self.engine_ready and self.engine_process is None:
                return
            
            print("[AI] Shutting down Pikafish engine...")
            
            if self.engine_stdin:
                try:
                    self._send_command("quit")
                    time.sleep(0.1)
                except:
                    pass
                self.engine_stdin = None
            
            if self.engine_process:
                try:
                    self.engine_process.terminate()
                    self.engine_process.wait(timeout=1)
                except:
                    try:
                        self.engine_process.kill()
                    except:
                        pass
                self.engine_process = None
            
            self.engine_stdout = None
            self.engine_ready = False
            print("[AI] Engine shutdown complete")
    
    def _send_command(self, cmd: str) -> bool:
        """Send command to Pikafish"""
        if not self.engine_stdin:
            print("[AI] Error: Engine stdin not available")
            return False
        
        try:
            print(f"[AI] Sending command: {cmd}")
            self.engine_stdin.write(f"{cmd}\n")
            self.engine_stdin.flush()
            return True
        except Exception as e:
            print(f"[AI] Error sending command: {e}")
            return False
    
    def _read_response(self, timeout_ms: int = 5000) -> str:
        """Read response from Pikafish"""
        if not self.engine_stdout:
            print("[AI] Error: Engine stdout not available")
            return ""
        
        response = ""
        start_time = time.time()
        timeout_sec = timeout_ms / 1000.0
        got_data = False
        found_terminator = False
        
        try:
            while True:
                elapsed = time.time() - start_time
                if elapsed > timeout_sec:
                    if not got_data:
                        print(f"[AI] Warning: Timeout waiting for response ({timeout_ms}ms)")
                    break
                
                # Try to read line
                line = self.engine_stdout.readline()
                if line:
                    got_data = True
                    response += line
                    
                    # Check for UCI response terminators
                    if any(term in response for term in ["uciok", "readyok", "bestmove", "nobestmove"]):
                        found_terminator = True
                        time.sleep(0.05)  # Wait a bit more
                        # Try to read any remaining data
                        while True:
                            try:
                                line = self.engine_stdout.readline()
                                if not line:
                                    break
                                response += line
                                if "bestmove" in response or "nobestmove" in response:
                                    break
                            except:
                                break
                        break
                else:
                    if got_data:
                        if not found_terminator:
                            time.sleep(0.1)
                            try:
                                line = self.engine_stdout.readline()
                                if line:
                                    response += line
                                    if any(term in response for term in ["uciok", "readyok", "bestmove"]):
                                        found_terminator = True
                                        break
                            except:
                                pass
                        if found_terminator or elapsed > timeout_sec / 2:
                            break
                    time.sleep(0.01)
        
        except Exception as e:
            print(f"[AI] Error reading response: {e}")
        
        # Remove trailing whitespace
        return response.strip()
    
    def _get_difficulty_params(self, difficulty: AIDifficulty) -> Tuple[int, int]:
        """Convert difficulty to depth and time"""
        if difficulty == AIDifficulty.EASY:
            return (3, 500)
        elif difficulty == AIDifficulty.MEDIUM:
            return (5, 1000)
        elif difficulty == AIDifficulty.HARD:
            return (8, 2000)
        else:
            return (5, 1000)  # Default to MEDIUM
    
    def get_best_move(self, fen_position: str, difficulty: AIDifficulty) -> str:
        """Get best move from current position"""
        with self.engine_lock:
            if not self.engine_ready:
                print("[AI] Error: Engine not ready")
                return ""
            
            depth, time_ms = self._get_difficulty_params(difficulty)
            
            difficulty_str = difficulty.value.upper()
            print(f"[AI] Getting best move (difficulty: {difficulty_str}, depth: {depth}, time: {time_ms}ms)")
            print(f"[AI] Position: {fen_position}")
            
            # Set position
            if fen_position.startswith("position "):
                position_cmd = fen_position
            else:
                position_cmd = f"position fen {fen_position}"
            
            if not self._send_command(position_cmd):
                print("[AI] Error: Failed to send position command")
                return ""
            
            # Set search parameters
            go_cmd = f"go depth {depth}"
            if not self._send_command(go_cmd):
                print("[AI] Error: Failed to send go command")
                return ""
            
            # Read best move with timeout
            response = self._read_response(time_ms + 1000)
            print(f"[AI] Engine response: {response}")
            
            # Parse response: "bestmove a0a1" or "bestmove a0a1 ponder b1b2"
            if "bestmove" in response:
                parts = response.split()
                bestmove_idx = parts.index("bestmove")
                if bestmove_idx + 1 < len(parts):
                    move = parts[bestmove_idx + 1]
                    print(f"[AI] Best move found: {move}")
                    return move
            
            print(f"[AI] Error: Failed to parse bestmove from response: {response}")
            return ""
    
    def suggest_move(self, fen_position: str) -> str:
        """Get move suggestion (same as getBestMove but with HARD difficulty)"""
        return self.get_best_move(fen_position, AIDifficulty.HARD)
    
    @staticmethod
    def parse_uci_move(uci_move: str) -> MovePayload:
        """Convert UCI move format to MovePayload"""
        # UCI format: "a0a1" means from a0 to a1
        # Chinese Chess coordinates: files are a-i (left to right), ranks are 0-9 (bottom to top)
        
        if len(uci_move) < 4:
            return MovePayload("", Coord(-1, -1), Coord(-1, -1))
        
        from_col_char = uci_move[0]
        from_row_char = uci_move[1]
        to_col_char = uci_move[2]
        to_row_char = uci_move[3]
        
        if not (from_col_char.isalpha() and to_col_char.isalpha() and 
                from_row_char.isdigit() and to_row_char.isdigit()):
            return MovePayload("", Coord(-1, -1), Coord(-1, -1))
        
        from_col = ord(from_col_char) - ord('a')  # a=0, b=1, ..., i=8
        from_row = int(from_row_char)  # UCI rank 0 = row 0 (Đỏ bottom)
        to_col = ord(to_col_char) - ord('a')
        to_row = int(to_row_char)
        
        return MovePayload("", Coord(from_row, from_col), Coord(to_row, to_col))
    
    @staticmethod
    def move_to_uci(move: MovePayload) -> str:
        """Convert MovePayload to UCI format"""
        from_col = chr(ord('a') + move.from_pos.col)
        from_row = str(move.from_pos.row)
        to_col = chr(ord('a') + move.to_pos.col)
        to_row = str(move.to_pos.row)
        
        return f"{from_col}{from_row}{to_col}{to_row}"
    
    @staticmethod
    def board_array_to_fen(board: List[List[str]], side_to_move: str = 'w', 
                          halfmove: int = 0, fullmove: int = 1) -> str:
        """Convert 2D board array to FEN string"""
        # board[0] = Đỏ (bottom) → FEN rank 9, board[9] = Đen (top) → FEN rank 0
        fen_parts = []
        
        for row in range(9, -1, -1):  # From row 9 to row 0
            rank_str = ""
            empty_count = 0
            
            for col in range(9):
                piece = board[row][col]
                
                if piece == ' ':
                    empty_count += 1
                else:
                    if empty_count > 0:
                        rank_str += str(empty_count)
                        empty_count = 0
                    rank_str += piece
            
            if empty_count > 0:
                rank_str += str(empty_count)
            
            fen_parts.append(rank_str)
        
        fen = "/".join(fen_parts)
        fen += f" {side_to_move} - - {halfmove} {fullmove}"
        
        return fen
    
    @staticmethod
    def fen_to_board_array(fen: str) -> Tuple[Optional[List[List[str]]], str, int, int]:
        """Convert FEN string to 2D board array"""
        # Initialize board to empty
        board = [[' ' for _ in range(9)] for _ in range(10)]
        
        parts = fen.split()
        if not parts:
            return None, 'w', 0, 1
        
        board_part = parts[0]
        side_to_move = parts[1] if len(parts) > 1 else 'w'
        halfmove = int(parts[4]) if len(parts) > 4 else 0
        fullmove = int(parts[5]) if len(parts) > 5 else 1
        
        # Parse board string (ranks separated by '/')
        ranks = board_part.split('/')
        if len(ranks) != 10:
            print(f"[AI] Error: Invalid FEN - expected 10 ranks, got {len(ranks)}")
            return None, side_to_move, halfmove, fullmove
        
        # Fill board array
        # FEN rank 0 = top (Đen) → board[9], FEN rank 9 = bottom (Đỏ) → board[0]
        for rank_idx, rank_str in enumerate(ranks):
            board_row = 9 - rank_idx  # Đảo ngược: FEN rank 0 → board row 9
            col = 0
            
            for c in rank_str:
                if col >= 9:
                    break
                
                if c.isdigit():
                    # Empty squares
                    empty_count = int(c)
                    for _ in range(empty_count):
                        if col < 9:
                            board[board_row][col] = ' '
                            col += 1
                elif c.isalpha():
                    # Piece
                    board[board_row][col] = c
                    col += 1
        
        return board, side_to_move, halfmove, fullmove
    
    @staticmethod
    def board_array_to_position_string(board: List[List[str]], 
                                       moves: List[MovePayload] = None,
                                       side_to_move: str = 'w') -> str:
        """Convert 2D board array + move history to Position String (UCI format)"""
        if moves is None:
            moves = []
        
        # Convert board array to FEN
        fen = PikafishEngine.board_array_to_fen(board, side_to_move)
        
        # Build position string
        position_str = f"position fen {fen}"
        
        # Add moves if any
        if moves:
            position_str += " moves"
            for move in moves:
                uci_move = PikafishEngine.move_to_uci(move)
                position_str += f" {uci_move}"
        
        return position_str
    
    @staticmethod
    def position_string_to_board_array(position_str: str) -> Tuple[Optional[List[List[str]]], List[MovePayload], str]:
        """Convert Position String (UCI) to 2D board array"""
        # Format: "position fen <fen> moves <move1> <move2> ..."
        
        parts = position_str.split()
        if len(parts) < 3 or parts[0] != "position" or parts[1] != "fen":
            print("[AI] Error: Invalid position string - missing 'position fen'")
            return None, [], 'w'
        
        # Find where moves start
        moves_start_idx = -1
        for i, part in enumerate(parts):
            if part == "moves":
                moves_start_idx = i
                break
        
        # Extract FEN
        if moves_start_idx > 0:
            fen_parts = parts[2:moves_start_idx]
        else:
            fen_parts = parts[2:]
        
        fen = " ".join(fen_parts)
        
        # Parse FEN to board array
        board, side_to_move, _, _ = PikafishEngine.fen_to_board_array(fen)
        if board is None:
            return None, [], side_to_move
        
        # Parse moves if present
        moves = []
        if moves_start_idx > 0 and moves_start_idx + 1 < len(parts):
            for i in range(moves_start_idx + 1, len(parts)):
                uci_move = parts[i]
                move = PikafishEngine.parse_uci_move(uci_move)
                if move.from_pos.row >= 0 and move.from_pos.col >= 0:
                    moves.append(move)
                else:
                    print(f"[AI] Warning: Invalid UCI move in position string: {uci_move}")
        
        return board, moves, side_to_move
    
    def is_ready(self) -> bool:
        """Check if engine is ready"""
        return self.engine_ready

