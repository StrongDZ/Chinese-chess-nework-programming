import os, sys
from typing import Tuple

# Thêm path để import move_generator
sys.path.append(os.path.join(os.path.dirname(__file__), '..', '..', 'ChineseChess_AI', 'Model', 'easy'))
try:
    from move_generator import generate_legal_moves, apply_move, is_in_check
except Exception:
    # fallback: nếu không import được tại runtime, giữ stub để tránh lỗi import
    def generate_legal_moves(board, side):
        return []
    def apply_move(board, move):
        pass
    def is_in_check(board, side):
        return False

Move = Tuple[Tuple[int,int], Tuple[int,int]]

def in_bounds(x: int, y: int) -> bool:
    return 0 <= x < 10 and 0 <= y < 9


def validate_move(state, move: Move, side: str):
    (x1,y1),(x2,y2) = move
    if side != state.side_to_move:
        return False, "WRONG_TURN"
    if not (in_bounds(x1,y1) and in_bounds(x2,y2)):
        return False, "OUT_OF_BOUNDS"
    piece = state.board[x1][y1]
    if piece == '.':
        return False, "NO_PIECE"
    if side == 'red' and not piece.isupper():
        return False, "NO_OWN_PIECE"
    if side == 'black' and not piece.islower():
        return False, "NO_OWN_PIECE"

    legal = generate_legal_moves(state.board, side)
    if move not in legal:
        return False, "ILLEGAL_MOVE"

    board2 = [row[:] for row in state.board]
    apply_move(board2, move)
    if is_in_check(board2, side):
        return False, "SELF_CHECK"

    return True, None
