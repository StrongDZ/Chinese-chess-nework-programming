# ELO Rating (K=32)

- expectedScore = 1 / (1 + 10^(Δ/400))
- newElo = oldElo + K * (actualScore - expectedScore)
  - actualScore: win=1, draw=0.5, loss=0
- K đề xuất: 32 (có thể 24 cho người có ELO cao)

Ví dụ:
- Red 1450 thắng Black 1435:
  - Δ = 1435 - 1450 = -15 ⇒ expectedRed = 1 / (1 + 10^(-15/400)) ≈ 0.521
  - redNew = 1450 + 32 * (1 - 0.521) ≈ 1465 (+15)
  - blackNew = 1435 - 32 * (1 - 0.521) ≈ 1420 (-15)
