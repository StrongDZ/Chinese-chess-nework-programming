#include <arpa/inet.h>
#include <errno.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <unistd.h>

#include <cstring>
#include <string>

#include "../../include/protocol/handle_socket.h"
#include "protocol/message_types.h"

using namespace std;

static bool recvAll(int fd, void *buffer, size_t bytes) {
  char *ptr = static_cast<char *>(buffer);
  size_t total = 0;
  while (total < bytes) {
    ssize_t n = recv(fd, ptr + total, bytes - total, 0);
    if (n < 0) {
      // Check if it's just "no data available" (OK in edge-triggered mode)
      if (errno == EAGAIN || errno == EWOULDBLOCK) {
        // No data yet, but connection is still open
        // errno is preserved for caller to check
        return false; // Signal "no data yet"
      }
      // Real error - connection closed or error
      return false;
    }
    if (n == 0) {
      // Connection closed by peer - set errno to indicate this
      errno = ECONNRESET;
      return false;
    }
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

bool sendMessage(int fd, MessageType type, const Payload &payload) {
  string data = makeMessage(type, payload);
  uint32_t len = htonl(static_cast<uint32_t>(data.size()));
  if (!sendAll(fd, &len, sizeof(len)))
    return false;
  return sendAll(fd, data.data(), data.size());
}

bool recvMessage(int fd, string &out) {
  uint32_t netLen = 0;
  if (!recvAll(fd, &netLen, sizeof(netLen))) {
    // Connection closed or error reading length
    return false;
  }
  uint32_t len = ntohl(netLen);
  if (len > 10 * 1024 * 1024) {
    // Message too large
    return false; // guard 10MB
  }
  out.resize(len);
  if (len == 0) {
    // Empty message is valid
    return true;
  }
  if (!recvAll(fd, out.data(), len)) {
    // Connection closed or error reading message body
    return false;
  }
  return true;
}