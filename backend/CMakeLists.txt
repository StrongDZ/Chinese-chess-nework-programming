cmake_minimum_required(VERSION 3.15)
project(ChineseChessNetworkProgramming VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

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
set(BSONCXX_INCLUDE_DIR /usr/local/include/bsoncxx/v_noabi)
set(MONGOCXX_LIB_DIR /usr/local/lib)

# Find required packages
find_package(Threads REQUIRED)

# Auto-detect source files
file(GLOB_RECURSE SOURCES
    "${CMAKE_CURRENT_SOURCE_DIR}/src/**/*.cpp"
)

# Auto-detect header files  
file(GLOB_RECURSE HEADERS
    "${CMAKE_CURRENT_SOURCE_DIR}/include/**/*.h"
)

# Create executable
add_executable(${PROJECT_NAME} ${SOURCES} ${HEADERS})

# Include directories
target_include_directories(${PROJECT_NAME} PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${MONGOCXX_INCLUDE_DIR}
    ${BSONCXX_INCLUDE_DIR}
    /usr/local/include/libmongoc-1.0
    /usr/local/include/libbson-1.0
    /usr/include/nlohmann
    /usr/include
)

# Link directories
target_link_directories(${PROJECT_NAME} PRIVATE ${MONGOCXX_LIB_DIR})

# Link libraries
target_link_libraries(${PROJECT_NAME} PRIVATE 
    Threads::Threads
    mongocxx
    bsoncxx
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

# Unit/Integration tests
add_subdirectory(tests)

# Installation
install(TARGETS ${PROJECT_NAME}
    RUNTIME DESTINATION bin
)

install(FILES ${HEADERS}
    DESTINATION include/protocol
)
