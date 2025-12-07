#include "../../include/auth/auth_service.h"
#include <openssl/sha.h>
#include <regex>
#include <iomanip>
#include <sstream>

using namespace std;

AuthService::AuthService(AuthRepository& repo) : repository(repo) {}

// ============================================
// PRIVATE HELPERS
// ============================================

string AuthService::hashPassword(const string& password) {
    unsigned char hash[SHA256_DIGEST_LENGTH];
    SHA256((unsigned char*)password.c_str(), password.length(), hash);
    
    stringstream ss;
    for (int i = 0; i < SHA256_DIGEST_LENGTH; i++) {
        ss << hex << setw(2) << setfill('0') << (int)hash[i];
    }
    return ss.str();
}

bool AuthService::isValidUsername(const string& username) {
    // 3-20 chars, alphanumeric + underscore
    if (username.length() < 3 || username.length() > 20) return false;
    regex pattern("^[a-zA-Z0-9_]+$");
    return regex_match(username, pattern);
}

bool AuthService::isValidPassword(const string& password) {
    // Min 6 chars
    return password.length() >= 6;
}

bool AuthService::isValidAvatarId(int avatarId) {
    // 1-10
    return avatarId >= 1 && avatarId <= 10;
}

// ============================================
// PUBLIC METHODS
// ============================================

AuthResult AuthService::registerUser(const string& username, 
                                     const string& password, 
                                     int avatarId) {
    AuthResult result;
    
    // 1. Validate input
    if (!isValidUsername(username)) {
        result.success = false;
        result.message = "Invalid username (3-20 chars, alphanumeric + underscore)";
        return result;
    }
    
    if (!isValidPassword(password)) {
        result.success = false;
        result.message = "Password must be at least 6 characters";
        return result;
    }
    
    if (!isValidAvatarId(avatarId)) {
        result.success = false;
        result.message = "Invalid avatar_id (must be 1-10)";
        return result;
    }
    
    // 2. Check username exists
    if (repository.usernameExists(username)) {
        result.success = false;
        result.message = "Username already exists";
        return result;
    }
    
    // 3. Hash password
    string passwordHash = hashPassword(password);
    
    // 4. Create user (returns username)
    string createdUsername = repository.createUser(username, passwordHash, avatarId);
    
    if (createdUsername.empty()) {
        result.success = false;
        result.message = "Failed to create user";
        return result;
    }
    
    // 5. Create default player stats (using username)
    repository.createDefaultStats(createdUsername);
    
    // 6. Return success
    result.success = true;
    result.message = "Registration successful";
    result.username = createdUsername;
    result.avatarId = avatarId;
    
    return result;
}

AuthResult AuthService::login(const string& username, const string& password) {
    AuthResult result;
    
    // 1. Find user
    auto userOpt = repository.findByUsername(username);
    
    if (!userOpt) {
        result.success = false;
        result.message = "Invalid username or password";
        return result;
    }
    
    User user = userOpt.value();
    
    // 2. Check password
    string passwordHash = hashPassword(password);
    
    if (user.password_hash != passwordHash) {
        result.success = false;
        result.message = "Invalid username or password";
        return result;
    }
    
    // 3. Check account status
    if (user.status == "banned") {
        result.success = false;
        result.message = "Account is banned";
        return result;
    }
    
    // 4. Update last login and online status
    repository.updateLastLogin(username);
    
    // 5. Return success (no token!)
    result.success = true;
    result.message = "Login successful";
    result.username = user.username;
    result.avatarId = user.avatar_id;
    
    return result;
}

AuthResult AuthService::logout(const string& username) {
    AuthResult result;
    
    // 1. Check user exists
    auto userOpt = repository.findByUsername(username);
    
    if (!userOpt) {
        result.success = false;
        result.message = "User not found";
        return result;
    }
    
    // 2. Update online status
    bool updated = repository.updateOnlineStatus(username, false);
    
    if (!updated) {
        result.success = false;
        result.message = "Failed to update online status";
        return result;
    }
    
    // 3. Return success
    result.success = true;
    result.message = "Logout successful";
    result.username = username;
    
    return result;
}

AuthResult AuthService::changeAvatar(const string& username, int newAvatarId) {
    AuthResult result;
    
    // 1. Validate avatar ID
    if (!isValidAvatarId(newAvatarId)) {
        result.success = false;
        result.message = "Invalid avatar_id (must be 1-10)";
        return result;
    }
    
    // 2. Check user exists
    auto userOpt = repository.findByUsername(username);
    
    if (!userOpt) {
        result.success = false;
        result.message = "User not found";
        return result;
    }
    
    // 3. Update avatar
    bool updated = repository.updateAvatar(username, newAvatarId);
    
    if (!updated) {
        result.success = false;
        result.message = "Failed to update avatar";
        return result;
    }
    
    // 4. Return success
    result.success = true;
    result.message = "Avatar updated successfully";
    result.username = username;
    result.avatarId = newAvatarId;
    
    return result;
}

bool AuthService::userExists(const string& username) {
    return repository.usernameExists(username);
}
