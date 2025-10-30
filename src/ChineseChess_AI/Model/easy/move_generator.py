import copy

def in_bounds(x, y):
    return 0 <= x < 10 and 0 <= y < 9

def is_red(piece):
    return piece.isupper()

def is_black(piece):
    return piece.islower()

def same_size(a, b):
    if( a=='.' or b=='.' ): return False
    return (is_red(a) and is_red(b)) or (is_black(a) and is_black(b))

# -----------------------------
# Các luật di chuyển
# -----------------------------

def generate_rook_moves(broad, x, y):  #XE
    moves  = []
    piece = broad[x][y]
    dirs = [(-1, 0), (1, 0), (0, -1), (0, 1)]
    for dx, dy in dirs:
        nx, ny = x + dx, y + dy
        while in_bounds(nx, ny):
            if broad[nx][ny] == '.':
                moves.append(((x,y),(nx, ny)))
            else:
                if not same_size(piece, broad[nx][ny]):
                    moves.append(((x,y),(nx, ny)))
                break
            nx+=dx
            ny+=dy
    return moves


def generate_knight_moves(broad, x, y):  #Mã
    moves  = []
    piece = broad[x][y]
    horse_moves = [(-2, -1), (-2, 1), (-1, -2), (-1, 2), (1, -2), (1, 2), (2, -1), (2, 1)]
    block_check = {(-2, -1): (-1,0), (-2,1): (-1,0), 
                    (2,-1): (1,0), (2,1): (1,0), 
                    (-1,-2): (0,-1), (-1,2): (0,1), 
                    (1,-2): (0,-1), (1,2): (0,1)
                }
    for dx, dy in horse_moves:
        nx, ny = x + dx, y + dy
        if not in_bounds(nx, ny): continue
        bx, by = x + block_check[(dx, dy)][0], y + block_check[(dx, dy)][1]
        if not in_bounds(bx, by): continue
        if broad[bx][by]!= '.': continue
        if not same_size(piece, broad[nx][ny]):
            moves.append(((x,y),(nx, ny)))
    return moves


def generate_canon_moves(broad, x, y):  #Pháo
    moves=[]
    piece=broad[x][y]
    dirs=[(0,1),(0,-1),(1,0),(-1,0)]
    for dx, dy in dirs:
        nx, ny = x + dx, y + dy
        blocked=False  #kiểm tra có bị chặn không
        while in_bounds(nx, ny):
            if not blocked:
                if broad[nx][ny] == '.':
                    moves.append(((x,y),(nx, ny)))
                else: 
                    blocked=True
            else:
                if broad[nx][ny] != '.':
                    if not same_size(piece, broad[nx][ny]):
                        moves.append(((x,y),(nx, ny)))
                    break
            nx+=dx
            ny+=dy
    return moves

def generate_elephant_moves(broad, x, y):  #Tượng
    moves=[]
    piece=broad[x][y]
    elephant_moves=[(-2,-2),(-2,2),(2,-2),(2,2)]
    for dx, dy in elephant_moves:
        nx, ny = x + dx, y + dy
        if not in_bounds(nx, ny): continue
        #không qua sông
        if is_red(piece) and nx < 5: continue
        if is_black(piece) and nx > 4: continue
        #chặn
        bx, by = x + dx//2, y + dy//2
        if broad[bx][by]!= '.': continue
        if not same_size(piece, broad[nx][ny]):
            moves.append(((x,y),(nx, ny)))
    return moves

def generate_advisor_moves(broad, x, y):  #Sĩ
    moves=[]
    piece=broad[x][y]
    advisor_moves=[(-1,-1),(-1,1),(1,-1),(1,1)]
    for dx, dy in advisor_moves:
        nx, ny = x+dx, y+dy
        if not in_bounds(nx,ny): continue
        if is_red(piece) and not (7<= nx <=9 and 3 <=ny<= 5): continue
        if is_black(piece) and not (0<= nx <=2 and 3 <=ny<= 5): continue
        if not same_size(piece, broad[nx][ny]):
            moves.append(((x,y),(nx, ny)))
    return moves

