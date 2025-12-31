"""
Board validation functions for Chinese Chess
Validates moves for all piece types
"""

from typing import List


def is_in_red_palace(row: int, col: int) -> bool:
    """Check if position is in red palace (bottom, rows 0-2, cols 3-5)"""
    return 0 <= row <= 2 and 3 <= col <= 5


def is_in_black_palace(row: int, col: int) -> bool:
    """Check if position is in black palace (top, rows 7-9, cols 3-5)"""
    return 7 <= row <= 9 and 3 <= col <= 5


def is_in_palace(row: int, col: int) -> bool:
    """Check if position is in palace (either red or black)"""
    return is_in_red_palace(row, col) or is_in_black_palace(row, col)


def red_crossed_river(row: int) -> bool:
    """Check if red piece has crossed the river (row > 4)"""
    return row > 4


def black_crossed_river(row: int) -> bool:
    """Check if black piece has crossed the river (row < 5)"""
    return row < 5


def count_pieces_between(board: List[List[str]], from_row: int, from_col: int,
                        to_row: int, to_col: int) -> int:
    """Count pieces between two positions (horizontal or vertical line)"""
    count = 0
    
    if from_row == to_row:
        # Horizontal line
        start_col = min(from_col, to_col)
        end_col = max(from_col, to_col)
        for col in range(start_col + 1, end_col):
            if board[from_row][col] != ' ':
                count += 1
    elif from_col == to_col:
        # Vertical line
        start_row = min(from_row, to_row)
        end_row = max(from_row, to_row)
        for row in range(start_row + 1, end_row):
            if board[row][from_col] != ' ':
                count += 1
    
    return count


def kings_face_each_other(board: List[List[str]]) -> bool:
    """Check if kings face each other directly (illegal position)"""
    # Find red king (K) and black king (k)
    red_king_row = -1
    red_king_col = -1
    black_king_row = -1
    black_king_col = -1
    
    for row in range(10):
        for col in range(9):
            if board[row][col] == 'K':
                red_king_row = row
                red_king_col = col
            elif board[row][col] == 'k':
                black_king_row = row
                black_king_col = col
    
    # If both kings found and in same column
    if (red_king_row >= 0 and black_king_row >= 0 and
            red_king_col == black_king_col):
        # Check if no pieces between them
        start_row = min(red_king_row, black_king_row)
        end_row = max(red_king_row, black_king_row)
        for row in range(start_row + 1, end_row):
            if board[row][red_king_col] != ' ':
                return False  # Piece blocking, kings don't face each other
        return True  # Kings face each other directly
    
    return False


def is_red_piece(piece: str) -> bool:
    """Check if piece is red (uppercase)"""
    return piece.isupper() and piece.isalpha()


def is_black_piece(piece: str) -> bool:
    """Check if piece is black (lowercase)"""
    return piece.islower() and piece.isalpha()


def isValidKingMove(board: List[List[str]], from_row: int, from_col: int,
                    to_row: int, to_col: int, is_red: bool) -> bool:
    """Validate King (K/k) move"""
    # King must stay in palace
    if not is_in_palace(to_row, to_col):
        return False
    
    # King can only move 1 square horizontally or vertically
    row_diff = abs(to_row - from_row)
    col_diff = abs(to_col - from_col)
    
    if (row_diff == 1 and col_diff == 0) or (row_diff == 0 and col_diff == 1):
        return True
    
    return False


def isValidAdvisorMove(board: List[List[str]], from_row: int, from_col: int,
                      to_row: int, to_col: int, is_red: bool) -> bool:
    """Validate Advisor (A/a) move"""
    # Advisor must stay in palace
    if not is_in_palace(to_row, to_col):
        return False
    
    # Advisor can only move 1 square diagonally
    row_diff = abs(to_row - from_row)
    col_diff = abs(to_col - from_col)
    
    if row_diff == 1 and col_diff == 1:
        return True
    
    return False


