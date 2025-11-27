# Kiến Trúc và Mô Hình AI trong Hệ Thống Cờ Tướng

## 1. Tổng Quan

Hệ thống AI được tích hợp vào server C++ để cung cấp đối thủ AI và tính năng gợi ý nước đi cho người chơi. AI sử dụng **Pikafish Engine** - một engine cờ tướng mạnh mẽ sử dụng thuật toán **Minimax với Alpha-Beta Pruning** và **NNUE (Efficiently Updatable Neural Networks)** để đánh giá thế cờ.

## 2. Mô Hình AI Được Triển Khai

### 2.1. Engine AI: Pikafish

**Pikafish** là một chess engine mã nguồn mở được tối ưu hóa cho cờ tướng (Chinese Chess/Xiangqi), sử dụng:

- **UCI Protocol (Universal Chess Interface)**: Giao thức chuẩn để giao tiếp với chess engines
- **Minimax Algorithm với Alpha-Beta Pruning**: Tìm kiếm nước đi tối ưu bằng cách duyệt cây trò chơi
- **NNUE Evaluation**: Mạng neural network nhẹ để đánh giá thế cờ nhanh chóng và chính xác
- **Transposition Table**: Cache các vị trí đã tính toán để tối ưu hiệu suất

### 2.2. Kiến Trúc Tích Hợp

Hệ thống AI được triển khai theo mô hình **Wrapper Pattern** với 2 lớp chính:

```
┌─────────────────────────────────────────┐
│         Server (server.cpp)              │
│  - Xử lý client requests                │
│  - Quản lý game sessions                 │
│  - Thread-safe message queue             │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│    GameStateManager (ai_engine.h/cpp)   │
│  - Quản lý trạng thái game              │
│  - Lưu trữ move history                 │
│  - Generate position strings             │
│  - Thread-safe với mutex                │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│    PikafishEngine (ai_engine.h/cpp)     │
│  - Wrapper cho Pikafish process          │
│  - Giao tiếp qua pipes (stdin/stdout)    │
│  - Parse UCI responses                  │
│  - Convert move formats                  │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│      Pikafish Process (External)        │
│  - Minimax + Alpha-Beta                 │
│  - NNUE Evaluation                      │
│  - UCI Protocol                         │
└─────────────────────────────────────────┘
```

## 3. Các Thành Phần Chính

### 3.1. PikafishEngine Class

**Vai trò**: Wrapper class để giao tiếp với Pikafish engine process

**Chức năng chính**:

1. **Process Management**:
   - `initialize()`: Khởi tạo Pikafish process bằng `fork()` và `exec()`
   - Tạo pipes để giao tiếp (stdin/stdout)
   - Handshake với engine qua UCI protocol (`uci` → `uciok`, `isready` → `readyok`)

2. **Move Generation**:
   - `getBestMove(position, difficulty)`: Yêu cầu engine tìm nước đi tốt nhất
     - Gửi lệnh: `position fen <fen> moves <move1> <move2> ...`
     - Gửi lệnh: `go depth <depth>` hoặc `go movetime <time_ms>`
     - Parse response để lấy `bestmove <move>`

3. **Move Suggestion**:
   - `suggestMove(position)`: Tương tự `getBestMove` nhưng dùng HARD difficulty

4. **Format Conversion**:
   - `parseUCIMove(uci_string)`: Convert "a0a1" → `MovePayload{from, to}`
   - `moveToUCI(move)`: Convert `MovePayload` → "a0a1"

**Đặc điểm kỹ thuật**:
- Sử dụng `std::mutex` để đảm bảo thread-safety
- Timeout mechanism để tránh hang
- Error handling và logging chi tiết

### 3.2. GameStateManager Class

**Vai trò**: Quản lý trạng thái game cho các ván đấu với AI

**Chức năng chính**:

1. **Game Initialization**:
   - `initializeGame(player_fd, ai_fd, difficulty)`: Khởi tạo game mới
   - Tạo FEN string ban đầu cho cờ tướng
   - Lưu difficulty level (EASY/MEDIUM/HARD)

2. **Move Management**:
   - `applyMove(player_fd, move)`: Áp dụng nước đi vào game state
   - Lưu move vào `move_history` (reliable hơn recalculating FEN)
   - Cập nhật `player_turn` flag

