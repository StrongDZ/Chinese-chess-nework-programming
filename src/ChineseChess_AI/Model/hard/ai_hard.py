"""
AI Hard - Tổng hợp tất cả các phương pháp
"""

import time
import random
import copy
import sys
import os
from typing import List, Tuple, Optional, Dict
from enum import Enum

# Add model paths
sys.path.append(os.path.join(os.path.dirname(__file__), '..', 'easy'))
sys.path.append(os.path.join(os.path.dirname(__file__), '..', 'medium'))

from move_generator import generate_legal_moves, apply_move, is_in_check, kings_facing
from evaluator import evaluate_board

class HardAIStrategy(Enum):
    MCTS = "mcts"
    MINIMAX = "minimax"
    ENSEMBLE = "ensemble"
    ADAPTIVE = "adaptive"

class XiangqiHardAI:
    """AI Hard cho cờ tướng"""
    
    def __init__(self, time_limit=3.0, strategy='adaptive'):
        self.time_limit = time_limit
        self.strategy = HardAIStrategy(strategy)
        
        # Performance tracking
        self.move_history = []
        self.performance_stats = {
            'total_moves': 0,
            'avg_time': 0,
            'success_rate': 0
        }
    
        # Cải thiện: thêm minimax và iterative deepening
        self.max_depth = 5  # <== Giảm xuống 5 để đảm bảo chất lượng/tốc độ tốt hơn
        self.transposition_table = {}  # Zobrist-key -> {depth, score, flag, best_move}
        # Killer moves và history heuristic
        self.killer_moves = {}  # depth -> [move1, move2]
        self.history_heuristic = {}  # (x1,y1,x2,y2) -> score
        # Zobrist hashing
        self._init_zobrist()
        # Tối ưu tốc độ: giới hạn nhánh và aspiration window
        self.max_moves_per_depth = 8  # <== Chỉ thử 8 nước tốt nhất per ply để search sâu hơn/nhanh
        self.asp_window = 0
        # Profile mặc định
        self.set_profile('balanced')

    def set_profile(self, profile: str):
        """Thiết lập profile chạy: 'fast' hoặc 'balanced'"""
        p = (profile or '').lower()
        if p == 'fast':
            self.max_moves_per_depth = 16
            self.asp_window = 40
        else:
            # balanced (default)
            self.max_moves_per_depth = 20
            self.asp_window = 50

    def _init_zobrist(self):
        import random
        random.seed(42)
        self.z_side = random.getrandbits(64)
        # 10x9 board, 14 piece types (7 red upper, 7 black lower)
        pieces = ['K','A','B','N','R','C','P','k','a','b','n','r','c','p']
        self.z_table = {}
        for x in range(10):
            for y in range(9):
                for p in pieces:
                    self.z_table[(x,y,p)] = random.getrandbits(64)

    def _zobrist_key(self, board, side):
        key = 0
        for x in range(10):
            for y in range(9):
                p = board[x][y]
                if p != '.':
                    key ^= self.z_table[(x,y,p)]
        if side == 'red':
            key ^= self.z_side
        return key
    
    def get_move(self, board, side, time_limit=None):
        """Lấy nước đi từ AI Hard"""
        if time_limit is None:
            time_limit = self.time_limit
        
        start_time = time.time()
        
        try:
            # Chọn strategy dựa trên tình huống
            if self.strategy == HardAIStrategy.ADAPTIVE:
                move = self._adaptive_strategy(board, side, time_limit)
            else:
                move = self._basic_strategy(board, side, time_limit)
            
            # Fallback nếu không có nước đi
            if move is None:
                legal_moves = generate_legal_moves(board, side)
                if legal_moves:
                    move = random.choice(legal_moves)
            
            # Update statistics
            self._update_stats(move, time.time() - start_time)
            
            return move
            
        except Exception as e:
            print(f"AI Hard error: {e}")
            # Fallback to random move
            legal_moves = generate_legal_moves(board, side)
            return random.choice(legal_moves) if legal_moves else None
    
    def _adaptive_strategy(self, board, side, time_limit):
        """Chiến lược thích ứng - Cải thiện với iterative deepening và minimax"""
        situation = self._analyze_position(board, side)
        
        # Sử dụng iterative deepening minimax cho tất cả strategies
        if situation == 'tactical':
            return self._iterative_deepening_minimax(board, side, time_limit, tactical=True)
        elif situation == 'positional':
            return self._iterative_deepening_minimax(board, side, time_limit, tactical=False)
        else:
            return self._iterative_deepening_minimax(board, side, time_limit, tactical=False)
    
    def _basic_strategy(self, board, side, time_limit):
        """Chiến lược cơ bản với enhanced evaluation"""
        legal_moves = generate_legal_moves(board, side)
        if not legal_moves:
            return None
        
        best_move = None
        best_score = -99999 if side == 'red' else 99999
        
        for move in legal_moves:
            score = self._evaluate_move_enhanced(board, move, side)
            
            if side == 'red' and score > best_score:
                best_score = score
                best_move = move
            elif side == 'black' and score < best_score:
                best_score = score
                best_move = move
        
        return best_move
    
    def _tactical_strategy(self, board, side, time_limit):
        """Chiến lược tactical - tập trung vào captures và checks"""
        legal_moves = generate_legal_moves(board, side)
        if not legal_moves:
            return None
        
        # Ưu tiên captures và checks
        tactical_moves = []
        for move in legal_moves:
            (x1, y1), (x2, y2) = move
            
            # Capture moves
            if board[x2][y2] != '.':
                tactical_moves.append((move, 1000))
                continue
            
            # Check moves
            new_board = copy.deepcopy(board)
            apply_move(new_board, move)
            opponent = 'black' if side == 'red' else 'red'
            if is_in_check(new_board, opponent):
                tactical_moves.append((move, 500))
        
        if tactical_moves:
            # Sort by priority
            tactical_moves.sort(key=lambda x: x[1], reverse=True)
            return tactical_moves[0][0]
        
        # Fallback to basic strategy
        return self._basic_strategy(board, side, time_limit)
    
    def _positional_strategy(self, board, side, time_limit):
        """Chiến lược positional - tập trung vào vị trí"""
        legal_moves = generate_legal_moves(board, side)
        if not legal_moves:
            return None
        
        best_move = None
        best_score = -99999 if side == 'red' else 99999
        
        for move in legal_moves:
            score = self._evaluate_positional(board, move, side)
            
            if side == 'red' and score > best_score:
                best_score = score
                best_move = move
            elif side == 'black' and score < best_score:
                best_score = score
                best_move = move
        
        return best_move
    
    def _analyze_position(self, board, side):
        """Phân tích tình huống bàn cờ"""
        # Đếm số quân còn lại
        piece_count = 0
        for row in board:
            for piece in row:
                if piece != '.':
                    piece_count += 1
        
        # Tactical nếu có nhiều captures/checks
        captures = self._count_captures(board, side)
        checks = self._count_checks(board, side)
        
        if captures > 2 or checks > 0:
            return 'tactical'
        
        # Positional nếu ít captures
        elif captures < 2:
            return 'positional'
        
        # Cân bằng
        else:
            return 'balanced'
    
    def _evaluate_move_enhanced(self, board, move, side):
        """Đánh giá nước đi với enhanced evaluation"""
        (x1, y1), (x2, y2) = move
        
        # Basic material evaluation
        score = 0
        
        # Capture bonus
        if board[x2][y2] != '.':
            captured_piece = board[x2][y2]
            capturing_piece = board[x1][y1]
            score += self._get_piece_value(captured_piece) - self._get_piece_value(capturing_piece)
        
        # Check bonus
        new_board = copy.deepcopy(board)
        apply_move(new_board, move)
        opponent = 'black' if side == 'red' else 'red'
        if is_in_check(new_board, opponent):
            score += 50
        
        # Escape check bonus
        if is_in_check(board, side) and not is_in_check(new_board, side):
            score += 100
        
        # Center control
        center_distance = abs(x2 - 4.5) + abs(y2 - 4)
        score -= center_distance * 5
        
        # King safety
        score += self._evaluate_king_safety(new_board, side)
        
        return score if side == 'red' else -score
    
    def _evaluate_positional(self, board, move, side):
        """Đánh giá positional factors"""
        (x1, y1), (x2, y2) = move
        score = 0
        
        # Center control
        center_distance = abs(x2 - 4.5) + abs(y2 - 4)
        score -= center_distance * 10
        
        # Piece activity
        new_board = copy.deepcopy(board)
        apply_move(new_board, move)
        activity = self._count_legal_moves(new_board, side)
        score += activity * 2
        
        return score if side == 'red' else -score
    
    def _evaluate_king_safety(self, board, side):
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
        
        return protection * 10
    
    def _count_captures(self, board, side):
        """Đếm số captures có thể"""
        captures = 0
        for move in generate_legal_moves(board, side):
            (x1, y1), (x2, y2) = move
            if board[x2][y2] != '.':
                captures += 1
        return captures
    
    def _count_checks(self, board, side):
        """Đếm số checks có thể"""
        checks = 0
        for move in generate_legal_moves(board, side):
            new_board = copy.deepcopy(board)
            apply_move(new_board, move)
            opponent = 'black' if side == 'red' else 'red'
            if is_in_check(new_board, opponent):
                checks += 1
        return checks
    
    def _count_legal_moves(self, board, side):
        """Đếm số nước đi hợp lệ"""
        return len(generate_legal_moves(board, side))
    
    def _get_piece_value(self, piece):
        """Lấy giá trị quân cờ"""
        values = {
            'K': 10000, 'A': 100, 'B': 100, 'N': 300, 'R': 500, 'C': 250, 'P': 50,
            'k': -10000, 'a': -100, 'b': -100, 'n': -300, 'r': -500, 'c': -250, 'p': -50,
            '.': 0
        }
        return values.get(piece, 0)
    
    def _update_stats(self, move, response_time):
        """Cập nhật thống kê"""
        self.move_history.append({
            'move': move,
            'time': response_time,
            'timestamp': time.time()
        })
        
        self.performance_stats['total_moves'] += 1
        self.performance_stats['avg_time'] = (
            (self.performance_stats['avg_time'] * (self.performance_stats['total_moves'] - 1) + response_time) 
            / self.performance_stats['total_moves']
        )
    
    def get_performance_stats(self):
        """Lấy thống kê hiệu suất"""
        return self.performance_stats.copy()

    def _iterative_deepening_minimax(self, board, side, time_limit, tactical=False):
        """Iterative deepening minimax với alpha-beta pruning"""
        import time
        start_time = time.time()
        
        legal_moves = generate_legal_moves(board, side)
        if not legal_moves:
            return None
        
        # Move ordering: ưu tiên captures và checks
        ordered_moves = self._order_moves_advanced(board, legal_moves, side, tactical)
        
        best_move = ordered_moves[0]  # Default move
        
        # Iterative deepening: tăng depth dần với aspiration windows
        prev_best_score = 0
        for depth in range(1, self.max_depth + 1):
            if time.time() - start_time > time_limit * 0.8:
                break  # Time limit
            
            current_best = None
            best_score = -99999 if side == 'red' else 99999
            
            # Limit moves per depth để tăng tốc
            moves_to_check = ordered_moves[:min(self.max_moves_per_depth, len(ordered_moves))]
            
            # Aspiration bounds
            alpha_bound = prev_best_score - self.asp_window
            beta_bound = prev_best_score + self.asp_window
            use_asp = depth > 2  # Bật lại aspiration cho depth > 2

            for idx, move in enumerate(moves_to_check):
                if time.time() - start_time > time_limit * 0.8:
                    break
                
                new_board = [row[:] for row in board]
                apply_move(new_board, move)
                
                opponent = 'black' if side == 'red' else 'red'
                if use_asp:
                    score = self._minimax_ab(new_board, opponent, depth - 1, 
                                              alpha_bound, beta_bound, side == 'red', tactical, depth, idx)
                    # Fail-soft: if outside window, re-search with full bounds once
                    if score <= alpha_bound or score >= beta_bound:
                        score = self._minimax_ab(new_board, opponent, depth - 1, 
                                                  -99999, 99999, side == 'red', tactical, depth, idx)
                else:
                    score = self._minimax_ab(new_board, opponent, depth - 1, 
                                              -99999, 99999, side == 'red', tactical, depth, idx)
                
                if side == 'red' and score > best_score:
                    best_score = score
                    current_best = move
                elif side == 'black' and score < best_score:
                    best_score = score
                    current_best = move
            
            if current_best:
                best_move = current_best
                prev_best_score = best_score
        
        return best_move
    
    def _minimax_ab(self, board, side, depth, alpha, beta, maximizing, tactical, root_depth, move_index):
        """Minimax với alpha-beta pruning"""
        # Lưu alpha/beta gốc để set TT flag
        orig_alpha = alpha
        orig_beta = beta
        
        # TT lookup
        key = self._zobrist_key(board, side)
        tt_entry = self.transposition_table.get(key)
        if tt_entry and tt_entry['depth'] >= depth:
            if tt_entry['flag'] == 'EXACT':
                return tt_entry['score']
            elif tt_entry['flag'] == 'LOWER' and tt_entry['score'] > alpha:
                alpha = tt_entry['score']
            elif tt_entry['flag'] == 'UPPER' and tt_entry['score'] < beta:
                beta = tt_entry['score']
            if alpha >= beta:
                return tt_entry['score']
        
        if depth == 0:
            return self._quiescence(board, side, alpha, beta, maximizing, tactical)
        
        legal_moves = generate_legal_moves(board, side)
        if not legal_moves:
            # Checkmate/stalemate detection
            if is_in_check(board, side):
                # Checkmate - trả về điểm rất thấp/cao dựa vào khoảng cách depth
                # Depth càng gần root càng tốt (tìm checkmate nhanh)
                mate_score = 50000 - root_depth * 100
                return -mate_score if maximizing else mate_score
            else:
                # Stalemate
                return 0
        
        # Move ordering
        ordered_moves = self._order_moves_scored(board, legal_moves, side, tactical, depth)
        moves_to_check = ordered_moves[:min(self.max_moves_per_depth + 4, len(ordered_moves))]  # Limit moves
        
        best_score_local = -99999 if maximizing else 99999
        best_move_local = None

        # PVS + LMR
        first = True
        for idx, move in enumerate(moves_to_check):
            new_board = [row[:] for row in board]
            apply_move(new_board, move)
            opponent = 'black' if side == 'red' else 'red'

            # Late move reduction for quiet late moves
            is_quiet = (board[move[1][0]][move[1][1]] == '.')
            reduction = 0
            if depth >= 3 and idx > 3 and is_quiet:
                reduction = 1

            if first:
                eval_score = self._minimax_ab(new_board, opponent, depth - 1 - reduction, alpha, beta, not maximizing, tactical, root_depth, idx)
                first = False
            else:
                # PVS narrow window
                eval_score = self._minimax_ab(new_board, opponent, depth - 1 - reduction, alpha, alpha + 1, not maximizing, tactical, root_depth, idx)
                if maximizing and eval_score > alpha and eval_score < beta:
                    eval_score = self._minimax_ab(new_board, opponent, depth - 1, alpha, beta, not maximizing, tactical, root_depth, idx)
                elif not maximizing and eval_score < beta and eval_score > alpha:
                    eval_score = self._minimax_ab(new_board, opponent, depth - 1, alpha, beta, not maximizing, tactical, root_depth, idx)

            if maximizing:
                if eval_score > best_score_local:
                    best_score_local = eval_score
                    best_move_local = move
                if eval_score > alpha:
                    alpha = eval_score
                if alpha >= beta:
                    self._store_killer_history(move, depth)
                    break
            else:
                if eval_score < best_score_local:
                    best_score_local = eval_score
                    best_move_local = move
                if eval_score < beta:
                    beta = eval_score
                if alpha >= beta:
                    self._store_killer_history(move, depth)
                    break

        # Store TT - dùng orig_alpha/orig_beta để set flag đúng
        flag = 'EXACT'
        score_store = best_score_local
        if best_score_local <= orig_alpha:
            flag = 'UPPER'
        elif best_score_local >= orig_beta:
            flag = 'LOWER'
        self.transposition_table[key] = {
            'depth': depth,
            'score': score_store,
            'flag': flag,
            'best_move': best_move_local
        }
        return best_score_local

    def _store_killer_history(self, move, depth):
        killers = self.killer_moves.get(depth, [])
        if move not in killers:
            killers = ([move] + killers)[:2]
            self.killer_moves[depth] = killers
        self.history_heuristic[move] = self.history_heuristic.get(move, 0) + 1

    def _order_moves_scored(self, board, moves, side, tactical, depth):
        # Chỉ dùng scoring rẻ tiền, KHÔNG gọi is_in_check trong mọi moves
        scored = []
        for move in moves:
            (x1,y1),(x2,y2) = move
            value = 0
            # Bonus capture
            if board[x2][y2] != '.':
                value += abs(self._get_piece_value(board[x2][y2])) * 10 - abs(self._get_piece_value(board[x1][y1]))
            # Add PST bonus sau nước đi
            tmp_board = [row[:] for row in board]
            apply_move(tmp_board, move)
            value += evaluate_board(tmp_board) - evaluate_board(board)
            scored.append((value, move))
        scored.sort(reverse=True)
        # Chỉ trả về tối đa self.max_moves_per_depth moves tốt nhất
        return [m for _,m in scored][:self.max_moves_per_depth]

    def _quiescence(self, board, side, alpha, beta, maximizing, tactical):
        # Quiescence chỉ cho capture (KHÔNG check-nước bất kỳ)
        stand_pat = evaluate_board(board)
        if maximizing:
            if stand_pat >= beta:
                return beta
            if stand_pat > alpha:
                alpha = stand_pat
        else:
            if stand_pat <= alpha:
                return alpha
            if stand_pat < beta:
                beta = stand_pat
        # Only captures
        moves = generate_legal_moves(board, side)
        captures = [mv for mv in moves if board[mv[1][0]][mv[1][1]] != '.']
        if not captures:
            return stand_pat
        for move in captures:
            new_board = [row[:] for row in board]
            apply_move(new_board, move)
            opponent = 'black' if side == 'red' else 'red'
            score = self._quiescence(new_board, opponent, alpha, beta, not maximizing, tactical)
            if maximizing:
                if score > alpha:
                    alpha = score
                if alpha >= beta:
                    break
            else:
                if score < beta:
                    beta = score
                if alpha >= beta:
                    break
        return alpha if maximizing else beta
    
    def _evaluate_move_enhanced_board(self, board, maximizing, tactical):
        """Đánh giá board: chỉ material+PST; KHÔNG gọi is_in_check nữa!"""
        score = evaluate_board(board)
        # KHÔNG bonus check, không gọi is_in_check -> tránh bottleneck
        return score
    
    def _evaluate_center_control_board(self, board, side):
        """Đánh giá center control cho board"""
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
    
    def _evaluate_king_safety_board(self, board, side):
        """Đánh giá king safety cho board"""
        king_pos = None
        for x in range(10):
            for y in range(9):
                piece = board[x][y]
                if (side == 'red' and piece == 'K') or (side == 'black' and piece == 'k'):
                    king_pos = (x, y)
                    break
        if not king_pos:
            return 0
        x, y = king_pos
        protection = 0
        for dx in [-1, 0, 1]:
            for dy in [-1, 0, 1]:
                nx, ny = x + dx, y + dy
                if 0 <= nx < 10 and 0 <= ny < 9:
                    piece = board[nx][ny]
                    if piece != '.' and ((side == 'red' and piece.isupper()) or (side == 'black' and piece.islower())):
                        protection += 1
        return protection * 10
    
    def _evaluate_piece_advancement(self, board):
        """Đánh giá tiến độ quân cờ - đã được tính trong PST, loại bỏ để tránh duplicate"""
        # PST đã tính advancement rồi, không cần thêm
        return 0
    
    def _order_moves_advanced(self, board, moves, side, tactical):
        """Sắp xếp nước đi nâng cao - ưu tiên captures, checks, tốt"""
        captures = []
        checks = []
        others = []
        
        for move in moves:
            (_, _), (x2, y2) = move
            # Check if capture
            if board[x2][y2] != '.':
                captured_value = abs(self._get_piece_value(board[x2][y2]))
                captures.append((move, captured_value))
                continue
            
            # Check if gives check
            new_board = [row[:] for row in board]
            apply_move(new_board, move)
            opponent = 'black' if side == 'red' else 'red'
            if is_in_check(new_board, opponent):
                checks.append(move)
            else:
                others.append(move)
        
        # Sort captures by value
        captures.sort(key=lambda x: x[1], reverse=True)
        capture_moves = [m[0] for m in captures]
        
        if tactical:
            return capture_moves + checks + others
        else:
            return capture_moves + checks + others
    
    def _board_to_key(self, board, side):
        """Tạo key cho transposition table"""
        board_str = ''.join(''.join(row) for row in board)
        return f"{board_str}_{side}"

def ai_hard_move(board, side, time_limit=3.0, strategy='adaptive', profile: str = None):
    """AI Hard chính
    profile: 'fast' | 'balanced' | None
    """
    ai = XiangqiHardAI(time_limit=time_limit, strategy=strategy)
    if profile:
        ai.set_profile(profile)
    return ai.get_move(board, side, time_limit)
