# Server Skeleton

Mục tiêu: Cung cấp khung mã cho 3 phần việc
- SIGNUP (bcrypt + validate)
- Validate move (server authoritative)
- Determine result + ELO

Cấu trúc:
```
server/
  app/
    __init__.py
    models.py           # dataclass GameState, Move
    repository/
      __init__.py
      users.py         # in-memory repo + interface
      stats.py
    services/
      __init__.py
      signup_service.py
      legality_service.py
      result_service.py
  tests/
    test_signup.py
    test_legality.py
    test_result.py
  requirements.txt
```

Lưu ý: Repo ở đây là in-memory để dễ chạy demo. Khi tích hợp thật, thay bằng DB thực sử dụng `backend_spec/signup/schema.sql`.
