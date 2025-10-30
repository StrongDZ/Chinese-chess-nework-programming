# Validate Move Legality (Server Authoritative)

## Mục tiêu
- Server là nguồn chân lý: chỉ chấp nhận nước đi hợp lệ theo luật cờ tướng.
- Dựa vào `Model/easy/move_generator.py` (generate_legal_moves, is_in_check).

## Luồng xử lý
1) Nhận `MovePacket{sessionID, gameID, from, to, timestamp}`
2) Lấy `game_state` (board, side_to_move, timers)
3) Gọi `validate_move(game_state, move, side)`
4) Nếu hợp lệ → áp dụng move, chuyển lượt, cập nhật timer, ghi `moves[]` → gửi `MOVE_ACCEPTED{newState}` cho người đi, và `OPPONENT_MOVE{...}` cho đối thủ
5) Nếu không hợp lệ → gửi `MOVE_REJECTED{reason}`

## `validate_move` – pseudo‑code
```python
def validate_move(state, move, side):
    (x1,y1),(x2,y2) = move
    if side != state.side_to_move:
        return False, "WRONG_TURN"
    if not in_bounds(x1,y1) or not in_bounds(x2,y2):
        return False, "OUT_OF_BOUNDS"
    piece = state.board[x1][y1]
    if piece == '.' or (side == 'red' and not piece.isupper()) or (side == 'black' and not piece.islower()):
        return False, "NO_OWN_PIECE"
    legal = generate_legal_moves(state.board, side)
    if move not in legal:
        return False, "ILLEGAL_MOVE"
    # Không để vua mình bị chiếu sau khi đi
    board2 = [row[:] for row in state.board]
    apply_move(board2, move)
    if is_in_check(board2, side):
        return False, "SELF_CHECK"
    return True, None
```

## Tích hợp timer
- Trước khi apply move: kiểm tra `remaining_time > 0`, nếu không → `TIMEOUT` kết thúc ván.
- Sau khi apply move: trừ thời gian đã dùng, chuyển timer cho đối thủ.

## Kiểm thử (khuyến nghị)
- Bộ test theo từng quân: Xe/Mã/Tượng/Sĩ/Tướng/Pháo/Tốt
- Các case đặc biệt: “tướng đối mặt”, “ăn qua sông”, “đi xong bị chiếu”

## Packets
Xem `MOVE_PACKET.md`.
