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

# Source files (NO REDIS - MongoDB only)
set(SOURCES
    protocol/server.cpp
    protocol/ai_engine.cpp
    protocol/handle_socket.cpp
    src/auth/auth_controller.cpp
    src/auth/auth_service.cpp
    src/auth/auth_repository.cpp
    src/challenge/challenge_controller.cpp
    src/challenge/challenge_service.cpp
    src/challenge/challenge_repository.cpp
    src/game/game_controller.cpp
    src/game/game_service.cpp
    src/game/game_repository.cpp
    src/friend/friend_controller.cpp
    src/friend/friend_service.cpp
    src/friend/friend_repository.cpp
    src/database/mongodb_client.cpp
)

# Header files (NO REDIS)
set(HEADERS
    include/protocol/MessageTypes.h
    include/protocol/server.h
    include/protocol/ai_engine.h
    include/auth/auth_controller.h
    include/auth/auth_service.h
    include/auth/auth_repository.h
    include/challenge/challenge_controller.h
    include/challenge/challenge_service.h
    include/challenge/challenge_repository.h
    include/game/game_controller.h
    include/game/game_service.h
    include/game/game_repository.h
    include/friend/friend_controller.h
    include/friend/friend_service.h
    include/friend/friend_repository.h
    include/database/mongodb_client.h
)

# Third-party libraries (if using submodules or installed packages)
# For nlohmann/json and rapidjson, assuming they are header-only
# and located in include/ or third_party/

# Create executable
add_executable(${PROJECT_NAME} ${SOURCES} ${HEADERS})

# Link libraries (NO hiredis - Redis removed)
target_link_libraries(${PROJECT_NAME} PRIVATE 
    Threads::Threads
    mongocxx
    bsoncxx
    jsoncpp
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

enable_testing()
add_subdirectory(tests)

# Installation
install(TARGETS ${PROJECT_NAME}
    RUNTIME DESTINATION bin
)

install(FILES ${HEADERS}
    DESTINATION include/protocol
)

