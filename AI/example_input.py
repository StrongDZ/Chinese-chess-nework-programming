"""
Ví dụ chi tiết về các loại INPUT mà AI nhận
"""

from ai import AI, AIDifficulty, Move, Coord

print("=" * 70)
print("CÁC LOẠI INPUT MÀ AI CÓ THỂ NHẬN")
print("=" * 70)

# ============================================================================
# LOẠI 1: FEN STRING (Forsyth-Edwards Notation)
# ============================================================================
print("\n📌 LOẠI 1: FEN STRING")
print("-" * 70)

# FEN string mô tả toàn bộ bàn cờ
fen_example = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"

print("Input:")
print(f'  fen_string = "{fen_example}"')
print("\nGiải thích:")
print("  - rnbakabnr: Hàng trên cùng (Đen) - Xe, Mã, Tượng, Sĩ, Tướng, Sĩ, Tượng, Mã, Xe")
print("  - 9: Hàng trống (9 ô trống)")
print("  - 1c5c1: Pháo ở cột 1 và 7")
print("  - p1p1p1p1p: 5 quân tốt")
print("  - w: Lượt của Đỏ (w=white/red, b=black)")
print("  - - - 0 1: Thông tin khác (không dùng trong cờ tướng)")

print("\nCách sử dụng:")
print("  ai = AI()")
print("  ai.initialize()")
print("  move = ai.predict_move(fen_string, AIDifficulty.MEDIUM)")

# ============================================================================
# LOẠI 2: POSITION STRING (FEN + Move History)
# ============================================================================
print("\n\n📌 LOẠI 2: POSITION STRING (FEN + Move History)")
print("-" * 70)

initial_fen = AI.INITIAL_FEN
moves = [
    Move(Coord(6, 0), Coord(7, 0)),  # Đỏ di chuyển từ (6,0) đến (7,0)
    Move(Coord(3, 0), Coord(4, 0)),  # Đen di chuyển từ (3,0) đến (4,0)
]

position_string = AI.build_position_string(initial_fen, moves)

print("Input:")
print(f'  initial_fen = "{initial_fen}"')
print("  moves = [")
print("      Move(Coord(6, 0), Coord(7, 0)),")
print("      Move(Coord(3, 0), Coord(4, 0)),")
print("  ]")
print(f'\n  position_string = "{position_string}"')

print("\nCách sử dụng:")
print("  position_str = AI.build_position_string(initial_fen, moves)")
print("  move = ai.predict_move(position_str, AIDifficulty.MEDIUM)")

# ============================================================================
# LOẠI 3: BOARD ARRAY (2D List)
# ============================================================================
print("\n\n📌 LOẠI 3: BOARD ARRAY (2D List 10x9)")
print("-" * 70)

# Board là mảng 2D: 10 hàng x 9 cột
# board[0] = Đỏ (bottom), board[9] = Đen (top)
board_example = [
    # Row 0 (Đỏ bottom - hàng dưới cùng)
    ['r', 'n', 'b', 'a', 'k', 'a', 'b', 'n', 'r'],
    # Row 1
    [' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '],
    # Row 2
    [' ', 'c', ' ', ' ', ' ', ' ', ' ', 'c', ' '],
    # Row 3
    ['p', ' ', 'p', ' ', 'p', ' ', 'p', ' ', 'p'],
    # Row 4
    [' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '],
    # Row 5
    [' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '],
    # Row 6
    ['P', ' ', 'P', ' ', 'P', ' ', 'P', ' ', 'P'],
    # Row 7
    [' ', 'C', ' ', ' ', ' ', ' ', ' ', 'C', ' '],
    # Row 8
    [' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '],
    # Row 9 (Đen top - hàng trên cùng)
    ['R', 'N', 'B', 'A', 'K', 'A', 'B', 'N', 'R'],
]

