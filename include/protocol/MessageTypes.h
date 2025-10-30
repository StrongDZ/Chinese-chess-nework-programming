#pragma once

#include "rapidjson/document.h"
#include "rapidjson/error/en.h"
#include <algorithm>
#include <cctype>
#include <nlohmann/json.hpp>
#include <optional>
#include <sstream>
#include <string>
#include <unordered_map>
#include <variant>
#include <vector>

using namespace std;

enum class MessageType {
  // === User Management ===
  LOGIN,
  REGISTER,
  LOGOUT,
  PLAYER_LIST,
  USER_STATUS,

  // === Challenge Management ===
  CHALLENGE,
  ACCEPT,
  DECLINE,

  // === Game Flow ===
  GAME_START,
  MOVE,
  INVALID_MOVE,
  GAME_END,
  RESULT,

  // === Game Control ===
  RESIGN,
  DRAW_REQUEST,
  DRAW_RESPONSE,
  REMATCH_REQUEST,
  REMATCH_RESPONSE,

  // === History / Replay ===
  GAME_HISTORY,
  REPLAY_REQUEST,
  REPLAY_DATA,

  // === Advanced / Optional ===
  CUSTOM_BOARD,
  TIME_SETTING,
  AI_GAME_REQUEST,
  AI_MOVE,

  // === System / Utility ===
  PING,
  PONG,
  ERROR,
  INFO,
  CHAT,

  UNKNOWN
};

// ===================== JSON Payload Schemas ===================== //

struct Coord {
  int row;
  int col;
};

// User payloads
struct LoginPayload {
  string username;
  string password;
};

struct RegisterPayload {
  string username;
  string password;
};

// Challenge payloads
struct ChallengePayload {
  string target_username;
};

struct AcceptPayload {
  string challenger_username;
};

// Game payloads
struct MovePayload {
  string piece;
  Coord from;
  Coord to;
};

struct GameEndPayload {
  string win_side;
};

struct ErrorPayload {
  string detail;
};

// Empty payload cho các message không cần data
struct EmptyPayload {};

// ===================== UNIFIED PAYLOAD ===================== //
using Payload =
    variant<EmptyPayload, LoginPayload, RegisterPayload, ChallengePayload,
            AcceptPayload, MovePayload, GameEndPayload, ErrorPayload>;

// ============= nlohmann::json converters ============= //
using nlohmann::json;

inline void to_json(json &j, const EmptyPayload &) { j = json(); }
inline void to_json(json &j, const LoginPayload &p) {
  j = json{{"username", p.username}, {"password", p.password}};
}
inline void to_json(json &j, const RegisterPayload &p) {
  j = json{{"username", p.username}, {"password", p.password}};
}
inline void to_json(json &j, const ChallengePayload &p) {
  j = json{{"target_username", p.target_username}};
}
inline void to_json(json &j, const AcceptPayload &p) {
  j = json{{"challenger_username", p.challenger_username}};
}
inline void to_json(json &j, const Coord &c) {
  j = json{{"row", c.row}, {"col", c.col}};
}
inline void to_json(json &j, const MovePayload &p) {
  j = json{{"piece", p.piece}, {"from", p.from}, {"to", p.to}};
}
inline void to_json(json &j, const GameEndPayload &p) {
  j = json{{"win_side", p.win_side}};
}
inline void to_json(json &j, const ErrorPayload &p) {
  j = json{{"detail", p.detail}};
}
inline void to_json(json &j, const Payload &v) {
  std::visit([&](auto const &alt) { j = json(alt); }, v);
}

struct ParsedMessage {
  MessageType type{MessageType::UNKNOWN};
  optional<Payload> payload; // Parsed payload
};

// ===================== PARSING FUNCTIONS ===================== //

