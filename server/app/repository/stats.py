from typing import Dict

class StatsRepo:
    def __init__(self):
        self.stats: Dict[str, dict] = {}

    def init(self, user_id: str):
        self.stats[user_id] = {
            'total_games': 0,
            'wins': 0,
            'losses': 0,
            'draws': 0,
            'last_game': None
        }
