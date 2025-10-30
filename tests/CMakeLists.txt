# tests/CMakeLists.txt
file(GLOB_RECURSE AUTH_TEST_SOURCES
    ${CMAKE_SOURCE_DIR}/tests/auth/*.cpp
    ${CMAKE_SOURCE_DIR}/src/auth/*.cpp
    ${CMAKE_SOURCE_DIR}/src/database/*.cpp
)

add_executable(test_auth_apis ${AUTH_TEST_SOURCES})

target_include_directories(test_auth_apis PRIVATE
    ${CMAKE_SOURCE_DIR}/include
)

target_link_libraries(test_auth_apis
    mongocxx
    bsoncxx
    jsoncpp
    ssl
    crypto
    pthread
)

target_compile_options(test_auth_apis PRIVATE -Wall -Wextra)

# Register test executable with CTest
add_test(NAME AuthAPIs COMMAND test_auth_apis)