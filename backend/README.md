# Chinese Chess Network Programming

A TCP-based network protocol implementation for Chinese Chess game server.

## Project Structure

```
├── CMakeLists.txt
├── cmake/
│   └── modules/              # Custom CMake modules (if needed)
├── include/
│   └── protocol/             # Header files (public API)
│       └── *.h
├── src/
│   └── protocol/             # Source implementation
│       └── *.cpp
├── tests/
│   ├── CMakeLists.txt
│   └── *.cpp                 # Unit tests
├── examples/
│   └── *.cpp                 # Example usage code
├── docs/
│   ├── *.md                  # Documentation
│   └── SequenceDiagram/     # Sequence diagrams
├── scripts/
│   ├── build.sh              # Build script
│   └── run_tests.sh          # Test runner script
└── build/                    # Generated build folder (ignored by git)
```

## Building

### Using the build script (recommended)

```bash
# Release build
./scripts/build.sh

# Debug build
./scripts/build.sh --debug

# Clean build
./scripts/build.sh --clean

# Custom number of parallel jobs
./scripts/build.sh -j 4
```

### Manual build

```bash
mkdir -p build
cd build
cmake .. -DCMAKE_BUILD_TYPE=Release
cmake --build . -j$(nproc)
```

The executable will be located at `build/bin/ChineseChessNetworkProgramming`.

## Running

```bash
# Default port (8080)
./build/bin/ChineseChessNetworkProgramming

# Custom port
./build/bin/ChineseChessNetworkProgramming 9090
```

## Testing

```bash
# Run all tests
./scripts/run_tests.sh

# Run specific test
./scripts/run_tests.sh --test test_name
```

## Documentation

See the `docs/` directory for:

-   Protocol specifications
-   Sequence diagrams
-   API documentation

## Dependencies

-   C++17 compiler
-   CMake 3.15+
-   nlohmann/json (header-only, included)
-   rapidjson (header-only, included)
-   POSIX sockets (Linux/macOS)

## License

[Add your license here]
