#include "auth/auth_controller.h"

using namespace std;

AuthController::AuthController(AuthService &svc) : service(svc) {}

// ============================================
// POST /register
// Input: { "username": "...", "password": "...", "avatar_id": 1 }
// ============================================
nlohmann::json AuthController::handleRegister(const nlohmann::json &request) {
  nlohmann::json response;

  try {
    // 1. Parse request
    if (!request.contains("username") || !request.contains("password")) {
      response["success"] = false;
      response["error"] = "Missing username or password";
      return response;
    }

    string username = request["username"].get<string>();
    string password = request["password"].get<string>();
    int avatarId = request.value("avatar_id", 1); // Default avatar 1

    // 2. Call service
    AuthResult result = service.registerUser(username, password, avatarId);

    // 3. Build response (no user_id!)
    response["success"] = result.success;

    if (result.success) {
      response["message"] = result.message;
      response["data"]["username"] = result.username;
      response["data"]["avatar_id"] = result.avatarId;
    } else {
      response["error"] = result.message;
    }

  } catch (const exception &e) {
    response["success"] = false;
    response["error"] = string("Registration error: ") + e.what();
  }

  return response;
}

// ============================================
// POST /login
// Input: { "username": "...", "password": "..." }
// Note: Không trả về token nữa - protocol layer sẽ mapping fd -> username
// ============================================
nlohmann::json AuthController::handleLogin(const nlohmann::json &request) {
  nlohmann::json response;

  try {
    // 1. Parse request
    if (!request.contains("username") || !request.contains("password")) {
      response["success"] = false;
      response["error"] = "Missing username or password";
      return response;
    }

    string username = request["username"].get<string>();
    string password = request["password"].get<string>();

    // 2. Call service
    AuthResult result = service.login(username, password);

    // 3. Build response (no user_id, no token!)
    response["success"] = result.success;

    if (result.success) {
      response["message"] = result.message;
      response["data"]["username"] = result.username;
      response["data"]["avatar_id"] = result.avatarId;
    } else {
      response["error"] = result.message;
    }

  } catch (const exception &e) {
    response["success"] = false;
    response["error"] = string("Login error: ") + e.what();
  }

  return response;
}

// ============================================
// POST /logout
// Input: { "username": "..." }
// Note: Thay thế token bằng username
// ============================================
nlohmann::json AuthController::handleLogout(const nlohmann::json &request) {
  nlohmann::json response;

  try {
    // 1. Parse request (username thay vì token)
    if (!request.contains("username")) {
      response["success"] = false;
      response["error"] = "Username required";
      return response;
    }

    string username = request["username"].get<string>();

    // 2. Call service
    AuthResult result = service.logout(username);

    // 3. Build response
    response["success"] = result.success;

    if (result.success) {
      response["message"] = result.message;
    } else {
      response["error"] = result.message;
    }

  } catch (const exception &e) {
    response["success"] = false;
    response["error"] = string("Logout error: ") + e.what();
  }

  return response;
}

// ============================================
// POST /change-avatar
// Input: { "username": "...", "avatar_id": 5 }
// Note: Thay thế token bằng username
// ============================================
nlohmann::json
AuthController::handleChangeAvatar(const nlohmann::json &request) {
  nlohmann::json response;

  try {
    // 1. Parse request (username thay vì token)
    if (!request.contains("username") || !request.contains("avatar_id")) {
      response["success"] = false;
      response["error"] = "Username and avatar_id required";
      return response;
    }

    string username = request["username"].get<string>();
    int avatarId = request["avatar_id"].get<int>();

    // 2. Call service
    AuthResult result = service.changeAvatar(username, avatarId);

    // 3. Build response
    response["success"] = result.success;

    if (result.success) {
      response["message"] = result.message;
      response["data"]["avatar_id"] = result.avatarId;
    } else {
      response["error"] = result.message;
    }

  } catch (const exception &e) {
    response["success"] = false;
    response["error"] = string("Change avatar error: ") + e.what();
  }

  return response;
}

// ============================================
// GET /avatars
// Output: List of available avatars (1-10)
// ============================================
nlohmann::json AuthController::handleGetAvatars() {
  nlohmann::json response;

  try {
    response["success"] = true;
    response["data"] = nlohmann::json::array();

    // Return 10 avatars
    for (int id = 1; id <= 10; id++) {
      nlohmann::json avatar;
      avatar["id"] = id;
      avatar["filename"] = "avatar_" + to_string(id) + ".jpg";
      response["data"].push_back(avatar);
    }

  } catch (const exception &e) {
    response["success"] = false;
    response["error"] = string("Get avatars error: ") + e.what();
  }

  return response;
}
