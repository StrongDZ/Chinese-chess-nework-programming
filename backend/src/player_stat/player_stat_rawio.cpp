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
  cout << "[handleUserStats] Processing USER_STATS request for fd=" << fd << endl;
  
  lock_guard<mutex> lock(g_clients_mutex);
  auto &sender = g_clients[fd];

  if (!pm.payload.has_value() ||
      !holds_alternative<UserStatsPayload>(*pm.payload)) {
    cout << "[handleUserStats] ERROR: Invalid payload or not UserStatsPayload" << endl;
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"USER_STATS requires target_username"});
    return;
  }

  if (g_player_stat_controller == nullptr) {
    cout << "[handleUserStats] ERROR: PlayerStat controller not initialized" << endl;
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"PlayerStat controller not initialized"});
    return;
  }

  try {
    const auto &p = get<UserStatsPayload>(*pm.payload);
    cout << "[handleUserStats] Parsed payload - target_username=" << p.target_username 
         << ", time_control=" << p.time_control << endl;

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

    cout << "[handleUserStats] Requesting stats - username=" << target 
         << ", time_control=" << request["time_control"] << endl;

    nlohmann::json response = g_player_stat_controller->handleGetStats(request);

    cout << "[handleUserStats] Got response, status=" 
         << (response.contains("status") ? response["status"].get<string>() : "unknown") << endl;

    // Wrap controller response into INFO payload
    sendMessage(fd, MessageType::INFO, InfoPayload{response});
    cout << "[handleUserStats] Sent INFO response to fd=" << fd << endl;
  } catch (const exception &e) {
    cout << "[handleUserStats] EXCEPTION: " << e.what() << endl;
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Failed to handle USER_STATS"});
  } catch (...) {
    cout << "[handleUserStats] UNKNOWN EXCEPTION" << endl;
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
    // Default request: blitz leaderboard, top 100
    nlohmann::json request;
    request["time_control"] = "blitz";
    request["limit"] = 100;

    nlohmann::json response =
        g_player_stat_controller->handleGetLeaderboard(request);

    sendMessage(fd, MessageType::INFO, InfoPayload{response});
  } catch (...) {
    sendMessage(fd, MessageType::ERROR,
                ErrorPayload{"Failed to handle LEADER_BOARD"});
  }
}