inline optional<Payload> parsePayload(MessageType type,
                                      const string &payloadStr) {
  if (payloadStr.empty()) {
    return EmptyPayload{};
  }

  rapidjson::Document doc;
  doc.Parse(payloadStr.c_str());

  if (doc.HasParseError() || !doc.IsObject()) {
    return nullopt;
  }

  try {
    switch (type) {
    case MessageType::LOGIN: {
      if (!doc.HasMember("username") || !doc.HasMember("password") ||
          !doc["username"].IsString() || !doc["password"].IsString()) {
        return nullopt;
      }
      LoginPayload p;
      p.username = doc["username"].GetString();
      p.password = doc["password"].GetString();
      return p;
    }

    case MessageType::REGISTER: {
      if (!doc.HasMember("username") || !doc.HasMember("password") ||
          !doc["username"].IsString() || !doc["password"].IsString()) {
        return nullopt;
      }
      RegisterPayload p;
      p.username = doc["username"].GetString();
      p.password = doc["password"].GetString();
      return p;
    }

    case MessageType::CHALLENGE: {
      if (!doc.HasMember("target_username") ||
          !doc["target_username"].IsString()) {
        return nullopt;
      }
      ChallengePayload p;
      p.target_username = doc["target_username"].GetString();
      return p;
    }

    case MessageType::ACCEPT: {
      if (!doc.HasMember("challenger_username") ||
          !doc["challenger_username"].IsString()) {
        return nullopt;
      }
      AcceptPayload p;
      p.challenger_username = doc["challenger_username"].GetString();
      return p;
    }

    case MessageType::MOVE: {
      if (!doc.HasMember("piece") || !doc["piece"].IsString() ||
          !doc.HasMember("from") || !doc["from"].IsObject() ||
          !doc.HasMember("to") || !doc["to"].IsObject()) {
        return nullopt;
      }

      const auto &fromObj = doc["from"];
      const auto &toObj = doc["to"];

      if (!fromObj.HasMember("row") || !fromObj.HasMember("col") ||
          !toObj.HasMember("row") || !toObj.HasMember("col") ||
          !fromObj["row"].IsInt() || !fromObj["col"].IsInt() ||
          !toObj["row"].IsInt() || !toObj["col"].IsInt()) {
        return nullopt;
      }

      MovePayload p;
      p.piece = doc["piece"].GetString();
      p.from.row = fromObj["row"].GetInt();
      p.from.col = fromObj["col"].GetInt();
      p.to.row = toObj["row"].GetInt();
      p.to.col = toObj["col"].GetInt();
      return p;
    }

    case MessageType::GAME_END: {
      if (!doc.HasMember("win_side") || !doc["win_side"].IsString()) {
        return nullopt;
      }
      GameEndPayload p;
      p.win_side = doc["win_side"].GetString();
      return p;
    }

    case MessageType::ERROR: {
      if (!doc.HasMember("detail") || !doc["detail"].IsString()) {
        return nullopt;
      }
      ErrorPayload p;
      p.detail = doc["detail"].GetString();
      return p;
    }

    default:
      return EmptyPayload{};
    }
  } catch (...) {
    return nullopt;
  }
}

inline string toUpperCopy(const string &s) {
  string r = s;
  transform(r.begin(), r.end(), r.begin(),
            [](unsigned char c) { return static_cast<char>(toupper(c)); });
  return r;
}

// Command map
static const unordered_map<string, MessageType> commandMap = {
    {"LOGIN", MessageType::LOGIN},
    {"REGISTER", MessageType::REGISTER},
    {"LOGOUT", MessageType::LOGOUT},
    {"PLAYER_LIST", MessageType::PLAYER_LIST},
    {"USER_STATUS", MessageType::USER_STATUS},
    {"CHALLENGE", MessageType::CHALLENGE},
    {"ACCEPT", MessageType::ACCEPT},
    {"DECLINE", MessageType::DECLINE},
    {"GAME_START", MessageType::GAME_START},
    {"MOVE", MessageType::MOVE},
    {"INVALID_MOVE", MessageType::INVALID_MOVE},
    {"GAME_END", MessageType::GAME_END},
    {"RESULT", MessageType::RESULT},
    {"RESIGN", MessageType::RESIGN},
    {"DRAW_REQUEST", MessageType::DRAW_REQUEST},
    {"DRAW_RESPONSE", MessageType::DRAW_RESPONSE},
    {"REMATCH_REQUEST", MessageType::REMATCH_REQUEST},
    {"REMATCH_RESPONSE", MessageType::REMATCH_RESPONSE},
    {"GAME_HISTORY", MessageType::GAME_HISTORY},
    {"REPLAY_REQUEST", MessageType::REPLAY_REQUEST},
    {"REPLAY_DATA", MessageType::REPLAY_DATA},
    {"CUSTOM_BOARD", MessageType::CUSTOM_BOARD},
    {"TIME_SETTING", MessageType::TIME_SETTING},
    {"AI_GAME_REQUEST", MessageType::AI_GAME_REQUEST},
    {"AI_MOVE", MessageType::AI_MOVE},
    {"PING", MessageType::PING},
    {"PONG", MessageType::PONG},
    {"ERROR", MessageType::ERROR},
    {"INFO", MessageType::INFO},
    {"CHAT", MessageType::CHAT}};

