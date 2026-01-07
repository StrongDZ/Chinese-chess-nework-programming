package application.game;

/**
 * Move Validator for Chinese Chess (Xiangqi)
 * Validates moves according to Chinese Chess rules before sending to server.
 * This provides client-side validation for better UX and reduces invalid requests.
 */
public class MoveValidator {
    
    // Board dimensions
    private static final int BOARD_ROWS = 10;
    private static final int BOARD_COLS = 9;
    
    /**
     * Check if a move is valid according to Chinese Chess rules
     * @param board The current board state (10x9 array, null or ' ' for empty)
     * @param fromRow Source row (0-9)
     * @param fromCol Source column (0-8)
     * @param toRow Destination row (0-9)
     * @param toCol Destination column (0-8)
     * @return true if the move is valid, false otherwise
     */
    public static boolean isValidMove(char[][] board, int fromRow, int fromCol, int toRow, int toCol) {
        // Validate coordinates
        if (fromRow < 0 || fromRow >= BOARD_ROWS || fromCol < 0 || fromCol >= BOARD_COLS ||
            toRow < 0 || toRow >= BOARD_ROWS || toCol < 0 || toCol >= BOARD_COLS) {
            return false;
        }
        
        // Check if source has a piece
        if (board == null || board[fromRow][fromCol] == ' ' || board[fromRow][fromCol] == '\0') {
            return false; // No piece at source
        }
        
        // Check if destination is different from source
        if (fromRow == toRow && fromCol == toCol) {
            return false; // Same position
        }
        
        char piece = board[fromRow][fromCol];
        
        // Determine piece color
        boolean isRed = isRedPiece(piece);
        boolean isBlack = isBlackPiece(piece);
        
        if (!isRed && !isBlack) {
            return false; // Invalid piece character
        }
        
        // Check if capturing own piece
        char captured = board[toRow][toCol];
        if (captured != ' ' && captured != '\0') {
            boolean capturedIsRed = isRedPiece(captured);
            boolean capturedIsBlack = isBlackPiece(captured);
            
            if ((isRed && capturedIsRed) || (isBlack && capturedIsBlack)) {
                return false; // Cannot capture own piece
            }
        }
        
        // Validate move based on piece type
        boolean validMove = false;
        char pieceUpper = Character.toUpperCase(piece);
        
        switch (pieceUpper) {
            case 'K':
                validMove = isValidKingMove(board, fromRow, fromCol, toRow, toCol, isRed);
                break;
            case 'A':
                validMove = isValidAdvisorMove(board, fromRow, fromCol, toRow, toCol, isRed);
                break;
            case 'B':
                validMove = isValidElephantMove(board, fromRow, fromCol, toRow, toCol, isRed);
                break;
            case 'N':
                validMove = isValidKnightMove(board, fromRow, fromCol, toRow, toCol, isRed);
                break;
            case 'R':
                validMove = isValidRookMove(board, fromRow, fromCol, toRow, toCol, isRed);
                break;
            case 'C':
                validMove = isValidCannonMove(board, fromRow, fromCol, toRow, toCol, isRed);
                break;
            case 'P':
                validMove = isValidPawnMove(board, fromRow, fromCol, toRow, toCol, isRed);
                break;
            default:
                return false; // Unknown piece type
        }
        
        if (!validMove) {
            return false;
        }
        
        // Check if move would result in kings facing each other
        // Simulate the move temporarily
        char[][] tempBoard = new char[BOARD_ROWS][BOARD_COLS];
        for (int i = 0; i < BOARD_ROWS; i++) {
            System.arraycopy(board[i], 0, tempBoard[i], 0, BOARD_COLS);
        }
        
        // Apply move temporarily
        tempBoard[toRow][toCol] = piece;
        tempBoard[fromRow][fromCol] = ' ';
        
        // Check if kings face each other after this move
        if (kingsFaceEachOther(tempBoard)) {
            return false; // Move would result in illegal king position
        }
        
        return true;
    }
    
    // Helper methods
    
    private static boolean isInRedPalace(int row, int col) {
        return (row >= 0 && row <= 2 && col >= 3 && col <= 5);
    }
    
    private static boolean isInBlackPalace(int row, int col) {
        return (row >= 7 && row <= 9 && col >= 3 && col <= 5);
    }
    
    private static boolean isInPalace(int row, int col) {
        return isInRedPalace(row, col) || isInBlackPalace(row, col);
    }
    
    private static boolean redCrossedRiver(int row) {
        return row > 4;
    }
    
    private static boolean blackCrossedRiver(int row) {
        return row < 5;
    }
    
