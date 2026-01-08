#pragma once

#include "rapidjson/document.h"
#include "rapidjson/stringbuffer.h"
#include "rapidjson/writer.h"
#include <algorithm>
#include <cctype>
#include <iostream>
#include <nlohmann/json.hpp>
#include <optional>
#include <sstream>
#include <string>
#include <unordered_map>
#include <variant>

using namespace std;

enum class MessageType {
  // === Authentication ===
  LOGIN,
  REGISTER,
  LOGOUT,
  AUTHENTICATED,

  // === Game Management ===
  QUICK_MATCHING,
  CANCEL_QM,
  CHALLENGE_CANCEL,
  CHALLENGE_REQUEST,
  CHALLENGE_RESPONSE,
  AI_MATCH,

  // === Game Flow ===
  GAME_START,
  MOVE,
  INVALID_MOVE,
  MESSAGE,
  GAME_END,
  SUGGEST_MOVE,

  // === Game Control ===
  RESIGN,
  DRAW_REQUEST,
  DRAW_RESPONSE,
  REMATCH_REQUEST,
  REMATCH_RESPONSE,

  // === Data Management ===
  USER_STATS,
  GAME_HISTORY,
  REPLAY_REQUEST,
  LEADER_BOARD,
  PLAYER_LIST,
  INFO,

  // === Friend Management ===
  REQUEST_ADD_FRIEND,
  RESPONSE_ADD_FRIEND,
  UNFRIEND,

