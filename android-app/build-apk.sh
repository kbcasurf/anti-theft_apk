#!/bin/bash

# Anti-Theft APK Build Script
# Builds the Android APK using Docker

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Anti-Theft APK Build Script${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Error: Docker is not installed${NC}"
    echo "Please install Docker: https://docs.docker.com/get-docker/"
    exit 1
fi

# Check if Docker daemon is running
if ! docker info &> /dev/null; then
    echo -e "${RED}Error: Docker daemon is not running${NC}"
    echo "Please start Docker and try again"
    exit 1
fi

echo -e "${GREEN}✓ Docker is available${NC}"
echo ""

# Create output directory
echo -e "${YELLOW}Creating output directory...${NC}"
mkdir -p build-output
echo -e "${GREEN}✓ Output directory created${NC}"
echo ""

# Build the Docker image
echo -e "${YELLOW}Building Docker image (this may take several minutes)...${NC}"
echo -e "${BLUE}Note: Building with --no-cache to ensure all changes are compiled${NC}"
echo ""

if docker build --no-cache -t anti-theft-apk-builder:latest -f Dockerfile.build .; then
    echo ""
    echo -e "${GREEN}✓ Docker image built successfully${NC}"
else
    echo ""
    echo -e "${RED}✗ Docker image build failed${NC}"
    exit 1
fi

echo ""

# Extract APK from Docker image
echo -e "${YELLOW}Extracting APK from Docker image...${NC}"

# Create a temporary container
CONTAINER_ID=$(docker create anti-theft-apk-builder:latest)

# Copy APK from container
if docker cp "$CONTAINER_ID:/output/anti-theft-debug.apk" ./build-output/anti-theft-debug.apk; then
    echo -e "${GREEN}✓ APK extracted successfully${NC}"
else
    echo -e "${RED}✗ Failed to extract APK${NC}"
    docker rm "$CONTAINER_ID"
    exit 1
fi

# Clean up container
docker rm "$CONTAINER_ID" > /dev/null

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}✓ Build completed successfully!${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "APK Location: ${GREEN}$(pwd)/build-output/anti-theft-debug.apk${NC}"
echo ""

# Show APK details
if [ -f build-output/anti-theft-debug.apk ]; then
    APK_SIZE=$(du -h build-output/anti-theft-debug.apk | cut -f1)
    echo -e "APK Size: ${YELLOW}$APK_SIZE${NC}"
    echo ""
    echo -e "${BLUE}Next steps:${NC}"
    echo "1. Transfer APK to Android device:"
    echo -e "   ${YELLOW}adb install build-output/anti-theft-debug.apk${NC}"
    echo ""
    echo "2. Or transfer via other means (email, USB, cloud storage, etc.)"
    echo ""
    echo "3. Install on device and configure server settings"
    echo ""
else
    echo -e "${RED}Warning: APK file not found${NC}"
fi
