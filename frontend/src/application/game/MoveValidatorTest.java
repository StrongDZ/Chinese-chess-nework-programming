package application.game;

/**
 * Comprehensive test suite for MoveValidator
 * Tests all Chinese Chess rules and edge cases
 */
public class MoveValidatorTest {
    
    private static int testsPassed = 0;
    private static int testsFailed = 0;
    
    public static void main(String[] args) {
        System.out.println("=== MoveValidator Test Suite ===\n");
        
        testKingMoves();
        testAdvisorMoves();
        testElephantMoves();
        testKnightMoves();
        testRookMoves();
        testCannonMoves();
        testPawnMoves();
        testKingsFacingEachOther();
        testCaptureOwnPiece();
        testInvalidCoordinates();
        testEmptySource();
        
        System.out.println("\n=== Test Results ===");
        System.out.println("Passed: " + testsPassed);
        System.out.println("Failed: " + testsFailed);
        System.out.println("Total: " + (testsPassed + testsFailed));
        
        if (testsFailed == 0) {
            System.out.println("\n✓ All tests passed!");
        } else {
            System.out.println("\n✗ Some tests failed!");
        }
    }
    
    private static void assertTest(boolean condition, String testName) {
        if (condition) {
            System.out.println("✓ " + testName);
            testsPassed++;
        } else {
            System.out.println("✗ " + testName);
            testsFailed++;
        }
    }
    
    // ==================== KING (Vua) Tests ====================
    private static void testKingMoves() {
        System.out.println("\n--- Testing King Moves ---");
        
        char[][] board = createEmptyBoard();
        // Red king at (0, 4) - center of red palace
        board[0][4] = 'K';
        
        // Valid: Move 1 square horizontally
        assertTest(MoveValidator.isValidMove(board, 0, 4, 0, 3), "King: Move left 1 square");
        assertTest(MoveValidator.isValidMove(board, 0, 4, 0, 5), "King: Move right 1 square");
        
        // Valid: Move 1 square vertically
        assertTest(MoveValidator.isValidMove(board, 0, 4, 1, 4), "King: Move down 1 square");
        
        // Test reverse move (need to set piece first)
        board[1][4] = 'K';
        board[0][4] = ' '; // Clear original position
        assertTest(MoveValidator.isValidMove(board, 1, 4, 0, 4), "King: Move up 1 square");
        
        // Reset for other tests
        board[0][4] = 'K';
        board[1][4] = ' ';
        
        // Invalid: Move 2 squares
        assertTest(!MoveValidator.isValidMove(board, 0, 4, 0, 6), "King: Cannot move 2 squares");
        assertTest(!MoveValidator.isValidMove(board, 0, 4, 2, 4), "King: Cannot move 2 squares vertically");
        
        // Invalid: Move diagonally
        assertTest(!MoveValidator.isValidMove(board, 0, 4, 1, 5), "King: Cannot move diagonally");
        
        // Invalid: Move outside palace
        assertTest(!MoveValidator.isValidMove(board, 0, 4, 0, 2), "King: Cannot move outside palace (left)");
        assertTest(!MoveValidator.isValidMove(board, 0, 4, 3, 4), "King: Cannot move outside palace (down)");
        assertTest(!MoveValidator.isValidMove(board, 2, 4, 3, 4), "King: Cannot move outside palace");
        
        // Black king (create new board to avoid conflicts)
        char[][] board2 = createEmptyBoard();
        board2[9][4] = 'k';
        assertTest(MoveValidator.isValidMove(board2, 9, 4, 9, 3), "Black King: Move left 1 square");
        assertTest(MoveValidator.isValidMove(board2, 9, 4, 8, 4), "Black King: Move up 1 square");
        
        // Test reverse move
        board2[8][4] = 'k';
        board2[9][4] = ' ';
        assertTest(MoveValidator.isValidMove(board2, 8, 4, 9, 4), "Black King: Move down 1 square");
    }
    
