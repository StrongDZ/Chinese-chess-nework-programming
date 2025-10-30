#ifndef AVATAR_CONFIG_H
#define AVATAR_CONFIG_H

#include <string>
#include <vector>

class AvatarConfig {
public:
    // Số lượng avatar có sẵn (1-10)
    static const int TOTAL_AVATARS = 10;
    
    // Validate avatar ID (1-10)
    static bool isValidAvatarId(int avatarId);
    
    // Get avatar filename by ID (avatar_1.jpg, avatar_2.jpg, ...)
    static std::string getAvatarFilename(int avatarId);
    
    // Get full avatar path
    static std::string getAvatarPath(int avatarId);
    
    // Get default avatar ID
    static int getDefaultAvatarId();
    
    // Get list of all available avatar IDs
    static std::vector<int> getAvailableAvatarIds();
};

#endif // AVATAR_CONFIG_H
