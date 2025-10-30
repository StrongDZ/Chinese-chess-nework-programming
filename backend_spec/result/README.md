# Determine Game Result + ELO

## 1) Các điều kiện kết thúc (MVP)
- Checkmate: không còn legal move và bên hiện tại đang bị chiếu.
- Stalemate: không còn legal move và không bị chiếu.
- Resign: client gửi `RESIGN{gameID}`.
- Timeout: hệ thống timer phát hiện hết giờ.

Optional (sau): threefold repetition; 50-move rule.

## 2) Thuật toán (pseudo‑code)
```python
def detect_result(board, side_to_move):
    legal = generate_legal_moves(board, side_to_move)
    if legal:
        return None  # chưa kết thúc
    if is_in_check(board, side_to_move):
        return {"result": "CHECKMATE", "winner": opponent(side_to_move)}
    else:
        return {"result": "STALEMATE"}
```

## 3) ELO
Xem `ELO.md`. Sau khi có `winner/loser/draw`, tính ELO mới và cập nhật DB:
- UPDATE `users` (elo, total_games, wins/losses/draws)
- INSERT `game_history(gameID, p1, p2, result, reason, moves[], start_time, end_time, mode)`

## 4) Packet
Xem `GAME_END_PACKET.md`.
