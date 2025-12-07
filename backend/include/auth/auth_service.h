#ifndef AUTH_SERVICE_H
#define AUTH_SERVICE_H

#include "auth_repository.h"
#include <string>

// Response struct cho Service layer
struct AuthResult {
    bool success;
    std::string message;
    std::string username;
    int avatarId;
};

class AuthService {
private:
    AuthRepository& repository;
    
    // Hash password bằng SHA256
    std::string hashPassword(const std::string& password);
    
    // Validate username (3-20 chars, alphanumeric + underscore)
    bool isValidUsername(const std::string& username);
    
    // Validate password (min 6 chars)
    bool isValidPassword(const std::string& password);
    
    // Validate avatar_id (1-10)
    bool isValidAvatarId(int avatarId);

public:
    explicit AuthService(AuthRepository& repo);
    
    // Đăng ký: username + password + avatar_id
    AuthResult registerUser(const std::string& username, 
                           const std::string& password, 
                           int avatarId);
    
    // Đăng nhập: username + password
    // Không trả về token nữa - layer protocol sẽ mapping fd -> username
    AuthResult login(const std::string& username, 
                    const std::string& password);
    
    // Đăng xuất (by username - khi người dùng ngắt kết nối)
    AuthResult logout(const std::string& username);
    
    // Đổi avatar (by username)
    AuthResult changeAvatar(const std::string& username, int newAvatarId);
    
    // Kiểm tra username có tồn tại không
    bool userExists(const std::string& username);
};

#endif
