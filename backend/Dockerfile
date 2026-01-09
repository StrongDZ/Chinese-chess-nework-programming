# Backend Dockerfile for Chinese Chess Server
FROM ubuntu:22.04

# Avoid interactive prompts during package installation
ENV DEBIAN_FRONTEND=noninteractive

# --- QUAN TRỌNG: Ngăn Python buffer output (tránh bị treo log/data) ---
ENV PYTHONUNBUFFERED=1

# Install build dependencies
# Thay python3-minimal bằng python3 đầy đủ để tránh thiếu thư viện
RUN apt-get update && apt-get install -y \
    build-essential \
    cmake \
    git \
    pkg-config \
    libssl-dev \
    libcurl4-openssl-dev \
    wget \
    ca-certificates \
    libjsoncpp-dev \
    zlib1g-dev \
    libsnappy-dev \
    libzstd-dev \
    python3 \
    autoconf \
    automake \
    libtool \
    iproute2 \
    netcat-openbsd \
    && rm -rf /var/lib/apt/lists/*

# ... (Giữ nguyên phần Build MongoDB C Driver) ...
RUN cd /tmp && \
    rm -rf mongo-c-driver && \
    git clone https://github.com/mongodb/mongo-c-driver.git --branch 1.25.0 --depth 1 && \
    cd mongo-c-driver && \
    rm -rf cmake-build && \
    mkdir cmake-build && cd cmake-build && \
    cmake .. \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_INSTALL_PREFIX=/usr/local \
        -DENABLE_AUTOMATIC_INIT_AND_CLEANUP=OFF && \
    cmake --build . -j$(nproc) && \
    cmake --install . && \
    cd / && rm -rf /tmp/mongo-c-driver && \
    ldconfig

# ... (Giữ nguyên phần Build MongoDB C++ Driver) ...
RUN cd /tmp && \
    rm -rf mongo-cxx-driver && \
    git clone https://github.com/mongodb/mongo-cxx-driver.git && \
    cd mongo-cxx-driver && \
    git checkout r3.8.0 2>/dev/null || git checkout r3.7.0 2>/dev/null || git checkout r3.6.7 && \
    rm -rf build && \
    mkdir -p build && cd build && \
    cmake .. \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_INSTALL_PREFIX=/usr/local \
        -DBSONCXX_POLY_USE_BOOST=0 \
        -DCMAKE_CXX_STANDARD=17 \
        -DCMAKE_PREFIX_PATH=/usr/local && \
    cmake --build . -j$(nproc) && \
    cmake --install . && \
    cd / && rm -rf /tmp/mongo-cxx-driver && \
    ldconfig

# ... (Giữ nguyên phần Install JSON libs) ...
RUN mkdir -p /usr/include/nlohmann && \
    wget -q https://github.com/nlohmann/json/releases/download/v3.11.2/json.hpp -O /usr/include/nlohmann/json.hpp

RUN mkdir -p /usr/include/rapidjson && \
    cd /tmp && \
    git clone https://github.com/Tencent/rapidjson.git --depth 1 && \
    cp -r rapidjson/include/rapidjson/* /usr/include/rapidjson/ && \
    rm -rf /tmp/rapidjson

# ... (Giữ nguyên phần Build Pikafish - Logic này của bạn đã rất tốt) ...
RUN cd /tmp && \
    git clone https://github.com/official-pikafish/Pikafish.git --depth 1 && \
    cd Pikafish/src && \
    ARCH_TYPE=$(uname -m) && \
    BUILD_SUCCESS=false && \
    if [ "$ARCH_TYPE" = "x86_64" ] || [ "$ARCH_TYPE" = "amd64" ]; then \
        echo "Building for x86-64 architecture" && \
        (make -j$(nproc) build ARCH=x86-64-sse41-popcnt COMP=gcc && BUILD_SUCCESS=true) || \
        (make clean 2>/dev/null; make -j$(nproc) build ARCH=x86-64-avx2 COMP=gcc && BUILD_SUCCESS=true) || \
        (make clean 2>/dev/null; make -j$(nproc) build ARCH=x86-64-bmi2 COMP=gcc && BUILD_SUCCESS=true) || \
        (make clean 2>/dev/null; make -j$(nproc) build ARCH=x86-64 COMP=gcc && BUILD_SUCCESS=true) || \
        (make clean 2>/dev/null; make -j$(nproc) build COMP=gcc && BUILD_SUCCESS=true); \
    elif [ "$ARCH_TYPE" = "aarch64" ] || [ "$ARCH_TYPE" = "arm64" ]; then \
        echo "Building for ARM64 architecture" && \
        (make -j$(nproc) build ARCH=armv8 COMP=gcc && BUILD_SUCCESS=true) || \
        (make clean 2>/dev/null; make -j$(nproc) build ARCH=arm64 COMP=gcc && BUILD_SUCCESS=true) || \
        (make clean 2>/dev/null; make -j$(nproc) build COMP=gcc && BUILD_SUCCESS=true); \
    else \
        echo "Unknown architecture $ARCH_TYPE, trying generic build" && \
        (make -j$(nproc) build COMP=gcc && BUILD_SUCCESS=true); \
    fi && \
    if [ "$BUILD_SUCCESS" = "false" ]; then \
        echo "WARNING: Pikafish build failed. Will use pre-built binary if available."; \
    fi && \
    ls -la && \
    if [ -f pikafish ]; then \
        mkdir -p /opt/pikafish && \
        cp pikafish /opt/pikafish/pikafish && \
        chmod +x /opt/pikafish/pikafish && \
        echo "Pikafish built and copied successfully"; \
    else \
        echo "WARNING: pikafish binary not found after build, will use pre-built if available" && \
        find . -name "pikafish" -type f 2>/dev/null || true; \
    fi && \
    cd / && \
    rm -rf /tmp/Pikafish && \
    echo "Pikafish build completed"

# Set working directory
WORKDIR /app

# Copy backend source code
COPY backend .

# Copy AI folder
COPY AI ./AI

# Build the project to /opt/app
RUN rm -rf /opt/app && \
    mkdir -p /opt/app/build && \
    cd /opt/app && \
    cp -r /app/* . && \
    rm -rf /opt/app/build && \
    mkdir -p /opt/app/build && \
    # Logic copy và phân quyền cho AI
    if [ -f /opt/pikafish/pikafish ]; then \
        cp /opt/pikafish/pikafish /opt/app/AI/pikafish && \
        echo "Pikafish copied to AI folder from build"; \
    elif [ -f /opt/app/AI/pikafish ]; then \
        echo "Using pre-built pikafish from AI folder"; \
    else \
        echo "WARNING: No pikafish binary found."; \
    fi && \
    # --- Cấp quyền thực thi cho CẢ Pikafish và Wrapper Script ---
    chmod +x /opt/app/AI/pikafish && \
    chmod +x /opt/app/AI/ai_persistent_wrapper.py && \
    \
    cd build && \
    cmake .. -DCMAKE_BUILD_TYPE=Release -DBUILD_TESTING=OFF && \
    cmake --build . -j$(nproc)

EXPOSE 8080

CMD ["/opt/app/build/bin/ChineseChessNetworkProgramming", "8080"]