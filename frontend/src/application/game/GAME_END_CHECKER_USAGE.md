# GameEndChecker - Hướng Dẫn Sử Dụng

## Tổng quan
`GameEndChecker` là class mới được thêm vào để implement đầy đủ các điều kiện kết thúc game theo luật cờ tướng chuẩn.

## Các tính năng đã implement

### 1. Check Detection (Phát hiện chiếu)
```java
boolean redInCheck = GameEndChecker.isKingInCheck(board, true);
boolean blackInCheck = GameEndChecker.isKingInCheck(board, false);
```

### 2. Checkmate Detection (Phát hiện chiếu hết)
```java
GameEndChecker checker = new GameEndChecker();
GameEndChecker.GameEndResult result = checker.checkGameEnd(board, isRedTurn);
if (result.isGameOver && result.result.equals("red")) {
    // Red wins by checkmate
}
```

### 3. Stalemate Detection (Hết nước đi)
Tự động phát hiện khi:
- Không có nước đi hợp lệ nào
- Vua không bị chiếu

### 4. Threefold Repetition (Lặp lại 3 lần)
```java
checker.recordMove(board, captured);
// Tự động phát hiện nếu cùng vị trí lặp lại 3 lần
```

### 5. 60-Move Rule (Luật 60 nước)
Tự động phát hiện khi:
- 120 ply (60 moves mỗi bên) không có quân nào bị ăn
- → Kết quả: Hòa

### 6. Perpetual Check (Trường chiếu)
Phát hiện khi:
- Cùng vị trí lặp lại 3 lần
- Và đang trong trạng thái chiếu liên tục
- → Bên gây ra trường chiếu bị xử thua

### 7. Insufficient Material (Không đủ quân)
Phát hiện các trường hợp:
- Chỉ còn 2 vua
- Vua vs Vua + Sĩ
- → Kết quả: Hòa

## Cách sử dụng

### Khởi tạo
```java
GameEndChecker checker = new GameEndChecker();
```

### Sau mỗi nước đi
```java
// 1. Record move để track history
boolean captured = (board[toRow][toCol] != ' ' && board[toRow][toCol] != '\0');
checker.recordMove(board, captured);

// 2. Check game end conditions
GameEndChecker.GameEndResult result = checker.checkGameEnd(board, isRedTurn);

if (result.isGameOver) {
    switch (result.result) {
        case "red":
            // Red wins
            break;
        case "black":
            // Black wins
            break;
        case "draw":
            // Draw
            break;
    }
    // result.termination: "checkmate", "stalemate", "60_move_rule", etc.
    // result.message: Mô tả chi tiết
}
```

### Reset khi bắt đầu game mới
```java
checker.reset();
```

## GameEndResult Structure

```java
public static class GameEndResult {
    public final boolean isGameOver;      // Game đã kết thúc?
    public final String result;           // "red", "black", "draw"
    public final String termination;      // "checkmate", "stalemate", "60_move_rule", "threefold_repetition", "perpetual_check", "insufficient_material"
    public final String message;          // Mô tả chi tiết
}
```

## Integration với MoveValidator

`MoveValidator` đã được cập nhật để:
- ✅ Kiểm tra nước đi không để vua bị chiếu (quan trọng!)
- ✅ Expose các method validation để `GameEndChecker` sử dụng

## Lưu ý quan trọng

1. **Phải record move sau mỗi nước đi** để track history cho repetition detection
2. **Phải check game end sau mỗi nước đi** để phát hiện checkmate/stalemate
3. **Reset checker khi bắt đầu game mới**
4. **Backend cũng cần implement tương tự** để validate server-side

## Next Steps

1. ✅ Frontend: Đã implement đầy đủ
2. ⏳ Backend: Cần implement tương tự trong C++
3. ⏳ UI: Hiển thị "Check!" khi vua bị chiếu
4. ⏳ UI: Highlight các nước đi hợp lệ khi vua bị chiếu