    // ==================== ADVISOR (Sĩ) Tests ====================
    private static void testAdvisorMoves() {
        System.out.println("\n--- Testing Advisor Moves ---");
        
        char[][] board = createEmptyBoard();
        board[0][4] = 'A'; // Red advisor
        
        // Valid: Move 1 square diagonally
        assertTest(MoveValidator.isValidMove(board, 0, 4, 1, 3), "Advisor: Move diagonal down-left");
        assertTest(MoveValidator.isValidMove(board, 0, 4, 1, 5), "Advisor: Move diagonal down-right");
        
        // Test reverse move
        board[1][3] = 'A';
        board[0][4] = ' ';
        assertTest(MoveValidator.isValidMove(board, 1, 3, 0, 4), "Advisor: Move diagonal up-right");
        
        // Invalid: Move horizontally or vertically
        assertTest(!MoveValidator.isValidMove(board, 0, 4, 0, 3), "Advisor: Cannot move horizontally");
        assertTest(!MoveValidator.isValidMove(board, 0, 4, 1, 4), "Advisor: Cannot move vertically");
        
        // Invalid: Move outside palace
        assertTest(!MoveValidator.isValidMove(board, 0, 4, 3, 4), "Advisor: Cannot move outside palace");
        assertTest(!MoveValidator.isValidMove(board, 2, 4, 3, 4), "Advisor: Cannot move outside palace");
    }
    
    // ==================== ELEPHANT (Tượng) Tests ====================
    private static void testElephantMoves() {
        System.out.println("\n--- Testing Elephant Moves ---");
        
        char[][] board = createEmptyBoard();
        board[0][2] = 'B'; // Red elephant
        
        // Valid: Move 2 squares diagonally (no blocking)
        assertTest(MoveValidator.isValidMove(board, 0, 2, 2, 0), "Elephant: Move 2 squares diagonal (down-left)");
        assertTest(MoveValidator.isValidMove(board, 0, 2, 2, 4), "Elephant: Move 2 squares diagonal (down-right)");
        
        // Invalid: Blocked by piece
        board[1][1] = 'P'; // Block the path
        assertTest(!MoveValidator.isValidMove(board, 0, 2, 2, 0), "Elephant: Cannot move if blocked");
        
        // Clear blocking piece
        board[1][1] = ' ';
        
        // Invalid: Cross river (Red elephant cannot go to row > 4)
        assertTest(!MoveValidator.isValidMove(board, 0, 2, 5, 7), "Elephant: Cannot cross river");
        assertTest(!MoveValidator.isValidMove(board, 2, 0, 4, 2), "Elephant: Cannot cross river");
        
        // Black elephant
        board[9][2] = 'b';
        assertTest(MoveValidator.isValidMove(board, 9, 2, 7, 0), "Black Elephant: Move 2 squares diagonal");
        assertTest(!MoveValidator.isValidMove(board, 9, 2, 4, 7), "Black Elephant: Cannot cross river");
    }
    
    // ==================== KNIGHT (Mã) Tests ====================
    private static void testKnightMoves() {
        System.out.println("\n--- Testing Knight Moves ---");
        
        char[][] board = createEmptyBoard();
        board[0][1] = 'N'; // Red knight
        
        // Valid: L-shape moves
        assertTest(MoveValidator.isValidMove(board, 0, 1, 2, 0), "Knight: L-shape (2 down, 1 left)");
        assertTest(MoveValidator.isValidMove(board, 0, 1, 2, 2), "Knight: L-shape (2 down, 1 right)");
        assertTest(MoveValidator.isValidMove(board, 0, 1, 1, 3), "Knight: L-shape (1 down, 2 right)");
        
        // Invalid: Horse leg blocked
        board[1][1] = 'P'; // Block horse leg
        assertTest(!MoveValidator.isValidMove(board, 0, 1, 2, 0), "Knight: Cannot move if horse leg blocked (vertical)");
        
        board[1][1] = ' ';
        board[0][2] = 'P'; // Block horse leg horizontally
        assertTest(!MoveValidator.isValidMove(board, 0, 1, 1, 3), "Knight: Cannot move if horse leg blocked (horizontal)");
        
        // Invalid: Not L-shape
        board[0][2] = ' ';
        assertTest(!MoveValidator.isValidMove(board, 0, 1, 2, 1), "Knight: Cannot move straight");
        assertTest(!MoveValidator.isValidMove(board, 0, 1, 1, 1), "Knight: Cannot move 1 square");
    }
    
