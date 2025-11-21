#!/bin/bash

# Build script for Chinese Chess Network Programming

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Default values
BUILD_TYPE="Release"
BUILD_DIR="build"
CLEAN=false
JOBS=$(nproc)

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -d|--debug)
            BUILD_TYPE="Debug"
            shift
            ;;
        -c|--clean)
            CLEAN=true
            shift
            ;;
        -j|--jobs)
            JOBS="$2"
            shift 2
            ;;
        -h|--help)
            echo "Usage: $0 [OPTIONS]"
            echo "Options:"
            echo "  -d, --debug     Build in Debug mode (default: Release)"
            echo "  -c, --clean     Clean build directory before building"
            echo "  -j, --jobs N    Number of parallel jobs (default: $(nproc))"
            echo "  -h, --help      Show this help message"
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

echo -e "${GREEN}Building Chinese Chess Network Programming...${NC}"
echo -e "Build type: ${YELLOW}${BUILD_TYPE}${NC}"
echo -e "Build directory: ${YELLOW}${BUILD_DIR}${NC}"

# Clean if requested
if [ "$CLEAN" = true ]; then
    echo -e "${YELLOW}Cleaning build directory...${NC}"
    rm -rf "$BUILD_DIR"
fi

# Create build directory
mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

# Configure CMake
echo -e "${GREEN}Configuring CMake...${NC}"
cmake .. -DCMAKE_BUILD_TYPE="$BUILD_TYPE"

# Build
echo -e "${GREEN}Building project...${NC}"
cmake --build . --config "$BUILD_TYPE" -j "$JOBS"

echo -e "${GREEN}Build completed successfully!${NC}"
echo -e "Executable location: ${YELLOW}${BUILD_DIR}/bin/${PROJECT_NAME}${NC}"

