"""
Ví dụ minh họa cách AI trả về kết quả
"""

from ai import AI, AIDifficulty, Move, Coord

# Khởi tạo AI
ai = AI()

# Khởi tạo engine
if ai.initialize():
    print("✅ AI Engine đã sẵn sàng\n")
    
    # FEN position mặc định (vị trí bàn cờ ban đầu)
    initial_fen = AI.INITIAL_FEN
    print(f"FEN position: {initial_fen}\n")
    
    # Ví dụ 1: Predict move từ FEN
    print("=" * 60)
    print("VÍ DỤ 1: predict_move() từ FEN")
    print("=" * 60)
    
    move = ai.predict_move(initial_fen, AIDifficulty.MEDIUM)
    
    if move:
        print(f"✅ AI trả về Move object:")
        print(f"   Type: {type(move)}")
        print(f"   From position: row={move.from_pos.row}, col={move.from_pos.col}")
        print(f"   To position:   row={move.to_pos.row}, col={move.to_pos.col}")
        print(f"   UCI format:    {AI.move_to_uci(move)}")
        print(f"\n   Cấu trúc Move object:")
        print(f"   move.from_pos.row  = {move.from_pos.row}")
        print(f"   move.from_pos.col  = {move.from_pos.col}")
        print(f"   move.to_pos.row    = {move.to_pos.row}")
        print(f"   move.to_pos.col    = {move.to_pos.col}")
    else:
        print("❌ AI trả về None (có lỗi xảy ra)")
    
    print("\n")
    
    # Ví dụ 2: Predict move từ board array
    print("=" * 60)
    print("VÍ DỤ 2: predict_move_from_board() từ board array")
    print("=" * 60)
    
    # Tạo board 10x9 (10 hàng, 9 cột)
    # board[0] = Đỏ (bottom), board[9] = Đen (top)
    board = [
        ['r', 'n', 'b', 'a', 'k', 'a', 'b', 'n', 'r'],  # row 0 - Đỏ bottom
        [' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '],  # row 1
        [' ', 'c', ' ', ' ', ' ', ' ', ' ', 'c', ' '],  # row 2
        ['p', ' ', 'p', ' ', 'p', ' ', 'p', ' ', 'p'],  # row 3
        [' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '],  # row 4
        [' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '],  # row 5
        ['P', ' ', 'P', ' ', 'P', ' ', 'P', ' ', 'P'],  # row 6
        [' ', 'C', ' ', ' ', ' ', ' ', ' ', 'C', ' '],  # row 7
        [' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '],  # row 8
        ['R', 'N', 'B', 'A', 'K', 'A', 'B', 'N', 'R'],  # row 9 - Đen top
    ]
    
    move2 = ai.predict_move_from_board(board, "w", AIDifficulty.EASY)
    
    if move2:
        print(f"✅ AI trả về Move object:")
        print(f"   From: ({move2.from_pos.row}, {move2.from_pos.col})")
        print(f"   To:   ({move2.to_pos.row}, {move2.to_pos.col})")
        print(f"   UCI:  {AI.move_to_uci(move2)}")
    else:
        print("❌ AI trả về None")
    
    print("\n")
    
    # Ví dụ 3: So sánh các difficulty levels
    print("=" * 60)
    print("VÍ DỤ 3: So sánh các difficulty levels")
    print("=" * 60)
    
    for diff in [AIDifficulty.EASY, AIDifficulty.MEDIUM, AIDifficulty.HARD]:
        move = ai.predict_move(initial_fen, diff)
        if move:
            print(f"{diff.value.upper():8s}: From ({move.from_pos.row},{move.from_pos.col}) → To ({move.to_pos.row},{move.to_pos.col}) | UCI: {AI.move_to_uci(move)}")
        else:
            print(f"{diff.value.upper():8s}: None")
    
    print("\n")
    
    # Ví dụ 4: Build position string từ moves
    print("=" * 60)
    print("VÍ DỤ 4: build_position_string() với move history")
    print("=" * 60)
    
    moves_history = [
        Move(Coord(6, 0), Coord(7, 0)),  # Đỏ di chuyển quân từ (6,0) đến (7,0)
        Move(Coord(3, 0), Coord(4, 0)),  # Đen di chuyển quân từ (3,0) đến (4,0)
    ]
    
    position_str = AI.build_position_string(initial_fen, moves_history)
    print(f"Position string: {position_str}\n")
    
    move3 = ai.predict_move(position_str, AIDifficulty.MEDIUM)
    if move3:
        print(f"✅ Nước đi tiếp theo: {AI.move_to_uci(move3)}")
        print(f"   From: ({move3.from_pos.row}, {move3.from_pos.col})")
        print(f"   To:   ({move3.to_pos.row}, {move3.to_pos.col})")
    
    # Shutdown
    ai.shutdown()
    print("\n✅ AI Engine đã shutdown")
    
else:
    print("❌ Không thể khởi tạo AI Engine")
    print("   Kiểm tra xem pikafish có sẵn không?")

