"""
AI Engine for Chinese Chess - Complete, Simple & Docker-Ready
Core Features:
- Initialize/shutdown engine (Non-blocking I/O Safe using select)
- Predict move from FEN or board array
- Helper functions to build position string from moves
- Convert between Move and UCI format
"""

import subprocess
import threading
import time
import os
import sys
import shutil
import select  # <--- Thư viện quan trọng để fix lỗi treo
from enum import Enum
from typing import Optional, List, Tuple
from dataclasses import dataclass


class AIDifficulty(Enum):
    """AI Difficulty levels"""

    EASY = "easy"  # Depth 3, time limit 1000ms
    MEDIUM = "medium"  # Depth 8, time limit 3000ms
    HARD = "hard"  # Depth 12, time limit 5000ms


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
        """
        with self.engine_lock:
            if self.engine_ready:
                return True

            resolved_path = self._find_pikafish(pikafish_path)

            if not resolved_path:
                print(f"[AI] Error: Pikafish binary not found at '{pikafish_path}' or in PATH", file=sys.stderr)
                return False

            try:
                # Start Pikafish process
                # bufsize=1 = line buffered (recommended for text mode)
                self.engine_process = subprocess.Popen(
                    [resolved_path], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, bufsize=1  # Line buffered
                )

                self.engine_stdin = self.engine_process.stdin
                self.engine_stdout = self.engine_process.stdout

                # Wait for process to start and flush any initial output
                time.sleep(0.3)

                # Initialize UCI protocol
                print("[AI] Sending 'uci' command...", file=sys.stderr)
                if not self._send_command("uci"):
                    print("[AI] Failed to send 'uci' command", file=sys.stderr)
                    self.shutdown()
                    return False

                # Tăng timeout lên 10s cho chắc chắn
                print("[AI] Waiting for 'uciok' response (timeout: 10s)...", file=sys.stderr)
                response = self._read_response(10000)

                print(f"[AI] Received response ({len(response)} chars):\n{response[:500]}", file=sys.stderr)

                if "uciok" not in response:
                    print(f"[AI] Init failed. Expected 'uciok'. Got:\n{response}", file=sys.stderr)
                    self.shutdown()
                    return False

                print("[AI] 'uciok' received successfully", file=sys.stderr)

                self._send_command("isready")
                ready_response = self._read_response(5000)

                if "readyok" in ready_response:
                    self.engine_ready = True
                    return True

                print(f"[AI] Init failed. Expected 'readyok'. Got:\n{ready_response}", file=sys.stderr)
                self.shutdown()
                return False

            except Exception as e:
                print(f"[AI] Error initializing engine: {e}", file=sys.stderr)
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
        return self.engine_ready

    def predict_move(self, fen_position: str, difficulty: AIDifficulty) -> Optional[Move]:
        """
        Predict best move from FEN position string
        """
        with self.engine_lock:
            if not self.engine_ready:
                print("[AI] predict_move: Engine not ready", file=sys.stderr)
                return None

            # Get depth and time from difficulty
            depth, time_ms = self._get_difficulty_params(difficulty)
            print(f"[AI] predict_move: depth={depth}, time_ms={time_ms}, difficulty={difficulty}", file=sys.stderr)

            # Set position
            if fen_position.startswith("position "):
                position_cmd = fen_position
            else:
                position_cmd = f"position fen {fen_position}"

            print(f"[AI] Sending position command: {position_cmd[:80]}...", file=sys.stderr)
            if not self._send_command(position_cmd):
                print("[AI] Failed to send position command", file=sys.stderr)
                return None

            # Set search parameters
            go_cmd = f"go depth {depth} movetime {time_ms}"
            print(f"[AI] Sending go command: {go_cmd}", file=sys.stderr)
            if not self._send_command(go_cmd):
                print("[AI] Failed to send go command", file=sys.stderr)
                return None

            # Read best move with timeout (time_ms + 5s buffer để đảm bảo đủ thời gian)
            timeout = time_ms + 5000
            print(f"[AI] Reading response (timeout: {timeout}ms)...", file=sys.stderr)
            response = self._read_response(timeout)

            print(f"[AI] Received response ({len(response)} chars): {response[:200]}", file=sys.stderr)

            # Parse response
            if "bestmove" in response:
                parts = response.split()
                try:
                    bestmove_idx = parts.index("bestmove")
                    if bestmove_idx + 1 < len(parts):
                        uci_move = parts[bestmove_idx + 1]
                        print(f"[AI] Parsed UCI move: {uci_move}", file=sys.stderr)
                        # Fix trường hợp engine trả về (none) khi hết cờ
                        if uci_move == "(none)":
                            print("[AI] Engine returned (none) - no move available", file=sys.stderr)
                            return None
                        move = self.parse_uci_move(uci_move)
                        if move:
                            print(
                                f"[AI] Parsed move: from=({move.from_pos.row},{move.from_pos.col}) to=({move.to_pos.row},{move.to_pos.col})",
                                file=sys.stderr,
                            )
                        return move
                except ValueError as e:
                    print(f"[AI] Error parsing bestmove: {e}", file=sys.stderr)

            print("[AI] No bestmove found in response", file=sys.stderr)
            return None

    def predict_move_from_board(self, board: List[List[str]], side_to_move: str, difficulty: AIDifficulty) -> Optional[Move]:
        fen = self.board_to_fen(board, side_to_move)
        return self.predict_move(fen, difficulty)

    def suggest_move(self, fen_position: str) -> Optional[Move]:
        return self.predict_move(fen_position, AIDifficulty.HARD)

    @staticmethod
    def parse_uci_move(uci_move: str) -> Optional[Move]:
        if len(uci_move) < 4:
            return None

        from_col_char = uci_move[0]
        from_row_char = uci_move[1]
        to_col_char = uci_move[2]
        to_row_char = uci_move[3]

        if not (from_col_char.isalpha() and to_col_char.isalpha() and from_row_char.isdigit() and to_row_char.isdigit()):
            return None

        from_col = ord(from_col_char) - ord("a")
        from_row = int(from_row_char)
        to_col = ord(to_col_char) - ord("a")
        to_row = int(to_row_char)

        return Move(from_pos=Coord(from_row, from_col), to_pos=Coord(to_row, to_col))

    @staticmethod
    def move_to_uci(move: Move) -> str:
        from_col = chr(ord("a") + move.from_pos.col)
        from_row = str(move.from_pos.row)
        to_col = chr(ord("a") + move.to_pos.col)
        to_row = str(move.to_pos.row)
        return f"{from_col}{from_row}{to_col}{to_row}"

    @staticmethod
    def build_position_string(initial_fen: str, moves: List[Move] = None) -> str:
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
    def board_to_fen(board: List[List[str]], side_to_move: str = "w") -> str:
        fen_parts = []
        for row in range(9, -1, -1):
            rank_str = ""
            empty_count = 0
            for col in range(9):
                piece = board[row][col]
                if piece == " ":
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

    def _find_pikafish(self, user_path: str = "pikafish") -> Optional[str]:
        # 1. User-specified path
        if user_path and user_path != "pikafish":
            if os.path.exists(user_path) and os.access(user_path, os.X_OK):
                return user_path

        # 2. Check in current executable directory
        if getattr(sys, "frozen", False):
            base_dir = os.path.dirname(sys.executable)
        else:
            base_dir = os.path.dirname(os.path.abspath(__file__))

        local_path = os.path.join(base_dir, "pikafish")
        if os.path.exists(local_path) and os.access(local_path, os.X_OK):
            return local_path

        # 3. Check in system PATH
        path_entry = shutil.which("pikafish")
        if path_entry:
            return path_entry

        return None

    def _get_difficulty_params(self, difficulty: AIDifficulty) -> Tuple[int, int]:
        if difficulty == AIDifficulty.EASY:
            return (8, 3000)
        elif difficulty == AIDifficulty.MEDIUM:
            return (12, 8000)
        elif difficulty == AIDifficulty.HARD:
            return (18, 15000)

        return (12, 8000)

    def _send_command(self, cmd: str) -> bool:
        if not self.engine_stdin:
            return False
        try:
            self.engine_stdin.write(f"{cmd}\n")
            self.engine_stdin.flush()
            return True
        except Exception as e:
            print(f"[AI Error] Send command failed: {e}", file=sys.stderr)
            return False

    def _read_response(self, timeout_ms: int = 5000) -> str:
        """
        Đọc phản hồi từ Engine với timeout. Sử dụng threading để đọc blocking.
        """
        if not self.engine_stdout:
            return ""

        response_lines = []
        response_lock = threading.Lock()
        read_complete = threading.Event()
        timeout_sec = timeout_ms / 1000.0

        def read_thread():
            """Thread đọc từ engine stdout"""
            try:
                while not read_complete.is_set():
                    # Đọc blocking - sẽ block cho đến khi có dữ liệu hoặc EOF
                    line = self.engine_stdout.readline()
                    if not line:  # EOF
                        break

                    line = line.strip()
                    if line:
                        with response_lock:
                            response_lines.append(line)
                            print(f"[AI Debug] Received line: {line}", file=sys.stderr)

                            # Kiểm tra keyword kết thúc
                            if "bestmove" in line or "uciok" in line or "readyok" in line:
                                read_complete.set()
                                break
            except Exception as e:
                print(f"[AI Error] Read thread error: {e}", file=sys.stderr)
            finally:
                read_complete.set()

        # Start reading thread
        reader = threading.Thread(target=read_thread, daemon=True)
        reader.start()

        # Wait for completion or timeout
        start_time = time.time()
        while not read_complete.is_set():
            elapsed = time.time() - start_time
            if elapsed > timeout_sec:
                print(f"[AI Warning] Read timeout after {elapsed:.2f}s", file=sys.stderr)
                read_complete.set()
                break

            # Check if process died
            if self.engine_process and self.engine_process.poll() is not None:
                print("[AI Error] Engine process died unexpectedly", file=sys.stderr)
                read_complete.set()
                break

            time.sleep(0.01)  # Small sleep to avoid busy waiting

        # Wait for thread to finish (max 0.5s)
        reader.join(timeout=0.5)

        # Build response string
        with response_lock:
            result = "\n".join(response_lines)
            if result:
                print(f"[AI Debug] Final response ({len(response_lines)} lines):\n{result[:300]}", file=sys.stderr)
            return result

    def _read_response_fallback(self, timeout_ms: int = 5000) -> str:
        """
        Fallback method: đọc trực tiếp với non-blocking check
        """
        if not self.engine_stdout:
            return ""

        response = []
        start_time = time.time()
        timeout_sec = timeout_ms / 1000.0

        # Set non-blocking mode nếu có thể (Linux/Unix only)
        try:
            import fcntl

            fd = self.engine_stdout.fileno()
            flags = fcntl.fcntl(fd, fcntl.F_GETFL)
            fcntl.fcntl(fd, fcntl.F_SETFL, flags | os.O_NONBLOCK)
        except (ImportError, AttributeError, OSError):
            # fcntl không có trên Windows hoặc không thể set non-blocking
            pass

        while time.time() - start_time < timeout_sec:
            try:
                line = self.engine_stdout.readline()
                if not line:
                    time.sleep(0.01)  # Ngủ ngắn trước khi thử lại
                    continue

                line = line.strip()
                if line:
                    response.append(line + "\n")
                    print(f"[AI Debug] Received line (fallback): {line}", file=sys.stderr)
                    if "bestmove" in line or "uciok" in line or "readyok" in line:
                        break
            except (IOError, OSError):
                time.sleep(0.01)
            except Exception as e:
                print(f"[AI Error] Fallback read error: {e}", file=sys.stderr)
                time.sleep(0.01)

        return "".join(response).strip()


# Convenience function
def predict_move(fen_position: str, difficulty: str = "medium", pikafish_path: str = "pikafish") -> Optional[Move]:
    ai = AI()
    if not ai.initialize(pikafish_path):
        return None

    difficulty_map = {"easy": AIDifficulty.EASY, "medium": AIDifficulty.MEDIUM, "hard": AIDifficulty.HARD}
    diff = difficulty_map.get(difficulty.lower(), AIDifficulty.MEDIUM)

    move = ai.predict_move(fen_position, diff)
    ai.shutdown()
    return move
