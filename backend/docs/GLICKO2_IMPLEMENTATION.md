# Glicko-2 Rating System Implementation

## Mục lục
1. [Tổng quan](#1-tổng-quan)
2. [Cấu trúc dữ liệu](#2-cấu-trúc-dữ-liệu)
3. [Repository Layer](#3-repository-layer)
4. [Service Layer - Thuật toán Glicko-2](#4-service-layer---thuật-toán-glicko-2)
5. [Ví dụ tính toán](#5-ví-dụ-tính-toán)

---

## 1. Tổng quan

### 1.1 Glicko-2 là gì?

Glicko-2 là hệ thống rating được phát triển bởi Mark Glickman, cải tiến từ ELO bằng cách thêm:
- **Rating Deviation (RD)**: Độ không chắc chắn của rating
- **Volatility (σ)**: Độ ổn định của rating theo thời gian

### 1.2 So sánh ELO vs Glicko-2

| Khía cạnh | ELO | Glicko-2 |
|-----------|-----|----------|
| Giá trị lưu trữ | Rating | Rating + RD + Volatility |
| Độ chắc chắn | Không biết | RD cho biết độ tin cậy |
| Người chơi mới | Thay đổi giống người cũ | Thay đổi nhiều hơn (RD cao) |
| Người chơi lâu | Thay đổi giống người mới | Thay đổi ít hơn (RD thấp) |

### 1.3 Files liên quan

```
backend/
├── include/game/
│   └── game_repository.h      # Định nghĩa PlayerGlickoStats struct
├── src/game/
│   ├── game_repository.cpp    # Lưu/lấy stats từ MongoDB
│   └── game_service.cpp       # Thuật toán Glicko-2
└── tests/
    └── test_glicko2.cpp       # Unit tests
```

---

## 2. Cấu trúc dữ liệu

### 2.1 PlayerGlickoStats Struct

**File: `include/game/game_repository.h`**

```cpp
struct PlayerGlickoStats {
  int rating;        // Điểm rating (mặc định: 1500)
  double rd;         // Rating Deviation (mặc định: 350.0)
  double volatility; // Độ biến động (mặc định: 0.06)
};
```

### 2.2 Ý nghĩa các giá trị

| Field | Mặc định | Min | Max | Ý nghĩa |
|-------|----------|-----|-----|---------|
| `rating` | 1500 | - | - | Điểm kỹ năng ước tính |
| `rd` | 350.0 | 30.0 | 350.0 | Độ không chắc chắn của rating |
| `volatility` | 0.06 | 0.01 | 0.15 | Mức độ rating thay đổi gần đây |

### 2.3 MongoDB Schema

```json
{
  "_id": ObjectId("..."),
  "username": "player1",
  "time_control": "blitz",      // hoặc "classical"
  "rating": 1500,
  "rd": 350.0,
  "volatility": 0.06,
  "highest_rating": 1500,
  "lowest_rating": 1500,
  "total_games": 0,
  "wins": 0,
  "losses": 0,
  "draws": 0
}
```

---

## 3. Repository Layer

### 3.1 getPlayerGlickoStats()

**File: `src/game/game_repository.cpp`**

```cpp
GameRepository::PlayerGlickoStats GameRepository::getPlayerGlickoStats(
    const string &username, const string &timeControl) {
  
  PlayerGlickoStats stats;
  // Giá trị mặc định cho người chơi mới
  stats.rating = 1500;
  stats.rd = 350.0;
  stats.volatility = 0.06;

  try {
    auto db = mongoClient.getDatabase();
    auto statsCol = db["player_stats"];

    // Tìm trong database
    auto result = statsCol.find_one(
        document{} << "username" << username 
                   << "time_control" << timeControl 
                   << finalize);

    if (result) {
      auto view = result->view();
      // Lấy giá trị từ database nếu tồn tại
      if (view["rating"]) {
        stats.rating = view["rating"].get_int32().value;
      }
      if (view["rd"]) {
        stats.rd = view["rd"].get_double().value;
      }
      if (view["volatility"]) {
        stats.volatility = view["volatility"].get_double().value;
      }
    }
  } catch (const exception &e) {
    cerr << "[getPlayerGlickoStats] Error: " << e.what() << endl;
  }

  return stats;
}
```

**Giải thích:**
1. Khởi tạo giá trị mặc định (cho người chơi mới)
2. Query MongoDB theo `username` + `time_control`
3. Nếu tìm thấy → lấy rating, rd, volatility từ DB
4. Nếu không tìm thấy → trả về giá trị mặc định

### 3.2 updatePlayerStats()

```cpp
bool GameRepository::updatePlayerStats(
    const string &username,
    const string &timeControl, 
    int newRating,
    double newRD,           // MỚI: cập nhật RD
    double newVolatility,   // MỚI: cập nhật volatility
    const string &resultField) {
  
  try {
    auto db = mongoClient.getDatabase();
    auto stats = db["player_stats"];

    // MongoDB update document
    auto updateDoc = document{}
      << "$set" << open_document 
        << "rating" << newRating
        << "rd" << newRD              // Lưu RD mới
        << "volatility" << newVolatility  // Lưu volatility mới
        << "username" << username
        << "time_control" << timeControl
      << close_document 
      << "$inc" << open_document
        << "total_games" << 1 
        << resultField << 1           // wins/losses/draws
      << close_document
      << "$max" << open_document 
        << "highest_rating" << newRating
      << close_document 
      << "$min" << open_document 
        << "lowest_rating" << newRating
      << close_document
      << "$setOnInsert" << open_document
        << "win_streak" << 0
        << "longest_win_streak" << 0
      << close_document
      << finalize;

    mongocxx::options::update options;
    options.upsert(true);  // Tạo mới nếu chưa tồn tại

    auto result = stats.update_one(
        document{} << "username" << username 
                   << "time_control" << timeControl 
                   << finalize,
        updateDoc.view(), options);

    return result && (result->matched_count() > 0 || result->upserted_id());

  } catch (const exception &e) {
    cerr << "[updatePlayerStats] Error: " << e.what() << endl;
    return false;
  }
}
```

**Giải thích:**
1. **$set**: Cập nhật rating, RD, volatility mới
2. **$inc**: Tăng total_games và wins/losses/draws
3. **$max/$min**: Cập nhật highest/lowest rating nếu cần
4. **upsert(true)**: Tự động tạo document mới nếu chưa tồn tại

---

## 4. Service Layer - Thuật toán Glicko-2

### 4.1 Hằng số Glicko-2

**File: `src/game/game_service.cpp`**

```cpp
// ============ Glicko-2 Constants ============
static const double GLICKO2_SCALE = 173.7178;   // Hệ số chuyển đổi scale
static const double GLICKO2_TAU = 0.5;          // Hằng số hệ thống (τ)
static const double GLICKO2_EPSILON = 0.000001; // Độ chính xác Illinois
static const double PI = 3.14159265358979323846;
```

| Hằng số | Giá trị | Ý nghĩa |
|---------|---------|---------|
| `GLICKO2_SCALE` | 173.7178 | Chuyển đổi giữa Glicko và Glicko-2 scale |
| `GLICKO2_TAU` | 0.5 | Giới hạn thay đổi volatility (0.3-1.2) |
| `GLICKO2_EPSILON` | 0.000001 | Độ chính xác hội tụ của Illinois algorithm |

### 4.2 Hàm chuyển đổi Scale

```cpp
// Glicko → Glicko-2 scale
static double toGlicko2Scale(double rating) {
  return (rating - 1500.0) / GLICKO2_SCALE;
}

// RD → Glicko-2 scale
static double rdToGlicko2Scale(double rd) {
  return rd / GLICKO2_SCALE;
}

// Glicko-2 → Glicko scale
static double fromGlicko2Scale(double mu) {
  return mu * GLICKO2_SCALE + 1500.0;
}

// Glicko-2 RD → Glicko RD
static double rdFromGlicko2Scale(double phi) {
  return phi * GLICKO2_SCALE;
}
```

**Công thức:**
- μ = (r - 1500) / 173.7178
- φ = RD / 173.7178
- r = μ × 173.7178 + 1500
- RD = φ × 173.7178

### 4.3 Hàm g(φ) - Giảm ảnh hưởng của RD đối thủ

```cpp
static double g(double phi) {
  return 1.0 / sqrt(1.0 + 3.0 * phi * phi / (PI * PI));
}
```

**Công thức:** g(φ) = 1 / √(1 + 3φ²/π²)

**Ý nghĩa:**
- Khi φ (RD) của đối thủ **cao** → g(φ) **nhỏ** → ảnh hưởng ít hơn
- Khi φ (RD) của đối thủ **thấp** → g(φ) **lớn** → ảnh hưởng nhiều hơn

### 4.4 Hàm E() - Xác suất thắng (Expected Score)

```cpp
static double E(double mu, double mu_j, double phi_j) {
  return 1.0 / (1.0 + exp(-g(phi_j) * (mu - mu_j)));
}
```

**Công thức:** E = 1 / (1 + e^(-g(φⱼ) × (μ - μⱼ)))

**Ý nghĩa:**
- E = 0.5: Hai người chơi ngang tài
- E > 0.5: Bạn mạnh hơn
- E < 0.5: Đối thủ mạnh hơn

**Ví dụ:**
| Rating bạn | Rating đối thủ | E (xác suất thắng) |
|------------|----------------|-------------------|
| 1500 | 1500 | 50% |
| 1700 | 1500 | ~76% |
| 1500 | 1700 | ~24% |

### 4.5 Illinois Algorithm - Cập nhật Volatility

```cpp
static double computeNewVolatility(double sigma, double phi, double v, double delta) {
  double a = log(sigma * sigma);
  double tau2 = GLICKO2_TAU * GLICKO2_TAU;
  double phi2 = phi * phi;
  double delta2 = delta * delta;
  
  // Hàm f(x) cần tìm nghiệm
  auto f = [&](double x) -> double {
    double ex = exp(x);
    double num = ex * (delta2 - phi2 - v - ex);
    double denom = 2.0 * pow(phi2 + v + ex, 2);
    return num / denom - (x - a) / tau2;
  };
  
  // Khởi tạo bounds cho Illinois
  double A = a;
  double B;
  
  if (delta2 > phi2 + v) {
    B = log(delta2 - phi2 - v);
  } else {
    int k = 1;
    while (f(a - k * GLICKO2_TAU) < 0) {
      k++;
      if (k > 100) break;  // Safety limit
    }
    B = a - k * GLICKO2_TAU;
  }
  
  double fA = f(A);
  double fB = f(B);
  
  // Illinois algorithm iteration
  int iter = 0;
  while (abs(B - A) > GLICKO2_EPSILON && iter < 100) {
    double C = A + (A - B) * fA / (fB - fA);
    double fC = f(C);
    
    if (fC * fB <= 0) {
      A = B;
      fA = fB;
    } else {
      fA = fA / 2.0;  // Illinois modification
    }
    
    B = C;
    fB = fC;
    iter++;
  }
  
  return exp(A / 2.0);  // σ' = e^(A/2)
}
```

**Giải thích:**
1. **Illinois algorithm** là biến thể của Regula Falsi method
2. Tìm nghiệm x sao cho f(x) = 0
3. Volatility mới σ' = e^(x/2)
4. Modification `fA = fA / 2.0` giúp hội tụ nhanh hơn

### 4.6 Hàm chính: calculateAndUpdateRatings()

```cpp
void GameService::calculateAndUpdateRatings(
    const string &redUsername,
    const string &blackUsername,
    const string &result,
    const string &timeControl) {
  
  // ========== BƯỚC 0: Lấy stats hiện tại ==========
  auto redStats = repository.getPlayerGlickoStats(redUsername, timeControl);
  auto blackStats = repository.getPlayerGlickoStats(blackUsername, timeControl);

  // Tính điểm thực tế
  double redScore = (result == "red_win") ? 1.0
                    : (result == "draw")  ? 0.5
                                          : 0.0;
  double blackScore = 1.0 - redScore;

  // ========== BƯỚC 1: Chuyển sang Glicko-2 scale ==========
  double red_mu = toGlicko2Scale(redStats.rating);
  double red_phi = rdToGlicko2Scale(redStats.rd);
  double red_sigma = redStats.volatility;
  
  double black_mu = toGlicko2Scale(blackStats.rating);
  double black_phi = rdToGlicko2Scale(blackStats.rd);
  double black_sigma = blackStats.volatility;
  
  // ========== BƯỚC 2: Tính g(φ) của đối thủ ==========
  double red_g_opponent = g(black_phi);
  double black_g_opponent = g(red_phi);
  
  // ========== BƯỚC 3: Tính E (xác suất thắng) ==========
  double red_E = E(red_mu, black_mu, black_phi);
  double black_E = E(black_mu, red_mu, red_phi);
  
  // ========== BƯỚC 4: Tính v (estimated variance) ==========
  double red_v = 1.0 / (red_g_opponent * red_g_opponent * red_E * (1.0 - red_E));
  double black_v = 1.0 / (black_g_opponent * black_g_opponent * black_E * (1.0 - black_E));
  
  // ========== BƯỚC 5: Tính Δ (improvement) ==========
  double red_delta = red_v * red_g_opponent * (redScore - red_E);
  double black_delta = black_v * black_g_opponent * (blackScore - black_E);
  
  // ========== BƯỚC 6: Cập nhật volatility (Illinois) ==========
  double red_sigma_new = computeNewVolatility(red_sigma, red_phi, red_v, red_delta);
  double black_sigma_new = computeNewVolatility(black_sigma, black_phi, black_v, black_delta);
  
  // ========== BƯỚC 7: Cập nhật RD ==========
  // φ* = √(φ² + σ'²)
  double red_phi_star = sqrt(red_phi * red_phi + red_sigma_new * red_sigma_new);
  double black_phi_star = sqrt(black_phi * black_phi + black_sigma_new * black_sigma_new);
  
  // φ' = 1 / √(1/φ*² + 1/v)
  double red_phi_new = 1.0 / sqrt(1.0 / (red_phi_star * red_phi_star) + 1.0 / red_v);
  double black_phi_new = 1.0 / sqrt(1.0 / (black_phi_star * black_phi_star) + 1.0 / black_v);
  
  // ========== BƯỚC 8: Cập nhật rating ==========
  // μ' = μ + φ'² × g(φⱼ) × (s - E)
  double red_mu_new = red_mu + red_phi_new * red_phi_new * red_g_opponent * (redScore - red_E);
  double black_mu_new = black_mu + black_phi_new * black_phi_new * black_g_opponent * (blackScore - black_E);
  
  // ========== BƯỚC 9: Chuyển về Glicko scale và clamp ==========
  int redNewRating = static_cast<int>(round(fromGlicko2Scale(red_mu_new)));
  int blackNewRating = static_cast<int>(round(fromGlicko2Scale(black_mu_new)));
  double redNewRD = max(30.0, min(350.0, rdFromGlicko2Scale(red_phi_new)));
  double blackNewRD = max(30.0, min(350.0, rdFromGlicko2Scale(black_phi_new)));
  
  // Clamp volatility
  red_sigma_new = max(0.01, min(0.15, red_sigma_new));
  black_sigma_new = max(0.01, min(0.15, black_sigma_new));

  // Xác định kết quả
  string redField = (result == "red_win") ? "wins"
                    : (result == "draw")  ? "draws"
                                          : "losses";
  string blackField = (result == "black_win") ? "wins"
                      : (result == "draw")    ? "draws"
                                              : "losses";

  // ========== BƯỚC 10: Lưu vào database ==========
  repository.updatePlayerStats(redUsername, timeControl, redNewRating, 
                               redNewRD, red_sigma_new, redField);
  repository.updatePlayerStats(blackUsername, timeControl, blackNewRating,
                               blackNewRD, black_sigma_new, blackField);
  
  // Log
  cout << "[Glicko-2] " << redUsername << ": " << redStats.rating 
       << " -> " << redNewRating
       << " (RD: " << redStats.rd << " -> " << redNewRD << ")" << endl;
  cout << "[Glicko-2] " << blackUsername << ": " << blackStats.rating 
       << " -> " << blackNewRating
       << " (RD: " << blackStats.rd << " -> " << blackNewRD << ")" << endl;
}
```

---

## 5. Ví dụ tính toán

### 5.1 Ví dụ 1: Hai người chơi mới, A thắng

**Trước trận:**
- Player A: Rating=1500, RD=350, σ=0.06
- Player B: Rating=1500, RD=350, σ=0.06

**Tính toán:**
1. Chuyển scale: μ_A = μ_B = 0, φ_A = φ_B = 2.015
2. g(φ_B) = 0.724
3. E = 0.5 (equal strength)
4. v = 11.31
5. Δ_A = 11.31 × 0.724 × (1 - 0.5) = 4.09
6. Cập nhật σ, φ, μ...

**Sau trận:**
- Player A: Rating=**1662**, RD=290, σ=0.06
- Player B: Rating=**1338**, RD=290, σ=0.06

### 5.2 Ví dụ 2: Người mới vs Người cũ

**Trước trận:**
- New Player: Rating=1500, RD=350 (không chắc chắn)
- Veteran: Rating=1500, RD=50 (rất chắc chắn)

**Sau trận (New Player thắng):**
- New Player: Rating=**1675** (+175), RD=248
- Veteran: Rating=**1495** (-5), RD=50

**Giải thích:**
- New Player có RD cao → rating thay đổi nhiều (+175)
- Veteran có RD thấp → rating thay đổi ít (-5)
- Hệ thống "không chắc chắn" về New Player nên điều chỉnh mạnh hơn

### 5.3 Ví dụ 3: RD giảm theo số trận

| Số trận | RD |
|---------|-----|
| 0 | 350 |
| 1 | 267 |
| 2 | 229 |
| 3 | 200 |
| 4 | 181 |
| 5 | 166 |

**Kết luận:** Càng chơi nhiều → RD càng thấp → Rating càng ổn định

---

## Tóm tắt Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    GLICKO-2 FLOW                            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. Game kết thúc                                           │
│         ↓                                                   │
│  2. Gọi calculateAndUpdateRatings()                         │
│         ↓                                                   │
│  3. Lấy stats từ DB (getPlayerGlickoStats)                  │
│         ↓                                                   │
│  4. Chuyển sang Glicko-2 scale (μ, φ, σ)                    │
│         ↓                                                   │
│  5. Tính g(φ), E, v, Δ                                      │
│         ↓                                                   │
│  6. Cập nhật σ bằng Illinois algorithm                      │
│         ↓                                                   │
│  7. Cập nhật φ (RD) và μ (rating)                           │
│         ↓                                                   │
│  8. Chuyển về Glicko scale + clamp                          │
│         ↓                                                   │
│  9. Lưu vào DB (updatePlayerStats)                          │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

*Document created: January 2026*
*Author: Group 5 - Network Programming Course*

## GLICKO-2 RATING - BẢN TÓM TẮT (ĐỌC KHI THẦY HỎI)

### 1. Glicko-2 là gì? (Khác ELO thế nào?)

| ELO truyền thống | Glicko-2 |
|------------------|----------|
| Chỉ có 1 số: **Rating** | Có 3 số: **Rating + RD + Volatility** |
| Không biết độ tin cậy | RD cho biết hệ thống "chắc chắn" bao nhiêu |
| Người mới = người cũ | Người mới thay đổi nhiều hơn |

### 2. Ba thành phần của Glicko-2

| Thành phần | Ý nghĩa | Mặc định |
|------------|---------|----------|
| **Rating (r)** | Điểm kỹ năng | 1500 |
| **RD (φ)** | Độ không chắc chắn (cao = chưa biết rõ skill) | 350 |
| **Volatility (σ)** | Độ ổn định gần đây (cao = kết quả thất thường) | 0.06 |

**Ví dụ dễ hiểu:**
- Player mới: Rating 1500, RD **350** → Hệ thống "không chắc" về player này
- Player 100 trận: Rating 1500, RD **50** → Hệ thống "rất chắc" đây là skill thật

### 3. Thuật toán hoạt động + Công thức

**TRƯỚC TRẬN:** Lấy rating (r), RD, volatility (σ) của 2 người chơi từ Database

**TÍNH TOÁN:**

| Bước | Công thức | Ý nghĩa |
|------|-----------|---------|
| 1. Chuyển scale | `μ = (r - 1500) / 173.7178` | Chuyển rating sang thang Glicko-2 |
| | `φ = RD / 173.7178` | Chuyển RD sang thang Glicko-2 |
| 2. Tính g(φ) | `g(φ) = 1 / √(1 + 3φ²/π²)` | Giảm ảnh hưởng nếu đối thủ có RD cao |
| 3. Tính E | `E = 1 / (1 + e^(-g(φ) × (μ - μⱼ)))` | Xác suất thắng (0.5 = ngang tài) |
| 4. Tính v | `v = 1 / (g(φ)² × E × (1-E))` | Estimated variance |
| 5. Tính Δ | `Δ = v × g(φ) × (s - E)` | s = kết quả (1/0.5/0), E = kỳ vọng |
| 6. Cập nhật σ | Illinois algorithm | Volatility mới |
| 7. Cập nhật φ | `φ* = √(φ² + σ'²)` | Pre-rating period RD |
| | `φ' = 1 / √(1/φ*² + 1/v)` | RD mới (giảm sau mỗi trận) |
| 8. Cập nhật μ | `μ' = μ + φ'² × g(φⱼ) × (s - E)` | Rating mới |
| 9. Chuyển về | `r' = μ' × 173.7178 + 1500` | Rating hiển thị |
| | `RD' = φ' × 173.7178` | RD hiển thị |

**SAU TRẬN:** Lưu rating, RD, volatility mới vào Database


### 4. Tại sao RD quan trọng?

**Ví dụ thực tế:**

| Trường hợp | Kết quả |
|------------|---------|
| Player mới (RD=350) thắng Player cũ (RD=50) | Player mới: **+175 điểm** (thay đổi lớn) |
| | Player cũ: **-5 điểm** (thay đổi nhỏ) |

**Giải thích:** Hệ thống "không chắc" về player mới → điều chỉnh mạnh. Hệ thống "rất chắc" về player cũ → điều chỉnh nhẹ.

### 5. RD giảm theo số trận

| Số trận đã chơi | RD |
|-----------------|-----|
| 0 (mới) | 350 |
| 5 trận | 166 |
| 10+ trận | ~50-80 |

**Kết luận:** Càng chơi nhiều → RD càng thấp → Rating càng ổn định

### 6. Separate Ratings

Mỗi player có **2 rating riêng biệt**:
- **Classical Rating** - Cho game không giới hạn thời gian
- **Blitz Rating** - Cho game có timer

### 7. Một câu tóm tắt

> **"Glicko-2 thông minh hơn ELO vì nó biết KHÔNG CHẮC CHẮN bao nhiêu về rating của mỗi người chơi, từ đó điều chỉnh phù hợp."**