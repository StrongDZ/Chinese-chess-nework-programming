#ifndef CHALLENGE_REPOSITORY_H
#define CHALLENGE_REPOSITORY_H

#include "database/mongodb_client.h"
#include <chrono>
#include <optional>
#include <string>
#include <vector>

/**
 * ChallengeRepository - Data Access Layer cho Challenge
 *
 * Chịu trách nhiệm:
 * - CRUD operations trên MongoDB collection "challenges"
 * - Không chứa business logic
 */

// Challenge model
struct Challenge {
  std::string id; // ObjectId as string
  std::string challenger_username;
  std::string challenged_username;
  std::string time_control; // blitz, classical
  bool rated;
  std::string status;  // pending, accepted, declined, cancelled, expired
  std::string message; // Optional message
  std::chrono::system_clock::time_point created_at;
  std::chrono::system_clock::time_point expires_at;
  std::optional<std::chrono::system_clock::time_point> responded_at;
  std::optional<std::string> game_id; // Created when accepted
};

class ChallengeRepository {
private:
  MongoDBClient &mongoClient;

public:
  explicit ChallengeRepository(MongoDBClient &mongo);

  // Create new challenge, returns challenge_id if success
  std::string create(const Challenge &challenge);

  // Find challenge by ID
  std::optional<Challenge> findById(const std::string &challengeId);

  // Find pending challenge between two users
  std::optional<Challenge>
  findPendingBetweenUsers(const std::string &challenger,
                          const std::string &challenged);

  // Update challenge status
  bool updateStatus(const std::string &challengeId,
                    const std::string &newStatus,
                    const std::optional<std::string> &gameId = std::nullopt);

  // Get challenges by user with filter
  // filter: "all", "sent", "received", "pending"
  std::vector<Challenge> findByUser(const std::string &username,
                                    const std::string &filter = "all",
                                    int limit = 50);

  // Check if user exists (helper for validation)
  bool userExists(const std::string &username);

  // Delete expired challenges (MongoDB TTL handles this, but manual cleanup
  // available)
  int deleteExpired();
};

#endif
