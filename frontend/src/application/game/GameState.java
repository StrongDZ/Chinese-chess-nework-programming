package application.game;

/**
 * Game State Manager for frontend
 * Manages the current board state and provides methods to validate and apply moves
 */
public class GameState {
    
    private char[][] board;
    private boolean isRedTurn;
    private boolean playerIsRed;
    
    public GameState() {
        // Initialize empty board
        board = new char[10][9];
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                board[i][j] = ' ';
            }
        }
        isRedTurn = true;
        playerIsRed = true;
    }
    
    /**
     * Initialize board from FEN string or server state
     * Standard starting position for Chinese Chess
     */
    public void initializeBoard() {
        // Clear board
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                board[i][j] = ' ';
            }
        }
        
        // Standard starting position
        // Red pieces (bottom, rows 0-4) - UPPERCASE
        board[0] = new char[]{'R', 'N', 'B', 'A', 'K', 'A', 'B', 'N', 'R'};
        board[2] = new char[]{' ', 'C', ' ', ' ', ' ', ' ', ' ', 'C', ' '};
        board[3] = new char[]{'P', ' ', 'P', ' ', 'P', ' ', 'P', ' ', 'P'};
        
        // Black pieces (top, rows 5-9) - lowercase
        board[6] = new char[]{'p', ' ', 'p', ' ', 'p', ' ', 'p', ' ', 'p'};
        board[7] = new char[]{' ', 'c', ' ', ' ', ' ', ' ', ' ', 'c', ' '};
        board[9] = new char[]{'r', 'n', 'b', 'a', 'k', 'a', 'b', 'n', 'r'};
    }
    
    /**
     * Update board from server state
     */
    public void updateBoard(char[][] newBoard) {
        if (newBoard != null && newBoard.length == 10 && newBoard[0].length == 9) {
            this.board = newBoard;
        }
    }
    
    /**
     * Get current board
     */
    public char[][] getBoard() {
        return board;
    }
    
    /**
     * Check if a move is valid before sending to server
     * This provides client-side validation for better UX
     */
    public boolean isValidMove(int fromRow, int fromCol, int toRow, int toCol) {
        // Check if it's player's turn
        if (playerIsRed != isRedTurn) {
            return false; // Not player's turn
        }
        
        // Check if piece belongs to player
        char piece = board[fromRow][fromCol];
        if (piece == ' ' || piece == '\0') {
            return false; // No piece at source
        }
        
        boolean pieceIsRed = Character.isUpperCase(piece) && Character.isLetter(piece);
        if (playerIsRed && !pieceIsRed) {
            return false; // Player is Red but trying to move Black piece
        }
        if (!playerIsRed && pieceIsRed) {
            return false; // Player is Black but trying to move Red piece
        }
        
        // Validate move according to Chinese Chess rules
        return MoveValidator.isValidMove(board, fromRow, fromCol, toRow, toCol);
    }
    
    /**
     * Apply move locally (for preview/optimistic update)
     * Note: Server will validate and send confirmation
     */
    public void applyMove(int fromRow, int fromCol, int toRow, int toCol) {
        if (fromRow >= 0 && fromRow < 10 && fromCol >= 0 && fromCol < 9 &&
            toRow >= 0 && toRow < 10 && toCol >= 0 && toCol < 9) {
            char piece = board[fromRow][fromCol];
            board[toRow][toCol] = piece;
            board[fromRow][fromCol] = ' ';
            isRedTurn = !isRedTurn; // Switch turn
        }
    }
    
    /**
     * Set player side (Red or Black)
     */
    public void setPlayerIsRed(boolean isRed) {
        this.playerIsRed = isRed;
    }
    
    /**
     * Set current turn
     */
    public void setRedTurn(boolean isRedTurn) {
        this.isRedTurn = isRedTurn;
    }
    
    /**
     * Check if it's player's turn
     */
    public boolean isPlayerTurn() {
        return playerIsRed == isRedTurn;
    }
}

