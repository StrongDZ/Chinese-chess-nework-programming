# Frontend Dockerfile for Chinese Chess JavaFX Application
# Note: JavaFX GUI apps in Docker require X11 forwarding or VNC
# This Dockerfile builds and runs the app using Maven

FROM maven:3.9-eclipse-temurin-17

WORKDIR /app

# Install X11 and display dependencies (for GUI)
RUN apt-get update && apt-get install -y \
    libx11-dev \
    libxext-dev \
    libxrender-dev \
    libxtst-dev \
    libxi-dev \
    xvfb \
    && rm -rf /var/lib/apt/lists/*

# Copy project files
COPY pom.xml .
COPY src ./src

# Download dependencies
RUN mvn dependency:go-offline -B

# Expose port
EXPOSE 8080

# Set environment variables
ENV DISPLAY=:99

# Create startup script to run Xvfb and JavaFX
RUN echo '#!/bin/bash\n\
set -e\n\
# Start Xvfb in background\n\
Xvfb :99 -screen 0 1024x768x24 > /dev/null 2>&1 &\n\
XVFB_PID=$!\n\
# Wait for Xvfb to be ready\n\
sleep 2\n\
# Run JavaFX application\n\
mvn clean javafx:run\n\
# Cleanup: kill Xvfb when JavaFX exits\n\
kill $XVFB_PID 2>/dev/null || true' > /app/start.sh && \
    chmod +x /app/start.sh

# Default: Run with Xvfb virtual display
# This allows JavaFX to run headless in Docker
CMD ["/app/start.sh"]

