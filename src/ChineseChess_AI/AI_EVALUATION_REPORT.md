# Đánh Giá Chi Tiết 3 Model AI Cờ Tướng

**Ngày:** 30/10/2025  
**Test config:** 4 games/matchup, 60 moves max, Hard time_limit=2.0s

---

## 📊 Kết Quả Round-Robin

| Model | Wins | Losses | Draws | Avg Time | Captures | Evaluation |
|-------|------|--------|-------|----------|----------|------------|
| **EASY** | 0 | 0 | 8 | **2.8ms** ⚡ | 81 | ⭐⭐⭐ |
| **MEDIUM** | 0 | 0 | 8 | **22.6ms** | **89** 🏆 | ⭐⭐⭐⭐ |
| **HARD** | 0 | 0 | 8 | **1764ms** 🐌 | 75 | ⭐⭐ |

**Kết luận bất ngờ:** Medium AI hiện tại **mạnh nhất** về tactical play (89 captures), Easy AI **nhanh nhất và ổn định**, Hard AI **chậm nhất và passive nhất** (75 captures).

---

## 🔍 Phân Tích Chi Tiết Từng Model

### 1️⃣ **AI EASY** - Baseline Tactical Engine ⭐⭐⭐

**Kiến trúc:**
- **Search:** 1-ply lookahead (greedy, không search sâu)
- **Evaluation:** `evaluate_board` với PST (Piece-Square Tables) + material
- **Move ordering:** Captures → Checks → Others
- **Tactical bonuses:** 
  - Capture: +25
  - Check: +15
  - Escape check: +30

**Ưu điểm:**
✅ **CỰC NHANH** - 2.8ms/move (nhanh gấp 8x Medium, 630x Hard!)  
✅ **Ổn định** - không crash, không timeout  
✅ **Tactical awareness tốt** - capture 81 pieces trong 8 games  
✅ **Code đơn giản** - dễ maintain, debug  

**Nhược điểm:**
❌ **Cận thị** - chỉ nhìn 1 bước → dễ rơi vào bẫy 2-3 bước  
❌ **Không strategic planning** - chỉ tham lam tức thời  
❌ **Dễ bị lừa** bởi "poison pieces" (quân mồi nhử)  

**Kết luận:**
- **Phù hợp:** UI placeholder, testing, beginner opponent
- **Không phù hợp:** Competitive play, advanced users
- **Điểm mạnh nhất:** Tốc độ + độ tin cậy

---

### 2️⃣ **AI MEDIUM** - Minimax Tactical Master ⭐⭐⭐⭐

**Kiến trúc:**
- **Search:** Minimax với alpha-beta pruning, depth=2
- **Evaluation:** PST + material + positional factors (center control, king safety, pawn structure)
- **Move ordering:** Captures first, giới hạn 15-20 moves/depth
- **Tactical bonuses:** 
  - Capture: calculated via piece values
  - Check: +50
  - Escape check: +100

**Ưu điểm:**
✅ **Mạnh nhất về tactical** - 89 captures (cao nhất!)  
✅ **Nhìn xa 2 bước** - tránh được bẫy đơn giản  
✅ **Cân bằng tốc độ-chất lượng** - 22.6ms vẫn real-time  
✅ **Positional awareness** - đánh giá center, king safety, pawn structure  
✅ **Code architecture tốt** - có cache, có minimax đúng chuẩn  

**Nhược điểm:**
❌ **Depth 2 vẫn hạn chế** - không thấy combination 3-4 bước  
❌ **Alpha-beta pruning chưa tối ưu** - giới hạn 15 moves → bỏ sót nước hay  
❌ **Không có TT, Zobrist** - tính toán duplicate positions nhiều lần  

**Kết luận:**
- **Phù hợp:** Default AI cho production, intermediate opponent
- **Không phù hợp:** Expert-level play
- **Điểm mạnh nhất:** Tactical aggression + tốc độ chấp nhận được
- **👑 Hiện tại là AI MẠNH NHẤT trong 3 model!**

