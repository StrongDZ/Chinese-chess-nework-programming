#!/bin/bash

# Test runner script for Chinese Chess Network Programming

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Default values
BUILD_DIR="build"
TEST_TARGET=""

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -b|--build-dir)
            BUILD_DIR="$2"
            shift 2
            ;;
        -t|--test)
            TEST_TARGET="$2"
            shift 2
            ;;
        -h|--help)
            echo "Usage: $0 [OPTIONS]"
            echo "Options:"
            echo "  -b, --build-dir DIR   Build directory (default: build)"
            echo "  -t, --test TARGET     Run specific test target"
            echo "  -h, --help            Show this help message"
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            exit 1
            ;;
    esac
done

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_DIR"

# Check if build directory exists
if [ ! -d "$BUILD_DIR" ]; then
    echo -e "${RED}Build directory not found: ${BUILD_DIR}${NC}"
    echo -e "${YELLOW}Run ./scripts/build.sh first${NC}"
    exit 1
fi

cd "$BUILD_DIR"

echo -e "${GREEN}Running tests...${NC}"

# Run tests using CTest
if [ -n "$TEST_TARGET" ]; then
    echo -e "Running specific test: ${YELLOW}${TEST_TARGET}${NC}"
    ctest -R "$TEST_TARGET" --output-on-failure
else
    echo -e "Running all tests..."
    ctest --output-on-failure
fi

if [ $? -eq 0 ]; then
    echo -e "${GREEN}All tests passed!${NC}"
else
    echo -e "${RED}Some tests failed!${NC}"
    exit 1
fi

