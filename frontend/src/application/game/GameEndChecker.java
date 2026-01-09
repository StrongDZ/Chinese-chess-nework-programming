package application.game;

import java.util.*;

/**
 * Game End Condition Checker for Chinese Chess (Xiangqi)
 * Implements full rules for check, checkmate, stalemate, draws, etc.
 */
public class GameEndChecker {
    
    private static final int BOARD_ROWS = 10;
    private static final int BOARD_COLS = 9;
    
    // History for detecting repetitions
    private List<String> positionHistory = new ArrayList<>();
    private Map<String, Integer> positionCount = new HashMap<>();
    private int movesWithoutCapture = 0;
    private static final int MAX_MOVES_WITHOUT_CAPTURE = 120; // 60 moves per side
    
    /**
     * Reset game end checker state
     */
    public void reset() {
        positionHistory.clear();
        positionCount.clear();
        movesWithoutCapture = 0;
    }
    
    /**
     * Record a move and update history
     * @param board Current board state
     * @param captured Whether a piece was captured
     */
    public void recordMove(char[][] board, boolean captured) {
        String positionHash = boardToHash(board);
        positionHistory.add(positionHash);
        positionCount.put(positionHash, positionCount.getOrDefault(positionHash, 0) + 1);
        
        if (captured) {
            movesWithoutCapture = 0;
        } else {
            movesWithoutCapture++;
        }
    }
    
    /**
     * Check all game end conditions
     * @param board Current board state
     * @param isRedTurn Whether it's red's turn
     * @return GameEndResult with status and reason
     */
    public GameEndResult checkGameEnd(char[][] board, boolean isRedTurn) {
        // 0. Check if King is captured (highest priority)
        boolean hasRedKing = false;
        boolean hasBlackKing = false;
        
        for (int row = 0; row < BOARD_ROWS; row++) {
            for (int col = 0; col < BOARD_COLS; col++) {
                char piece = board[row][col];
                if (piece == 'K') {
                    hasRedKing = true;
                } else if (piece == 'k') {
                    hasBlackKing = true;
                }
            }
        }
        
        // If a King is missing, game ends immediately
        if (!hasRedKing) {
            return new GameEndResult(true, "black", "king_captured", 
                "Red King was captured");
        }
        if (!hasBlackKing) {
            return new GameEndResult(true, "red", "king_captured", 
                "Black King was captured");
        }
        
        // 1. Check for check
        boolean redInCheck = isKingInCheck(board, true);
        boolean blackInCheck = isKingInCheck(board, false);
        
        // 2. Get all legal moves for current player
        List<int[]> legalMoves = getAllLegalMoves(board, isRedTurn);
        
        // 3. Check checkmate
        if ((isRedTurn && redInCheck) || (!isRedTurn && blackInCheck)) {
            if (legalMoves.isEmpty()) {
                // Checkmate - current player loses
                String winner = isRedTurn ? "black" : "red";
                return new GameEndResult(true, winner, "checkmate", 
                    (isRedTurn ? "Red" : "Black") + " is checkmated");
            }
        }
        
        // 4. Check stalemate (no check but no legal moves)
        if (legalMoves.isEmpty() && !redInCheck && !blackInCheck) {
            return new GameEndResult(true, "draw", "stalemate", 
                (isRedTurn ? "Red" : "Black") + " has no legal moves");
        }
        
        // 5. Check 60-move rule (120 ply without capture)
        if (movesWithoutCapture >= MAX_MOVES_WITHOUT_CAPTURE) {
            return new GameEndResult(true, "draw", "60_move_rule", 
                "60 moves without capture");
        }
        
        // 6. Check threefold repetition
        String currentPosition = boardToHash(board);
        int repetitionCount = positionCount.getOrDefault(currentPosition, 0);
        if (repetitionCount >= 3) {
            // Check if it's perpetual check
            if (isPerpetualCheck()) {
                // Perpetual check - the side causing it loses
                String loser = isRedTurn ? "red" : "black";
                String winner = isRedTurn ? "black" : "red";
                return new GameEndResult(true, winner, "perpetual_check", 
                    loser + " caused perpetual check");
            } else {
                // Normal repetition - draw
                return new GameEndResult(true, "draw", "threefold_repetition", 
                    "Same position repeated 3 times");
            }
        }
        
        // 7. Check insufficient material
        if (isInsufficientMaterial(board)) {
            return new GameEndResult(true, "draw", "insufficient_material", 
                "Insufficient material to checkmate");
        }
        
        // Game continues
        return new GameEndResult(false, null, null, null);
    }
    