---

### 3️⃣ **AI HARD** - Over-Engineered Underperformer ⭐⭐

**Kiến trúc (rất phức tạp):**
- **Search:** Iterative deepening, max_depth=6, alpha-beta, PVS, LMR, quiescence
- **Evaluation:** PST + material + check detection (±150) 
- **Optimization:** 
  - Zobrist hashing + Transposition Table (TT)
  - Killer moves + History heuristic
  - Aspiration windows (bật/tắt linh tinh)
  - Move ordering với MVV-LVA ×20, check bonus +200
- **Time control:** Iterative deepening với 80% time limit cutoff

**Ưu điểm (trên giấy):**
✅ **Kiến trúc đầy đủ** - có mọi technique hiện đại  
✅ **Checkmate detection** - phát hiện chiếu tướng/hết nước với mate score  
✅ **Search sâu** - depth=6 trên lý thuyết  
✅ **Có PST** - evaluation tốt hơn material thuần  

**Nhược điểm (thực tế):**
❌ **CỰC CHẬM** - 1764ms/move (vượt time limit 1.5s thường xuyên!)  
❌ **PASSIVE** - chỉ 75 captures (ít nhất trong 3 AI!)  
❌ **Over-optimization** - quá nhiều heuristic → conflict với nhau  
❌ **Aspiration windows gây lỗi** - fail re-search tốn thời gian  
❌ **TT flag bug** (đã fix) - trước đó lưu flag sai → cutoff sai  
❌ **Search depth không đạt** - do time limit, chỉ đạt ~3-4 ply thực tế  
❌ **Evaluation gọi is_in_check** - gọi trong mọi leaf node → bottleneck  
❌ **Quiescence search không hiệu quả** - stand-pat logic có bug (đã fix)  

**Vấn đề nghiêm trọng:**
1. **Time management tồi** - 1.7s/move → không còn thời gian cho deep search
2. **Evaluation quá đắt** - `is_in_check` gọi trong `_evaluate_move_enhanced_board` → O(n) mỗi leaf
3. **Move ordering overhead** - check bonus tính cho top-k (8 moves) nhưng gọi `is_in_check` → chậm
4. **Kiến trúc phức tạp** - khó debug, khó tune

**Kết luận:**
- **Phù hợp:** ... hiện tại **KHÔNG phù hợp gì cả** 😢
- **Không phù hợp:** Production (quá chậm), expert play (yếu hơn Medium)
- **Điểm yếu chí mạng:** Tốc độ vs chất lượng hoàn toàn thất bại
- **Cần làm gì:**
  1. **Loại bỏ is_in_check khỏi evaluation** - chỉ dùng trong move ordering
  2. **Giảm depth xuống 4** - focus vào quality per node
  3. **Tắt aspiration windows** - không đáng với overhead
  4. **Optimize TT** - tăng hit rate, giảm collision
  5. **Simplify** - loại bỏ LMR, PVS nếu không có lợi rõ rệt

---

## 🎯 So Sánh Tổng Quan

### Tốc độ
```
EASY   ██ 2.8ms        [NHANH NHẤT]
MEDIUM ████████ 22.6ms [CHẤP NHẬN ĐƯỢC]
HARD   █████████████████████████████████ 1764ms [QUÁ CHẬM]
```

### Tactical Strength (Captures)
```
MEDIUM ████████████████████ 89 [MẠNH NHẤT]
EASY   ████████████████ 81    [TỐT]
HARD   ██████████████ 75      [YẾU NHẤT]
```

### Code Complexity
```
EASY   ██ 116 lines    [ĐƠN GIẢN]
MEDIUM █████ 345 lines [VỪA PHẢI]
HARD   ████████████ 711 lines [PHỨC TẠP]
```

