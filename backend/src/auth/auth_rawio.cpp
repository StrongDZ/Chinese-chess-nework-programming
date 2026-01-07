#include "auth/auth_controller.h"
#include "protocol/handle_socket.h"
#include "protocol/message_types.h"
#include "protocol/server.h"
#include <iostream>
#include <map>
#include <mutex>
#include <string>
#include <variant>

using namespace std;

// External global variables from server.cpp
extern map<int, PlayerInfo> g_clients;
extern map<string, int> g_username_to_fd;
extern mutex g_clients_mutex;
extern AuthController *g_auth_controller;

void handleLogin(const ParsedMessage &pm, int fd) {
  lock_guard<mutex> lock(g_clients_mutex);
  auto &sender = g_clients[fd];
  if (!pm.payload.has_value() ||
      !holds_alternative<LoginPayload>(*pm.payload)) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"LOGIN requires username and password"});
    return;
  }
  if (g_auth_controller == nullptr) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Auth controller not initialized"});
    return;
  }
  try {
    const auto &p = get<LoginPayload>(*pm.payload);

    // Convert payload to nlohmann::json for controller
    nlohmann::json request;
    request["username"] = p.username;
    request["password"] = p.password;

    // Call controller
    nlohmann::json response = g_auth_controller->handleLogin(request);

    if (response.contains("success") && response["success"].get<bool>()) {
      const string &username = p.username;
      if (g_username_to_fd.count(username) &&
          g_username_to_fd[username] != fd) {
        sendMessage(fd, MessageType::ERROR,
                    ErrorPayload{"Username already in use"});
        return;
      }
      if (!sender.username.empty() && g_username_to_fd.count(sender.username)) {
        g_username_to_fd.erase(sender.username);
      }
      sender.username = username;
      // Store avatar_id from response
      if (response.contains("data") && response["data"].contains("avatar_id")) {
        sender.avatar_id = response["data"]["avatar_id"].get<int>();
      } else {
        sender.avatar_id = 1; // Default avatar
      }
      g_username_to_fd[sender.username] = fd;
      sendMessage(fd, MessageType::AUTHENTICATED);
    } else {
      string errorMsg = response.contains("error")
                            ? response["error"].get<string>()
                            : "Login failed";
      sendMessage(fd, MessageType::ERROR, ErrorPayload{errorMsg});
    }
  } catch (...) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"Invalid payload"});
  }
}

void handleRegister(const ParsedMessage &pm, int fd) {
  lock_guard<mutex> lock(g_clients_mutex);
  auto &sender = g_clients[fd];

  // Basic validation before accessing payload
  if (!pm.payload.has_value()) {
    cout << "handleRegister: payload is nullopt" << endl;
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"REGISTER requires username and password"});
    return;
  }
  if (!holds_alternative<RegisterPayload>(*pm.payload)) {
    cout << "handleRegister: payload is not RegisterPayload" << endl;
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"REGISTER requires username and password"});
    return;
  }

  if (g_auth_controller == nullptr) {
    cout << "handleRegister: Auth controller not initialized" << endl;
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Auth controller not initialized"});
    return;
  }

  try {
    const auto &p = get<RegisterPayload>(*pm.payload);

    cout << "handleRegister: username=" << p.username << endl;

    // Convert payload to nlohmann::json for controller
    nlohmann::json request;
    request["username"] = p.username;
    request["password"] = p.password;
    request["avatar_id"] = 1; // Default avatar_id

    // Call controller
    cout << "handleRegister: request=" << request.dump() << endl;
    nlohmann::json response = g_auth_controller->handleRegister(request);

    cout << "handleRegister: controller response=" << response.dump() << endl;

    if (response.contains("success") && response["success"].get<bool>()) {
      // Registration successful - auto-login the user
      const string &username = p.username;
      if (g_username_to_fd.count(username) &&
          g_username_to_fd[username] != fd) {
        sendMessage(fd, MessageType::ERROR,
                    ErrorPayload{"Username already in use"});
        return;
      }
      if (!sender.username.empty() && g_username_to_fd.count(sender.username)) {
        g_username_to_fd.erase(sender.username);
      }
      sender.username = username;
      // Store avatar_id from response
      if (response.contains("data") && response["data"].contains("avatar_id")) {
        sender.avatar_id = response["data"]["avatar_id"].get<int>();
      } else {
        sender.avatar_id = 1; // Default avatar
      }
      g_username_to_fd[sender.username] = fd;
      sendMessage(fd, MessageType::AUTHENTICATED);
    } else {
      string errorMsg = response.contains("error")
                            ? response["error"].get<string>()
                            : "Registration failed";
      sendMessage(fd, MessageType::ERROR, ErrorPayload{errorMsg});
    }
  } catch (const std::exception &e) {
    cout << "handleRegister: exception=" << e.what() << endl;
    sendMessage(
        fd, MessageType::ERROR,
        ErrorPayload{"Invalid payload: exception while handling REGISTER"});
  } catch (...) {
    cout << "handleRegister: unknown exception" << endl;
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"Invalid payload"});
  }
}