    // ==================== ROOK (Xe) Tests ====================
    private static void testRookMoves() {
        System.out.println("\n--- Testing Rook Moves ---");
        
        char[][] board = createEmptyBoard();
        board[0][0] = 'R'; // Red rook
        
        // Valid: Move horizontally (clear path)
        assertTest(MoveValidator.isValidMove(board, 0, 0, 0, 8), "Rook: Move horizontally (clear path)");
        assertTest(MoveValidator.isValidMove(board, 0, 0, 0, 4), "Rook: Move horizontally (short)");
        
        // Valid: Move vertically (clear path)
        assertTest(MoveValidator.isValidMove(board, 0, 0, 9, 0), "Rook: Move vertically (clear path)");
        
        // Invalid: Path blocked
        board[0][4] = 'P'; // Block path
        assertTest(!MoveValidator.isValidMove(board, 0, 0, 0, 8), "Rook: Cannot move if path blocked");
        
        // Invalid: Not horizontal or vertical
        board[0][4] = ' ';
        assertTest(!MoveValidator.isValidMove(board, 0, 0, 2, 2), "Rook: Cannot move diagonally");
    }
    
    // ==================== CANNON (Pháo) Tests ====================
    private static void testCannonMoves() {
        System.out.println("\n--- Testing Cannon Moves ---");
        
        char[][] board = createEmptyBoard();
        board[2][1] = 'C'; // Red cannon
        
        // Valid: Move to empty square (clear path)
        assertTest(MoveValidator.isValidMove(board, 2, 1, 2, 8), "Cannon: Move to empty (clear path)");
        assertTest(MoveValidator.isValidMove(board, 2, 1, 9, 1), "Cannon: Move to empty vertically");
        
        // Invalid: Path blocked when moving to empty
        board[2][4] = 'P'; // Block path
        assertTest(!MoveValidator.isValidMove(board, 2, 1, 2, 8), "Cannon: Cannot move to empty if path blocked");
        
        // Valid: Capture with exactly 1 piece between (screen)
        board[2][4] = 'p'; // Black piece as screen
        board[2][8] = 'p'; // Black piece to capture
        assertTest(MoveValidator.isValidMove(board, 2, 1, 2, 8), "Cannon: Can capture with 1 screen");
        
        // Invalid: Capture with 0 screens
        board[2][4] = ' '; // No screen
        assertTest(!MoveValidator.isValidMove(board, 2, 1, 2, 8), "Cannon: Cannot capture without screen");
        
        // Invalid: Capture with 2 screens
        board[2][4] = 'p';
        board[2][6] = 'p'; // Second screen
        assertTest(!MoveValidator.isValidMove(board, 2, 1, 2, 8), "Cannon: Cannot capture with 2 screens");
    }
    
    // ==================== PAWN (Tốt) Tests ====================
    private static void testPawnMoves() {
        System.out.println("\n--- Testing Pawn Moves ---");
        
        char[][] board = createEmptyBoard();
        board[3][0] = 'P'; // Red pawn (before river)
        
        // Valid: Move forward 1 square (before river)
        assertTest(MoveValidator.isValidMove(board, 3, 0, 4, 0), "Pawn: Move forward (before river)");
        
        // Invalid: Move sideways (before river)
        assertTest(!MoveValidator.isValidMove(board, 3, 0, 3, 1), "Pawn: Cannot move sideways before river");
        
        // After crossing river
        board[5][0] = 'P'; // Red pawn (after river, row > 4)
        
        // Valid: Move forward or sideways (after river)
        assertTest(MoveValidator.isValidMove(board, 5, 0, 6, 0), "Pawn: Move forward (after river)");
        assertTest(MoveValidator.isValidMove(board, 5, 0, 5, 1), "Pawn: Move sideways (after river)");
        
        // Invalid: Move backward
        assertTest(!MoveValidator.isValidMove(board, 5, 0, 4, 0), "Pawn: Cannot move backward");
        
        // Black pawn
        board[6][0] = 'p'; // Black pawn (before river, row < 5)
        
        // Valid: Move forward (up, row decreases)
        assertTest(MoveValidator.isValidMove(board, 6, 0, 5, 0), "Black Pawn: Move forward (before river)");
        
        // After crossing river
        board[4][0] = 'p'; // Black pawn (after river, row < 5)
        
        // Valid: Move forward or sideways
        assertTest(MoveValidator.isValidMove(board, 4, 0, 3, 0), "Black Pawn: Move forward (after river)");
        assertTest(MoveValidator.isValidMove(board, 4, 0, 4, 1), "Black Pawn: Move sideways (after river)");
    }
    
