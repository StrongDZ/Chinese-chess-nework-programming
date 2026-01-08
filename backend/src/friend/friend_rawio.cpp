#include "protocol/handle_socket.h"
#include "protocol/message_types.h"
#include "protocol/server.h"
#include "friend/friend_controller.h"
#include <map>
#include <mutex>
#include <string>
#include <variant>
#include <nlohmann/json.hpp>

using namespace std;

// External global variables from server.cpp
extern map<int, PlayerInfo> g_clients;
extern map<string, int> g_username_to_fd;
extern mutex g_clients_mutex;

// External global controller
extern FriendController *g_friend_controller;

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
    
    if (target == sender.username) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"Cannot send friend request to yourself"});
      return;
    }
    
    // Always save to database first (even if target is offline)
    if (g_friend_controller == nullptr) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"Friend controller not initialized"});
      return;
    }
    
    nlohmann::json request;
    request["username"] = sender.username;
    request["friend_username"] = target;
    nlohmann::json response = g_friend_controller->handleSendFriendRequest(request);
    
    if (response["status"] == "error") {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{response["message"].get<string>()});
      return;
    }
    
    // If target is online, forward notification
    auto it = g_username_to_fd.find(target);
    if (it != g_username_to_fd.end()) {
      int target_fd = it->second;
      if (g_clients.count(target_fd)) {
    // Forward request to target user with from_user field
    RequestAddFriendPayload forwardPayload;
    forwardPayload.from_user = sender.username;
    forwardPayload.to_user = "";
    sendMessage(target_fd, MessageType::REQUEST_ADD_FRIEND, forwardPayload);
      }
    }
    
    // Send success response to sender
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
    
    // Always save to database first (even if requester is offline)
    if (g_friend_controller == nullptr) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"Friend controller not initialized"});
      return;
    }
    
    nlohmann::json request;
    request["username"] = sender.username;
    request["friend_username"] = requesterName;
    
    nlohmann::json response;
    if (p.accept) {
      // Accept friend request
      response = g_friend_controller->handleAcceptFriendRequest(request);
    } else {
      // Decline friend request
      response = g_friend_controller->handleDeclineFriendRequest(request);
    }
    
    if (response["status"] == "error") {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{response["message"].get<string>()});
      return;
    }
    
    // If requester is online, forward notification
    auto it = g_username_to_fd.find(requesterName);
    if (it != g_username_to_fd.end()) {
      int requester_fd = it->second;
      if (g_clients.count(requester_fd)) {
    // Forward response to requester with from_user field
    ResponseAddFriendPayload forwardPayload;
    forwardPayload.from_user = sender.username;
    forwardPayload.to_user = "";
    forwardPayload.accept = p.accept;
    sendMessage(requester_fd, MessageType::RESPONSE_ADD_FRIEND, forwardPayload);
      }
    }
    
    // Send success response to sender
    sendMessage(fd, MessageType::INFO,
                InfoPayload{nlohmann::json{{"friend_response_sent", true},
                                           {"to_user", requesterName},
                                           {"accept", p.accept}}});
  } catch (...) {
    sendMessage(fd, MessageType::ERROR, ErrorPayload{"Invalid payload"});
  }
}