  // === System ===
  ERROR,

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

struct LogoutPayload {
  string username;
};

// Challenge payloads
struct ChallengeRequestPayload {
  string to_user;   // Client -> Server: target user
  string from_user; // Server -> Client: sender user (when forwarding)
};

struct ChallengeCancelPayload {
  string to_user;   // Client -> Server: target user
  string from_user; // Server -> Client: sender user (when forwarding)
};

struct ChallengeResponsePayload {
  string to_user;   // Client -> Server: original challenger
  string from_user; // Server -> Client: responder (when forwarding)
  bool accept;
};

struct AIMatchPayload {
  string gamemode;
};

// Game payloads
struct MovePayload {
  string piece;
  Coord from;
  Coord to;
};

struct GameStartPayload {
  string opponent;              // Empty if playing with AI
  nlohmann::json opponent_data; // Optional opponent data
  string game_mode;
};

struct GameEndPayload {
  string win_side;
};

struct InvalidMovePayload {
  string reason;
};

struct MessagePayload {
  string message;
};

struct UserStatsPayload {
  string target_username;
  string time_control; // Optional: classical, blitz, or "all"
};

struct GameHistoryPayload {
  string target_username;
};

struct ReplayRequestPayload {
  string game_id;
};

// Friend management payloads
struct RequestAddFriendPayload {
  string to_user;   // Client -> Server: target user
  string from_user; // Server -> Client: sender user
};

struct ResponseAddFriendPayload {
  string to_user;   // Client -> Server: original requester
  string from_user; // Server -> Client: responder
  bool accept;
};

struct UnfriendPayload {
  string to_user;
};

struct ErrorPayload {
  string message;
};

// INFO payload: can carry ANY JSON value via nlohmann::json
struct InfoPayload {
  nlohmann::json data;
};

// Game control payloads
struct DrawRequestPayload {
  // Empty payload - no data needed
};

struct DrawResponsePayload {
  bool accept_draw;
};

struct RematchRequestPayload {
  // Empty payload - no data needed
};

struct RematchResponsePayload {
  bool accept_rematch;
};

// Quick matching payload
struct QuickMatchingPayload {};
struct CancelQMPayload {};

// Empty payload cho các message không cần data
struct EmptyPayload {};

// ===================== UNIFIED PAYLOAD ===================== //
using Payload =
    variant<EmptyPayload, LoginPayload, RegisterPayload, LogoutPayload,
            ChallengeRequestPayload, ChallengeCancelPayload,
            ChallengeResponsePayload, AIMatchPayload, GameStartPayload,
            MovePayload, InvalidMovePayload, MessagePayload, GameEndPayload,
            UserStatsPayload, GameHistoryPayload, ReplayRequestPayload,
            RequestAddFriendPayload, ResponseAddFriendPayload, UnfriendPayload,
            DrawRequestPayload, DrawResponsePayload, RematchRequestPayload,
            RematchResponsePayload, CancelQMPayload, ErrorPayload, InfoPayload>;

// ============= nlohmann::json converters ============= //
using nlohmann::json;

inline void to_json(json &j, const EmptyPayload &) { j = json(); }
inline void to_json(json &j, const LoginPayload &p) {
  j = json{{"username", p.username}, {"password", p.password}};
}
inline void to_json(json &j, const RegisterPayload &p) {
  j = json{{"username", p.username}, {"password", p.password}};
}
inline void to_json(json &j, const LogoutPayload &p) {
  j = json{{"username", p.username}};
}
inline void to_json(json &j, const ChallengeRequestPayload &p) {
  if (!p.from_user.empty()) {
    // Server -> Client format
    j = json{{"from_user", p.from_user}};
  } else {
    // Client -> Server format
    j = json{{"to_user", p.to_user}};
  }
}
inline void to_json(json &j, const ChallengeCancelPayload &p) {
  if (!p.from_user.empty()) {
    // Server -> Client format
    j = json{{"from_user", p.from_user}};
  } else {
    // Client -> Server format
    j = json{{"to_user", p.to_user}};
  }
}
inline void to_json(json &j, const ChallengeResponsePayload &p) {
  if (!p.from_user.empty()) {
    // Server -> Client format
    j = json{{"from_user", p.from_user}, {"accept", p.accept}};
  } else {
    // Client -> Server format
    j = json{{"to_user", p.to_user}, {"accept", p.accept}};
  }
}
inline void to_json(json &j, const AIMatchPayload &p) {
  j = json{{"gamemode", p.gamemode}};
}
inline void to_json(json &j, const GameStartPayload &p) {
  j = json{{"opponent", p.opponent}, {"game_mode", p.game_mode}};
  if (!p.opponent_data.is_null()) {
    j["opponent_data"] = p.opponent_data;
  }
}
inline void to_json(json &j, const InvalidMovePayload &p) {
  j = json{{"reason", p.reason}};
}
inline void to_json(json &j, const MessagePayload &p) {
  j = json{{"message", p.message}};
}
inline void to_json(json &j, const UserStatsPayload &p) {
  j = json{{"target_username", p.target_username}};
  if (!p.time_control.empty()) {
    j["time_control"] = p.time_control;
  }
}
inline void to_json(json &j, const GameHistoryPayload &p) {
  j = json{{"target_username", p.target_username}};
}
inline void to_json(json &j, const ReplayRequestPayload &p) {
  j = json{{"game_id", p.game_id}};
}
inline void to_json(json &j, const UnfriendPayload &p) {
  j = json{{"to_user", p.to_user}};
}
inline void to_json(json &j, const DrawRequestPayload &) {
  j = json(); // Empty payload
}
inline void to_json(json &j, const DrawResponsePayload &p) {
  j = json{{"accept_draw", p.accept_draw}};
}
inline void to_json(json &j, const RematchRequestPayload &) {
  j = json(); // Empty payload
}
inline void to_json(json &j, const RematchResponsePayload &p) {
  j = json{{"accept_rematch", p.accept_rematch}};
}
inline void to_json(json &j, const CancelQMPayload &) {
  j = json(); // Empty payload
}
inline void to_json(json &j, const RequestAddFriendPayload &p) {
  if (!p.from_user.empty()) {
    // Server -> Client format
    j = json{{"from_user", p.from_user}};
  } else {
    // Client -> Server format
    j = json{{"to_user", p.to_user}};
  }
}
inline void to_json(json &j, const ResponseAddFriendPayload &p) {
  if (!p.from_user.empty()) {
    // Server -> Client format
    j = json{{"from_user", p.from_user}, {"accept", p.accept}};
  } else {
    // Client -> Server format
    j = json{{"to_user", p.to_user}, {"accept", p.accept}};
  }
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
  j = json{{"message", p.message}};
}
inline void to_json(json &j, const InfoPayload &p) {
  j = json{{"data", p.data}};
}
inline void to_json(json &j, const Payload &v) {
  std::visit(
      [&](auto const &alt) {
        json tmp;
        to_json(tmp, alt);
        j = std::move(tmp);
      },
      v);
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

    case MessageType::LOGOUT: {
      if (!doc.HasMember("username") || !doc["username"].IsString()) {
        return nullopt;
      }
      LogoutPayload p;
      p.username = doc["username"].GetString();
      return p;
    }

    case MessageType::CHALLENGE_REQUEST: {
      ChallengeRequestPayload p;
      // Client -> Server: has "to_user"
      if (doc.HasMember("to_user") && doc["to_user"].IsString()) {
        p.to_user = doc["to_user"].GetString();
        p.from_user = "";
      }
      // Server -> Client: has "from_user"
      else if (doc.HasMember("from_user") && doc["from_user"].IsString()) {
        p.from_user = doc["from_user"].GetString();
        p.to_user = "";
      } else {
        return nullopt;
      }
      return p;
    }

    case MessageType::CHALLENGE_CANCEL: {
      ChallengeCancelPayload p;
      // Client -> Server: has "to_user"
      if (doc.HasMember("to_user") && doc["to_user"].IsString()) {
        p.to_user = doc["to_user"].GetString();
        p.from_user = "";
      }
      // Server -> Client: has "from_user"
      else if (doc.HasMember("from_user") && doc["from_user"].IsString()) {
        p.from_user = doc["from_user"].GetString();
        p.to_user = "";
      } else {
        return nullopt;
      }
      return p;
    }

    case MessageType::CHALLENGE_RESPONSE: {
      if (!doc.HasMember("accept") || !doc["accept"].IsBool()) {
        return nullopt;
      }
      ChallengeResponsePayload p;
      p.accept = doc["accept"].GetBool();
      // Client -> Server: has "to_user"
      if (doc.HasMember("to_user") && doc["to_user"].IsString()) {
        p.to_user = doc["to_user"].GetString();
        p.from_user = "";
      }
      // Server -> Client: has "from_user"
      else if (doc.HasMember("from_user") && doc["from_user"].IsString()) {
        p.from_user = doc["from_user"].GetString();
        p.to_user = "";
      } else {
        return nullopt;
      }
      return p;
    }

    case MessageType::AI_MATCH: {
      if (!doc.HasMember("gamemode") || !doc["gamemode"].IsString()) {
        return nullopt;
      }
      AIMatchPayload p;
      p.gamemode = doc["gamemode"].GetString();
      return p;
    }

    case MessageType::GAME_START: {
      if (!doc.HasMember("opponent") || !doc["opponent"].IsString() ||
          !doc.HasMember("game_mode") || !doc["game_mode"].IsString()) {
        return nullopt;
      }
      GameStartPayload p;
      p.opponent = doc["opponent"].GetString();
      p.game_mode = doc["game_mode"].GetString();
      // Optional opponent_data field
      if (doc.HasMember("opponent_data")) {
        // Convert rapidjson::Value to nlohmann::json
        rapidjson::StringBuffer buffer;
        rapidjson::Writer<rapidjson::StringBuffer> writer(buffer);
        doc["opponent_data"].Accept(writer);
        p.opponent_data = nlohmann::json::parse(buffer.GetString());
      } else {
        p.opponent_data = nlohmann::json();
      }
      return p;
    }

    case MessageType::INVALID_MOVE: {
      if (!doc.HasMember("reason") || !doc["reason"].IsString()) {
        return nullopt;
      }
      InvalidMovePayload p;
      p.reason = doc["reason"].GetString();
      return p;
    }

    case MessageType::MESSAGE: {
      if (!doc.HasMember("message") || !doc["message"].IsString()) {
        return nullopt;
      }
      MessagePayload p;
      p.message = doc["message"].GetString();
      return p;
    }

    case MessageType::USER_STATS: {
      if (!doc.HasMember("target_username") ||
          !doc["target_username"].IsString()) {
        return nullopt;
      }
      UserStatsPayload p;
      p.target_username = doc["target_username"].GetString();
      // Optional time_control field
      if (doc.HasMember("time_control") && doc["time_control"].IsString()) {
        p.time_control = doc["time_control"].GetString();
      } else {
        p.time_control = "all"; // Default to all
      }
      return p;
    }

    case MessageType::GAME_HISTORY: {
      if (!doc.HasMember("target_username") ||
          !doc["target_username"].IsString()) {
        return nullopt;
      }
      GameHistoryPayload p;
      p.target_username = doc["target_username"].GetString();
      return p;
    }

    case MessageType::REPLAY_REQUEST: {
      if (!doc.HasMember("game_id") || !doc["game_id"].IsString()) {
        return nullopt;
      }
      ReplayRequestPayload p;
      p.game_id = doc["game_id"].GetString();
      return p;
    }

    case MessageType::UNFRIEND: {
      if (!doc.HasMember("to_user") || !doc["to_user"].IsString()) {
        return nullopt;
      }
      UnfriendPayload p;
      p.to_user = doc["to_user"].GetString();
      return p;
    }

    case MessageType::DRAW_REQUEST: {
      // Empty payload
      return DrawRequestPayload{};
    }

    case MessageType::DRAW_RESPONSE: {
      if (!doc.HasMember("accept_draw") || !doc["accept_draw"].IsBool()) {
        return nullopt;
      }
      DrawResponsePayload p;
      p.accept_draw = doc["accept_draw"].GetBool();
      return p;
    }

    case MessageType::REMATCH_REQUEST: {
      // Empty payload
      return RematchRequestPayload{};
    }

    case MessageType::REMATCH_RESPONSE: {
      if (!doc.HasMember("accept_rematch") || !doc["accept_rematch"].IsBool()) {
        return nullopt;
      }
      RematchResponsePayload p;
      p.accept_rematch = doc["accept_rematch"].GetBool();
      return p;
    }

    case MessageType::CANCEL_QM: {
      // Empty payload
      return CancelQMPayload{};
    }

    case MessageType::REQUEST_ADD_FRIEND: {
      RequestAddFriendPayload p;
      // Client -> Server: has "to_user"
      if (doc.HasMember("to_user") && doc["to_user"].IsString()) {
        p.to_user = doc["to_user"].GetString();
        p.from_user = "";
      }
      // Server -> Client: has "from_user"
      else if (doc.HasMember("from_user") && doc["from_user"].IsString()) {
        p.from_user = doc["from_user"].GetString();
        p.to_user = "";
      } else {
        return nullopt;
      }
      return p;
    }

    case MessageType::RESPONSE_ADD_FRIEND: {
      if (!doc.HasMember("accept") || !doc["accept"].IsBool()) {
        return nullopt;
      }
      ResponseAddFriendPayload p;
      p.accept = doc["accept"].GetBool();
      // Client -> Server: has "to_user"
      if (doc.HasMember("to_user") && doc["to_user"].IsString()) {
        p.to_user = doc["to_user"].GetString();
        p.from_user = "";
      }
      // Server -> Client: has "from_user"
      else if (doc.HasMember("from_user") && doc["from_user"].IsString()) {
        p.from_user = doc["from_user"].GetString();
        p.to_user = "";
      } else {
        return nullopt;
      }
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
      if (!doc.HasMember("message") || !doc["message"].IsString()) {
        return nullopt;
      }
      ErrorPayload p;
      p.message = doc["message"].GetString();
      return p;
    }

    case MessageType::INFO: {
      // Convert rapidjson::Value to nlohmann::json for INFO payload
      rapidjson::StringBuffer buffer;
      rapidjson::Writer<rapidjson::StringBuffer> writer(buffer);
      doc.Accept(writer);
      InfoPayload p;
      p.data = nlohmann::json::parse(buffer.GetString());
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
    {"AUTHENTICATED", MessageType::AUTHENTICATED},
    {"QUICK_MATCHING", MessageType::QUICK_MATCHING},
    {"CANCEL_QM", MessageType::CANCEL_QM},
    {"CHALLENGE_CANCEL", MessageType::CHALLENGE_CANCEL},
    {"CHALLENGE_REQUEST", MessageType::CHALLENGE_REQUEST},
    {"CHALLENGE_RESPONSE", MessageType::CHALLENGE_RESPONSE},
    {"AI_MATCH", MessageType::AI_MATCH},
    {"GAME_START", MessageType::GAME_START},
    {"MOVE", MessageType::MOVE},
    {"INVALID_MOVE", MessageType::INVALID_MOVE},
    {"MESSAGE", MessageType::MESSAGE},
    {"GAME_END", MessageType::GAME_END},
    {"SUGGEST_MOVE", MessageType::SUGGEST_MOVE},
    {"RESIGN", MessageType::RESIGN},
    {"DRAW_REQUEST", MessageType::DRAW_REQUEST},
    {"DRAW_RESPONSE", MessageType::DRAW_RESPONSE},
    {"REMATCH_REQUEST", MessageType::REMATCH_REQUEST},
    {"REMATCH_RESPONSE", MessageType::REMATCH_RESPONSE},
    {"USER_STATS", MessageType::USER_STATS},
    {"GAME_HISTORY", MessageType::GAME_HISTORY},
    {"REPLAY_REQUEST", MessageType::REPLAY_REQUEST},
    {"LEADER_BOARD", MessageType::LEADER_BOARD},
    {"PLAYER_LIST", MessageType::PLAYER_LIST},
    {"INFO", MessageType::INFO},
    {"REQUEST_ADD_FRIEND", MessageType::REQUEST_ADD_FRIEND},
    {"RESPONSE_ADD_FRIEND", MessageType::RESPONSE_ADD_FRIEND},
    {"UNFRIEND", MessageType::UNFRIEND},
    {"ERROR", MessageType::ERROR}};

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
    {MessageType::AUTHENTICATED, "AUTHENTICATED"},
    {MessageType::QUICK_MATCHING, "QUICK_MATCHING"},
    {MessageType::CANCEL_QM, "CANCEL_QM"},
    {MessageType::CHALLENGE_CANCEL, "CHALLENGE_CANCEL"},
    {MessageType::CHALLENGE_REQUEST, "CHALLENGE_REQUEST"},
    {MessageType::CHALLENGE_RESPONSE, "CHALLENGE_RESPONSE"},
    {MessageType::AI_MATCH, "AI_MATCH"},
    {MessageType::GAME_START, "GAME_START"},
    {MessageType::MOVE, "MOVE"},
    {MessageType::INVALID_MOVE, "INVALID_MOVE"},
    {MessageType::MESSAGE, "MESSAGE"},
    {MessageType::GAME_END, "GAME_END"},
    {MessageType::SUGGEST_MOVE, "SUGGEST_MOVE"},
    {MessageType::RESIGN, "RESIGN"},
    {MessageType::DRAW_REQUEST, "DRAW_REQUEST"},
    {MessageType::DRAW_RESPONSE, "DRAW_RESPONSE"},
    {MessageType::REMATCH_REQUEST, "REMATCH_REQUEST"},
    {MessageType::REMATCH_RESPONSE, "REMATCH_RESPONSE"},
    {MessageType::USER_STATS, "USER_STATS"},
    {MessageType::GAME_HISTORY, "GAME_HISTORY"},
    {MessageType::REPLAY_REQUEST, "REPLAY_REQUEST"},
    {MessageType::LEADER_BOARD, "LEADER_BOARD"},
    {MessageType::PLAYER_LIST, "PLAYER_LIST"},
    {MessageType::INFO, "INFO"},
    {MessageType::REQUEST_ADD_FRIEND, "REQUEST_ADD_FRIEND"},
    {MessageType::RESPONSE_ADD_FRIEND, "RESPONSE_ADD_FRIEND"},
    {MessageType::UNFRIEND, "UNFRIEND"},
    {MessageType::ERROR, "ERROR"}};

inline const char *messageTypeToString(MessageType type) {
  auto it = typeStrings.find(type);
  return (it != typeStrings.end()) ? it->second : "UNKNOWN";
}

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