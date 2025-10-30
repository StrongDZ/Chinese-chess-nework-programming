"""
Round-robin evaluation between Easy, Medium(wrapper), and Hard AIs.
Computes win rates, average move times, and captures across multiple games.

Usage:
  python3 round_robin_eval.py --games 6 --max_moves 80 --hard_time_limit 1.0
"""
import sys
import os
import time
import argparse

BASE_DIR = os.path.dirname(__file__)
sys.path.append(os.path.join(BASE_DIR, 'Model', 'easy'))
sys.path.append(os.path.join(BASE_DIR, 'Model', 'medium'))
sys.path.append(os.path.join(BASE_DIR, 'Model', 'hard'))

from Model.easy.broad import INITIAL_BOARD
from Model.easy.move_generator import apply_move, generate_legal_moves
from Model.easy.ai_easy import ai_easy_move
from Model.medium.ai_medium_wrapper import ai_medium_move
from Model.hard.ai_hard import ai_hard_move


def play_game(white_name, white_fn, black_name, black_fn, max_moves: int):
    board = [row[:] for row in INITIAL_BOARD]
    stats = {
        white_name: {'moves': 0, 'time': 0.0, 'captures': 0},
        black_name: {'moves': 0, 'time': 0.0, 'captures': 0},
    }
    for move_num in range(max_moves):
        side = 'red' if move_num % 2 == 0 else 'black'
        name = white_name if side == 'red' else black_name
        fn = white_fn if side == 'red' else black_fn

        legal = generate_legal_moves(board, side)
        if not legal:
            winner = black_name if side == 'red' else white_name
            return winner, stats, move_num

        t0 = time.time()
        move = fn(board, side)
        dt = time.time() - t0
        stats[name]['moves'] += 1
        stats[name]['time'] += dt

        if move is None:
            winner = black_name if side == 'red' else white_name
            return winner, stats, move_num

        (_, _), (x2, y2) = move
        if board[x2][y2] != '.':
            stats[name]['captures'] += 1
        apply_move(board, move)

        # After move, see if opponent has no moves
        next_side = 'black' if side == 'red' else 'red'
        if not generate_legal_moves(board, next_side):
            return name, stats, move_num + 1

    return 'draw', stats, max_moves


def main():
    parser = argparse.ArgumentParser(description='Round-robin Easy/Medium/Hard')
    parser.add_argument('--games', type=int, default=6, help='Games per pairing (half white/half black)')
    parser.add_argument('--max_moves', type=int, default=80, help='Max moves per game')
    parser.add_argument('--hard_time_limit', type=float, default=1.0, help='Hard AI time limit')
    args = parser.parse_args()

    AIs = {
        'easy': lambda b, s: ai_easy_move(b, s),
        'medium': lambda b, s: ai_medium_move(b, s),
        'hard': lambda b, s: ai_hard_move(b, s, time_limit=args.hard_time_limit, strategy='adaptive'),
    }

    names = list(AIs.keys())
    results = {a: {'wins': 0, 'losses': 0, 'draws': 0, 'moves': 0, 'time': 0.0, 'captures': 0} for a in names}

    pairings = [('easy', 'medium'), ('easy', 'hard'), ('medium', 'hard')]
    for p in pairings:
        a, b = p
        # Half games each side as red
        per_side = max(1, args.games // 2)
        for _ in range(per_side):
            # a as red
            winner, stats, mvs = play_game(a, AIs[a], b, AIs[b], args.max_moves)
            for k in stats:
                results[k]['moves'] += stats[k]['moves']
                results[k]['time'] += stats[k]['time']
                results[k]['captures'] += stats[k]['captures']
            if winner == 'draw':
                results[a]['draws'] += 1; results[b]['draws'] += 1
            elif winner == a:
                results[a]['wins'] += 1; results[b]['losses'] += 1
            else:
                results[b]['wins'] += 1; results[a]['losses'] += 1

            # b as red
            winner, stats, mvs = play_game(b, AIs[b], a, AIs[a], args.max_moves)
            for k in stats:
                results[k]['moves'] += stats[k]['moves']
                results[k]['time'] += stats[k]['time']
                results[k]['captures'] += stats[k]['captures']
            if winner == 'draw':
                results[a]['draws'] += 1; results[b]['draws'] += 1
            elif winner == a:
                results[a]['wins'] += 1; results[b]['losses'] += 1
            else:
                results[b]['wins'] += 1; results[a]['losses'] += 1

    print("\n===== ROUND-ROBIN SUMMARY =====")
    for a in names:
        mv = results[a]['moves']
        avg_ms = (results[a]['time'] / mv * 1000.0) if mv else 0.0
        print(f"{a.upper():7} | W:{results[a]['wins']} L:{results[a]['losses']} D:{results[a]['draws']} | avg {avg_ms:.1f} ms | captures {results[a]['captures']}")


if __name__ == '__main__':
    main()