def generate_king_moves(broad, x, y):  #Vua
    moves=[]
    piece=broad[x][y]
    king_moves=[(1,0), (-1, 0), (0, 1), (0,-1)]
    for dx, dy in king_moves:
        nx, ny = x+dx, y+dy
        if is_red(piece) and not (7<= nx <=9 and 3 <=ny<= 5): continue
        if is_black(piece) and not (0<= nx <=2 and 3 <=ny<= 5): continue
        if not same_size(piece, broad[nx][ny]):
            moves.append(((x,y),(nx, ny)))
    return moves
    
    
def generate_pawn_moves(broad, x, y):  #Tốt
    moves=[]
    piece=broad[x][y]
    if is_red(piece):
        directions = [(-1,0)]
        if x < 5: #qua sông
            directions += [(0,-1), (0,1)]
    else:
        directions = [(1,0)]
        if x > 4: #qua sông
            directions += [(0,-1), (0,1)]
    for dx, dy in directions:
        nx, ny = x+dx, y+dy
        if in_bounds(nx,ny) and not same_size(piece, broad[nx][ny]):
            moves.append(((x,y),(nx, ny)))
    return moves


#--------------------------------
# Tổng hợp hàm sinh nước đi
#--------------------------------
def get_piece_moves(broad, x, y):
    piece=broad[x][y]
    if piece in ['R', 'r']: return generate_rook_moves(broad, x, y)
    if piece in ['N', 'n']: return generate_knight_moves(broad, x, y)
    if piece in ['C', 'c']: return generate_canon_moves(broad, x, y)
    if piece in ['B', 'b']: return generate_elephant_moves(broad, x, y)
    if piece in ['A', 'a']: return generate_advisor_moves(broad, x, y)
    if piece in ['K', 'k']: return generate_king_moves(broad, x, y)
    if piece in ['P', 'p']: return generate_pawn_moves(broad, x, y)
    return []

def generate_legal_moves(broad, side):
    moves=[]
    for x in range(10):
        for y in range(9):
            piece=broad[x][y]
            if piece == '.': continue
            if side == 'red' and is_red(piece):
                moves.extend(get_piece_moves(broad, x, y))
            elif side == 'black' and is_black(piece):
                moves.extend(get_piece_moves(broad, x, y))
    # filter to legal moves: king safety + flying general
    legal_moves = []
    for move in moves:
        board_copy = copy.deepcopy(broad)
        apply_move(board_copy, move)
        if kings_facing(board_copy):
            continue
        if is_in_check(board_copy, side):
            continue
        legal_moves.append(move)
    return legal_moves

def apply_move(broad, move):
    (x1, y1), (x2, y2) = move
    broad[x2][y2] = broad[x1][y1]
    broad[x1][y1] = '.'

# -----------------------------
# King safety helpers
# -----------------------------
def get_king_position(board, side):
    target = 'K' if side == 'red' else 'k'
    for i in range(10):
        for j in range(9):
            if board[i][j] == target:
                return (i, j)
    return None

def kings_facing(board):
    red_pos = get_king_position(board, 'red')
    black_pos = get_king_position(board, 'black')
    if not red_pos or not black_pos:
        return False
    rx, ry = red_pos
    bx, by = black_pos
    if ry != by:
        return False
    step = 1 if rx < bx else -1
    x = rx + step
    while x != bx:
        if board[x][ry] != '.':
            return False
        x += step
    return True

def is_in_check(board, side):
    king_pos = get_king_position(board, side)
    if not king_pos:
        return False
    # flying general counts as check
    if kings_facing(board):
        return True
    kx, ky = king_pos
    opponent = 'black' if side == 'red' else 'red'
    # any opponent pseudo-legal move that lands on king square?
    for x in range(10):
        for y in range(9):
            p = board[x][y]
            if p == '.':
                continue
            if opponent == 'red' and is_red(p):
                for mv in get_piece_moves(board, x, y):
                    (_, _), (tx, ty) = mv
                    if tx == kx and ty == ky:
                        return True
            elif opponent == 'black' and is_black(p):
                for mv in get_piece_moves(board, x, y):
                    (_, _), (tx, ty) = mv
                    if tx == kx and ty == ky:
                        return True
    return False





