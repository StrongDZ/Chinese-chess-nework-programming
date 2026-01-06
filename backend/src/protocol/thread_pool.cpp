#include "protocol/thread_pool.h"
#include "protocol/handle_socket.h"
#include "protocol/server.h"
#include <iostream>

using namespace std;

// ===================== ThreadPool Implementation ===================== //

ThreadPool::ThreadPool(size_t num_threads) : stop(false) {
  for (size_t i = 0; i < num_threads; ++i) {
    workers.emplace_back([this] {
      while (true) {
        std::function<void()> task;
        {
          std::unique_lock<std::mutex> lock(queue_mutex);
          condition.wait(lock, [this] { return stop || !tasks.empty(); });
          if (stop && tasks.empty())
            return;
          task = std::move(tasks.front());
          tasks.pop();
        }
        task();
      }
    });
  }
}

ThreadPool::~ThreadPool() {
  {
    std::unique_lock<std::mutex> lock(queue_mutex);
    stop = true;
  }
  condition.notify_all();
  for (std::thread &worker : workers)
    worker.join();
}

// ===================== Message Queue Implementation ===================== //

// Global message queues
static std::queue<AIMessage> g_ai_message_queue;
static std::mutex g_ai_queue_mutex;
static std::condition_variable g_ai_queue_cv;

static std::queue<ClientMessage> g_client_message_queue;
static std::mutex g_client_queue_mutex;
static std::condition_variable g_client_queue_cv;

// Forward declarations (from server.cpp)
void processMessage(const ParsedMessage &pm, int fd);
extern map<int, PlayerInfo> g_clients;
extern mutex g_clients_mutex;

std::vector<std::thread> startClientMessageWorkers(bool &stop_flag) {
  std::vector<std::thread> workers;
  for (size_t i = 0; i < 4; ++i) {
    workers.emplace_back([&stop_flag]() {
      while (true) {
        ClientMessage client_msg;
        {
          std::unique_lock<std::mutex> lock(g_client_queue_mutex);
          g_client_queue_cv.wait(lock, [&stop_flag] {
            return stop_flag || !g_client_message_queue.empty();
          });
          if (stop_flag && g_client_message_queue.empty()) {
            return;
          }
          client_msg = std::move(g_client_message_queue.front());
          g_client_message_queue.pop();
        }
        // Process message in worker thread
        try {
          processMessage(client_msg.parsed_msg, client_msg.fd);
        } catch (const std::exception &e) {
          cerr << "Worker exception: " << e.what() << endl;
        } catch (...) {
          cerr << "Worker unknown exception" << endl;
        }
      }
    });
  }
  return workers;
}

void stopClientMessageWorkers(std::vector<std::thread> &workers,
                              bool &stop_flag) {
  {
    std::lock_guard<std::mutex> lock(g_client_queue_mutex);
    stop_flag = true;
  }
  g_client_queue_cv.notify_all();
  for (auto &worker : workers) {
    worker.join();
  }
}

void pushClientMessage(const ParsedMessage &pm, int fd) {
  {
    std::lock_guard<std::mutex> lock(g_client_queue_mutex);
    g_client_message_queue.push({pm, fd});
  }
  g_client_queue_cv.notify_one();
}

void processAIMessageQueue() {
  std::lock_guard<std::mutex> lock(g_ai_queue_mutex);
  while (!g_ai_message_queue.empty()) {
    AIMessage msg = g_ai_message_queue.front();
    g_ai_message_queue.pop();

    // Send message to client (from main thread - thread-safe)
    {
      lock_guard<mutex> lock(g_clients_mutex);
      if (g_clients.count(msg.player_fd)) {
        sendMessage(msg.player_fd, msg.type, msg.payload);
      }
    }
  }
}

void pushAIMessage(int player_fd, MessageType type, const Payload &payload) {
  {
    std::lock_guard<std::mutex> lock(g_ai_queue_mutex);
    g_ai_message_queue.push({player_fd, type, payload});
  }
  g_ai_queue_cv.notify_one();
}
