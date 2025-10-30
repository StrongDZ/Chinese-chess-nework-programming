"""
Enhanced Board Evaluator với Piece-Square Tables cho cờ tướng
"""

# Piece-Square Tables (từ góc nhìn RED, Black sẽ mirror)
# Bàn cờ: x=0-9 (hàng), y=0-8 (cột)

# Tướng (King) - ở trong cung 9 giường
KING_PST = [
    [  0,   0,   0,   0,   0,   0,   0,   0,   0],
    [  0,   0,   0,   0,   0,   0,   0,   0,   0],
    [  0,   0,   0,   0,   0,   0,   0,   0,   0],
    [  0,   0,   0,   0,   0,   0,   0,   0,   0],
    [  0,   0,   0,   0,   0,   0,   0,   0,   0],
    [  0,   0,   0,   0,   0,   0,   0,   0,   0],
    [  0,   0,   0,   0,   0,   0,   0,   0,   0],
    [  0,   0,   0,   5,  10,   5,   0,   0,   0],
    [  0,   0,   0,   0,   0,   0,   0,   0,   0],
    [  0,   0,   0,   5,   8,   5,   0,   0,   0],
]

# Sĩ (Advisor) - ưu tiên ở giữa cung
ADVISOR_PST = [
    [  0,   0,   0,   0,   0,   0,   0,   0,   0],
    [  0,   0,   0,   0,   0,   0,   0,   0,   0],
    [  0,   0,   0,   0,   0,   0,   0,   0,   0],
    [  0,   0,   0,   0,   0,   0,   0,   0,   0],
    [  0,   0,   0,   0,   0,   0,   0,   0,   0],
    [  0,   0,   0,   0,   0,   0,   0,   0,   0],
    [  0,   0,   0,   0,   0,   0,   0,   0,   0],
    [  0,   0,   0,   5,   0,   5,   0,   0,   0],
    [  0,   0,   0,   0,   8,   0,   0,   0,   0],
    [  0,   0,   0,   5,   0,   5,   0,   0,   0],
]

# Tượng (Bishop) - không qua sông, ưu tiên ở vị trí trung tâm phòng thủ
BISHOP_PST = [
    [  0,   0,   4,   0,   0,   0,   4,   0,   0],
    [  0,   0,   0,   0,   0,   0,   0,   0,   0],
    [  2,   0,   0,   0,   6,   0,   0,   0,   2],
    [  0,   0,   0,   0,   0,   0,   0,   0,   0],
    [  0,   0,   4,   0,   0,   0,   4,   0,   0],
    [  0,   0,   0,   0,   0,   0,   0,   0,   0],
    [  0,   0,   0,   0,   0,   0,   0,   0,   0],
    [  0,   0,   0,   0,   0,   0,   0,   0,   0],
    [  0,   0,   0,   0,   0,   0,   0,   0,   0],
    [  0,   0,   0,   0,   0,   0,   0,   0,   0],
]

# Mã (Knight) - mạnh ở trung tâm
KNIGHT_PST = [
    [  0,   2,   4,   3,   2,   3,   4,   2,   0],
    [  2,   0,   6,   4,   8,   4,   6,   0,   2],
    [  4,   6,   8,   7,  10,   7,   8,   6,   4],
    [  3,   4,   7,   9,  12,   9,   7,   4,   3],
    [  2,   8,  10,  12,  14,  12,  10,   8,   2],
    [  2,   8,  10,  12,  14,  12,  10,   8,   2],
    [  3,   4,   7,   9,  12,   9,   7,   4,   3],
    [  4,   6,   8,   7,  10,   7,   8,   6,   4],
    [  2,   0,   6,   4,   8,   4,   6,   0,   2],
    [  0,   2,   4,   3,   2,   3,   4,   2,   0],
]

# Xe (Rook) - mạnh nhất, ưu tiên hàng ngang/dọc mở, tiến gần địch
ROOK_PST = [
    [ 12,  12,  14,  15,  16,  15,  14,  12,  12],
    [ 10,  12,  14,  15,  16,  15,  14,  12,  10],
    [  8,  10,  12,  13,  14,  13,  12,  10,   8],
    [  6,   8,  10,  11,  12,  11,  10,   8,   6],
    [  4,   6,   8,   9,  10,   9,   8,   6,   4],
    [  4,   6,   8,   9,  10,   9,   8,   6,   4],
    [  2,   4,   6,   7,   8,   7,   6,   4,   2],
    [  2,   4,   6,   7,   8,   7,   6,   4,   2],
    [  0,   2,   4,   5,   6,   5,   4,   2,   0],
    [  0,   2,   4,   5,   6,   5,   4,   2,   0],
]

