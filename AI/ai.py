"""
AI Engine for Chinese Chess - Complete and Simple
Chỉ giữ lại những tính năng cần thiết để predict move

Core Features:
- Initialize/shutdown engine
- Predict move từ FEN hoặc board array
- Helper functions để build position string từ moves
- Convert giữa Move và UCI format
"""

import subprocess
import threading
import time
import os
import sys
from enum import Enum
from typing import Optional, List, Tuple
from dataclasses import dataclass


class AIDifficulty(Enum):
    """AI Difficulty levels"""
    EASY = "easy"    # Depth 3, time limit 500ms
    MEDIUM = "medium"  # Depth 5, time limit 1000ms
    HARD = "hard"     # Depth 8, time limit 2000ms


@dataclass
class Coord:
    """Coordinate on the board"""
    row: int  # 0-9
    col: int  # 0-8


@dataclass
class Move:
    """Move structure"""
    from_pos: Coord
    to_pos: Coord


class AI:
    """
    AI Engine for Chinese Chess - Complete implementation
    
    Chỉ giữ lại những tính năng cần thiết:
    - Initialize/shutdown engine
    - Predict move từ FEN hoặc board array
    - Helper functions để build position string
    
    Usage:
        ai = AI()
        if ai.initialize():
            # Predict từ FEN
            move = ai.predict_move(fen_string, AIDifficulty.MEDIUM)
            
            # Hoặc từ board array
            move = ai.predict_move_from_board(board_array, "w", AIDifficulty.MEDIUM)
            
            # Hoặc build position string từ initial FEN + moves
            moves = [Move(Coord(6,0), Coord(7,0))]
            position_str = AI.build_position_string(initial_fen, moves)
            move = ai.predict_move(position_str, AIDifficulty.MEDIUM)
            
            ai.shutdown()
    """
    
    # Standard starting FEN for Chinese Chess
    INITIAL_FEN = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"
    
    def __init__(self):
        self.engine_process: Optional[subprocess.Popen] = None
        self.engine_stdin: Optional[subprocess.PIPE] = None
        self.engine_stdout: Optional[subprocess.PIPE] = None
        self.engine_ready = False
        self.engine_lock = threading.Lock()
    
    def initialize(self, pikafish_path: str = "pikafish") -> bool:
        """
        Initialize engine (start Pikafish process)
        
        Args:
            pikafish_path: Path to pikafish executable (default: "pikafish")
        
        Returns:
            True if successful, False otherwise
        """
        with self.engine_lock:
            if self.engine_ready:
                return True
            
            resolved_path = self._find_pikafish(pikafish_path)
            
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
                
                # Wait for process to start
                time.sleep(0.2)
                
                # Initialize UCI protocol
                if not self._send_command("uci"):
                    self.shutdown()
                    return False
                
                response = self._read_response(3000)
                if "uciok" not in response:
                    self.shutdown()
                    return False
                
                self._send_command("isready")
                ready_response = self._read_response(2000)
                
                if "readyok" in ready_response:
                    self.engine_ready = True
                    return True
                
                self.shutdown()
                return False
                
            except Exception as e:
                print(f"[AI] Error initializing engine: {e}")
                self.shutdown()
                return False
    
    def shutdown(self):
        """Shutdown engine and cleanup resources"""
        with self.engine_lock:
            if not self.engine_ready and self.engine_process is None:
                return
            
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
    
    def is_ready(self) -> bool:
        """
        Check if engine is ready
        
        Returns:
            True if engine is ready, False otherwise
        """
        return self.engine_ready
    
    def predict_move(self, fen_position: str, difficulty: AIDifficulty) -> Optional[Move]:
        """
        Predict best move từ FEN position string
        
        Args:
            fen_position: FEN string hoặc position string
                          Ví dụ FEN: "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"
                          Ví dụ position: "position fen <fen> moves a6a7 a3a4"
            difficulty: AIDifficulty.EASY, MEDIUM, hoặc HARD
        
        Returns:
            Move object với from_pos và to_pos, hoặc None nếu lỗi
        """
        with self.engine_lock:
            if not self.engine_ready:
                return None
            
            # Get depth and time from difficulty
            depth, time_ms = self._get_difficulty_params(difficulty)
            
            # Set position
            if fen_position.startswith("position "):
                position_cmd = fen_position
            else:
                position_cmd = f"position fen {fen_position}"
            
            if not self._send_command(position_cmd):
                return None
            
            # Set search parameters
            go_cmd = f"go depth {depth}"
            if not self._send_command(go_cmd):
                return None
            
            # Read best move with timeout
            response = self._read_response(time_ms + 1000)
            
            # Parse response: "bestmove a0a1" or "bestmove a0a1 ponder b1b2"
            if "bestmove" in response:
                parts = response.split()
                bestmove_idx = parts.index("bestmove")
                if bestmove_idx + 1 < len(parts):
                    uci_move = parts[bestmove_idx + 1]
                    return self.parse_uci_move(uci_move)
            
            return None
    
    def predict_move_from_board(self, board: List[List[str]], side_to_move: str, difficulty: AIDifficulty) -> Optional[Move]:
        """
        Predict best move từ board array
        
        Args:
            board: 2D list 10x9 (10 rows, 9 cols)
                   board[0] = Đỏ (bottom), board[9] = Đen (top)
            side_to_move: "w" (đỏ) hoặc "b" (đen)
            difficulty: AIDifficulty.EASY, MEDIUM, hoặc HARD
        
        Returns:
            Move object với from_pos và to_pos, hoặc None nếu lỗi
        """
        fen = self.board_to_fen(board, side_to_move)
        return self.predict_move(fen, difficulty)
    
    def suggest_move(self, fen_position: str) -> Optional[Move]:
        """
        Suggest move (wrapper cho predict_move với HARD difficulty)
        
        Args:
            fen_position: FEN string hoặc position string
        
        Returns:
            Move object hoặc None nếu lỗi
        """
        return self.predict_move(fen_position, AIDifficulty.HARD)
    
    @staticmethod
    def parse_uci_move(uci_move: str) -> Optional[Move]:
        """
        Convert UCI move format to Move object
        
        Args:
            uci_move: UCI format string (ví dụ: "a0a1")
        
        Returns:
            Move object hoặc None nếu invalid
        """
        if len(uci_move) < 4:
            return None
        
        from_col_char = uci_move[0]
        from_row_char = uci_move[1]
        to_col_char = uci_move[2]
        to_row_char = uci_move[3]
        
        if not (from_col_char.isalpha() and to_col_char.isalpha() and 
                from_row_char.isdigit() and to_row_char.isdigit()):
            return None
        
        from_col = ord(from_col_char) - ord('a')  # a=0, b=1, ..., i=8
        from_row = int(from_row_char)  # UCI rank 0 = row 0 (Đỏ bottom)
        to_col = ord(to_col_char) - ord('a')
        to_row = int(to_row_char)
        
        return Move(
            from_pos=Coord(from_row, from_col),
            to_pos=Coord(to_row, to_col)
        )
    
    @staticmethod
    def move_to_uci(move: Move) -> str:
        """
        Convert Move object to UCI format string
        
        Args:
            move: Move object
        
        Returns:
            UCI format string (ví dụ: "a0a1")
        """
        from_col = chr(ord('a') + move.from_pos.col)
        from_row = str(move.from_pos.row)
        to_col = chr(ord('a') + move.to_pos.col)
        to_row = str(move.to_pos.row)
        
        return f"{from_col}{from_row}{to_col}{to_row}"
    
    @staticmethod
    def build_position_string(initial_fen: str, moves: List[Move] = None) -> str:
        """
        Build position string từ initial FEN và move history
        
        Args:
            initial_fen: Initial FEN string
            moves: List of Move objects (optional)
        
        Returns:
            Position string (ví dụ: "position fen <fen> moves a6a7 a3a4")
        
        Example:
            initial_fen = AI.INITIAL_FEN
            moves = [Move(Coord(6,0), Coord(7,0))]
            position_str = AI.build_position_string(initial_fen, moves)
            move = ai.predict_move(position_str, AIDifficulty.MEDIUM)
        """
        if moves is None:
            moves = []
        
        position_str = f"position fen {initial_fen}"
        
        if moves:
            position_str += " moves"
            for move in moves:
                uci_move = AI.move_to_uci(move)
                position_str += f" {uci_move}"
        
        return position_str
    
    @staticmethod
    def board_to_fen(board: List[List[str]], side_to_move: str = 'w') -> str:
        """
        Convert 2D board array to FEN string
        
        Args:
            board: 2D list 10x9 (10 rows, 9 cols)
                   board[0] = Đỏ (bottom), board[9] = Đen (top)
            side_to_move: "w" (đỏ) hoặc "b" (đen)
        
        Returns:
            FEN string
        """
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
        fen += f" {side_to_move} - - 0 1"
        
        return fen
    
    def _find_pikafish(self, user_path: str = "pikafish") -> str:
        """Try to find pikafish executable in various locations"""
        # 1. User-specified path
        if user_path and user_path != "pikafish":
            if '/' in user_path:
                if os.access(user_path, os.X_OK):
                    return user_path
        
        # 2. Check in executable directory
        if getattr(sys, 'frozen', False):
            exe_dir = os.path.dirname(sys.executable)
        else:
            exe_dir = os.path.dirname(os.path.abspath(__file__))
        
        local_path = os.path.join(exe_dir, "pikafish")
        if os.access(local_path, os.X_OK):
            return local_path
        
        # 3. Check in PATH
        if user_path == "pikafish" or not user_path:
            path_env = os.environ.get("PATH", "")
            for path_entry in path_env.split(os.pathsep):
                test_path = os.path.join(path_entry, "pikafish")
                if os.access(test_path, os.X_OK):
                    return test_path
        
        return user_path
    
    def _get_difficulty_params(self, difficulty: AIDifficulty) -> Tuple[int, int]:
        """Convert difficulty to depth and time limit"""
        if difficulty == AIDifficulty.EASY:
            return (3, 500)
        elif difficulty == AIDifficulty.MEDIUM:
            return (5, 1000)
        elif difficulty == AIDifficulty.HARD:
            return (8, 2000)
        else:
            return (5, 1000)  # Default to MEDIUM
    
    def _send_command(self, cmd: str) -> bool:
        """Send command to Pikafish engine"""
        if not self.engine_stdin:
            return False
        
        try:
            self.engine_stdin.write(f"{cmd}\n")
            self.engine_stdin.flush()
            return True
        except Exception:
            return False
    
    def _read_response(self, timeout_ms: int = 5000) -> str:
        """Read response from Pikafish engine"""
        if not self.engine_stdout:
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
                        break
                    break
                
                line = self.engine_stdout.readline()
                if line:
                    got_data = True
                    response += line
                    
                    if any(term in response for term in ["uciok", "readyok", "bestmove", "nobestmove"]):
                        found_terminator = True
                        time.sleep(0.05)
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
        
        except Exception:
            pass
        
        return response.strip()


# Convenience function for simple usage
def predict_move(fen_position: str, difficulty: str = "medium", pikafish_path: str = "pikafish") -> Optional[Move]:
    """
    Simple function to predict move (tự động initialize và shutdown)
    
    Args:
        fen_position: FEN string hoặc position string
        difficulty: "easy", "medium", hoặc "hard"
        pikafish_path: Path to pikafish executable
    
    Returns:
        Move object hoặc None nếu lỗi
    
    Example:
        move = predict_move("rnbakabnr/9/... w - - 0 1", "medium")
        if move:
            print(f"From: ({move.from_pos.row}, {move.from_pos.col})")
            print(f"To: ({move.to_pos.row}, {move.to_pos.col})")
    """
    ai = AI()
    if not ai.initialize(pikafish_path):
        return None
    
    difficulty_map = {
        "easy": AIDifficulty.EASY,
        "medium": AIDifficulty.MEDIUM,
        "hard": AIDifficulty.HARD
    }
    diff = difficulty_map.get(difficulty.lower(), AIDifficulty.MEDIUM)
    
    move = ai.predict_move(fen_position, diff)
    ai.shutdown()
    return move

