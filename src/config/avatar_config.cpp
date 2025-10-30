#include "../../include/config/avatar_config.h"

bool AvatarConfig::isValidAvatarId(int avatarId) {
    return avatarId >= 1 && avatarId <= TOTAL_AVATARS;
}

std::string AvatarConfig::getAvatarFilename(int avatarId) {
    if (!isValidAvatarId(avatarId)) {
        avatarId = getDefaultAvatarId();
    }
    return "avatar_" + std::to_string(avatarId) + ".jpg";
}

std::string AvatarConfig::getAvatarPath(int avatarId) {
    if (!isValidAvatarId(avatarId)) {
        avatarId = getDefaultAvatarId();
    }
    return "resources/avatars/avatar_" + std::to_string(avatarId) + ".jpg";
}

int AvatarConfig::getDefaultAvatarId() {
    return 1; // Avatar 1 is default
}

std::vector<int> AvatarConfig::getAvailableAvatarIds() {
    std::vector<int> ids;
    for (int i = 1; i <= TOTAL_AVATARS; i++) {
        ids.push_back(i);
    }
    return ids;
}