inline ParsedMessage parseMessage(const string &msg) {
  ParsedMessage pm;
  istringstream iss(msg);
  string cmd;
  if (!(iss >> cmd))
    return pm;

  string ucmd = toUpperCopy(cmd);

  auto it = commandMap.find(ucmd);
  pm.type = (it != commandMap.end()) ? it->second : MessageType::UNKNOWN;

  // Extract payload string
  string rest;
  getline(iss, rest);
  if (!rest.empty() && rest[0] == ' ')
    rest.erase(0, 1);
  pm.payload = parsePayload(pm.type, rest);

  // Parse typed payload
  return pm;
}

// Type strings map
static const unordered_map<MessageType, const char *> typeStrings = {
    {MessageType::LOGIN, "LOGIN"},
    {MessageType::REGISTER, "REGISTER"},
    {MessageType::LOGOUT, "LOGOUT"},
    {MessageType::PLAYER_LIST, "PLAYER_LIST"},
    {MessageType::USER_STATUS, "USER_STATUS"},
    {MessageType::CHALLENGE, "CHALLENGE"},
    {MessageType::ACCEPT, "ACCEPT"},
    {MessageType::DECLINE, "DECLINE"},
    {MessageType::GAME_START, "GAME_START"},
    {MessageType::MOVE, "MOVE"},
    {MessageType::INVALID_MOVE, "INVALID_MOVE"},
    {MessageType::GAME_END, "GAME_END"},
    {MessageType::RESULT, "RESULT"},
    {MessageType::RESIGN, "RESIGN"},
    {MessageType::DRAW_REQUEST, "DRAW_REQUEST"},
    {MessageType::DRAW_RESPONSE, "DRAW_RESPONSE"},
    {MessageType::REMATCH_REQUEST, "REMATCH_REQUEST"},
    {MessageType::REMATCH_RESPONSE, "REMATCH_RESPONSE"},
    {MessageType::GAME_HISTORY, "GAME_HISTORY"},
    {MessageType::REPLAY_REQUEST, "REPLAY_REQUEST"},
    {MessageType::REPLAY_DATA, "REPLAY_DATA"},
    {MessageType::CUSTOM_BOARD, "CUSTOM_BOARD"},
    {MessageType::TIME_SETTING, "TIME_SETTING"},
    {MessageType::AI_GAME_REQUEST, "AI_GAME_REQUEST"},
    {MessageType::AI_MOVE, "AI_MOVE"},
    {MessageType::PING, "PING"},
    {MessageType::PONG, "PONG"},
    {MessageType::ERROR, "ERROR"},
    {MessageType::INFO, "INFO"},
    {MessageType::CHAT, "CHAT"}};

inline string makeMessage(MessageType type,
                          const Payload &payload = EmptyPayload{}) {
  string message = json(payload).dump();

  auto it = typeStrings.find(type);
  if (it == typeStrings.end()) {
    return "UNKNOWN";
  }

  if (message.empty()) {
    return it->second;
  }

  string result;
  result.reserve(strlen(it->second) + 1 + message.size());
  result = it->second;
  result += ' ';
  result += message;
  return result;
}