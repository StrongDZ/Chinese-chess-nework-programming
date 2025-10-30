from dataclasses import dataclass
from typing import List, Tuple, Optional, Dict

Board = List[List[str]]  # 10x9
Move = Tuple[Tuple[int, int], Tuple[int, int]]  # ((x1,y1),(x2,y2))

@dataclass
class GameState:
    game_id: str
    board: Board
    side_to_move: str  # 'red' | 'black'
    start_time_ms: int = 0
    remaining_ms: Dict[str, int] = None  # {'red': ms, 'black': ms}
    moves: List[Move] = None
