#pragma once

#include "message_types.h"
#include <string>

using namespace std;

struct PlayerInfo {
  int playerId{-1};
  string username;
  bool in_game{false};
  int opponent_fd{-1};
  bool is_red{false};    // true if player is playing red side (goes first)
  int avatar_id{1};      // Avatar ID (1-10)
  string game_id;        // Current active game ID in database
  string current_turn;   // "red" or "black" - tracks whose turn it is
  // Pending challenge info (stored when receiving challenge, used when responding)
  string pending_challenge_mode;    // "classical" or "blitz"
  int pending_challenge_time{0};    // Time limit in seconds
  string pending_challenger;        // Username of the challenger
};

// ===================== Handler Declarations ===================== //
// Note: All handlers use global variables (g_clients, g_username_to_fd,
// g_clients_mutex) instead of passing them as parameters
void handleLogin(const ParsedMessage &pm, int fd);
void handleRegister(const ParsedMessage &pm, int fd);
void handleLogout(const ParsedMessage &pm, int fd);
void handleUserStats(const ParsedMessage &pm, int fd);
void handleLeaderBoard(const ParsedMessage &pm, int fd);
void handleChallenge(const ParsedMessage &pm, int fd);
void handleChallengeResponse(const ParsedMessage &pm, int fd);
void handleQuickMatching(const ParsedMessage &pm, int fd);
void handleMove(const ParsedMessage &pm, int fd);
void handleMessage(const ParsedMessage &pm, int fd);
void handleDrawRequest(const ParsedMessage &pm, int fd);
void handleDrawResponse(const ParsedMessage &pm, int fd);
void handleResign(const ParsedMessage &pm, int fd);
void handleCancelQM(const ParsedMessage &pm, int fd);
void handleRequestAddFriend(const ParsedMessage &pm, int fd);
void handleResponseAddFriend(const ParsedMessage &pm, int fd);
void handleGameHistory(const ParsedMessage &pm, int fd);
void handleReplayRequest(const ParsedMessage &pm, int fd);
void processMessage(const ParsedMessage &pm, int fd);
void processAIMatch(const ParsedMessage &pm, int fd);
void handleAIMatch(const ParsedMessage &pm, int fd);
void handleSuggestMove(const ParsedMessage &pm, int fd);
void handleAIMove(int player_fd);
void handleStartGame(int player1_fd, int player2_fd, const string& mode = "classical", int time_limit = 0);