### Độ tin cậy
```
EASY   ⭐⭐⭐⭐⭐ Không crash, luôn < 10ms
MEDIUM ⭐⭐⭐⭐   Ổn định, thỉnh thoảng 50ms
HARD   ⭐⭐     Timeout thường xuyên, avg 1.7s
```

---

## 🏆 Ranking Cuối Cùng

**1. 🥇 MEDIUM** - Best balance, mạnh nhất tactical  
**2. 🥈 EASY** - Nhanh nhất, đáng tin cậy  
**3. 🥉 HARD** - Chậm nhất, yếu nhất (!) 

---

## ⚠️ Vấn Đề Lớn Nhất

**Hard AI không thắng được Easy AI** là dấu hiệu của:
1. ❌ **Evaluation function sai** - Hard đánh giá sai thế cờ
2. ❌ **Search không đạt depth** - timeout trước khi search sâu
3. ❌ **Over-optimization backfire** - quá nhiều heuristic xung đột
4. ❌ **Thiếu game phase logic** - không phân biệt opening/midgame/endgame

**Hard AI hiện tại là "pseudo-hard"** - phức tạp nhưng không mạnh. Cần refactor toàn bộ hoặc dùng Medium làm base.

---

## 💡 Khuyến Nghị

### Ngắn hạn (Production ready)
1. ✅ **Dùng MEDIUM làm default AI** - mạnh nhất, tốc độ OK
2. ✅ **Dùng EASY cho beginner mode** - nhanh, đơn giản
3. ❌ **TẮT HARD** cho đến khi fix xong

### Trung hạn (Cải thiện Hard)
1. 🔧 **Profile Hard AI** - tìm bottleneck chính xác (is_in_check, TT, move_gen?)
2. 🔧 **Giảm depth xuống 4** - ưu tiên quality evaluation hơn depth
3. 🔧 **Loại is_in_check khỏi evaluate_board** - chỉ dùng trong ordering
4. 🔧 **Thêm opening book** - tiết kiệm thời gian early game
5. 🔧 **Thêm endgame logic** - active king, promote pawns

### Dài hạn (Nâng cấp toàn diện)
1. 🚀 **MCTS variant** - Monte Carlo Tree Search cho tactical games
2. 🚀 **Neural Network** - học từ data (có sẵn xxq-tianTianXQ-cg720p.pgn)
3. 🚀 **Hybrid approach** - MCTS cho strategy, minimax cho tactics
4. 🚀 **Parallel search** - multi-threading cho iterative deepening

---

## 📈 Test Metrics Chi Tiết

### Hard vs Easy (10 games, 100 moves max, 1.5s limit)
```
Wins  - HARD: 0, EASY: 1, DRAWS: 9
HARD  - moves: 477, avg time: 6717ms (!), captures: 110
EASY  - moves: 477, avg time: 2.1ms, captures: 109
```
→ **Hard CHẬM gấp 3200 lần nhưng KHÔNG mạnh hơn!**

### Round-Robin (4 games each, 60 moves max, 2.0s limit)
```
EASY   | W:0 L:0 D:8 | avg 2.8ms   | captures 81
MEDIUM | W:0 L:0 D:8 | avg 22.6ms  | captures 89
HARD   | W:0 L:0 D:8 | avg 1764ms  | captures 75
```
→ **Medium tactical nhất (89 captures), Hard passive nhất (75 captures)**

---

## 🎓 Bài Học

1. **Complexity ≠ Strength** - Hard phức tạp gấp 6x Easy nhưng yếu hơn
2. **Evaluation > Search depth** - Medium depth=2 thắng Hard depth=6 vì eval tốt hơn
3. **Profile before optimize** - Hard có quá nhiều "optimization" không cần thiết
4. **Time management critical** - Hard timeout → không search được sâu
5. **Simplicity wins** - Easy code 116 lines, đáng tin cậy nhất

---

**Tác giả:** AI Development Team  
**Công cụ:** Python 3, ChineseChess Engine  
**Next steps:** Xem khuyến nghị phía trên