    // ==================== KINGS FACING EACH OTHER Tests ====================
    private static void testKingsFacingEachOther() {
        System.out.println("\n--- Testing Kings Facing Each Other ---");
        
        char[][] board = createEmptyBoard();
        board[0][4] = 'K'; // Red king
        board[9][4] = 'k'; // Black king (same column)
        
        // Invalid: Move that causes kings to face each other
        // Move red advisor to block the line (removing blocker)
        board[1][4] = 'A'; // Red advisor (currently blocking)
        // Moving advisor away vertically would cause kings to face
        assertTest(!MoveValidator.isValidMove(board, 1, 4, 2, 4), "Kings: Cannot move if results in facing each other");
        // Moving advisor away horizontally would also cause kings to face
        assertTest(!MoveValidator.isValidMove(board, 1, 4, 1, 3), "Kings: Cannot move blocker away (causes facing)");
        
        // Valid: If there's another piece between, kings don't face
        // Create new board with piece between kings
        char[][] board3 = createEmptyBoard();
        board3[0][4] = 'K';
        board3[9][4] = 'k';
        board3[1][3] = 'R'; // Rook at (1,3) - not between kings
        board3[5][4] = 'P'; // Piece between kings at (5,4) - this blocks
        // Moving rook horizontally is valid because piece at (5,4) still blocks
        assertTest(MoveValidator.isValidMove(board3, 1, 3, 1, 2), "Kings: Can move if piece between kings");
        
        // Valid: Move piece that's not between kings (need piece between kings first)
        char[][] board4 = createEmptyBoard();
        board4[0][4] = 'K';
        board4[9][4] = 'k';
        board4[5][4] = 'P'; // Piece between kings - makes board valid
        board4[1][3] = 'R'; // Rook not between kings (at col 3, kings at col 4)
        // Moving rook horizontally is valid (not affecting kings facing since rook not in same column)
        assertTest(MoveValidator.isValidMove(board4, 1, 3, 1, 2), "Kings: Can move if doesn't cause facing");
        
        // Another test: Move piece in different column (definitely doesn't affect kings)
        char[][] board5 = createEmptyBoard();
        board5[0][4] = 'K';
        board5[9][4] = 'k';
        board5[5][4] = 'P'; // Piece between kings - makes board valid
        board5[2][0] = 'R'; // Rook at completely different position
        assertTest(MoveValidator.isValidMove(board5, 2, 0, 2, 1), "Kings: Can move piece in different column");
    }
    
    // ==================== CAPTURE OWN PIECE Tests ====================
    private static void testCaptureOwnPiece() {
        System.out.println("\n--- Testing Capture Own Piece ---");
        
        char[][] board = createEmptyBoard();
        board[0][0] = 'R'; // Red rook
        board[0][4] = 'P'; // Red pawn (own piece)
        
        // Invalid: Cannot capture own piece
        assertTest(!MoveValidator.isValidMove(board, 0, 0, 0, 4), "Cannot capture own piece (Red)");
        
        // Valid: Can capture opponent piece
        board[0][4] = 'p'; // Black pawn
        assertTest(MoveValidator.isValidMove(board, 0, 0, 0, 4), "Can capture opponent piece");
        
        // Black pieces
        board[9][0] = 'r'; // Black rook
        board[9][4] = 'p'; // Black pawn (own piece)
        assertTest(!MoveValidator.isValidMove(board, 9, 0, 9, 4), "Cannot capture own piece (Black)");
    }
    
    // ==================== INVALID COORDINATES Tests ====================
    private static void testInvalidCoordinates() {
        System.out.println("\n--- Testing Invalid Coordinates ---");
        
        char[][] board = createEmptyBoard();
        board[0][0] = 'R';
        
        // Invalid: Out of bounds
        assertTest(!MoveValidator.isValidMove(board, 0, 0, -1, 0), "Invalid: Negative row");
        assertTest(!MoveValidator.isValidMove(board, 0, 0, 0, -1), "Invalid: Negative col");
        assertTest(!MoveValidator.isValidMove(board, 0, 0, 10, 0), "Invalid: Row >= 10");
        assertTest(!MoveValidator.isValidMove(board, 0, 0, 0, 9), "Invalid: Col >= 9");
    }
    
    // ==================== EMPTY SOURCE Tests ====================
    private static void testEmptySource() {
        System.out.println("\n--- Testing Empty Source ---");
        
        char[][] board = createEmptyBoard();
        
        // Invalid: No piece at source
        assertTest(!MoveValidator.isValidMove(board, 0, 0, 0, 4), "Invalid: No piece at source");
    }
    
    // ==================== HELPER METHODS ====================
    private static char[][] createEmptyBoard() {
        char[][] board = new char[10][9];
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                board[i][j] = ' ';
            }
        }
        return board;
    }
}

