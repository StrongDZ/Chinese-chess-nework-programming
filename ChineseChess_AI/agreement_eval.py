"""
Agreement evaluation: how often Easy/Medium match Hard's move (as proxy oracle).
Generates diverse positions by making a few random legal moves from the start.

Usage:
  python3 agreement_eval.py --positions 50 --random_plies 6 --hard_oracle_time 2.0
"""
import os
import sys
import time
import random
import argparse

BASE_DIR = os.path.dirname(__file__)
sys.path.append(os.path.join(BASE_DIR, 'Model', 'easy'))
sys.path.append(os.path.join(BASE_DIR, 'Model', 'medium'))
sys.path.append(os.path.join(BASE_DIR, 'Model', 'hard'))

from Model.easy.broad import INITIAL_BOARD
from Model.easy.move_generator import generate_legal_moves, apply_move
from Model.easy.ai_easy import ai_easy_move
from Model.medium.ai_medium_wrapper import ai_medium_move
from Model.hard.ai_hard import ai_hard_move


def randomize_position(board, plies: int, seed: int = None):
    rng = random.Random(seed)
    b = [row[:] for row in board]
    for ply in range(plies):
        side = 'red' if ply % 2 == 0 else 'black'
        legal = generate_legal_moves(b, side)
        if not legal:
            break
        mv = rng.choice(legal)
        apply_move(b, mv)
    return b


def main():
    parser = argparse.ArgumentParser(description='Agreement with Hard (oracle)')
    parser.add_argument('--positions', type=int, default=50, help='Number of random positions')
    parser.add_argument('--random_plies', type=int, default=6, help='Random plies to diversify positions')
    parser.add_argument('--hard_oracle_time', type=float, default=2.0, help='Time limit for hard oracle (seconds)')
    args = parser.parse_args()

    agree_easy = 0
    agree_medium = 0
    total = 0
    t_easy = 0.0
    t_medium = 0.0
    t_oracle = 0.0

    for i in range(args.positions):
        base = [row[:] for row in INITIAL_BOARD]
        pos = randomize_position(base, args.random_plies, seed=i)
        # Decide side to move by parity
        side = 'red' if args.random_plies % 2 == 0 else 'black'
        legal = generate_legal_moves(pos, side)
        if not legal:
            continue

        # Oracle
        t0 = time.time()
        oracle_move = ai_hard_move(pos, side, time_limit=args.hard_oracle_time, strategy='adaptive')
        t_oracle += time.time() - t0
        if oracle_move is None:
            continue

        # Easy
        t0 = time.time()
        easy_move = ai_easy_move(pos, side)
        t_easy += time.time() - t0
        # Medium
        t0 = time.time()
        medium_move = ai_medium_move(pos, side)
        t_medium += time.time() - t0

        agree_easy += 1 if easy_move == oracle_move else 0
        agree_medium += 1 if medium_move == oracle_move else 0
        total += 1

    print("\n===== AGREEMENT SUMMARY (Hard as oracle) =====")
    if total == 0:
        print("No evaluable positions.")
        return
    print(f"Positions: {total}")
    print(f"Easy   agreement: {agree_easy}/{total} = {agree_easy/total*100:.1f}% | avg time: {t_easy/total*1000:.1f} ms")
    print(f"Medium agreement: {agree_medium}/{total} = {agree_medium/total*100:.1f}% | avg time: {t_medium/total*1000:.1f} ms")
    print(f"Oracle avg time: {t_oracle/total*1000:.1f} ms (Hard time={args.hard_oracle_time}s)")


if __name__ == '__main__':
    main()


