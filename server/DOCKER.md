# Docker Setup for Anti-Theft Server

This guide explains how to run the Anti-Theft server in a Docker container.

## Prerequisites

- Docker installed (version 20.10 or higher)
- Docker Compose installed (version 2.0 or higher)
- SSL certificates generated in `server/certs/` directory

## Quick Start

### 1. Generate SSL Certificates (if not already done)

```bash
cd server
mkdir -p certs
openssl req -x509 -newkey rsa:4096 -keyout certs/key.pem -out certs/cert.pem -days 365 -nodes -subj "/CN=localhost"
```

### 2. Build and Run with Docker Compose

From the project root directory:

```bash
# Build and start the container
docker-compose up -d

# View logs
docker-compose logs -f anti-theft-server

# Stop the container
docker-compose down
```

### 3. Access the Server

- Web Dashboard: https://localhost:3000
- API Endpoint: https://localhost:3000/api
- WebSocket: wss://localhost:3000/ws

## Manual Docker Commands

If you prefer not to use Docker Compose:

```bash
# Build the image
cd server
docker build -t anti-theft-server:latest .

# Run the container
docker run -d \
  --name anti-theft-server \
  -p 3000:3000 \
  -v $(pwd)/certs:/app/certs:ro \
  -v $(pwd)/public:/app/public:ro \
  -e PORT=3000 \
  -e NODE_ENV=production \
  --restart unless-stopped \
  anti-theft-server:latest

# View logs
docker logs -f anti-theft-server

# Stop and remove container
docker stop anti-theft-server
docker rm anti-theft-server
```

## Configuration

### Environment Variables

You can customize the server by setting environment variables in `docker-compose.yml`:

- `PORT` - Server port (default: 3000)
- `NODE_ENV` - Environment mode (development/production)
- `LOG_LEVEL` - Logging level (info/debug/error)

### IP Whitelisting

The server includes IP whitelisting for security. To configure allowed IPs:

1. Edit `src/config/config.js`
2. Add your Docker network IP ranges to `whitelistedIPs`
3. Common Docker network ranges:
   - `172.17.0.0/16` (default Docker bridge)
   - `192.168.0.0/16` (custom networks)

To allow access from the host machine, add:
```javascript
whitelistedIPs: [
    '127.0.0.1',
    '::1',
    '::ffff:127.0.0.1',
    '172.17.0.0/16',     // Docker bridge network
    'host.docker.internal', // For host access
    // ... your other IPs
]
```

## Volumes

The container uses the following volume mounts:

- `./server/certs:/app/certs:ro` - SSL certificates (read-only)
- `./server/public:/app/public:ro` - Static files (read-only, optional)

## Health Checks

The container includes a health check that runs every 30 seconds:

```bash
# Check container health
docker inspect --format='{{.State.Health.Status}}' anti-theft-server
```

## Troubleshooting

### Certificate Errors

If you see SSL certificate errors:
```bash
# Verify certificates exist
ls -la server/certs/

# Check certificate permissions
chmod 644 server/certs/cert.pem
chmod 600 server/certs/key.pem
```

### Connection Issues

If you can't connect to the server:
1. Check if the container is running: `docker ps`
2. Check logs: `docker-compose logs anti-theft-server`
3. Verify IP whitelist configuration includes Docker network ranges
4. Check port mapping: `docker port anti-theft-server`

### Rebuild After Changes

If you modify the server code:
```bash
# Rebuild and restart
docker-compose up -d --build

# Or with manual commands
docker build -t anti-theft-server:latest .
docker restart anti-theft-server
```

## Resource Limits

The container is configured with the following limits in `docker-compose.yml`:
- CPU: 1.0 cores max, 0.25 cores reserved
- Memory: 512MB max, 128MB reserved

Adjust these values based on your needs.

## Security Notes

- The container runs as a non-root user (appuser)
- SSL certificates are mounted as read-only
- Uses Alpine Linux for minimal attack surface
- Includes dumb-init for proper signal handling
- Health checks ensure service availability

## Production Deployment

For production use:

1. Use proper SSL certificates (not self-signed)
2. Configure IP whitelist carefully
3. Use Docker secrets for sensitive data
4. Set up log rotation
5. Monitor resource usage
6. Use a reverse proxy (nginx/traefik) for additional security

## Useful Commands

```bash
# View container stats
docker stats anti-theft-server

# Execute commands in container
docker exec -it anti-theft-server sh

# View real-time logs
docker-compose logs -f

# Restart container
docker-compose restart

# Remove everything (including volumes)
docker-compose down -v
```