# Pháo (Cannon) - mạnh ở xa, kiểm soát cột
CANNON_PST = [
    [  6,   8,   7,   6,   5,   6,   7,   8,   6],
    [  4,   5,   6,   5,   4,   5,   6,   5,   4],
    [  2,   4,   5,   4,   3,   4,   5,   4,   2],
    [  0,   2,   3,   2,   1,   2,   3,   2,   0],
    [  0,   0,   1,   0,   0,   0,   1,   0,   0],
    [  0,   0,   1,   0,   0,   0,   1,   0,   0],
    [  0,   2,   3,   2,   1,   2,   3,   2,   0],
    [  2,   4,   5,   4,   3,   4,   5,   4,   2],
    [  4,   5,   6,   5,   4,   5,   6,   5,   4],
    [  6,   8,   7,   6,   5,   6,   7,   8,   6],
]

# Tốt (Pawn) - mạnh khi qua sông và tiến gần địch
PAWN_PST = [
    [ 10,  12,  14,  16,  18,  16,  14,  12,  10],
    [  8,  10,  12,  14,  16,  14,  12,  10,   8],
    [  6,   8,  10,  12,  14,  12,  10,   8,   6],
    [  4,   6,   8,  10,  12,  10,   8,   6,   4],
    [  2,   4,   6,   8,  10,   8,   6,   4,   2],
    [  0,   2,   4,   6,   8,   6,   4,   2,   0],
    [  0,   0,   2,   4,   6,   4,   2,   0,   0],
    [  0,   0,   0,   2,   4,   2,   0,   0,   0],
    [  0,   0,   0,   0,   0,   0,   0,   0,   0],
    [  0,   0,   0,   0,   0,   0,   0,   0,   0],
]

PST_MAP = {
    'K': KING_PST, 'A': ADVISOR_PST, 'B': BISHOP_PST,
    'N': KNIGHT_PST, 'R': ROOK_PST, 'C': CANNON_PST, 'P': PAWN_PST
}

def evaluate_board(board):
    """
    Đánh giá bàn cờ với material + positional values
    Return: điểm số từ góc nhìn RED (dương = tốt cho RED, âm = tốt cho BLACK)
    """
    # Material values
    PIECE_VALUES = {
        'K': 10000, 'A': 200, 'B': 200, 'N': 400, 'R': 900, 'C': 450, 'P': 100,
        'k': -10000, 'a': -200, 'b': -200, 'n': -400, 'r': -900, 'c': -450, 'p': -100,
        '.': 0
    }
    
    score = 0
    red_pieces = 0
    black_pieces = 0
    
    for x in range(10):
        for y in range(9):
            piece = board[x][y]
            if piece == '.':
                continue
            
            # Material score
            material = PIECE_VALUES.get(piece, 0)
            score += material
            
            # Positional score
            if piece.isupper():  # RED
                red_pieces += 1
                piece_type = piece
                pst = PST_MAP.get(piece_type)
                if pst:
                    # RED ở phía dưới (x=7-9), đọc PST từ dưới lên
                    score += pst[x][y]
            else:  # BLACK
                black_pieces += 1
                piece_type = piece.upper()
                pst = PST_MAP.get(piece_type)
                if pst:
                    # BLACK ở phía trên (x=0-2), mirror PST
                    mirror_x = 9 - x
                    score -= pst[mirror_x][y]
    
    # Game phase bonus (endgame vs opening/midgame)
    total_pieces = red_pieces + black_pieces
    if total_pieces <= 10:  # Endgame
        # Khuyến khích active king trong endgame
        score += evaluate_endgame_bonus(board)
    
    return score

def evaluate_endgame_bonus(board):
    """Bonus cho endgame - khuyến khích tướng chủ động"""
    bonus = 0
    
    # Tìm vị trí tướng
    red_king_pos = None
    black_king_pos = None
    
    for x in range(10):
        for y in range(9):
            if board[x][y] == 'K':
                red_king_pos = (x, y)
            elif board[x][y] == 'k':
                black_king_pos = (x, y)
    
    # Khuyến khích tướng RED tiến lên (x giảm) trong endgame
    if red_king_pos:
        x, y = red_king_pos
        bonus += (9 - x) * 3  # Tiến lên = bonus
    
    # Phạt tướng BLACK lùi xuống (x tăng) trong endgame
    if black_king_pos:
        x, y = black_king_pos
        bonus -= x * 3
    
    return bonus
