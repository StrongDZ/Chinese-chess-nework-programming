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

void handleRequestAddFriend(const ParsedMessage &pm, int fd) {
  lock_guard<mutex> lock(g_clients_mutex);
  auto &sender = g_clients[fd];
  if (sender.username.empty()) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Please LOGIN before sending friend request"});
    return;
  }
  if (!pm.payload.has_value() ||
      !holds_alternative<RequestAddFriendPayload>(*pm.payload)) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"REQUEST_ADD_FRIEND requires to_user"});
    return;
  }
  try {
    const auto &p = get<RequestAddFriendPayload>(*pm.payload);
    const string &target = p.to_user;
    auto it = g_username_to_fd.find(target);
    if (it == g_username_to_fd.end()) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"Target user is offline"});
      return;
    }
    int target_fd = it->second;
    if (target_fd == fd) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"Cannot send friend request to yourself"});
      return;
    }
    // Forward request to target user with from_user field
    RequestAddFriendPayload forwardPayload;
    forwardPayload.from_user = sender.username;
    forwardPayload.to_user = "";
    sendMessage(target_fd, MessageType::REQUEST_ADD_FRIEND, forwardPayload);
    sendMessage(fd, MessageType::INFO,
                InfoPayload{nlohmann::json{{"friend_request_sent", true},
                                           {"to_user", target}}});
  } catch (...) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"Invalid payload"});
  }
}

void handleResponseAddFriend(const ParsedMessage &pm, int fd) {
  lock_guard<mutex> lock(g_clients_mutex);
  auto &sender = g_clients[fd];
  if (sender.username.empty()) {
    sendMessage(
        fd, MessageType::ERROR,
        ErrorPayload{"Please LOGIN before responding to friend request"});
    return;
  }
  if (!pm.payload.has_value() ||
      !holds_alternative<ResponseAddFriendPayload>(*pm.payload)) {
    sendMessage(
        fd, MessageType::ERROR,
        ErrorPayload{"RESPONSE_ADD_FRIEND requires to_user and accept"});
    return;
  }
  try {
    const auto &p = get<ResponseAddFriendPayload>(*pm.payload);
    const string &requesterName = p.to_user;
    auto it = g_username_to_fd.find(requesterName);
    if (it == g_username_to_fd.end()) {
      sendMessage(fd, MessageType::ERROR, ErrorPayload{"Requester is offline"});
      return;
    }
    int requester_fd = it->second;
    if (!g_clients.count(requester_fd)) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"Requester socket missing"});
      return;
    }
    // Forward response to requester with from_user field
    ResponseAddFriendPayload forwardPayload;
    forwardPayload.from_user = sender.username;
    forwardPayload.to_user = "";
    forwardPayload.accept = p.accept;
    sendMessage(requester_fd, MessageType::RESPONSE_ADD_FRIEND, forwardPayload);
    sendMessage(fd, MessageType::INFO,
                InfoPayload{nlohmann::json{{"friend_response_sent", true},
                                           {"to_user", requesterName},
                                           {"accept", p.accept}}});
  } catch (...) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"Invalid payload"});
  }
}

