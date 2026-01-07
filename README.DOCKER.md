# Docker Setup for Chinese Chess Network Programming

This guide explains how to run the Chinese Chess application using Docker Compose.

## Prerequisites

- Docker Engine 20.10+
- Docker Compose 2.0+

## Quick Start

### Option 1: Full Stack (Backend + MongoDB + Frontend in Docker)

**Note:** Frontend GUI in Docker requires X11 forwarding. For better experience, see Option 2.

```bash
# Build and start all services
docker-compose up --build

# Or run in detached mode
docker-compose up -d --build
```

### Option 2: Development Mode (Recommended)

Run backend and MongoDB in Docker, frontend on host:

```bash
# Start backend and MongoDB
docker-compose -f docker-compose.dev.yml up --build

# In another terminal, run frontend on host
cd frontend
mvn javafx:run
```

## Services

### MongoDB
- **Port:** 27017
- **Database:** chinese_chess
- **Data persistence:** Stored in Docker volume `mongodb_data`

### Backend
- **Port:** 8080
- **Environment variables:**
  - `MONGODB_URI`: MongoDB connection string (default: `mongodb://mongodb:27017`)
  - `MONGODB_DB`: Database name (default: `chinese_chess`)
  - `PIKAFISH_PATH`: Path to Pikafish AI engine (default: `pikafish`)

### Frontend
- **Port:** 8081 (mapped from container 8080)
- **Note:** GUI apps in Docker require X11 forwarding

## Running Frontend with X11 Forwarding (Linux)

If you want to run frontend in Docker with GUI:

1. Allow X11 connections:
```bash
xhost +local:docker
```

2. Run docker-compose with X11 forwarding:
```bash
export DISPLAY=:0
docker-compose up --build
```

3. Or modify `docker-compose.yml` to uncomment X11 volume mounts.

## Useful Commands

### View logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f backend
docker-compose logs -f mongodb
docker-compose logs -f frontend
```

### Stop services
```bash
docker-compose down
```

### Stop and remove volumes (clean slate)
```bash
docker-compose down -v
```

### Rebuild specific service
```bash
docker-compose build backend
docker-compose up -d backend
```

### Execute commands in container
```bash
# Backend
docker-compose exec backend bash

# MongoDB
docker-compose exec mongodb mongosh
```

## Troubleshooting

### Backend can't connect to MongoDB
- Check MongoDB is healthy: `docker-compose ps`
- Check logs: `docker-compose logs mongodb`
- Verify network: `docker network inspect chinese-chess-network`

### Frontend GUI doesn't display
- JavaFX GUI in Docker requires X11 forwarding
- **Recommended:** Run frontend directly on host: `cd frontend && mvn javafx:run`
- Or use VNC server in container for remote access

### Port conflicts
- Change ports in `docker-compose.yml` if 8080, 27017, or 8081 are already in use

### Pikafish AI not working
- Pikafish needs to be installed in the container or available in PATH
- You can mount a local Pikafish binary:
  ```yaml
  volumes:
    - /path/to/pikafish:/usr/local/bin/pikafish
  ```

## Development Workflow

1. **Start infrastructure:**
   ```bash
   docker-compose -f docker-compose.dev.yml up -d
   ```

2. **Run frontend on host:**
   ```bash
   cd frontend
   mvn javafx:run
   ```

3. **Backend auto-reloads** if you mount source code (see `docker-compose.dev.yml`)

4. **Stop when done:**
   ```bash
   docker-compose -f docker-compose.dev.yml down
   ```

## Production Considerations

For production deployment:
- Use proper secrets management for MongoDB credentials
- Configure proper networking and firewall rules
- Set up health checks and monitoring
- Use multi-stage builds to reduce image size
- Consider using Kubernetes instead of Docker Compose for orchestration

















