import re
from typing import Tuple
try:
    from passlib.hash import bcrypt
    _bcrypt_available = True
except Exception:
    _bcrypt_available = False

USERNAME_RE = re.compile(r'^[A-Za-z0-9._-]{3,20}$')
EMAIL_RE = re.compile(r'^[^@\s]+@[^@\s]+\.[^@\s]+$')


def validate_username(u: str) -> bool:
    return bool(USERNAME_RE.match(u or ''))


def validate_email(e: str) -> bool:
    return bool(EMAIL_RE.match(e or ''))


def validate_password(p: str) -> bool:
    if not p or len(p) < 8:
        return False
    has_upper = any(c.isupper() for c in p)
    has_lower = any(c.islower() for c in p)
    has_digit = any(c.isdigit() for c in p)
    return has_upper and has_lower and has_digit


def hash_password(p: str) -> str:
    if _bcrypt_available:
        return bcrypt.hash(p)
    # Fallback demo only (không dùng production)
    import hashlib
    return hashlib.sha256(p.encode()).hexdigest()


def signup(users_repo, stats_repo, username: str, email: str, password: str) -> Tuple[bool, dict]:
    if not (validate_username(username) and validate_email(email) and validate_password(password)):
        return False, {"status": "FAIL", "msg": "INVALID_INPUT"}
    if users_repo.exists_username(username) or users_repo.exists_email(email):
        return False, {"status": "FAIL", "msg": "USERNAME_OR_EMAIL_TAKEN"}
    pwd_hash = hash_password(password)
    user_id = users_repo.insert(username, email, pwd_hash)
    stats_repo.init(user_id)
    return True, {"status": "SUCCESS", "userID": user_id, "username": username}
