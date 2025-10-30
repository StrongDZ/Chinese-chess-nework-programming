from typing import Optional, Tuple

try:
    from move_generator import generate_legal_moves, is_in_check
except Exception:
    def generate_legal_moves(board, side):
        return []
    def is_in_check(board, side):
        return False


def opponent(side: str) -> str:
    return 'black' if side == 'red' else 'red'


def detect_result(board, side_to_move) -> Optional[dict]:
    legal = generate_legal_moves(board, side_to_move)
    if legal:
        return None
    if is_in_check(board, side_to_move):
        return {"type": "CHECKMATE", "winner": opponent(side_to_move)}
    return {"type": "STALEMATE"}


def expected_score(delta_elo: int) -> float:
    return 1.0 / (1.0 + 10 ** (delta_elo / 400.0))


def apply_elo(red_elo: int, black_elo: int, result: str, K: int = 32) -> Tuple[int, int, int, int]:
    # result: 'RED_WIN' | 'BLACK_WIN' | 'DRAW'
    if result == 'RED_WIN':
        a = 1.0
        b = 0.0
    elif result == 'BLACK_WIN':
        a = 0.0
        b = 1.0
    else:
        a = b = 0.5
    exp_red = 1 - expected_score(black_elo - red_elo)
    exp_black = 1 - exp_red
    red_new = round(red_elo + K * (a - exp_red))
    black_new = round(black_elo + K * (b - exp_black))
    return red_new, black_new, red_new - red_elo, black_new - black_elo
