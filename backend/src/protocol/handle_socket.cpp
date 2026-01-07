#include <arpa/inet.h>
#include <errno.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <unistd.h>

#include <cstring>
#include <iostream>
#include <string>

#include "../../include/protocol/handle_socket.h"
#include "protocol/message_types.h"

using namespace std;

// Global read buffers
map<int, ConnectionReadBuffer> g_read_buffers;
mutex g_read_buffers_mutex;

void initReadBuffer(int fd) {
    lock_guard<mutex> lock(g_read_buffers_mutex);
    g_read_buffers[fd] = ConnectionReadBuffer();
}

void cleanupReadBuffer(int fd) {
    lock_guard<mutex> lock(g_read_buffers_mutex);
    g_read_buffers.erase(fd);
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
  if (!sendAll(fd, &len, sizeof(len))) {
    cerr << "[SEND fd=" << fd << "] FAILED to send "
         << messageTypeToString(type) << " (length header)" << endl;
    return false;
  }
  if (!sendAll(fd, data.data(), data.size())) {
    cerr << "[SEND fd=" << fd << "] FAILED to send "
         << messageTypeToString(type) << " (message body)" << endl;
    return false;
  }
  cout << "[SEND fd=" << fd << "] " << messageTypeToString(type) << " " << data
       << endl;
  return true;
}

bool recvMessage(int fd, string &out) {
  ConnectionReadBuffer* buf = nullptr;
  {
    lock_guard<mutex> lock(g_read_buffers_mutex);
    auto it = g_read_buffers.find(fd);
    if (it == g_read_buffers.end()) {
      // Initialize buffer if not exists
      g_read_buffers[fd] = ConnectionReadBuffer();
      it = g_read_buffers.find(fd);
    }
    buf = &it->second;
  }
  
  // Phase 1: Read length header (4 bytes)
  if (buf->state == ConnectionReadBuffer::State::READING_LENGTH) {
    while (buf->bytes_read < 4) {
      ssize_t n = recv(fd, buf->length_buffer.data() + buf->bytes_read, 
                       4 - buf->bytes_read, 0);
      if (n < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK) {
          // No more data - wait for next epoll event
          // State is preserved, will resume on next call
          return false;
        }
        // Real error
        buf->reset();
        return false;
      }
      if (n == 0) {
        // Connection closed
        errno = ECONNRESET;
        buf->reset();
        return false;
      }
      buf->bytes_read += static_cast<size_t>(n);
    }
    
    // Got full length header
    uint32_t netLen;
    memcpy(&netLen, buf->length_buffer.data(), 4);
    buf->expected_length = ntohl(netLen);
    
    // Validate length
    if (buf->expected_length > 10 * 1024 * 1024) {
      cerr << "[RECV fd=" << fd << "] ERROR: Message too large: " 
           << buf->expected_length << " bytes" << endl;
      buf->reset();
      errno = EINVAL;
      return false;
    }
    
    // Move to body reading phase
    buf->state = ConnectionReadBuffer::State::READING_BODY;
    buf->bytes_read = 0;
    buf->body_buffer.resize(buf->expected_length);
  }
  
  // Phase 2: Read message body
  if (buf->state == ConnectionReadBuffer::State::READING_BODY) {
    if (buf->expected_length == 0) {
      // Empty message
      out.clear();
      buf->reset();
      return true;
    }
    
    while (buf->bytes_read < buf->expected_length) {
      ssize_t n = recv(fd, buf->body_buffer.data() + buf->bytes_read,
                       buf->expected_length - buf->bytes_read, 0);
      if (n < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK) {
          // No more data - wait for next epoll event
          // State is preserved, will resume on next call
          return false;
        }
        // Real error
        buf->reset();
        return false;
      }
      if (n == 0) {
        // Connection closed
        errno = ECONNRESET;
        buf->reset();
        return false;
      }
      buf->bytes_read += static_cast<size_t>(n);
    }
    
    // Got full message
    out.assign(buf->body_buffer.begin(), buf->body_buffer.end());
    buf->reset();
    return true;
  }
  
  return false;
}