    private static boolean isRedPiece(char piece) {
        return Character.isUpperCase(piece) && Character.isLetter(piece);
    }
    
    private static boolean isBlackPiece(char piece) {
        return Character.isLowerCase(piece) && Character.isLetter(piece);
    }
    
    private static int countPiecesBetween(char[][] board, int fromRow, int fromCol, int toRow, int toCol) {
        int count = 0;
        
        if (fromRow == toRow) {
            // Horizontal line
            int startCol = Math.min(fromCol, toCol);
            int endCol = Math.max(fromCol, toCol);
            for (int col = startCol + 1; col < endCol; col++) {
                if (board[fromRow][col] != ' ' && board[fromRow][col] != '\0') {
                    count++;
                }
            }
        } else if (fromCol == toCol) {
            // Vertical line
            int startRow = Math.min(fromRow, toRow);
            int endRow = Math.max(fromRow, toRow);
            for (int row = startRow + 1; row < endRow; row++) {
                if (board[row][fromCol] != ' ' && board[row][fromCol] != '\0') {
                    count++;
                }
            }
        }
        
        return count;
    }
    
    private static boolean kingsFaceEachOther(char[][] board) {
        // Find red king (K) and black king (k)
        int redKingRow = -1;
        int redKingCol = -1;
        int blackKingRow = -1;
        int blackKingCol = -1;
        
        for (int row = 0; row < BOARD_ROWS; row++) {
            for (int col = 0; col < BOARD_COLS; col++) {
                if (board[row][col] == 'K') {
                    redKingRow = row;
                    redKingCol = col;
                } else if (board[row][col] == 'k') {
                    blackKingRow = row;
                    blackKingCol = col;
                }
            }
        }
        
        // If both kings found and in same column
        if (redKingRow >= 0 && blackKingRow >= 0 && redKingCol == blackKingCol) {
            // Check if no pieces between them
            int startRow = Math.min(redKingRow, blackKingRow);
            int endRow = Math.max(redKingRow, blackKingRow);
            for (int row = startRow + 1; row < endRow; row++) {
                if (board[row][redKingCol] != ' ' && board[row][redKingCol] != '\0') {
                    return false; // Piece blocking, kings don't face each other
                }
            }
            return true; // Kings face each other directly
        }
        
        return false;
    }
    
    // Piece-specific move validators
    
    private static boolean isValidKingMove(char[][] board, int fromRow, int fromCol, int toRow, int toCol, boolean isRed) {
        // King must stay in palace
        if (!isInPalace(toRow, toCol)) {
            return false;
        }
        
        // King can only move 1 square horizontally or vertically
        int rowDiff = Math.abs(toRow - fromRow);
        int colDiff = Math.abs(toCol - fromCol);
        
        return (rowDiff == 1 && colDiff == 0) || (rowDiff == 0 && colDiff == 1);
    }
    
    private static boolean isValidAdvisorMove(char[][] board, int fromRow, int fromCol, int toRow, int toCol, boolean isRed) {
        // Advisor must stay in palace
        if (!isInPalace(toRow, toCol)) {
            return false;
        }
        
        // Advisor can only move 1 square diagonally
        int rowDiff = Math.abs(toRow - fromRow);
        int colDiff = Math.abs(toCol - fromCol);
        
        return rowDiff == 1 && colDiff == 1;
    }
    
    private static boolean isValidElephantMove(char[][] board, int fromRow, int fromCol, int toRow, int toCol, boolean isRed) {
        // Elephant cannot cross the river
        if (isRed && toRow > 4) {
            return false; // Red elephant cannot cross river
        }
        if (!isRed && toRow < 5) {
            return false; // Black elephant cannot cross river
        }
        
        // Elephant moves 2 squares diagonally
        int rowDiff = Math.abs(toRow - fromRow);
        int colDiff = Math.abs(toCol - fromCol);
        
        if (rowDiff == 2 && colDiff == 2) {
            // Check if there's a piece blocking the diagonal path
            int midRow = (fromRow + toRow) / 2;
            int midCol = (fromCol + toCol) / 2;
            
            if (board[midRow][midCol] != ' ' && board[midRow][midCol] != '\0') {
                return false; // Blocked
            }
            
            return true;
        }
        
        return false;
    }
    
