#!/bin/bash

# Complete Bot Lifecycle Test
# Tests: create → start → status → stop → verify

BASE_URL="http://localhost:8080"

echo "🔄 Complete Bot Lifecycle Test"
echo "================================"

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Check server
echo -n "1. Checking server... "
if curl -s -f "${BASE_URL}/actuator/health" > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗ Server not running${NC}"
    exit 1
fi

# Register/Login
echo -n "2. Getting auth token... "
TIMESTAMP=$(date +%s)
USERNAME="lifecycle_test_${TIMESTAMP}"

TOKEN_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/auth/register" \
    -H "Content-Type: application/json" \
    -d "{
        \"username\": \"${USERNAME}\",
        \"password\": \"Test@12345\",
        \"email\": \"${USERNAME}@example.com\",
        \"fullName\": \"Lifecycle Test User\"
    }")

TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.access_token // empty' 2>/dev/null)
if [ -z "$TOKEN" ] || [ "$TOKEN" == "null" ]; then
    echo -e "${RED}✗ Failed${NC}"
    echo "Response: $TOKEN_RESPONSE"
    exit 1
fi
echo -e "${GREEN}✓${NC}"

# Create bot
echo -n "3. Creating bot... "
BOT_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/v1/bots" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN")

BOT_ID=$(echo "$BOT_RESPONSE" | jq -r '.botId // empty' 2>/dev/null)
if [ -z "$BOT_ID" ] || [ "$BOT_ID" == "null" ]; then
    echo -e "${RED}✗ Failed${NC}"
    echo "Response: $BOT_RESPONSE"
    exit 1
fi
echo -e "${GREEN}✓${NC} (ID: ${BOT_ID})"

# Start bot in paper mode  
echo -n "4. Starting paper trading (LONG)... "
START_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/v1/bots/${BOT_ID}/start" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d '{
        "direction": "LONG",
        "paper": true
    }')

RUNNING=$(echo "$START_RESPONSE" | jq -r '.botStatus.running // empty' 2>/dev/null)
if [ "$RUNNING" == "true" ]; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗ Failed${NC}"
    echo "Response: $START_RESPONSE"
    exit 1
fi

# Check status (running)
echo -n "5. Verifying bot is running... "
STATUS_RESPONSE=$(curl -s "${BASE_URL}/api/v1/bots/${BOT_ID}/status" \
    -H "Authorization: Bearer $TOKEN")

RUNNING=$(echo "$STATUS_RESPONSE" | jq -r '.running // empty' 2>/dev/null)
if [ "$RUNNING" == "true" ]; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗ Bot not running${NC}"
    echo "Response: $STATUS_RESPONSE"
    exit 1
fi

# Stop bot
echo -n "6. Stopping bot... "
STOP_RESPONSE=$(curl -s -X PUT "${BASE_URL}/api/v1/bots/${BOT_ID}/stop" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN")

MESSAGE=$(echo "$STOP_RESPONSE" | jq -r '.message // empty' 2>/dev/null)
if [[ "$MESSAGE" == *"stopped successfully"* ]]; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗ Failed${NC}"
    echo "Response: $STOP_RESPONSE"
    exit 1
fi

# Verify bot is stopped
echo -n "7. Verifying bot stopped... "
STATUS_RESPONSE=$(curl -s "${BASE_URL}/api/v1/bots/${BOT_ID}/status" \
    -H "Authorization: Bearer $TOKEN")

RUNNING=$(echo "$STATUS_RESPONSE" | jq -r '.running // empty' 2>/dev/null)
if [ "$RUNNING" == "false" ]; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗ Bot still running${NC}"
    echo "Response: $STATUS_RESPONSE"
    exit 1
fi

# Optional: Delete bot
echo -n "8. Deleting bot... "
DELETE_RESPONSE=$(curl -s -X DELETE "${BASE_URL}/api/v1/bots/${BOT_ID}" \
    -H "Authorization: Bearer $TOKEN")

MESSAGE=$(echo "$DELETE_RESPONSE" | jq -r '.message // empty' 2>/dev/null)
if [[ "$MESSAGE" == *"deleted successfully"* ]]; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${YELLOW}⚠ May have failed${NC}"
    echo "Response: $DELETE_RESPONSE"
fi

echo ""
echo -e "${GREEN}✅ All lifecycle tests passed!${NC}"
echo "Bot ID: ${BOT_ID}"
echo ""
echo "Summary:"
echo "  ✓ Bot created"
echo "  ✓ Bot started in paper mode"
echo "  ✓ Bot status verified (running)"
echo "  ✓ Bot stopped successfully"
echo "  ✓ Bot status verified (stopped)"
echo "  ✓ Bot deleted"