3. **Position String Generation**:
   - `getPositionString(player_fd)`: Tạo chuỗi position cho engine
     - Format: `position fen <initial_fen> moves <move1> <move2> ...`
     - Engine tự tính toán board state từ move history (chính xác 100%)

4. **State Queries**:
   - `getCurrentFEN(player_fd)`: Lấy FEN hiện tại (backward compatibility)
   - `getAIDifficulty(player_fd)`: Lấy difficulty level
   - `hasGame(player_fd)`: Kiểm tra game có tồn tại không

**Đặc điểm kỹ thuật**:
- Sử dụng `mutable std::mutex` để cho phép lock trong const methods
- Thread-safe với `std::lock_guard`
- Lưu trữ move history thay vì recalculate FEN (reliable hơn)

### 3.3. Server Integration

**Vai trò**: Tích hợp AI vào server event loop

**Các handler chính**:

1. **handleAIMatch()**:
   - Xử lý request `AI_MATCH {"gamemode":"easy|medium|hard"}`
   - Khởi tạo `GameStateManager` với difficulty tương ứng
   - Set `opponent_fd = -1` để đánh dấu AI game
   - Gửi `GAME_START` response

2. **handleMove()**:
   - Nếu là AI game:
     - Apply player's move vào game state
     - Echo move back cho player
     - Gọi `handleAIMove()` để trigger AI response

3. **handleAIMove()** (CRITICAL - Non-blocking):
   - **Spawn worker thread** để gọi `getBestMove()` (blocking operation)
   - AI thread:
     1. Gọi `g_ai_engine.getBestMove(position_str, difficulty)`
     2. Parse UCI move → `MovePayload`
     3. Apply AI move vào game state
     4. Push message vào thread-safe queue
   - Main thread: Process queue và gửi AI move về client

4. **handleSuggestMove()**:
   - Xử lý request `SUGGEST_MOVE`
   - Gọi `g_ai_engine.suggestMove(current_fen)` với HARD difficulty
   - Gửi suggestion về client

**Thread-Safe Message Queue**:
```cpp
struct AIMessage {
  int player_fd;
  MessageType type;
  Payload payload;
};
std::queue<AIMessage> g_ai_message_queue;
std::mutex g_ai_queue_mutex;
std::condition_variable g_ai_queue_cv;
```

Main loop process queue mỗi iteration để gửi AI responses.

## 4. Luồng Hoạt Động

### 4.1. Khởi Tạo AI Engine

```
1. Server start
   ↓
2. main() gọi g_ai_engine.initialize()
   ↓
3. PikafishEngine::initialize():
   - Tìm Pikafish executable (env var → exe dir → PATH)
   - fork() để tạo child process
   - exec() để chạy Pikafish
   - Tạo pipes (stdin/stdout) để giao tiếp
   - Gửi "uci" → nhận "uciok"
   - Gửi "isready" → nhận "readyok"
   ↓
4. Engine ready, server tiếp tục listen
```

### 4.2. Bắt Đầu Game với AI

```
1. Client gửi: AI_MATCH {"gamemode":"easy"}
   ↓
2. handleAIMatch():
   - Parse gamemode → AIDifficulty (EASY/MEDIUM/HARD)
   - g_game_state.initializeGame(fd, -1, difficulty)
   - Set sender.in_game = true, opponent_fd = -1
   ↓
3. Gửi GAME_START response về client
```

### 4.3. Player Makes Move → AI Responds

```
1. Client gửi: MOVE {"from":{"row":6,"col":0},"to":{"row":5,"col":0}}
   ↓
2. handleMove():
   - Detect AI game (opponent_fd == -1 && hasGame(fd))
   - g_game_state.applyMove(fd, move) → lưu vào move_history
   - Echo move back cho player
   ↓
3. handleAIMove():
   - Get position_str = "position fen ... moves a3a4"
   - Get difficulty = EASY
   - Spawn std::thread ai_thread([...] {
       - g_ai_engine.getBestMove(position_str, EASY)
         → Gửi "position fen ... moves a3a4"
         → Gửi "go depth 3"
         → Parse "bestmove h7e7"
       - Parse "h7e7" → MovePayload
       - g_game_state.applyMove(fd, ai_move)
       - Push {player_fd, MOVE, ai_move} vào queue
     })
   - ai_thread.detach() → non-blocking
   ↓
4. Main loop (next iteration):
   - Process g_ai_message_queue
   - Gửi MOVE response về client
```