    /**
     * Check if king is in check
     * @param board Current board state
     * @param isRedKing Whether checking red king (true) or black king (false)
     * @return true if king is in check
     */
    public static boolean isKingInCheck(char[][] board, boolean isRedKing) {
        // Find king position
        int kingRow = -1;
        int kingCol = -1;
        char kingChar = isRedKing ? 'K' : 'k';
        
        for (int row = 0; row < BOARD_ROWS; row++) {
            for (int col = 0; col < BOARD_COLS; col++) {
                if (board[row][col] == kingChar) {
                    kingRow = row;
                    kingCol = col;
                    break;
                }
            }
            if (kingRow >= 0) break;
        }
        
        if (kingRow < 0) {
            return false; // King not found (shouldn't happen)
        }
        
        // Check if any opponent piece can attack the king
        boolean opponentIsRed = !isRedKing;
        
        for (int row = 0; row < BOARD_ROWS; row++) {
            for (int col = 0; col < BOARD_COLS; col++) {
                char piece = board[row][col];
                if (piece == ' ' || piece == '\0') continue;
                
                boolean pieceIsRed = Character.isUpperCase(piece) && Character.isLetter(piece);
                boolean pieceIsBlack = Character.isLowerCase(piece) && Character.isLetter(piece);
                
                // Check if this is an opponent piece
                if ((opponentIsRed && !pieceIsRed) || (!opponentIsRed && !pieceIsBlack)) {
                    continue; // Not opponent piece
                }
                
                // Check if this piece can attack the king
                if (canAttackSquare(board, row, col, kingRow, kingCol, opponentIsRed)) {
                    return true; // King is in check
                }
            }
        }
        
        // Also check for flying general (kings facing each other)
        return MoveValidator.kingsFaceEachOther(board);
    }
    
    /**
     * Check if a piece can attack a specific square
     * @param board Current board state
     * @param fromRow Piece row
     * @param fromCol Piece column
     * @param toRow Target row
     * @param toCol Target column
     * @param isRedPiece Whether the piece is red
     * @return true if piece can attack the square
     */
    private static boolean canAttackSquare(char[][] board, int fromRow, int fromCol, 
                                          int toRow, int toCol, boolean isRedPiece) {
        char piece = board[fromRow][fromCol];
        char pieceUpper = Character.toUpperCase(piece);
        
        // Create a copy of board to test move
        char[][] testBoard = copyBoard(board);
        
        // For cannon, we need to check if target square has a piece (capture)
        // For other pieces, temporarily clear target
        char originalTarget = testBoard[toRow][toCol];
        if (pieceUpper != 'C') {
            testBoard[toRow][toCol] = ' '; // Temporarily clear target for non-cannon
        }
        
        boolean canAttack = false;
        
        switch (pieceUpper) {
            case 'K':
                canAttack = MoveValidator.isValidKingMove(testBoard, fromRow, fromCol, toRow, toCol, isRedPiece);
                break;
            case 'A':
                canAttack = MoveValidator.isValidAdvisorMove(testBoard, fromRow, fromCol, toRow, toCol, isRedPiece);
                break;
            case 'B':
                canAttack = MoveValidator.isValidElephantMove(testBoard, fromRow, fromCol, toRow, toCol, isRedPiece);
                break;
            case 'N':
                canAttack = MoveValidator.isValidKnightMove(testBoard, fromRow, fromCol, toRow, toCol, isRedPiece);
                break;
            case 'R':
                canAttack = MoveValidator.isValidRookMove(testBoard, fromRow, fromCol, toRow, toCol, isRedPiece);
                break;
            case 'C':
                // Cannon: can attack if there's exactly one piece between AND target has a piece
                // For check detection, we check if cannon can capture the king
                if (originalTarget != ' ' && originalTarget != '\0') {
                    canAttack = MoveValidator.isValidCannonMove(board, fromRow, fromCol, toRow, toCol, isRedPiece);
                }
                break;
            case 'P':
                canAttack = MoveValidator.isValidPawnMove(testBoard, fromRow, fromCol, toRow, toCol, isRedPiece);
                break;
        }
        
        return canAttack;
    }
    
