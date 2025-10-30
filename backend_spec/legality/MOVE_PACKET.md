# MOVE Packet

## Request (Client → Server)
```json
{
  "type": "MOVE",
  "sessionID": "uuid",
  "gameID": "uuid",
  "from": {"x": 7, "y": 1},
  "to":   {"x": 0, "y": 1},
  "timestamp": 1730260000
}
```

## Response
Accepted:
```json
{
  "status": "MOVE_ACCEPTED",
  "gameID": "uuid",
  "from": {"x": 7, "y": 1},
  "to":   {"x": 0, "y": 1},
  "newBoard": "...optional...",
  "nextTurn": "black",
  "remainingTime": {"red": 294000, "black": 300000}
}
```
Rejected:
```json
{ "status": "MOVE_REJECTED", "reason": "WRONG_TURN|OUT_OF_BOUNDS|NO_OWN_PIECE|ILLEGAL_MOVE|SELF_CHECK|TIMEOUT" }
```

Opponent notify:
```json
{
  "type": "OPPONENT_MOVE",
  "gameID": "uuid",
  "from": {"x": 7, "y": 1},
  "to":   {"x": 0, "y": 1},
  "remainingTime": {"red": 294000, "black": 300000}
}
```