### 4.4. Move Suggestion

```
1. Client gửi: SUGGEST_MOVE
   ↓
2. handleSuggestMove():
   - Get current_fen từ GameStateManager
   - g_ai_engine.suggestMove(current_fen) → HARD difficulty
   - Parse UCI move → MovePayload
   ↓
3. Gửi SUGGEST_MOVE response về client
```

## 5. Difficulty Levels

Hệ thống hỗ trợ 3 mức độ khó:

| Difficulty | Depth | Time Limit | Use Case |
|------------|-------|------------|----------|
| **EASY**   | 3     | 500ms      | Người chơi mới |
| **MEDIUM** | 5     | 1000ms     | Người chơi trung bình |
| **HARD**   | 8     | 2000ms     | Người chơi giỏi, suggestions |

**Depth**: Độ sâu tìm kiếm trong cây Minimax (càng sâu càng mạnh nhưng chậm hơn)

**Time Limit**: Thời gian tối đa để engine suy nghĩ (fallback nếu depth chưa đạt)

## 6. Đặc Điểm Kỹ Thuật Quan Trọng

### 6.1. Non-Blocking Architecture

**Vấn đề**: `getBestMove()` là blocking operation (0.5-2 giây), nếu gọi trực tiếp sẽ làm server đơ.

**Giải pháp**: 
- Spawn worker thread để gọi `getBestMove()`
- Sử dụng thread-safe message queue để gửi kết quả về main thread
- Main thread process queue mỗi loop iteration

**Kết quả**: Server vẫn responsive khi AI đang suy nghĩ, nhiều games có thể chạy song song.

### 6.2. Move History vs FEN Recalculation

**Vấn đề**: Recalculate FEN sau mỗi move dễ sai sót (capture, move counters, etc.)

**Giải pháp**:
- Lưu `move_history` vector thay vì recalculate FEN
- Generate position string: `position fen <initial> moves <move1> <move2> ...`
- Engine tự tính toán board state (chính xác 100%)

### 6.3. Thread Safety

- `PikafishEngine`: `std::mutex engine_mutex_` cho mọi operations
- `GameStateManager`: `mutable std::mutex games_mutex_` cho state access
- Message Queue: `std::mutex g_ai_queue_mutex` + `std::condition_variable`

### 6.4. Error Handling

- Timeout mechanism trong `readResponse()` để tránh hang
- Check game exists trước khi apply move (client có thể disconnect)
- Try-catch trong AI thread để handle exceptions
- Logging chi tiết với `[AI]` và `[GameState]` prefixes

## 7. Protocol và Format

### 7.1. UCI Protocol (Universal Chess Interface)

**Lưu ý quan trọng về Protocol**:
- **Pikafish sử dụng UCI protocol** (Universal Chess Interface) - giao thức chuẩn cho chess engines
- UCI được thiết kế cho cờ vua nhưng Pikafish đã adapt cho cờ tướng
- Các engine cờ tướng cũ thường dùng **UCCI** (Universal Chinese Chess Interface)
- Code hiện tại đã được test và verify với Pikafish UCI implementation

**UCI Commands**:
```
Engine → Server:
  "uciok"                    // UCI handshake complete
  "readyok"                  // Engine ready
  "bestmove h7e7"            // Best move found
  "info depth 3 score cp 7"  // Search info (optional)

Server → Engine:
  "uci"                      // Initialize UCI protocol
  "isready"                  // Check if ready
  "position fen <fen> moves <move1> <move2> ..."
  "go depth 3"               // Search with depth limit
  "go movetime 500"          // Search with time limit
  "quit"                     // Shutdown engine
```

### 7.2. Move Format Conversion

**UCI Format**: `"a0a1"` (4 characters: from_col, from_row, to_col, to_row)
- Columns: a-i (0-8)
- Rows: 0-9 (bottom to top)

**MovePayload Format**:
```cpp
struct MovePayload {
  string piece;
  Position from;  // {row: 0-9, col: 0-8}
  Position to;    // {row: 0-9, col: 0-8}
};
```

**Conversion Logic**:
- UCI → MovePayload: Parse 4 chars, convert row (9 - row_char) để match coordinate system
- MovePayload → UCI: Convert row/col to chars, reverse row conversion

**Verification**:
- Code đã được test với Pikafish và hoạt động đúng
- Conversion logic: `row = 9 - (row_char - '0')` để match coordinate system của Pikafish
- Format "a0a1" đã được verify với actual Pikafish responses