    /**
     * Get all legal moves for current player
     * Legal moves = valid moves that don't leave own king in check
     * @param board Current board state
     * @param isRedTurn Whether it's red's turn
     * @return List of legal moves [fromRow, fromCol, toRow, toCol]
     */
    public static List<int[]> getAllLegalMoves(char[][] board, boolean isRedTurn) {
        List<int[]> legalMoves = new ArrayList<>();
        
        // Find all pieces of current player
        for (int fromRow = 0; fromRow < BOARD_ROWS; fromRow++) {
            for (int fromCol = 0; fromCol < BOARD_COLS; fromCol++) {
                char piece = board[fromRow][fromCol];
                if (piece == ' ' || piece == '\0') continue;
                
                boolean pieceIsRed = Character.isUpperCase(piece) && Character.isLetter(piece);
                boolean pieceIsBlack = Character.isLowerCase(piece) && Character.isLetter(piece);
                
                // Check if this is current player's piece
                if ((isRedTurn && !pieceIsRed) || (!isRedTurn && !pieceIsBlack)) {
                    continue; // Not current player's piece
                }
                
                // Get all valid moves for this piece
                List<int[]> validMoves = MoveValidator.getValidMoves(board, fromRow, fromCol);
                
                // Filter moves that don't leave own king in check
                for (int[] move : validMoves) {
                    int toRow = move[0];
                    int toCol = move[1];
                    
                    // Simulate move
                    char[][] tempBoard = copyBoard(board);
                    tempBoard[toRow][toCol] = piece;
                    tempBoard[fromRow][fromCol] = ' ';
                    
                    // Check if own king is in check after this move
                    if (!isKingInCheck(tempBoard, isRedTurn)) {
                        legalMoves.add(new int[]{fromRow, fromCol, toRow, toCol});
                    }
                }
            }
        }
        
        return legalMoves;
    }
    
    /**
     * Check if current position is perpetual check
     * (Simplified: check if last 3 positions all had check)
     */
    private boolean isPerpetualCheck() {
        // This is a simplified check - in full implementation, 
        // we'd track which side was checking in each position
        // For now, if same position repeated 3 times and it's a checking position,
        // consider it perpetual check
        if (positionHistory.size() < 3) {
            return false;
        }
        
        // Check last 3 positions
        String lastPos = positionHistory.get(positionHistory.size() - 1);
        if (positionCount.getOrDefault(lastPos, 0) >= 3) {
            // Same position repeated - could be perpetual check
            // In full implementation, we'd check if this position always had check
            return true; // Simplified: assume it's perpetual check if repeated
        }
        
        return false;
    }
    
    /**
     * Check if there's insufficient material to checkmate
     * Examples: Only 2 kings, king vs king + advisor, etc.
     */
    private static boolean isInsufficientMaterial(char[][] board) {
        int redPieces = 0;
        int blackPieces = 0;
        boolean hasRedKing = false;
        boolean hasBlackKing = false;
        
        // Count pieces
        for (int row = 0; row < BOARD_ROWS; row++) {
            for (int col = 0; col < BOARD_COLS; col++) {
                char piece = board[row][col];
                if (piece == ' ' || piece == '\0') continue;
                
                char pieceUpper = Character.toUpperCase(piece);
                boolean isRed = Character.isUpperCase(piece) && Character.isLetter(piece);
                
                if (pieceUpper == 'K') {
                    if (isRed) hasRedKing = true;
                    else hasBlackKing = true;
                } else {
                    if (isRed) redPieces++;
                    else blackPieces++;
                }
            }
        }
        
        // Both sides must have kings
        if (!hasRedKing || !hasBlackKing) {
            return false; // Game should have ended already
        }
        
        // Only 2 kings - draw
        if (redPieces == 0 && blackPieces == 0) {
            return true;
        }
        
        // King vs King + Advisor (usually insufficient)
        if ((redPieces == 0 && blackPieces == 1) || (redPieces == 1 && blackPieces == 0)) {
            // Check if the single piece is an advisor
            for (int row = 0; row < BOARD_ROWS; row++) {
                for (int col = 0; col < BOARD_COLS; col++) {
                    char piece = board[row][col];
                    if (piece == ' ' || piece == '\0') continue;
                    char pieceUpper = Character.toUpperCase(piece);
                    if (pieceUpper == 'A' || pieceUpper == 'a') {
                        return true; // King vs King + Advisor - insufficient
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Convert board to hash string for repetition detection
     */
    private static String boardToHash(char[][] board) {
        StringBuilder sb = new StringBuilder();
        for (int row = 0; row < BOARD_ROWS; row++) {
            for (int col = 0; col < BOARD_COLS; col++) {
                char c = board[row][col];
                sb.append(c == ' ' || c == '\0' ? '.' : c);
            }
            sb.append('/');
        }
        return sb.toString();
    }
    
    /**
     * Copy board array
     */
    private static char[][] copyBoard(char[][] board) {
        char[][] copy = new char[BOARD_ROWS][BOARD_COLS];
        for (int i = 0; i < BOARD_ROWS; i++) {
            System.arraycopy(board[i], 0, copy[i], 0, BOARD_COLS);
        }
        return copy;
    }
    
    /**
     * Result of game end check
     */
    public static class GameEndResult {
        public final boolean isGameOver;
        public final String result; // "red", "black", "draw"
        public final String termination; // "checkmate", "stalemate", "60_move_rule", etc.
        public final String message;
        
        public GameEndResult(boolean isGameOver, String result, String termination, String message) {
            this.isGameOver = isGameOver;
            this.result = result;
            this.termination = termination;
            this.message = message;
        }
    }
}

