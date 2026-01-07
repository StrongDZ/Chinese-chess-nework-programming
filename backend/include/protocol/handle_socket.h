#pragma once

#include "message_types.h"
#include <string>
#include <vector>
#include <map>
#include <mutex>

// Per-connection read buffer for handling partial reads in edge-triggered mode
struct ConnectionReadBuffer {
    enum class State {
        READING_LENGTH,  // Waiting to read 4-byte length header
        READING_BODY     // Waiting to read message body
    };
    
    State state = State::READING_LENGTH;
    uint32_t expected_length = 0;      // Expected message body length
    size_t bytes_read = 0;             // Bytes read so far in current phase
    std::vector<char> length_buffer;   // Buffer for 4-byte length (partial)
    std::vector<char> body_buffer;     // Buffer for message body (partial)
    
    ConnectionReadBuffer() : length_buffer(4), body_buffer() {}
    
    void reset() {
        state = State::READING_LENGTH;
        expected_length = 0;
        bytes_read = 0;
        body_buffer.clear();
    }
};

// Global read buffers (protected by mutex)
extern std::map<int, ConnectionReadBuffer> g_read_buffers;
extern std::mutex g_read_buffers_mutex;

// Socket communication functions
bool sendMessage(int fd, MessageType type,
                 const Payload &payload = EmptyPayload{});
bool recvMessage(int fd, std::string &out);

// Initialize/cleanup read buffer for a connection
void initReadBuffer(int fd);
void cleanupReadBuffer(int fd);