def isValidElephantMove(board: List[List[str]], from_row: int, from_col: int,
                        to_row: int, to_col: int, is_red: bool) -> bool:
    """Validate Elephant (B/b) move"""
    # Elephant cannot cross the river
    if is_red and to_row > 4:
        return False  # Red elephant cannot cross river
    if not is_red and to_row < 5:
        return False  # Black elephant cannot cross river
    
    # Elephant moves 2 squares diagonally
    row_diff = abs(to_row - from_row)
    col_diff = abs(to_col - from_col)
    
    if row_diff == 2 and col_diff == 2:
        # Check if there's a piece blocking the diagonal path
        mid_row = (from_row + to_row) // 2
        mid_col = (from_col + to_col) // 2
        
        if board[mid_row][mid_col] != ' ':
            return False  # Blocked
        
        return True
    
    return False


def isValidKnightMove(board: List[List[str]], from_row: int, from_col: int,
                     to_row: int, to_col: int, is_red: bool) -> bool:
    """Validate Knight (N/n) move"""
    # Knight moves in L-shape: 2 squares in one direction, then 1 square perpendicular
    row_diff = abs(to_row - from_row)
    col_diff = abs(to_col - from_col)
    
    # Must be L-shape: (2,1) or (1,2)
    if not ((row_diff == 2 and col_diff == 1) or (row_diff == 1 and col_diff == 2)):
        return False
    
    # Check for blocking piece (horse leg)
    if row_diff == 2:
        # Moving 2 rows, block is 1 row away
        block_row = from_row + (1 if to_row > from_row else -1)
        block_col = from_col
    else:
        # Moving 2 cols, block is 1 col away
        block_row = from_row
        block_col = from_col + (1 if to_col > from_col else -1)
    
    if board[block_row][block_col] != ' ':
        return False  # Blocked by piece (horse leg blocked)
    
    return True


def isValidRookMove(board: List[List[str]], from_row: int, from_col: int,
                   to_row: int, to_col: int, is_red: bool) -> bool:
    """Validate Rook (R/r) move"""
    # Rook moves horizontally or vertically
    if from_row != to_row and from_col != to_col:
        return False  # Not horizontal or vertical
    
    # Check if path is clear
    pieces_between = count_pieces_between(board, from_row, from_col, to_row, to_col)
    if pieces_between > 0:
        return False  # Path blocked
    
    return True


def isValidCannonMove(board: List[List[str]], from_row: int, from_col: int,
                     to_row: int, to_col: int, is_red: bool) -> bool:
    """Validate Cannon (C/c) move"""
    # Cannon moves horizontally or vertically
    if from_row != to_row and from_col != to_col:
        return False  # Not horizontal or vertical
    
    target_piece = board[to_row][to_col]
    pieces_between = count_pieces_between(board, from_row, from_col, to_row, to_col)
    
    if target_piece == ' ':
        # Moving to empty square: path must be clear
        return pieces_between == 0
    else:
        # Capturing: must have exactly one piece between (screen)
        return pieces_between == 1


def isValidPawnMove(board: List[List[str]], from_row: int, from_col: int,
                   to_row: int, to_col: int, is_red: bool) -> bool:
    """Validate Pawn (P/p) move"""
    row_diff = to_row - from_row
    col_diff = abs(to_col - from_col)
    
    if is_red:
        # Red pawn (P) starts at bottom (row 0-4), moves forward (toward black) = row increases
        if red_crossed_river(from_row):
            # After crossing river (row > 4): can move forward or sideways
            if row_diff == 1 and col_diff == 0:
                return True  # Forward (row increases)
            if row_diff == 0 and col_diff == 1:
                return True  # Sideways
        else:
            # Before crossing river (row <= 4): can only move forward
            if row_diff == 1 and col_diff == 0:
                return True  # Forward (row increases)
    else:
        # Black pawn (p) starts at top (row 5-9), moves forward (toward red) = row decreases
        if black_crossed_river(from_row):
            # After crossing river (row < 5): can move forward or sideways
            if row_diff == -1 and col_diff == 0:
                return True  # Forward (row decreases)
            if row_diff == 0 and col_diff == 1:
                return True  # Sideways
        else:
            # Before crossing river (row >= 5): can only move forward
            if row_diff == -1 and col_diff == 0:
                return True  # Forward (row decreases)
    
    return False

