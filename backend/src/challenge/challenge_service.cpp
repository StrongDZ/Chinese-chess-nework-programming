#include "../../include/challenge/challenge_service.h"
#include <regex>
#include <chrono>

using namespace std;

ChallengeService::ChallengeService(ChallengeRepository& repo) 
    : repository(repo) {}

bool ChallengeService::isValidTimeControl(const string& timeControl) {
    return (timeControl == "blitz" || 
            timeControl == "classical" || 
            timeControl == "bullet");
}

bool ChallengeService::isValidUsername(const string& username) {
    if (username.length() < 3 || username.length() > 20) return false;
    regex pattern("^[a-zA-Z0-9_]+$");
    return regex_match(username, pattern);
}

ChallengeResult ChallengeService::createChallenge(const string& challengerUsername,
                                                   const string& challengedUsername,
                                                   const string& timeControl,
                                                   bool rated,
                                                   const string& message) {
    ChallengeResult result;
    result.success = false;
    
    // 1. Validate usernames
    if (!isValidUsername(challengerUsername) || !isValidUsername(challengedUsername)) {
        result.message = "Invalid username format";
        return result;
    }
    
    // 2. Cannot challenge yourself
    if (challengerUsername == challengedUsername) {
        result.message = "Cannot challenge yourself";
        return result;
    }
    
    // 3. Validate time control
    if (!isValidTimeControl(timeControl)) {
        result.message = "Invalid time_control. Must be: bullet, blitz, or classical";
        return result;
    }
    
    // 4. Check both users exist
    if (!repository.userExists(challengerUsername)) {
        result.message = "Challenger not found";
        return result;
    }
    
    if (!repository.userExists(challengedUsername)) {
        result.message = "Challenged user not found";
        return result;
    }
    
    // 5. Check for existing pending challenge
    auto existingChallenge = repository.findPendingBetweenUsers(challengerUsername, challengedUsername);
    if (existingChallenge) {
        result.message = "You already have a pending challenge to this user";
        return result;
    }
    
    // 6. Create challenge
    Challenge challenge;
    challenge.challenger_username = challengerUsername;
    challenge.challenged_username = challengedUsername;
    challenge.time_control = timeControl;
    challenge.rated = rated;
    challenge.status = "pending";
    
    // Limit message length
    if (message.length() > 200) {
        challenge.message = message.substr(0, 200);
    } else {
        challenge.message = message;
    }
    
    challenge.created_at = chrono::system_clock::now();
    challenge.expires_at = challenge.created_at + chrono::seconds(PENDING_TTL_SECONDS);
    
    // 7. Insert to database
    string challengeId = repository.create(challenge);
    
    if (challengeId.empty()) {
        result.message = "Failed to create challenge";
        return result;
    }
    
    challenge.id = challengeId;
    
    // 8. Success
    result.success = true;
    result.message = "Challenge created successfully";
    result.challenge = challenge;
    
    return result;
}

ChallengeResult ChallengeService::cancelChallenge(const string& username, 
                                                   const string& challengeId) {
    ChallengeResult result;
    result.success = false;
    
    // 1. Load challenge
    auto challengeOpt = repository.findById(challengeId);
    
    if (!challengeOpt) {
        result.message = "Challenge not found or expired";
        return result;
    }
    
    Challenge challenge = challengeOpt.value();
    
    // 2. Verify user is the challenger
    if (challenge.challenger_username != username) {
        result.message = "Only the challenger can cancel the challenge";
        return result;
    }
    
    // 3. Check if challenge is still pending
    if (challenge.status != "pending") {
        result.message = "Can only cancel pending challenges";
        return result;
    }
    
    // 4. Update status to cancelled
    if (!repository.updateStatus(challengeId, "cancelled")) {
        result.message = "Failed to cancel challenge";
        return result;
    }
    
    challenge.status = "cancelled";
    
    // 5. Success
    result.success = true;
    result.message = "Challenge cancelled successfully";
    result.challenge = challenge;
    
    return result;
}

ChallengeResult ChallengeService::acceptChallenge(const string& username, 
                                                   const string& challengeId) {
    ChallengeResult result;
    result.success = false;
    
    // 1. Load challenge
    auto challengeOpt = repository.findById(challengeId);
    
    if (!challengeOpt) {
        result.message = "Challenge not found or expired";
        return result;
    }
    
    Challenge challenge = challengeOpt.value();
    
    // 2. Verify user is the challenged player
    if (challenge.challenged_username != username) {
        result.message = "Only the challenged player can accept";
        return result;
    }
    
    // 3. Check if challenge is still pending
    if (challenge.status != "pending") {
        result.message = "Challenge is no longer pending";
        return result;
    }
    
    // 4. Check if challenge has expired
    if (chrono::system_clock::now() > challenge.expires_at) {
        repository.updateStatus(challengeId, "expired");
        result.message = "Challenge has expired";
        return result;
    }
    
    // 5. Update status to accepted
    if (!repository.updateStatus(challengeId, "accepted")) {
        result.message = "Failed to accept challenge";
        return result;
    }
    
    challenge.status = "accepted";
    
    // 6. Success
    result.success = true;
    result.message = "Challenge accepted successfully";
    result.challenge = challenge;
    
    return result;
}

ChallengeResult ChallengeService::declineChallenge(const string& username, 
                                                    const string& challengeId) {
    ChallengeResult result;
    result.success = false;
    
    // 1. Load challenge
    auto challengeOpt = repository.findById(challengeId);
    
    if (!challengeOpt) {
        result.message = "Challenge not found or expired";
        return result;
    }
    
    Challenge challenge = challengeOpt.value();
    
    // 2. Verify user is the challenged player
    if (challenge.challenged_username != username) {
        result.message = "Only the challenged player can decline";
        return result;
    }
    
    // 3. Check if challenge is still pending
    if (challenge.status != "pending") {
        result.message = "Challenge is no longer pending";
        return result;
    }
    
    // 4. Update status to declined
    if (!repository.updateStatus(challengeId, "declined")) {
        result.message = "Failed to decline challenge";
        return result;
    }
    
    challenge.status = "declined";
    
    // 5. Success
    result.success = true;
    result.message = "Challenge declined successfully";
    result.challenge = challenge;
    
    return result;
}

ChallengeResult ChallengeService::listChallenges(const string& username, 
                                                  const string& filter) {
    ChallengeResult result;
    result.success = true;
    result.message = "Challenges retrieved successfully";
    result.challenges = repository.findByUser(username, filter);
    
    return result;
}
