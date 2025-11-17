#!/bin/bash

# Quick Paper Trading Test
# A simplified version for rapid testing

BASE_URL="http://localhost:8080/api/v1"

echo "🚀 Quick Paper Trading Test"
echo "================================"

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

# Check server
echo -n "1. Checking server... "
if curl -s "${BASE_URL}/bots" > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗ Server not running${NC}"
    exit 1
fi

# Create bot
echo -n "2. Creating bot... "
RESPONSE=$(curl -s -X POST "${BASE_URL}/bots")
BOT_ID=$(echo "$RESPONSE" | jq -r '.botId' 2>/dev/null)

if [ -n "$BOT_ID" ] && [ "$BOT_ID" != "null" ]; then
    echo -e "${GREEN}✓${NC} (ID: ${BOT_ID})"
else
    echo -e "${RED}✗ Failed${NC}"
    exit 1
fi

# Start bot in paper mode
echo -n "3. Starting paper trading (LONG)... "
RESPONSE=$(curl -s -X POST "${BASE_URL}/bots/${BOT_ID}/start" \
    -H "Content-Type: application/json" \
    -d '{"direction": "LONG", "paper": true}')

SUCCESS=$(echo "$RESPONSE" | jq -r '.success' 2>/dev/null)
if [ "$SUCCESS" == "true" ]; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗ Failed${NC}"
    echo "$RESPONSE" | jq '.' 2>/dev/null || echo "$RESPONSE"
    exit 1
fi

# Check status
echo -n "4. Checking bot status... "
RESPONSE=$(curl -s "${BASE_URL}/bots/${BOT_ID}")
RUNNING=$(echo "$RESPONSE" | jq -r '.running' 2>/dev/null)

if [ "$RUNNING" == "true" ]; then
    echo -e "${GREEN}✓ Running${NC}"
else
    echo -e "${RED}✗ Not running${NC}"
fi

echo ""
echo "📊 Bot Details:"
echo "$RESPONSE" | jq '{botId, status, running, direction, paper}' 2>/dev/null || echo "$RESPONSE"

echo ""
echo "🎯 Paper trading is active!"
echo ""
echo "Next steps:"
echo "  • Monitor logs: tail -f logs/application.log"
echo "  • Stop bot: curl -X POST ${BASE_URL}/bots/${BOT_ID}/stop"
echo "  • Check status: curl ${BASE_URL}/bots/${BOT_ID}"
echo "  • Delete bot: curl -X DELETE ${BASE_URL}/bots/${BOT_ID}"
echo ""
echo "Bot ID for your reference: ${BOT_ID}"
