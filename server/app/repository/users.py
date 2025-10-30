from typing import Optional, Dict
import uuid

class UsersRepo:
    def __init__(self):
        self.users: Dict[str, dict] = {}
        self.by_username: Dict[str, str] = {}
        self.by_email: Dict[str, str] = {}

    def exists_username(self, username: str) -> bool:
        return username.lower() in self.by_username

    def exists_email(self, email: str) -> bool:
        return email.lower() in self.by_email

    def insert(self, username: str, email: str, password_hash: str) -> str:
        user_id = str(uuid.uuid4())
        rec = {
            'id': user_id,
            'username': username,
            'email': email,
            'password_hash': password_hash,
            'elo': 1200,
            'status': 'OFFLINE'
        }
        self.users[user_id] = rec
        self.by_username[username.lower()] = user_id
        self.by_email[email.lower()] = user_id
        return user_id
