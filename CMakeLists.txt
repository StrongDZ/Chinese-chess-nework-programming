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

# ==================== TESTS ====================

# Test MongoDB connection
add_executable(test_mongo_simple
    tests/database/test_mongo_simple.cpp
    src/database/mongodb_client.cpp
)

target_link_libraries(test_mongo_simple
    mongocxx
    bsoncxx
    jsoncpp
    pthread
)

# Test Redis connection
add_executable(test_redis_simple
    tests/database/test_redis_simple.cpp
    src/database/redis_client.cpp
)

target_link_libraries(test_redis_simple
    hiredis
    jsoncpp
    pthread
)

# Test Protocol (binary message encoding/decoding)
add_executable(test_protocol
    tests/network/test_protocol.cpp
    src/network/protocol.cpp
)

target_link_libraries(test_protocol
    jsoncpp
    pthread
)

# Test AuthHandler
add_executable(test_auth_handler
    tests/auth/test_auth_handler.cpp
    src/auth/auth_handler.cpp
    src/database/mongodb_client.cpp
    src/database/redis_client.cpp
)

target_link_libraries(test_auth_handler
    mongocxx
    bsoncxx
    jsoncpp
    hiredis
    ssl
    crypto
    pthread
)
