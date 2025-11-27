cmake_minimum_required(VERSION 3.15)
project(ChineseChessNetworkProgramming VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Build type
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

# Output directories
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin)
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib)
set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib)

# MongoDB paths
set(MONGOCXX_INCLUDE_DIR /usr/local/include/mongocxx/v_noabi)
set(MONGOCXX_LIB_DIR /usr/local/lib)
set(BSONCXX_INCLUDE_DIR /usr/local/include/bsoncxx/v_noabi)

# Include directories
include_directories(
    ${CMAKE_SOURCE_DIR}/include
    ${MONGOCXX_INCLUDE_DIR}
    ${BSONCXX_INCLUDE_DIR}
    /usr/local/include/libmongoc-1.0
    /usr/local/include/libbson-1.0
    /usr/include/jsoncpp
)

link_directories(${MONGOCXX_LIB_DIR})

# Find required packages
find_package(Threads REQUIRED)

# Source files
set(SOURCES
    protocol/server.cpp
    protocol/handle_socket.cpp
    src/auth/auth_handler.cpp
    src/challenge/challenge_handler.cpp
    src/game/game_handler.cpp
    src/player_stat/player_stat_handler.cpp
    src/friend/friend_handler.cpp
    src/database/mongodb_client.cpp
    src/database/redis_client.cpp
)

# Header files
set(HEADERS
    include/protocol/MessageTypes.h
    include/protocol/server.h
    include/auth/auth_handler.h
    include/challenge/challenge_handler.h
    include/game/game_handler.h
    include/player_stat/player_stat_handler.h
    include/friend/friend_handler.h
    include/database/mongodb_client.h
    include/database/redis_client.h
)

# Third-party libraries (if using submodules or installed packages)
# For nlohmann/json and rapidjson, assuming they are header-only
# and located in include/ or third_party/

# Create executable
add_executable(${PROJECT_NAME} ${SOURCES} ${HEADERS})

# Link libraries
target_link_libraries(${PROJECT_NAME} PRIVATE 
    Threads::Threads
    mongocxx
    bsoncxx
    jsoncpp
    hiredis
    ssl
    crypto
)

# Compiler options
target_compile_options(${PROJECT_NAME} PRIVATE
    -Wall
    -Wextra
    -Wpedantic
)

# Debug flags
target_compile_definitions(${PROJECT_NAME} PRIVATE
    $<$<CONFIG:Debug>:DEBUG>
)

# Add tests subdirectory
enable_testing()
add_subdirectory(tests)

# Installation
install(TARGETS ${PROJECT_NAME}
    RUNTIME DESTINATION bin
)

install(FILES ${HEADERS}
    DESTINATION include/protocol
)

