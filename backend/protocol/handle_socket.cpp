#include <arpa/inet.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <unistd.h>

#include <cstring>
#include <string>

#include "../include/protocol/MessageTypes.h"

using namespace std;

static bool recvAll(int fd, void *buffer, size_t bytes) {
  char *ptr = static_cast<char *>(buffer);
  size_t total = 0;
  while (total < bytes) {
    ssize_t n = recv(fd, ptr + total, bytes - total, 0);
    if (n <= 0)
      return false; // error or closed
    total += static_cast<size_t>(n);
  }
  return true;
}

static bool sendAll(int fd, const void *buffer, size_t bytes) {
  const char *ptr = static_cast<const char *>(buffer);
  size_t total = 0;
  while (total < bytes) {
    ssize_t n = send(fd, ptr + total, bytes - total, 0);
    if (n <= 0)
      return false;
    total += static_cast<size_t>(n);
  }
  return true;
}

static bool sendMessage(int fd, MessageType type,
                        const Payload &payload = EmptyPayload{}) {
  string data = makeMessage(type, payload);
  uint32_t len = htonl(static_cast<uint32_t>(data.size()));
  if (!sendAll(fd, &len, sizeof(len)))
    return false;
  return sendAll(fd, data.data(), data.size());
}

static bool recvMessage(int fd, string &out) {
  uint32_t netLen = 0;
  if (!recvAll(fd, &netLen, sizeof(netLen)))
    return false;
  uint32_t len = ntohl(netLen);
  if (len > 10 * 1024 * 1024)
    return false; // guard 10MB
  out.resize(len);
  if (len == 0)
    return true;
  return recvAll(fd, out.data(), len);
}