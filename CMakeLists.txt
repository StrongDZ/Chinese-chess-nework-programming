cmake_minimum_required(VERSION 3.15)
project(ChessServer VERSION 1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_SOURCE_DIR}/bin)

# MongoDB paths
set(MONGOCXX_INCLUDE_DIR /usr/local/include/mongocxx/v_noabi)
set(MONGOCXX_LIB_DIR /usr/local/lib)
set(BSONCXX_INCLUDE_DIR /usr/local/include/bsoncxx/v_noabi)

include_directories(
    ${CMAKE_SOURCE_DIR}/include
    ${MONGOCXX_INCLUDE_DIR}
    ${BSONCXX_INCLUDE_DIR}
    /usr/local/include/libmongoc-1.0
    /usr/local/include/libbson-1.0
    /usr/include/jsoncpp
)

link_directories(${MONGOCXX_LIB_DIR})

# Test Database Connections
add_executable(test_connections
    tests/database/test_connections.cpp
    src/database/mongodb_client.cpp
    src/database/redis_client.cpp
)

target_link_libraries(test_connections
    mongocxx
    bsoncxx
    jsoncpp
    hiredis
    pthread
)

# Test Auth Handler
add_executable(test_auth
    tests/auth/test_auth_handler.cpp
    src/auth/auth_handler.cpp
    src/database/mongodb_client.cpp
    src/database/redis_client.cpp
)

target_link_libraries(test_auth
    mongocxx
    bsoncxx
    jsoncpp
    hiredis
    ssl
    crypto
    pthread
)

# Test Challenge Handler
add_executable(test_challenge
    tests/challenge/test_challenge_handler.cpp
    src/challenge/challenge_handler.cpp
    src/auth/auth_handler.cpp
    src/database/mongodb_client.cpp
    src/database/redis_client.cpp
)

target_link_libraries(test_challenge
    mongocxx
    bsoncxx
    jsoncpp
    hiredis
    ssl
    crypto
    pthread
)

# Test Game Handler
add_executable(test_game
    tests/game/test_game_handler.cpp
    src/game/game_handler.cpp
    src/challenge/challenge_handler.cpp
    src/auth/auth_handler.cpp
    src/database/mongodb_client.cpp
    src/database/redis_client.cpp
)

target_link_libraries(test_game
    mongocxx
    bsoncxx
    jsoncpp
    hiredis
    ssl
    crypto
    pthread
)

# Test Player Stats Handler
add_executable(test_player_stat
    tests/player_stat/test_player_stat_handler.cpp
    src/player_stat/player_stat_handler.cpp
    src/game/game_handler.cpp
    src/challenge/challenge_handler.cpp
    src/auth/auth_handler.cpp
    src/database/mongodb_client.cpp
    src/database/redis_client.cpp
)

target_link_libraries(test_player_stat
    mongocxx
    bsoncxx
    jsoncpp
    hiredis
    ssl
    crypto
    pthread
)

