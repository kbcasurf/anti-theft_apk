#!/bin/bash

# Anti-Theft APK Installation Script with Auto Permission Grant
# This script installs the APK and grants all necessary runtime permissions

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Anti-Theft App Installer${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Check if device is connected
if ! adb devices | grep -q "device$"; then
    echo -e "${RED}Error: No Android device connected${NC}"
    echo "Please connect a device via USB and enable USB debugging"
    exit 1
fi

echo -e "${GREEN}✓ Android device connected${NC}"
echo ""

# Check if APK exists
APK_PATH="build-output/anti-theft-debug.apk"
if [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}Error: APK not found at $APK_PATH${NC}"
    echo "Please build the APK first using: ./build-apk.sh"
    exit 1
fi

echo -e "${GREEN}✓ APK found${NC}"
echo ""

# Install APK
echo -e "${YELLOW}Installing APK...${NC}"
if adb install -r "$APK_PATH" 2>&1 | grep -q "Success"; then
    echo -e "${GREEN}✓ APK installed successfully${NC}"
else
    # Try uninstalling first if install failed
    echo -e "${YELLOW}Installation failed, trying to uninstall old version first...${NC}"
    adb uninstall com.antitheft 2>/dev/null || true

    if adb install "$APK_PATH" 2>&1 | grep -q "Success"; then
        echo -e "${GREEN}✓ APK installed successfully${NC}"
    else
        echo -e "${RED}✗ Failed to install APK${NC}"
        exit 1
    fi
fi

echo ""

# Grant all runtime permissions
echo -e "${YELLOW}Granting runtime permissions...${NC}"

PERMISSIONS=(
    "android.permission.ACCESS_FINE_LOCATION"
    "android.permission.ACCESS_COARSE_LOCATION"
    "android.permission.ACCESS_BACKGROUND_LOCATION"
    "android.permission.CAMERA"
    "android.permission.RECORD_AUDIO"
    "android.permission.POST_NOTIFICATIONS"
)

for permission in "${PERMISSIONS[@]}"; do
    if adb shell pm grant com.antitheft "$permission" 2>/dev/null; then
        echo -e "${GREEN}  ✓ Granted: $permission${NC}"
    else
        echo -e "${YELLOW}  ⚠ Could not grant: $permission (may already be granted)${NC}"
    fi
done

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}✓ Installation completed successfully!${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
SERVER_IP=$(hostname -I | awk '{print $1}')

echo -e "${YELLOW}Next steps:${NC}"
echo -e "1. Open the 'System Service' app on your device"
echo -e "2. Enter server IP: ${GREEN}${SERVER_IP}${NC}"
echo -e "3. Enter server port: ${GREEN}3000${NC}"
echo -e "4. Tap 'Save Settings'"
echo -e "5. Tap 'Test Connection'"
echo ""
echo -e "The app will automatically check in with the server every ${GREEN}1 minute${NC}"
echo ""
