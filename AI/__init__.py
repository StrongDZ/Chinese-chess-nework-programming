"""
AI Engine for Chinese Chess - Simple and Complete
Chỉ giữ lại những tính năng cần thiết để predict move
"""

from .ai import (
    AI,
    AIDifficulty,
    Move,
    Coord,
    predict_move,
)

__all__ = [
    'AI',
    'AIDifficulty',
    'Move',
    'Coord',
    'predict_move',
]
