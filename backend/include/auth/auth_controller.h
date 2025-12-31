#ifndef AUTH_CONTROLLER_H
#define AUTH_CONTROLLER_H

#include "auth_service.h"
#include <nlohmann/json.hpp>

class AuthController {
private:
  AuthService &service;

public:
  explicit AuthController(AuthService &svc);

  // POST /register
  // Input: { "username": "...", "password": "...", "avatar_id": 1 }
  nlohmann::json handleRegister(const nlohmann::json &request);

  // POST /login
  // Input: { "username": "...", "password": "..." }
  // Note: Không trả về token nữa - protocol layer sẽ mapping fd -> username
  nlohmann::json handleLogin(const nlohmann::json &request);

  // POST /logout
  // Input: { "username": "..." }
  // Note: Thay thế token bằng username
  nlohmann::json handleLogout(const nlohmann::json &request);

  // POST /change-avatar
  // Input: { "username": "...", "avatar_id": 5 }
  // Note: Thay thế token bằng username
  nlohmann::json handleChangeAvatar(const nlohmann::json &request);

  // GET /avatars
  // Output: List of available avatars (1-10)
  nlohmann::json handleGetAvatars();
};

#endif
