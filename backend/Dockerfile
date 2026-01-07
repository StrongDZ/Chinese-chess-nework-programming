# Backend Dockerfile for Chinese Chess Server
FROM ubuntu:22.04

# Avoid interactive prompts during package installation
ENV DEBIAN_FRONTEND=noninteractive

# Install build dependencies
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
    python3-minimal \
    autoconf \
    automake \
    libtool \
    iproute2 \
    netcat-openbsd \
    && rm -rf /var/lib/apt/lists/*

# Build MongoDB C driver from source (required for C++ driver)
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

# Build MongoDB C++ driver from source (not available in Ubuntu repos)
# Clone and checkout stable release tag
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

# Install nlohmann/json (header-only library)
RUN mkdir -p /usr/include/nlohmann && \
    wget -q https://github.com/nlohmann/json/releases/download/v3.11.2/json.hpp -O /usr/include/nlohmann/json.hpp

# Install rapidjson (header-only library)
RUN mkdir -p /usr/include/rapidjson && \
    cd /tmp && \
    git clone https://github.com/Tencent/rapidjson.git --depth 1 && \
    cp -r rapidjson/include/rapidjson/* /usr/include/rapidjson/ && \
    rm -rf /tmp/rapidjson

# Set working directory
WORKDIR /app

# Copy source code
COPY . .

# Build the project to /opt/app (outside volume mount)
RUN rm -rf /opt/app && \
    mkdir -p /opt/app/build && \
    cd /opt/app && \
    cp -r /app/* . && \
    cd build && \
    cmake .. -DCMAKE_BUILD_TYPE=Release -DBUILD_TESTING=OFF && \
    cmake --build . -j$(nproc)

# Expose port (default 8080)
EXPOSE 8080

# Run the server from /opt/app/build (not affected by volume mount)
CMD ["/opt/app/build/bin/ChineseChessNetworkProgramming", "8080"]

