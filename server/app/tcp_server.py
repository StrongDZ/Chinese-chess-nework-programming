import socket
import json
import threading
import os, sys
from typing import Dict

# Add AI model path for board initial state
sys.path.append(os.path.join(os.path.dirname(__file__), '..', '..', 'ChineseChess_AI', 'Model', 'easy'))
try:
    from broad import INITIAL_BOARD
    from move_generator import apply_move
except Exception:
    INITIAL_BOARD = [['.' for _ in range(9)] for __ in range(10)]
    def apply_move(board, move):
        (x1,y1),(x2,y2) = move
        board[x2][y2] = board[x1][y1]
        board[x1][y1] = '.'

from app.models import GameState
from app.repository.users import UsersRepo
from app.repository.stats import StatsRepo
from app.services.signup_service import signup
from app.services.legality_service import validate_move
from app.services.result_service import detect_result, apply_elo

users_repo = UsersRepo()
stats_repo = StatsRepo()

GAMES: Dict[str, GameState] = {}


def handle_client(conn, addr):
    with conn:
        buf = b''
        while True:
            data = conn.recv(4096)
            if not data:
                break
            buf += data
            # Simple line-delimited JSON
            while b'\n' in buf:
                line, buf = buf.split(b'\n', 1)
                if not line.strip():
                    continue
                try:
                    pkt = json.loads(line.decode('utf-8'))
                except Exception:
                    conn.sendall(b'{"status":"FAIL","msg":"BAD_JSON"}\n')
                    continue
                resp = process_packet(pkt)
                conn.sendall((json.dumps(resp) + "\n").encode('utf-8'))


def process_packet(pkt: dict) -> dict:
    t = pkt.get('type')
    if t == 'SIGNUP':
        ok, resp = signup(users_repo, stats_repo, pkt.get('username',''), pkt.get('email',''), pkt.get('password',''))
        return resp
    if t == 'START_GAME':
        game_id = pkt.get('gameID') or addr_like_id()
        board = [row[:] for row in INITIAL_BOARD]
        GAMES[game_id] = GameState(game_id=game_id, board=board, side_to_move='red', remaining_ms={'red':300000,'black':300000}, moves=[])
        return {"status":"GAME_START","gameID":game_id,"nextTurn":"red"}
    if t == 'MOVE':
        game_id = pkt.get('gameID')
        g = GAMES.get(game_id)
        if not g:
            return {"status":"MOVE_REJECTED","reason":"NO_GAME"}
        move = ((pkt['from']['x'], pkt['from']['y']), (pkt['to']['x'], pkt['to']['y']))
        ok, reason = validate_move(g, move, g.side_to_move)
        if not ok:
            return {"status":"MOVE_REJECTED","reason":reason}
        apply_move(g.board, move)
        g.moves.append(move)
        g.side_to_move = 'black' if g.side_to_move == 'red' else 'red'
        # detect result
        rs = detect_result(g.board, g.side_to_move)
        if rs:
            result = rs['type']
            # For demo: ELO tĩnh 1450 vs 1450
            red_new, black_new, d_red, d_black = apply_elo(1450,1450,'RED_WIN' if rs.get('winner')=='red' else ('BLACK_WIN' if rs.get('winner')=='black' else 'DRAW'))
            return {"type":"GAME_END","gameID":game_id,"result":"DRAW" if result=="STALEMATE" else "WIN","reason":result,
                    "eloChange":{"red":d_red,"black":d_black},"newELO":{"red":red_new,"black":black_new}}
        return {"status":"MOVE_ACCEPTED","gameID":game_id,"from":pkt['from'],"to":pkt['to'],"nextTurn":g.side_to_move}
    if t == 'RESIGN':
        game_id = pkt.get('gameID')
        side = pkt.get('side')
        if not game_id or game_id not in GAMES:
            return {"status":"FAIL","msg":"NO_GAME"}
        winner = 'black' if side == 'red' else 'red'
        red_new, black_new, d_red, d_black = apply_elo(1450,1450,'RED_WIN' if winner=='red' else 'BLACK_WIN')
        return {"type":"GAME_END","gameID":game_id,"result":"WIN","reason":"RESIGN","winner":winner,
                "eloChange":{"red":d_red,"black":d_black},"newELO":{"red":red_new,"black":black_new}}
    return {"status":"FAIL","msg":"UNKNOWN_TYPE"}


def addr_like_id():
    import uuid
    return str(uuid.uuid4())


def serve(host='0.0.0.0', port=8080):
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind((host, port))
    s.listen(5)
    print(f"TCP server listening on {host}:{port}")
    try:
        while True:
            conn, addr = s.accept()
            threading.Thread(target=handle_client, args=(conn, addr), daemon=True).start()
    finally:
        s.close()

if __name__ == '__main__':
    serve()
