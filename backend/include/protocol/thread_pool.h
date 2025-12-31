#pragma once

#include "message_types.h"
#include <condition_variable>
#include <functional>
#include <mutex>
#include <queue>
#include <thread>
#include <vector>

// Forward declarations
struct ParsedMessage;

/**
 * Simple thread pool for handling background tasks (e.g., AI processing).
 */
class ThreadPool {
public:
  explicit ThreadPool(size_t num_threads);
  ~ThreadPool();

  template <class F> void enqueue(F &&f);

private:
  std::vector<std::thread> workers;
  std::queue<std::function<void()>> tasks;
  std::mutex queue_mutex;
  std::condition_variable condition;
  bool stop;
};

// Template implementation
template <class F> void ThreadPool::enqueue(F &&f) {
  {
    std::unique_lock<std::mutex> lock(queue_mutex);
    tasks.emplace(std::forward<F>(f));
  }
  condition.notify_one();
}

// ===================== Message Queue Structures ===================== //

// Thread-safe message queue for AI responses
struct AIMessage {
  int player_fd;
  MessageType type;
  Payload payload;
};

// Thread-safe message queue for client messages
struct ClientMessage {
  ParsedMessage parsed_msg;
  int fd;
};

// ===================== Message Queue Functions ===================== //

// Initialize and start client message queue workers
// Returns vector of worker threads and stop flag reference
std::vector<std::thread> startClientMessageWorkers(bool &stop_flag);

// Stop client message workers
void stopClientMessageWorkers(std::vector<std::thread> &workers,
                              bool &stop_flag);

// Push client message to queue
void pushClientMessage(const ParsedMessage &pm, int fd);

// Process AI message queue (called from main thread)
void processAIMessageQueue();

// Push AI message to queue (called from AI threads)
void pushAIMessage(int player_fd, MessageType type, const Payload &payload);