print("Input:")
print("  board = [")
print("      ['r', 'n', 'b', 'a', 'k', 'a', 'b', 'n', 'r'],  # Row 0 - Đỏ")
print("      [' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '],  # Row 1")
print("      [' ', 'c', ' ', ' ', ' ', ' ', ' ', 'c', ' '],  # Row 2")
print("      ['p', ' ', 'p', ' ', 'p', ' ', 'p', ' ', 'p'],  # Row 3")
print("      [' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '],  # Row 4")
print("      [' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '],  # Row 5")
print("      ['P', ' ', 'P', ' ', 'P', ' ', 'P', ' ', 'P'],  # Row 6")
print("      [' ', 'C', ' ', ' ', ' ', ' ', ' ', 'C', ' '],  # Row 7")
print("      [' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '],  # Row 8")
print("      ['R', 'N', 'B', 'A', 'K', 'A', 'B', 'N', 'R'],  # Row 9 - Đen")
print("  ]")
print("  side_to_move = 'w'  # 'w' = Đỏ, 'b' = Đen")

print("\nKý hiệu quân cờ:")
print("  Đỏ (chữ HOA):")
print("    R = Xe (Rook), N = Mã (Knight), B = Tượng (Bishop)")
print("    A = Sĩ (Advisor), K = Tướng (King)")
print("    C = Pháo (Cannon), P = Tốt (Pawn)")
print("  Đen (chữ thường):")
print("    r = Xe, n = Mã, b = Tượng")
print("    a = Sĩ, k = Tướng")
print("    c = Pháo, p = Tốt")
print("  ' ' = Ô trống")

print("\nCách sử dụng:")
print("  move = ai.predict_move_from_board(board, 'w', AIDifficulty.MEDIUM)")

# ============================================================================
# LOẠI 4: DIFFICULTY LEVELS
# ============================================================================
print("\n\n📌 LOẠI 4: DIFFICULTY LEVELS")
print("-" * 70)

print("Input:")
print("  AIDifficulty.EASY    # Depth 3, timeout 500ms")
print("  AIDifficulty.MEDIUM  # Depth 5, timeout 1000ms")
print("  AIDifficulty.HARD    # Depth 8, timeout 2000ms")

print("\nCách sử dụng:")
print("  move = ai.predict_move(fen_string, AIDifficulty.EASY)")
print("  move = ai.predict_move(fen_string, AIDifficulty.MEDIUM)")
print("  move = ai.predict_move(fen_string, AIDifficulty.HARD)")

# ============================================================================
# VÍ DỤ TỔNG HỢP
# ============================================================================
print("\n\n" + "=" * 70)
print("VÍ DỤ TỔNG HỢP - CODE HOÀN CHỈNH")
print("=" * 70)

print("""
# Khởi tạo AI
ai = AI()
ai.initialize()

# CÁCH 1: Dùng FEN string
fen = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"
move1 = ai.predict_move(fen, AIDifficulty.MEDIUM)

# CÁCH 2: Dùng board array
board = [
    ['r', 'n', 'b', 'a', 'k', 'a', 'b', 'n', 'r'],
    [' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '],
    [' ', 'c', ' ', ' ', ' ', ' ', ' ', 'c', ' '],
    ['p', ' ', 'p', ' ', 'p', ' ', 'p', ' ', 'p'],
    [' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '],
    [' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '],
    ['P', ' ', 'P', ' ', 'P', ' ', 'P', ' ', 'P'],
    [' ', 'C', ' ', ' ', ' ', ' ', ' ', 'C', ' '],
    [' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '],
    ['R', 'N', 'B', 'A', 'K', 'A', 'B', 'N', 'R'],
]
move2 = ai.predict_move_from_board(board, "w", AIDifficulty.MEDIUM)

# CÁCH 3: Dùng position string với move history
initial_fen = AI.INITIAL_FEN
moves = [Move(Coord(6, 0), Coord(7, 0))]
position_str = AI.build_position_string(initial_fen, moves)
move3 = ai.predict_move(position_str, AIDifficulty.MEDIUM)

# Kiểm tra kết quả
if move1:
    print(f"Move 1: From ({move1.from_pos.row}, {move1.from_pos.col}) → To ({move1.to_pos.row}, {move1.to_pos.col})")

ai.shutdown()
""")

print("\n" + "=" * 70)


