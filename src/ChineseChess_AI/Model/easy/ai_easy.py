"""
AI Easy - Improved Version
Optimized evaluation and better move selection
"""
import copy
import random
from move_generator import generate_legal_moves, apply_move, is_in_check
from evaluator import evaluate_board

def ai_easy_move(board, side):
    """
    AI Easy - Cải thiện với move ordering và capture bonus tốt hơn
    
    Args:
        board: Bàn cờ 10x9
        side: 'red' hoặc 'black'
    
    Returns:
        Tuple ((x1, y1), (x2, y2)) hoặc None
    """
    moves = generate_legal_moves(board, side)
    if not moves:
        return None
    
    # Move ordering: ưu tiên captures và checks
    moves = _order_moves(board, moves, side)
    
    best_move = None
    best_score = -99999 if side == 'red' else 99999
    capture_bonus = 25  # Tăng từ 10 lên 25 - cân bằng hơn
    escape_check_bonus = 30  # Tăng từ 20 lên 30
    check_bonus = 15  # Thêm bonus cho checks
    
    tied_moves = []
    
    for move in moves:
        score = _evaluate_move(board, move, side, capture_bonus, 
                              escape_check_bonus, check_bonus)
        
        if side == 'red' and score > best_score:
            best_score = score
            best_move = move
            tied_moves = [move]  # Reset tied moves
        elif side == 'black' and score < best_score:
            best_score = score
            best_move = move
            tied_moves = [move]
        elif side == 'red' and score == best_score:
            tied_moves.append(move)
        elif side == 'black' and score == best_score:
            tied_moves.append(move)
    
    if not best_move:
        return None
    
    # Random choice among tied moves for variety
    if len(tied_moves) > 1:
        return random.choice(tied_moves)
    
    return best_move


def _order_moves(board, moves, side):
    """
    Sắp xếp nước đi: captures và checks trước
    """
    capture_moves = []
    check_moves = []
    other_moves = []
    
    for move in moves:
        (_, _), (x2, y2) = move
        # Check if capture
        if board[x2][y2] != '.':
            capture_moves.append(move)
            continue
        
        # Check if gives check
        new_board = [row[:] for row in board]
        apply_move(new_board, move)
        opponent = 'black' if side == 'red' else 'red'
        if is_in_check(new_board, opponent):
            check_moves.append(move)
        else:
            other_moves.append(move)
    
    # Return ordered: captures, checks, others
    return capture_moves + check_moves + other_moves


def _evaluate_move(board, move, side, capture_bonus, escape_check_bonus, check_bonus):
    """
    Đánh giá nước đi - tối ưu không duplicate
    """
    new_board = [row[:] for row in board]
    apply_move(new_board, move)
    
    score = evaluate_board(new_board)
    
    (_, _), (tx, ty) = move
    
    # Capture bonus
    if board[tx][ty] != '.':
        score += capture_bonus if side == 'red' else -capture_bonus
    
    # Check bonus
    opponent = 'black' if side == 'red' else 'red'
    if is_in_check(new_board, opponent):
        score += check_bonus if side == 'red' else -check_bonus
    
    # Escape check bonus
    if is_in_check(board, side) and not is_in_check(new_board, side):
        score += escape_check_bonus if side == 'red' else -escape_check_bonus
    
    return score
