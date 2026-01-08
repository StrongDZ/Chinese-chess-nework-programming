#!/usr/bin/env python3
"""
Simple AI wrapper for Pikafish - designed for direct subprocess calls from C++
"""

import subprocess
import sys
import os


def get_best_move(fen: str, depth: int = 3) -> str:
    """
    Get best move from Pikafish for given FEN position

    Args:
        fen: FEN string (e.g., "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w")
        depth: Search depth (3=easy, 5=medium, 8=hard)

    Returns:
        UCI move string (e.g., "a3a4") or "error" if failed
    """
    # Find pikafish in same directory
    script_dir = os.path.dirname(os.path.abspath(__file__))
    pikafish_path = os.path.join(script_dir, "pikafish")

    # Check if pikafish exists and is executable
    if not os.path.exists(pikafish_path) or not os.access(pikafish_path, os.X_OK):
        # Try system PATH
        import shutil

        pikafish_in_path = shutil.which("pikafish")
        if pikafish_in_path:
            pikafish_path = pikafish_in_path
        else:
            print(f"error: pikafish not found at {pikafish_path} or in PATH", file=sys.stderr)
            return "error"

    # Build UCI commands
    commands = f"""uci
isready
position fen {fen}
go depth {depth}
quit
"""

    try:
        result = subprocess.run([pikafish_path], input=commands, capture_output=True, text=True, timeout=30)

        # Log errors to stderr for debugging
        if result.returncode != 0:
            print(f"error: pikafish returned code {result.returncode}", file=sys.stderr)
            if result.stderr:
                print(f"stderr: {result.stderr}", file=sys.stderr)
            return "error"

        # Parse output for bestmove
        for line in result.stdout.split("\n"):
            if line.startswith("bestmove"):
                parts = line.split()
                if len(parts) >= 2:
                    move = parts[1]
                    if move != "(none)":
                        return move

        # If no bestmove found, log debug info
        print(f"error: no bestmove found in output", file=sys.stderr)
        print(f"stdout: {result.stdout[:500]}", file=sys.stderr)
        return "error"

    except subprocess.TimeoutExpired:
        print("error: pikafish timeout", file=sys.stderr)
        return "error"
    except FileNotFoundError:
        print(f"error: pikafish not found at {pikafish_path}", file=sys.stderr)
        return "error"
    except Exception as e:
        print(f"error: {str(e)}", file=sys.stderr)
        return "error"


def difficulty_to_depth(difficulty: str) -> int:
    """Convert difficulty string to search depth"""
    difficulty = difficulty.lower()
    if difficulty == "easy":
        return 3
    elif difficulty == "medium":
        return 5
    elif difficulty == "hard":
        return 8
    else:
        return 5  # default medium


if __name__ == "__main__":
    # Command line usage: python3 ai_simple.py "<fen>" "<difficulty>"
    if len(sys.argv) < 2:
        print("error")
        sys.exit(1)

    fen = sys.argv[1]
    difficulty = sys.argv[2] if len(sys.argv) > 2 else "medium"

    depth = difficulty_to_depth(difficulty)
    move = get_best_move(fen, depth)
    print(move)
