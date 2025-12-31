#include "auth/auth_controller.h"
#include "protocol/handle_socket.h"
#include "protocol/message_types.h"
#include "protocol/server.h"
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
