# Multi-stage build for Anti-Theft Server
# Stage 1: Build stage
FROM node:24.9-alpine3.22 AS builder

# Set working directory
WORKDIR /app

# Copy package files
COPY package*.json ./

# Install dependencies
RUN npm ci --only=production && \
    npm cache clean --force

# Stage 2: Production stage
FROM node:24.9-alpine3.22

# Install dumb-init for proper signal handling
RUN apk add --no-cache dumb-init

# Set working directory
WORKDIR /app

# Copy dependencies from builder (use built-in 'node' user from node:alpine, uid 1000)
COPY --from=builder --chown=node:node /app/node_modules ./node_modules

# Copy application source
COPY --chown=node:node package*.json ./
COPY --chown=node:node src ./src
COPY --chown=node:node public ./public

# Create directories for mounted volumes
RUN mkdir -p /app/certs /app/data && \
    chown node:node /app/certs /app/data

# Switch to non-root user (built-in 'node' user from node:alpine)
USER node

# Expose the server port
EXPOSE 3000

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD node -e "process.env.NODE_TLS_REJECT_UNAUTHORIZED='0';require('https').get('https://localhost:3000/api/status',r=>{process.exit(r.statusCode===200?0:1)}).on('error',()=>process.exit(1))"

# Use dumb-init to handle signals properly
ENTRYPOINT ["dumb-init", "--"]

# Start the server
CMD ["node", "src/server.js"]