## 8. Tóm Tắt Vai Trò và Chức Năng

### PikafishEngine
- **Vai trò**: Interface với Pikafish process
- **Chức năng**: 
  - Process management (fork/exec)
  - UCI protocol communication
  - Move generation và suggestion
  - Format conversion

### GameStateManager
- **Vai trò**: Quản lý game state cho AI matches
- **Chức năng**:
  - Game initialization
  - Move history tracking
  - Position string generation
  - State queries

### Server Integration
- **Vai trò**: Tích hợp AI vào server logic
- **Chức năng**:
  - Handle AI match requests
  - Trigger AI moves (non-blocking)
  - Process AI responses
  - Handle move suggestions

## 9. Performance và Scalability

- **Concurrent Games**: Hỗ trợ nhiều AI games cùng lúc nhờ threading
- **Resource Usage**: Mỗi game chỉ spawn 1 thread tạm thời (detach sau khi xong)
- **Memory**: Move history lưu trong memory (nhẹ, ~100 bytes/move)
- **Latency**: AI response time 0.5-2 giây tùy difficulty, không block server

## 10. Các Điểm Cần Lưu Ý và Hạn Chế

### 10.1. Hiệu Năng Engine (Bottleneck)

**Vấn đề**: 
- Hiện tại sử dụng một `g_ai_engine` toàn cục được bảo vệ bởi `std::mutex`
- Khi có nhiều games cùng lúc (ví dụ 10 games), các requests sẽ bị serialized
- Nếu mỗi nước đi tốn 2 giây, người chơi thứ 10 sẽ phải đợi ~20 giây

**Giải pháp hiện tại**:
- Phù hợp cho quy mô đồ án môn học (thường < 10 concurrent games)
- Tiết kiệm tài nguyên (chỉ 1 Pikafish process)

**Giải pháp mở rộng (cho production)**:
1. **Engine Pool**: Tạo pool gồm 5-10 Pikafish processes sẵn sàng
2. **Per-Game Engine**: Mỗi game khởi tạo engine riêng (tốn RAM nhưng nhanh)
3. **Load Balancing**: Phân phối requests đến engine ít tải nhất

### 10.2. Xử Lý Crash (Robustness)

**Vấn đề**: 
- Nếu Pikafish process bị crash, pipe sẽ bị broken
- Ghi vào broken pipe có thể gây SIGPIPE signal → Server crash

**Giải pháp**:
- Thêm `signal(SIGPIPE, SIG_IGN);` trong `main()` để ignore SIGPIPE
- Check pipe status trước khi ghi/đọc
- Implement engine restart mechanism nếu cần

### 10.3. UCI vs UCCI Protocol

**Lưu ý quan trọng**:
- **Pikafish sử dụng UCI protocol** (Universal Chess Interface) - tương tự cờ vua nhưng áp dụng cho cờ tướng
- Các engine cờ tướng cũ thường dùng **UCCI** (Universal Chinese Chess Interface)
- Cần đảm bảo conversion logic khớp với quy ước tọa độ của Pikafish:
  - Files: a-i (0-8)
  - Ranks: 0-9 (có thể đảo ngược tùy engine)
  - Format: "a0a1" (from_col, from_row, to_col, to_row)

**Verification**:
- Code hiện tại đã test với Pikafish và hoạt động đúng
- Conversion logic: `row = 9 - (row_char - '0')` để match coordinate system

## 11. Kết Luận

Hệ thống AI được thiết kế với:
- ✅ **Non-blocking architecture** - Server luôn responsive
- ✅ **Thread-safe** - An toàn với concurrent access
- ✅ **Reliable state management** - Sử dụng move history thay vì recalculate
- ✅ **Flexible difficulty** - 3 levels phù hợp với mọi trình độ
- ✅ **Error handling** - Robust với timeout và exception handling
- ✅ **Production-ready** - Sẵn sàng cho deployment (với lưu ý về scalability)

**Hạn chế hiện tại**:
- ⚠️ Single engine instance (bottleneck với nhiều concurrent games)
- ⚠️ Chưa có engine restart mechanism
- ⚠️ Cần thêm SIGPIPE handling để tăng robustness

**Hướng phát triển**:
- Engine pool cho scalability
- Health check và auto-restart cho engine
- Metrics và monitoring cho performance tracking