    private static boolean isValidKnightMove(char[][] board, int fromRow, int fromCol, int toRow, int toCol, boolean isRed) {
        // Knight moves in L-shape: 2 squares in one direction, then 1 square perpendicular
        int rowDiff = Math.abs(toRow - fromRow);
        int colDiff = Math.abs(toCol - fromCol);
        
        // Must be L-shape: (2,1) or (1,2)
        if (!((rowDiff == 2 && colDiff == 1) || (rowDiff == 1 && colDiff == 2))) {
            return false;
        }
        
        // Check for blocking piece (horse leg)
        int blockRow, blockCol;
        if (rowDiff == 2) {
            // Moving 2 rows, block is 1 row away
            blockRow = fromRow + (toRow > fromRow ? 1 : -1);
            blockCol = fromCol;
        } else {
            // Moving 2 cols, block is 1 col away
            blockRow = fromRow;
            blockCol = fromCol + (toCol > fromCol ? 1 : -1);
        }
        
        if (board[blockRow][blockCol] != ' ' && board[blockRow][blockCol] != '\0') {
            return false; // Blocked by piece (horse leg blocked)
        }
        
        return true;
    }
    
    private static boolean isValidRookMove(char[][] board, int fromRow, int fromCol, int toRow, int toCol, boolean isRed) {
        // Rook moves horizontally or vertically
        if (fromRow != toRow && fromCol != toCol) {
            return false; // Not horizontal or vertical
        }
        
        // Check if path is clear
        int piecesBetween = countPiecesBetween(board, fromRow, fromCol, toRow, toCol);
        return piecesBetween == 0; // Path must be clear
    }
    
    private static boolean isValidCannonMove(char[][] board, int fromRow, int fromCol, int toRow, int toCol, boolean isRed) {
        // Cannon moves horizontally or vertically
        if (fromRow != toRow && fromCol != toCol) {
            return false; // Not horizontal or vertical
        }
        
        char targetPiece = board[toRow][toCol];
        int piecesBetween = countPiecesBetween(board, fromRow, fromCol, toRow, toCol);
        
        if (targetPiece == ' ' || targetPiece == '\0') {
            // Moving to empty square: path must be clear
            return piecesBetween == 0;
        } else {
            // Capturing: must have exactly one piece between (screen)
            return piecesBetween == 1;
        }
    }
    
    private static boolean isValidPawnMove(char[][] board, int fromRow, int fromCol, int toRow, int toCol, boolean isRed) {
        int rowDiff = toRow - fromRow;
        int colDiff = Math.abs(toCol - fromCol);
        
        if (isRed) {
            // Red pawn (P) starts at bottom (row 0-4), moves forward (toward black) = row increases
            if (redCrossedRiver(fromRow)) {
                // After crossing river (row > 4): can move forward or sideways
                if (rowDiff == 1 && colDiff == 0) {
                    return true; // Forward (row increases)
                }
                if (rowDiff == 0 && colDiff == 1) {
                    return true; // Sideways
                }
            } else {
                // Before crossing river (row <= 4): can only move forward
                if (rowDiff == 1 && colDiff == 0) {
                    return true; // Forward (row increases)
                }
            }
        } else {
            // Black pawn (p) starts at top (row 5-9), moves forward (toward red) = row decreases
            if (blackCrossedRiver(fromRow)) {
                // After crossing river (row < 5): can move forward or sideways
                if (rowDiff == -1 && colDiff == 0) {
                    return true; // Forward (row decreases)
                }
                if (rowDiff == 0 && colDiff == 1) {
                    return true; // Sideways
                }
            } else {
                // Before crossing river (row >= 5): can only move forward
                if (rowDiff == -1 && colDiff == 0) {
                    return true; // Forward (row decreases)
                }
            }
        }
        
        return false;
    }
    
    /**
     * Get all valid moves for a piece at the given position
     * @param board The current board state
     * @param fromRow Source row (0-9)
     * @param fromCol Source column (0-8)
     * @return List of valid destination coordinates [row, col]
     */
    public static java.util.List<int[]> getValidMoves(char[][] board, int fromRow, int fromCol) {
        java.util.List<int[]> validMoves = new java.util.ArrayList<>();
        
        // Validate source position
        if (fromRow < 0 || fromRow >= BOARD_ROWS || fromCol < 0 || fromCol >= BOARD_COLS) {
            return validMoves;
        }
        
        // Check if source has a piece
        if (board == null || board[fromRow][fromCol] == ' ' || board[fromRow][fromCol] == '\0') {
            return validMoves;
        }
        
        // Check all possible destinations
        for (int toRow = 0; toRow < BOARD_ROWS; toRow++) {
            for (int toCol = 0; toCol < BOARD_COLS; toCol++) {
                // Skip if same position
                if (fromRow == toRow && fromCol == toCol) {
                    continue;
                }
                
                // Check if move is valid
                if (isValidMove(board, fromRow, fromCol, toRow, toCol)) {
                    validMoves.add(new int[]{toRow, toCol});
                }
            }
        }
        
        return validMoves;
    }
}

