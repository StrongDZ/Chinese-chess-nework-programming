"""
AI Medium Wrapper - Tích hợp với Easy AI format
"""

import sys
import os
import time
import random
from typing import List, Tuple, Optional

# Add easy model path
sys.path.append(os.path.join(os.path.dirname(__file__), '..', 'easy'))

# Import from easy model
from Model.easy.move_generator import generate_legal_moves, apply_move, is_in_check
from Model.easy.evaluator import evaluate_board

class XiangqiMediumAI:
    """AI Medium sử dụng enhanced evaluation và search"""
    
    def __init__(self, search_depth=2):
        self.search_depth = search_depth
        self.move_history = []
        self.use_minimax = True  # Thêm minimax option
        self.evaluation_cache = {}  # Cache để tăng tốc
        
    def get_move(self, board: List[List[str]], side: str) -> Optional[Tuple]:
        """Lấy nước đi từ AI Medium - Cải thiện với minimax nông"""
        start_time = time.time()
        
        legal_moves = generate_legal_moves(board, side)
        if not legal_moves:
            return None
        
        # Sử dụng minimax nếu search_depth > 1
        if self.use_minimax and self.search_depth > 1:
            best_move = self._minimax_search(board, side, self.search_depth)
        else:
            # Enhanced evaluation với positional factors
            best_move = None
            best_score = -99999 if side == 'red' else 99999
            
            # Move ordering: ưu tiên captures
            ordered_moves = self._order_moves(board, legal_moves, side)
            
            for move in ordered_moves:
                score = self._evaluate_move(board, move, side)
                
                if side == 'red' and score > best_score:
                    best_score = score
                    best_move = move
                elif side == 'black' and score < best_score:
                    best_score = score
                    best_move = move
        
        # Giảm randomness để tập trung vào quality
        if best_move and random.random() < 0.05:  # Giảm từ 10% xuống 5%
            best_move = random.choice(legal_moves)
        
        # Record move
        self.move_history.append({
            'move': best_move,
            'time': time.time() - start_time
        })
        
        return best_move
    
    def _order_moves(self, board: List[List[str]], moves: List[Tuple], side: str) -> List[Tuple]:
        """Sắp xếp nước đi: captures trước"""
        captures = []
        others = []
        for move in moves:
            (_, _), (x2, y2) = move
            if board[x2][y2] != '.':
                captures.append(move)
            else:
                others.append(move)
        return captures + others
    
    def _minimax_search(self, board: List[List[str]], side: str, depth: int) -> Optional[Tuple]:
        """Minimax search với depth nông (2-3)"""
        legal_moves = generate_legal_moves(board, side)
        if not legal_moves:
            return None
        
        best_move = None
        best_score = -99999 if side == 'red' else 99999
        
        # Giới hạn số nước đi để tăng tốc
        moves_to_evaluate = legal_moves[:20] if len(legal_moves) > 20 else legal_moves
        
        for move in moves_to_evaluate:
            new_board = [row[:] for row in board]
            apply_move(new_board, move)
            
            # Minimax recursive
            opponent = 'black' if side == 'red' else 'red'
            score = self._minimax(new_board, opponent, depth - 1, 
                                 -99999, 99999, side == 'red')
            
            if side == 'red' and score > best_score:
                best_score = score
                best_move = move
            elif side == 'black' and score < best_score:
                best_score = score
                best_move = move
        
        return best_move if best_move else legal_moves[0]
    
    def _minimax(self, board: List[List[str]], side: str, depth: int, 
                 alpha: float, beta: float, maximizing: bool) -> float:
        """Minimax với alpha-beta pruning"""
        if depth == 0:
            return self._evaluate_position(board, 'red' if maximizing else 'black')
        
        legal_moves = generate_legal_moves(board, side)
        if not legal_moves:
            # Checkmate hoặc stalemate
            return -99999 if maximizing else 99999
        
        if maximizing:
            max_eval = -99999
            for move in legal_moves[:15]:  # Giới hạn để tăng tốc
                new_board = [row[:] for row in board]
                apply_move(new_board, move)
                eval_score = self._minimax(new_board, 'black', depth - 1, alpha, beta, False)
                max_eval = max(max_eval, eval_score)
                alpha = max(alpha, eval_score)
                if beta <= alpha:
                    break  # Alpha-beta cut-off
            return max_eval
        else:
            min_eval = 99999
            for move in legal_moves[:15]:
                new_board = [row[:] for row in board]
                apply_move(new_board, move)
                eval_score = self._minimax(new_board, 'red', depth - 1, alpha, beta, True)
                min_eval = min(min_eval, eval_score)
                beta = min(beta, eval_score)
                if beta <= alpha:
                    break
            return min_eval
    
    def _evaluate_position(self, board: List[List[str]], side: str) -> float:
        """Đánh giá vị trí tổng thể cho minimax - luôn từ góc nhìn RED"""
        score = evaluate_board(board)
        # Thêm positional bonus nhẹ
        positional = (self._evaluate_center_control(board, side) + 
                     self._evaluate_king_safety(board, side) * 0.5) * 0.05
        total = score + positional
        # Luôn return từ góc nhìn RED, minimax sẽ tự xử lý max/min
        return total
    
    def _evaluate_move(self, board: List[List[str]], move: Tuple, side: str) -> float:
        """Đánh giá nước đi với enhanced evaluation"""
        # Make move
        new_board = [row[:] for row in board]
        apply_move(new_board, move)
        
        # Basic material evaluation
        score = evaluate_board(new_board)
        
        # Add positional factors
        positional_score = 0
        
        # 1. Center control
        positional_score += self._evaluate_center_control(new_board, side)
        
        # 2. Piece activity
        positional_score += self._evaluate_piece_activity(new_board, side)
        
        # 3. King safety
        positional_score += self._evaluate_king_safety(new_board, side)
        
        # 4. Pawn structure
        positional_score += self._evaluate_pawn_structure(new_board, side)
        
        # 5. Tactical factors
        positional_score += self._evaluate_tactics(new_board, move, side)
        
        # Combine scores
        total_score = score + positional_score * 0.1
        
        return total_score if side == 'red' else -total_score
    
    def _evaluate_center_control(self, board: List[List[str]], side: str) -> float:
        """Đánh giá kiểm soát trung tâm"""
        center_squares = [(4, 4), (4, 5), (5, 4), (5, 5)]
        control = 0
        
        for x, y in center_squares:
            if 0 <= x < 10 and 0 <= y < 9:
                piece = board[x][y]
                if piece != '.':
                    if (side == 'red' and piece.isupper()) or (side == 'black' and piece.islower()):
                        control += 1
                    else:
                        control -= 1
        
        return control * 10
    
    def _evaluate_piece_activity(self, board: List[List[str]], side: str) -> float:
        """Đánh giá hoạt động của quân cờ - Tối ưu hóa"""
        activity = 0
        
        # Tối ưu: tính một lần generate_legal_moves thay vì nhiều lần
        all_moves = generate_legal_moves(board, side)
        move_count_by_position = {}
        
        for move in all_moves:
            (x, y), _ = move
            if (x, y) not in move_count_by_position:
                move_count_by_position[(x, y)] = 0
            move_count_by_position[(x, y)] += 1
        
        activity = sum(move_count_by_position.values())
        return activity
    
    def _evaluate_king_safety(self, board: List[List[str]], side: str) -> float:
        """Đánh giá an toàn của tướng"""
        king_pos = None
        for x in range(10):
            for y in range(9):
                piece = board[x][y]
                if (side == 'red' and piece == 'K') or (side == 'black' and piece == 'k'):
                    king_pos = (x, y)
                    break
        
        if not king_pos:
            return 0
        
        # Count pieces around king
        x, y = king_pos
        protection = 0
        for dx in [-1, 0, 1]:
            for dy in [-1, 0, 1]:
                nx, ny = x + dx, y + dy
                if 0 <= nx < 10 and 0 <= ny < 9:
                    piece = board[nx][ny]
                    if piece != '.' and ((side == 'red' and piece.isupper()) or (side == 'black' and piece.islower())):
                        protection += 1
        
        return protection * 5
    
    def _evaluate_pawn_structure(self, board: List[List[str]], side: str) -> float:
        """Đánh giá cấu trúc tốt"""
        pawn_score = 0
        pawn_positions = []
        
        # Find pawns
        for x in range(10):
            for y in range(9):
                piece = board[x][y]
                if (side == 'red' and piece == 'P') or (side == 'black' and piece == 'p'):
                    pawn_positions.append((x, y))
        
        # Evaluate pawn structure
        for x, y in pawn_positions:
            # Center pawns are better
            center_bonus = 10 - abs(y - 4)
            pawn_score += center_bonus
            
            # Connected pawns
            for dx, dy in [(0, 1), (0, -1)]:
                nx, ny = x + dx, y + dy
                if 0 <= nx < 10 and 0 <= ny < 9:
                    if (side == 'red' and board[nx][ny] == 'P') or (side == 'black' and board[nx][ny] == 'p'):
                        pawn_score += 5
        
        return pawn_score
    
    def _evaluate_tactics(self, board: List[List[str]], move: Tuple, side: str) -> float:
        """Đánh giá các yếu tố tactical"""
        score = 0
        (x1, y1), (x2, y2) = move
        
        # Capture bonus
        if board[x2][y2] != '.':
            captured_piece = board[x2][y2]
            capturing_piece = board[x1][y1]
            score += self._get_piece_value(captured_piece) - self._get_piece_value(capturing_piece)
        
        # Check bonus
        new_board = [row[:] for row in board]
        apply_move(new_board, move)
        opponent = 'black' if side == 'red' else 'red'
        if is_in_check(new_board, opponent):
            score += 50
        
        # Escape check bonus
        if is_in_check(board, side) and not is_in_check(new_board, side):
            score += 100
        
        return score
    
    def _get_piece_value(self, piece: str) -> int:
        """Lấy giá trị quân cờ"""
        values = {
            'K': 10000, 'A': 100, 'B': 100, 'N': 300, 'R': 500, 'C': 250, 'P': 50,
            'k': -10000, 'a': -100, 'b': -100, 'n': -300, 'r': -500, 'c': -250, 'p': -50,
            '.': 0
        }
        return values.get(piece, 0)
    
    def get_stats(self):
        """Lấy thống kê"""
        if not self.move_history:
            return {}
        
        times = [move['time'] for move in self.move_history]
        return {
            'total_moves': len(self.move_history),
            'avg_time': sum(times) / len(times),
            'max_time': max(times),
            'min_time': min(times)
        }

def ai_medium_move(board: List[List[str]], side: str) -> Optional[Tuple]:
    """AI Medium chính"""
    ai = XiangqiMediumAI(search_depth=2)
    return ai.get_move(board, side)

if __name__ == "__main__":
    # Test AI Medium
    from broad import INITIAL_BOARD, print_broad
    from move_generator import apply_move
    
    board = [row[:] for row in INITIAL_BOARD]
    print("Testing AI Medium...")
    print("Initial board:")
    print_broad(board)
    
    for i in range(3):
        side = 'red' if i % 2 == 0 else 'black'
        move = ai_medium_move(board, side)
        print(f"\\nMove {i+1} ({side}): {move}")
        
        if move:
            apply_move(board, move)
            print("Board after move:")
            print_broad(board)
        else:
            print("No legal moves!")
            break
