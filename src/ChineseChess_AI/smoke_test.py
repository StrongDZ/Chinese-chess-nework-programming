"""
Lightweight smoke test for Easy, Medium (wrapper), and Hard AIs.
Runs a few plies from the initial position and prints chosen moves and timings.
Supports CLI args: --plies, --repeats, --hard_time_limit.
"""
import time
import sys
import os
import argparse

# Ensure model paths are available
BASE_DIR = os.path.dirname(__file__)
sys.path.append(os.path.join(BASE_DIR, 'Model', 'easy'))
sys.path.append(os.path.join(BASE_DIR, 'Model', 'medium'))
sys.path.append(os.path.join(BASE_DIR, 'Model', 'hard'))

from Model.easy.broad import INITIAL_BOARD, print_broad
from Model.easy.move_generator import apply_move, generate_legal_moves
from Model.easy.ai_easy import ai_easy_move
from Model.medium.ai_medium_wrapper import ai_medium_move
from Model.hard.ai_hard import ai_hard_move


def run_ai(ai_name, move_fn, plies=6):
    board = [row[:] for row in INITIAL_BOARD]
    print(f"\n=== {ai_name.upper()} SMOKE TEST ({plies} plies) ===")
    print_broad(board)

    for ply in range(plies):
        side = 'red' if ply % 2 == 0 else 'black'
        legal = generate_legal_moves(board, side)
        if not legal:
            print(f"No legal moves for {side}. Test ended.")
            break

        t0 = time.time()
        move = move_fn(board, side)
        dt = (time.time() - t0) * 1000.0

        if move is None:
            print(f"{ai_name} returned None for {side}. Test ended.")
            break

        print(f"ply {ply+1:02d} | {side:<5} | move: {move} | {dt:.1f} ms")
        apply_move(board, move)

    print("\nBoard after test:")
    print_broad(board)


def main():
    parser = argparse.ArgumentParser(description='Smoke test for Easy/Medium/Hard AIs')
    parser.add_argument('--plies', type=int, default=6, help='Number of plies per run')
    parser.add_argument('--repeats', type=int, default=1, help='Number of repeated runs per AI')
    parser.add_argument('--hard_time_limit', type=float, default=1.5, help='Time limit (s) per Hard AI move')
    args = parser.parse_args()

    for r in range(args.repeats):
        if args.repeats > 1:
            print(f"\n====== REPEAT {r+1}/{args.repeats} ======")
        # Easy
        run_ai('easy', ai_easy_move, plies=args.plies)
        # Medium (wrapper)
        run_ai('medium(wrapper)', ai_medium_move, plies=args.plies)
        # Hard
        run_ai('hard', lambda b, s: ai_hard_move(b, s, time_limit=args.hard_time_limit, strategy='adaptive'), plies=args.plies)

    print("\n✅ Smoke tests completed.")


if __name__ == "__main__":
    main()


