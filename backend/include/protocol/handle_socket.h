#pragma once

#include "MessageTypes.h"
#include <string>

// Socket communication functions
bool sendMessage(int fd, MessageType type, const Payload &payload = EmptyPayload{});
bool recvMessage(int fd, std::string &out);

