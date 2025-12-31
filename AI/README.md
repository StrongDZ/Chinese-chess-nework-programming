# Chinese Chess AI Engine - Python Implementation

Python implementation of the AI engine for Chinese Chess, wrapping the Pikafish UCI engine.

## Features

- **PikafishEngine**: Wrapper for Pikafish UCI engine
  - Initialize and manage Pikafish process
  - Get best moves with configurable difficulty levels
  - Convert between UCI format and internal move representation
  - FEN string conversion and board array operations

- **GameStateManager**: Manages game states for multiple concurrent games
  - Track move history
  - Generate position strings for engine
  - Board state management

- **Board Validation**: Complete validation for all Chinese Chess pieces
  - King, Advisor, Elephant, Knight, Rook, Cannon, Pawn
  - Special rules (palace restrictions, river crossing, etc.)

## Requirements

- Python 3.7+
- Pikafish executable (must be in PATH or same directory as script)

## Installation

1. Ensure Pikafish is installed and accessible:
   ```bash
   # Check if pikafish is in PATH
   which pikafish
   
   # Or place pikafish in the same directory as the Python scripts
   ```

2. No Python dependencies required - uses only standard library.

## Usage

### Basic Example

```python
from python_ai import PikafishEngine, AIDifficulty, MovePayload, Coord

# Initialize engine
engine = PikafishEngine()
if not engine.initialize():
    print("Failed to initialize AI engine")
    exit(1)

# Get best move
fen_position = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"
best_move = engine.get_best_move(fen_position, AIDifficulty.MEDIUM)
print(f"Best move: {best_move}")

# Convert to MovePayload
move = PikafishEngine.parse_uci_move(best_move)
print(f"From: ({move.from_pos.row}, {move.from_pos.col})")
print(f"To: ({move.to_pos.row}, {move.to_pos.col})")

# Cleanup
engine.shutdown()
```

### Game State Management

```python
from python_ai import GameStateManager, AIDifficulty, MovePayload, Coord

# Create game state manager
state_manager = GameStateManager()

# Initialize a game
player_fd = 1
ai_fd = -1
state_manager.initialize_game(player_fd, ai_fd, AIDifficulty.MEDIUM)

# Apply a move
move = MovePayload("", Coord(6, 0), Coord(7, 0))
state_manager.apply_move(player_fd, move)

# Get position string for engine
position_str = state_manager.get_position_string(player_fd)
print(f"Position: {position_str}")

# Get board array
board = state_manager.get_current_board_array(player_fd)
if board:
    for row in board:
        print(''.join(row))

# Cleanup
state_manager.end_game(player_fd)
```

### Board Validation

```python
from python_ai import GameStateManager, MovePayload, Coord

# Create a board (10x9 array)
board = [[' ' for _ in range(9)] for _ in range(10)]
# ... populate board with pieces ...

# Validate a move
move = MovePayload("R", Coord(0, 0), Coord(0, 5))
is_valid = GameStateManager.is_valid_move_on_board(board, move)
print(f"Move is valid: {is_valid}")
```

## Difficulty Levels

- **EASY**: Depth 3, time limit 500ms
- **MEDIUM**: Depth 5, time limit 1000ms
- **HARD**: Depth 8, time limit 2000ms

## File Structure

```
python_ai/
├── __init__.py          # Package initialization
├── ai_engine.py          # PikafishEngine class
├── game_state.py         # GameStateManager class
├── board_validator.py    # Board validation functions
├── requirements.txt      # Python dependencies (none required)
└── README.md            # This file
```

## Notes

- The Python implementation maintains the same API as the C++ version
- Thread-safe operations using threading.Lock
- Compatible with the existing C++ backend protocol
- All coordinate systems match the C++ implementation

## Integration with C++ Backend

This Python module can be integrated with the C++ backend using:
- Python C API
- ctypes
- subprocess calls
- REST API wrapper

See the main backend documentation for integration details.

