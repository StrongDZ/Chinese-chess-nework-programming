#include "player_stat/player_stat_controller.h"
#include "protocol/handle_socket.h"
#include "protocol/message_types.h"
#include "protocol/server.h"

#include <map>
#include <mutex>
#include <string>
#include <variant>
#include <iostream>

using namespace std;

// External global variables from server.cpp
extern map<int, PlayerInfo> g_clients;
extern map<string, int> g_username_to_fd;
extern mutex g_clients_mutex;
extern PlayerStatController *g_player_stat_controller;

void handleUserStats(const ParsedMessage &pm, int fd) {
  lock_guard<mutex> lock(g_clients_mutex);
  auto &sender = g_clients[fd];

  if (!pm.payload.has_value() ||
      !holds_alternative<UserStatsPayload>(*pm.payload)) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"USER_STATS requires target_username"});
    return;
  }

  if (g_player_stat_controller == nullptr) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"PlayerStat controller not initialized"});
    return;
  }

  try {
    const auto &p = get<UserStatsPayload>(*pm.payload);

    // Build request JSON for controller
    nlohmann::json request;

    // If target_username is empty, default to current sender
    string target = p.target_username;
    if (target.empty()) {
      target = sender.username;
    }

    if (target.empty()) {
      sendMessage(fd, MessageType::ERROR,
                  ErrorPayload{"USER_STATS requires target_username"});
      return;
    }

    request["username"] = target;
    // Use time_control from payload, or default to "all"
    if (!p.time_control.empty()) {
      request["time_control"] = p.time_control;
    } else {
      request["time_control"] = "all";
    }

    nlohmann::json response = g_player_stat_controller->handleGetStats(request);

    // Wrap controller response into INFO payload
    sendMessage(fd, MessageType::INFO, InfoPayload{response});
  } catch (const exception &e) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Failed to handle USER_STATS"});
  } catch (...) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Failed to handle USER_STATS"});
  }
}

void handleLeaderBoard(const ParsedMessage &pm, int fd) {
  (void)pm; // Currently no structured payload for LEADER_BOARD

  lock_guard<mutex> lock(g_clients_mutex);

  if (g_player_stat_controller == nullptr) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"PlayerStat controller not initialized"});
    return;
  }

  try {
    // Get all users stats (both classical and blitz)
    nlohmann::json request;
    nlohmann::json response =
        g_player_stat_controller->handleGetAllUsersStats(request);

    sendMessage(fd, MessageType::INFO, InfoPayload{response});
  } catch (...) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Failed to handle LEADER_BOARD"});
  }
}
