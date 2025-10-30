# GAME_END Packet

Notify to both players when a game ends.

```json
{
  "type": "GAME_END",
  "gameID": "uuid",
  "result": "WIN|LOSS|DRAW",
  "reason": "CHECKMATE|STALEMATE|RESIGN|TIMEOUT|REPETITION|FIFTY_MOVE",
  "winner": "red|black|null",
  "eloChange": { "red": +15, "black": -15 },
  "newELO": { "red": 1465, "black": 1435 }
}
```
