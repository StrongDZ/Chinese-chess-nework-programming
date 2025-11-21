#pragma once

#include "MessageTypes.h"
#include <map>
#include <string>

using namespace std;

struct PlayerInfo {
  int playerId{-1};
  string username;
  // Handler declarations
  bool in_game{false};
  int opponent_fd{-1};
};

static void handleLogin(const ParsedMessage &pm, int fd,
                        map<int, PlayerInfo> &clients,
                        map<string, int> &username_to_fd);
static void handleChallenge(const ParsedMessage &pm, int fd,
                            map<int, PlayerInfo> &clients,
                            map<string, int> &username_to_fd);
static void handleAccept(const ParsedMessage &pm, int fd,
                         map<int, PlayerInfo> &clients,
                         map<string, int> &username_to_fd);
static void handleMove(const ParsedMessage &pm, int fd,
                       map<int, PlayerInfo> &clients);