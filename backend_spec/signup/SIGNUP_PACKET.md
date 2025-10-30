# SIGNUP Packet

Request (Client → Server):
```json
{ "type": "SIGNUP", "username": "string", "email": "string", "password": "string" }
```
Constraints: username 3–20 `[A-Za-z0-9._-]`, email hợp lệ, password ≥ 8 (hoa/thường/số).

Response (Server → Client):
```json
{ "status": "SUCCESS", "userID": "uuid", "username": "string", "createdAt": "ISO" }
```
Hoặc
```json
{ "status": "FAIL", "msg": "USERNAME_OR_EMAIL_TAKEN | INVALID_INPUT | SERVER_ERROR" }
```

Ghi chú:
- Không trả password/hash về client.
- Hash: bcrypt/argon2.
