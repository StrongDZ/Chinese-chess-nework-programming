# Frontend Move Validation

## Overview

Frontend move validation ensures that moves are validated on the client side before being sent to the server. This provides:
- **Better UX**: Immediate feedback to users
- **Reduced server load**: Fewer invalid requests
- **Network efficiency**: Less bandwidth usage

## Usage

### 1. Initialize GameState

```java
import application.game.GameState;

GameState gameState = new GameState();
gameState.initializeBoard(); // Initialize with standard starting position
gameState.setPlayerIsRed(true); // Set player side
```

### 2. Validate Move Before Sending

```java
import application.game.GameState;
import application.game.MoveValidator;
import application.network.SocketClient;
import application.network.MessageType;
import com.google.gson.Gson;

// When user clicks to make a move
int fromRow = ...; // Source row (0-9)
int fromCol = ...; // Source column (0-8)
int toRow = ...;   // Destination row (0-9)
int toCol = ...;   // Destination column (0-8)

// Validate move before sending
if (gameState.isValidMove(fromRow, fromCol, toRow, toCol)) {
    // Move is valid, send to server
    char piece = gameState.getBoard()[fromRow][fromCol];
    
    // Create move payload
    Map<String, Object> movePayload = new HashMap<>();
    movePayload.put("piece", String.valueOf(piece));
    movePayload.put("from", Map.of("row", fromRow, "col", fromCol));
    movePayload.put("to", Map.of("row", toRow, "col", toCol));
    
    // Send to server
    Gson gson = new Gson();
    String json = gson.toJson(movePayload);
    socketClient.send(MessageType.MOVE, json);
    
    // Optimistic update (optional)
    gameState.applyMove(fromRow, fromCol, toRow, toCol);
} else {
    // Show error message to user
    System.out.println("Invalid move!");
    // Or show UI error message
}
```

### 3. Update Board from Server

```java
// When receiving board state from server (e.g., after GAME_START or MOVE)
// Parse board from server response
char[][] serverBoard = parseBoardFromServer(response);
gameState.updateBoard(serverBoard);
gameState.setRedTurn(isRedTurn); // Update turn from server
```

## Validation Rules

The `MoveValidator` checks:

1. **Coordinates**: Valid range (0-9 rows, 0-8 cols)
2. **Piece exists**: Source position has a piece
3. **Different positions**: Source ≠ destination
4. **Piece color**: Valid piece character
5. **Own piece**: Cannot capture own piece
6. **Piece-specific rules**:
   - **King (K/k)**: Must stay in palace, move 1 square horizontally/vertically
   - **Advisor (A/a)**: Must stay in palace, move 1 square diagonally
   - **Elephant (B/b)**: Cannot cross river, move 2 squares diagonally
   - **Knight (N/n)**: L-shape move, check horse leg blocking
   - **Rook (R/r)**: Horizontal/vertical, path must be clear
   - **Cannon (C/c)**: Horizontal/vertical, clear path for move, 1 piece between for capture
   - **Pawn (P/p)**: Forward only before river, forward or sideways after river
7. **Kings facing**: Cannot result in kings facing each other

## Important Notes

- **Server is authoritative**: Frontend validation is for UX only
- **Always trust server response**: Server will validate again and send INVALID_MOVE if needed
- **Optimistic updates**: Can update UI immediately, but revert if server rejects
- **Turn validation**: Check `gameState.isPlayerTurn()` before allowing moves

## Integration Example

```java
public class GameController {
    private GameState gameState;
    private SocketClient socketClient;
    
    public void handlePieceClick(int row, int col) {
        if (selectedPiece == null) {
            // Select piece
            if (gameState.isPlayerTurn() && 
                gameState.getBoard()[row][col] != ' ') {
                selectedPiece = new int[]{row, col};
            }
        } else {
            // Try to move
            int fromRow = selectedPiece[0];
            int fromCol = selectedPiece[1];
            
            if (gameState.isValidMove(fromRow, fromCol, row, col)) {
                sendMoveToServer(fromRow, fromCol, row, col);
                selectedPiece = null;
            } else {
                // Show error
                showError("Invalid move!");
                selectedPiece = null;
            }
        }
    }
    
    private void sendMoveToServer(int fromRow, int fromCol, int toRow, int toCol) {
        // ... create and send move payload
    }
}
```

