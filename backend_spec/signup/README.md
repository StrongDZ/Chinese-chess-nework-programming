# SIGNUP - Hướng dẫn triển khai

## 1) Bảng & Chỉ mục
Xem `schema.sql`. Tổng quan:
- `users(id UUID pk, username unique, email unique, password_hash, elo=1200, status, created_at, last_login)`
- `user_stats(user_id fk, total_games, wins, losses, draws, last_game)`
- Unique index: LOWER(username), LOWER(email)

## 2) Quy tắc bảo mật
- Hash: `bcrypt`/`argon2` (không dùng SHA-256 thuần)
- Không log plaintext password
- Rate limiting: 10 req/5 phút mỗi IP/User

## 3) Validate input
- username: 3–20, `[A-Za-z0-9._-]`
- email: định dạng hợp lệ
- password: ≥ 8, có chữ hoa, chữ thường, số

## 4) Luồng xử lý
1. Validate input (client + server)
2. Kiểm tra trùng username/email
3. Hash password
4. Insert `users`, init `user_stats`
5. Trả `SUCCESS` | `FAIL {msg}`

## 5) Pseudo-code
```python
if not valid_username(u) or not valid_email(e) or not strong_password(p):
    return FAIL("INVALID_INPUT")
if user_repo.exists_username(u) or user_repo.exists_email(e):
    return FAIL("USERNAME_OR_EMAIL_TAKEN")
hash = bcrypt_hash(p)
user_id = uuid4()
user_repo.insert(user_id, u, e, hash)
stats_repo.init(user_id)
return SUCCESS(user_id, u, now())
```

## 6) Kiểm thử
- Unit: validate, duplicate, hash, insert
- Integration (socket): gửi `SIGNUP` và kiểm tra response

## 7) Packet
Xem `SIGNUP_PACKET.md`.
