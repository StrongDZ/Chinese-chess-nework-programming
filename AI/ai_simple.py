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
    
    if not os.access(pikafish_path, os.X_OK):
        # Try system PATH
        pikafish_path = "pikafish"
    
    # Build UCI commands
    commands = f"""uci
isready
position fen {fen}
go depth {depth}
quit
"""
    
    try:
        result = subprocess.run(
            [pikafish_path],
            input=commands,
            capture_output=True,
            text=True,
            timeout=30
        )
        
        # Parse output for bestmove
        for line in result.stdout.split('\n'):
            if line.startswith('bestmove'):
                parts = line.split()
                if len(parts) >= 2:
                    return parts[1]
        
        return "error"
        
    except subprocess.TimeoutExpired:
        return "error"
    except Exception as e:
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
