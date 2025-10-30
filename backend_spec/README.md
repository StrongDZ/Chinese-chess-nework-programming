# Backend Spec (Project Root)

Tài liệu và đặc tả cho 3 hạng mục:
1) Account Registration (SIGNUP)
2) Validate Move Legality (authoritative)
3) Determine Game Result (+ ELO)

Cấu trúc thư mục:
```
backend_spec/
  signup/
    README.md           # Hướng dẫn triển khai
    schema.sql          # Bảng users, user_stats, index
    SIGNUP_PACKET.md    # Đặc tả gói SIGNUP
  legality/
    README.md           # Luồng validate move (server authoritative)
    MOVE_PACKET.md      # Đặc tả MovePacket/MOVE_ACCEPTED/MOVE_REJECTED
  result/
    README.md           # Phát hiện kết quả ván + kết sổ
    GAME_END_PACKET.md  # Đặc tả GAME_END
    ELO.md              # Công thức ELO (K=32)
```

Gợi ý triển khai theo thứ tự: signup → legality → result. Mỗi README có checklist cụ thể.
