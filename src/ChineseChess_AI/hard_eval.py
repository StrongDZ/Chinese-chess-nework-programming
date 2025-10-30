"""
Hard vs Easy evaluation runner.
Runs multiple games and aggregates stats: wins, captures, avg response time.
Uses improved ai_hard_move vs ai_easy_move.
"""
import time
import sys
import os
import argparse

BASE_DIR = os.path.dirname(__file__)
sys.path.append(os.path.join(BASE_DIR, 'Model', 'easy'))
sys.path.append(os.path.join(BASE_DIR, 'Model', 'hard'))

from Model.easy.broad import INITIAL_BOARD, print_broad
from Model.easy.move_generator import apply_move, generate_legal_moves, is_in_check
from Model.easy.ai_easy import ai_easy_move
from Model.hard.ai_hard import ai_hard_move


def play_one(max_moves: int, hard_time_limit: float, verbose: bool = False):
    board = [row[:] for row in INITIAL_BOARD]
    move_count = 0
    stats = {
        'hard': {'moves': 0, 'total_time': 0.0, 'captures': 0},
        'easy': {'moves': 0, 'total_time': 0.0, 'captures': 0},
    }

    for move_num in range(max_moves):
        side = 'red' if move_num % 2 == 0 else 'black'
        ai_type = 'hard' if side == 'red' else 'easy'
        bucket = stats[ai_type]

        # Time and get move
        t0 = time.time()
        if ai_type == 'hard':
            move = ai_hard_move(board, side, time_limit=hard_time_limit, strategy='adaptive')
        else:
            move = ai_easy_move(board, side)
        dt = time.time() - t0
        bucket['moves'] += 1
        bucket['total_time'] += dt

        if move is None:
            winner = 'black' if side == 'red' else 'red'
            return {
                'winner': winner,
                'moves': move_count,
                'stats': stats
            }

        # Capture check
        (_, _), (x2, y2) = move
        captured = board[x2][y2]
        if captured != '.':
            bucket['captures'] += 1
            if verbose:
                print(f"capture by {ai_type}: {captured}")

        apply_move(board, move)
        move_count += 1

        # Early termination if opponent has no legal moves
        next_side = 'black' if side == 'red' else 'red'
        next_legal = generate_legal_moves(board, next_side)
        if not next_legal:
            return {
                'winner': side,
                'moves': move_count,
                'stats': stats
            }

    return {
        'winner': 'draw',
        'moves': move_count,
        'stats': stats
    }


def main():
    parser = argparse.ArgumentParser(description='Hard vs Easy evaluation runner')
    parser.add_argument('--games', type=int, default=5, help='Number of games to run')
    parser.add_argument('--max_moves', type=int, default=80, help='Max moves per game')
    parser.add_argument('--hard_time_limit', type=float, default=1.0, help='Hard AI time limit per move (seconds)')
    parser.add_argument('--verbose', action='store_true', help='Print captures and details')
    args = parser.parse_args()

    hard_wins = 0
    easy_wins = 0
    draws = 0
    agg = {
        'hard': {'moves': 0, 'total_time': 0.0, 'captures': 0},
        'easy': {'moves': 0, 'total_time': 0.0, 'captures': 0},
    }

    for g in range(args.games):
        if args.verbose:
            print(f"\n=== Game {g+1}/{args.games} ===")
        res = play_one(args.max_moves, args.hard_time_limit, verbose=args.verbose)
        winner = res['winner']
        if winner == 'red':
            hard_wins += 1
        elif winner == 'black':
            easy_wins += 1
        else:
            draws += 1

        for side in ['hard', 'easy']:
            agg[side]['moves'] += res['stats'][side]['moves']
            agg[side]['total_time'] += res['stats'][side]['total_time']
            agg[side]['captures'] += res['stats'][side]['captures']

    print("\n===== SUMMARY =====")
    print(f"Games: {args.games}, Max moves/game: {args.max_moves}, Hard time limit: {args.hard_time_limit:.2f}s")
    print(f"Wins - HARD: {hard_wins}, EASY: {easy_wins}, DRAWS: {draws}")
    for side in ['hard', 'easy']:
        moves = agg[side]['moves']
        avg_time = (agg[side]['total_time'] / moves * 1000.0) if moves else 0.0
        print(f"{side.upper()} -> moves: {moves}, avg time: {avg_time:.1f} ms, captures: {agg[side]['captures']}")


if __name__ == '__main__':
    main()


