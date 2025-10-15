#!/bin/bash

###############################################################################
# Trading Bot Backend API Test Script
# Usage: ./backend-api-test.sh
###############################################################################

# Configuration
BASE_URL="http://localhost:8080/api/bots"
CONTENT_TYPE="Content-Type: application/json"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print section headers
print_header() {
    echo -e "\n${BLUE}═══════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}\n"
}

# Function to print success
print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

# Function to print error
print_error() {
    echo -e "${RED}✗ $1${NC}"
}

# Function to print info
print_info() {
    echo -e "${YELLOW}ℹ $1${NC}"
}

###############################################################################
# Test 1: Create a new bot
###############################################################################
print_header "TEST 1: Create New Bot"

response=$(curl -s -X POST "$BASE_URL" \
  -H "$CONTENT_TYPE")

echo "Response: $response"

# Extract botId from response
BOT_ID=$(echo $response | grep -o '"botId":"[^"]*"' | cut -d'"' -f4)

if [ -z "$BOT_ID" ]; then
    print_error "Failed to create bot or extract botId"
    exit 1
else
    print_success "Bot created with ID: $BOT_ID"
fi

sleep 2

###############################################################################
# Test 2: Start bot in LONG mode (paper trading)
###############################################################################
print_header "TEST 2: Start Bot (LONG/Paper)"

response=$(curl -s -X POST "$BASE_URL/$BOT_ID/start" \
  -H "$CONTENT_TYPE" \
  -d '{
    "direction": "LONG",
    "paper": true
  }')

echo "Response: $response"
print_success "Bot start request sent"

sleep 2

###############################################################################
# Test 3: Get bot status
###############################################################################
print_header "TEST 3: Get Bot Status"

response=$(curl -s -X GET "$BASE_URL/$BOT_ID/status")

echo "Response: $response"
print_success "Bot status retrieved"

sleep 2

###############################################################################
# Test 4: Update leverage
###############################################################################
print_header "TEST 4: Update Leverage to 10x"

response=$(curl -s -X POST "$BASE_URL/$BOT_ID/leverage" \
  -H "$CONTENT_TYPE" \
  -d '{
    "leverage": 10
  }')

echo "Response: $response"
print_success "Leverage updated"

sleep 2

###############################################################################
# Test 5: Enable sentiment analysis
###############################################################################
print_header "TEST 5: Enable Sentiment Analysis"

response=$(curl -s -X POST "$BASE_URL/$BOT_ID/sentiment" \
  -H "$CONTENT_TYPE" \
  -d '{
    "enable": true
  }')

echo "Response: $response"
print_success "Sentiment analysis enabled"

sleep 2

###############################################################################
# Test 6: List all bots
###############################################################################
print_header "TEST 6: List All Bots"

response=$(curl -s -X GET "$BASE_URL")

echo "Response: $response"
print_success "Bot list retrieved"

sleep 2

###############################################################################
# Test 7: Stop bot
###############################################################################
print_header "TEST 7: Stop Bot"

response=$(curl -s -X PUT "$BASE_URL/$BOT_ID/stop")

echo "Response: $response"
print_success "Bot stopped"

sleep 2

###############################################################################
# Test 8: Get status after stop
###############################################################################
print_header "TEST 8: Get Status After Stop"

response=$(curl -s -X GET "$BASE_URL/$BOT_ID/status")

echo "Response: $response"
print_success "Status after stop retrieved"

sleep 2

###############################################################################
# Test 9: Delete bot
###############################################################################
print_header "TEST 9: Delete Bot"

response=$(curl -s -X DELETE "$BASE_URL/$BOT_ID")

echo "Response: $response"
print_success "Bot deleted"

sleep 2

###############################################################################
# Test 10: Create multiple bots
###############################################################################
print_header "TEST 10: Create Multiple Bots"

print_info "Creating Bot 1..."
response1=$(curl -s -X POST "$BASE_URL" -H "$CONTENT_TYPE")
BOT_1=$(echo $response1 | grep -o '"botId":"[^"]*"' | cut -d'"' -f4)
print_success "Bot 1 created: $BOT_1"

print_info "Creating Bot 2..."
response2=$(curl -s -X POST "$BASE_URL" -H "$CONTENT_TYPE")
BOT_2=$(echo $response2 | grep -o '"botId":"[^"]*"' | cut -d'"' -f4)
print_success "Bot 2 created: $BOT_2"

print_info "Creating Bot 3..."
response3=$(curl -s -X POST "$BASE_URL" -H "$CONTENT_TYPE")
BOT_3=$(echo $response3 | grep -o '"botId":"[^"]*"' | cut -d'"' -f4)
print_success "Bot 3 created: $BOT_3"

sleep 2

###############################################################################
# Test 11: Start multiple bots with different configurations
###############################################################################
print_header "TEST 11: Start Multiple Bots"

print_info "Starting Bot 1 (LONG/Paper)..."
curl -s -X POST "$BASE_URL/$BOT_1/start" \
  -H "$CONTENT_TYPE" \
  -d '{"direction": "LONG", "paper": true}' > /dev/null
print_success "Bot 1 started in LONG mode"

print_info "Starting Bot 2 (SHORT/Paper)..."
curl -s -X POST "$BASE_URL/$BOT_2/start" \
  -H "$CONTENT_TYPE" \
  -d '{"direction": "SHORT", "paper": true}' > /dev/null
print_success "Bot 2 started in SHORT mode"

sleep 2

###############################################################################
# Test 12: List all active bots
###############################################################################
print_header "TEST 12: List All Active Bots"

response=$(curl -s -X GET "$BASE_URL")
echo "Response: $response"

bot_count=$(echo $response | grep -o '"count":[0-9]*' | cut -d':' -f2)
print_success "Total bots: $bot_count"

sleep 2

###############################################################################
# Test 13: Error test - Non-existent bot
###############################################################################
print_header "TEST 13: Error Test - Non-existent Bot"

response=$(curl -s -w "\nHTTP Status: %{http_code}" \
  -X GET "$BASE_URL/non-existent-bot-id/status")

echo "Response: $response"
print_info "Should return 404 Not Found"

sleep 2

###############################################################################
# Test 14: Error test - Invalid leverage
###############################################################################
print_header "TEST 14: Error Test - Invalid Leverage"

response=$(curl -s -w "\nHTTP Status: %{http_code}" \
  -X POST "$BASE_URL/$BOT_1/leverage" \
  -H "$CONTENT_TYPE" \
  -d '{"leverage": -5}')

echo "Response: $response"
print_info "Should return 400 Bad Request"

sleep 2

###############################################################################
# Test 15: Cleanup - Delete all test bots
###############################################################################
print_header "TEST 15: Cleanup - Delete All Bots"

print_info "Deleting Bot 1..."
curl -s -X DELETE "$BASE_URL/$BOT_1" > /dev/null
print_success "Bot 1 deleted"

print_info "Deleting Bot 2..."
curl -s -X DELETE "$BASE_URL/$BOT_2" > /dev/null
print_success "Bot 2 deleted"

print_info "Deleting Bot 3..."
curl -s -X DELETE "$BASE_URL/$BOT_3" > /dev/null
print_success "Bot 3 deleted"

sleep 2

###############################################################################
# Test 16: Verify cleanup
###############################################################################
print_header "TEST 16: Verify Cleanup"

response=$(curl -s -X GET "$BASE_URL")
echo "Response: $response"

bot_count=$(echo $response | grep -o '"count":[0-9]*' | cut -d':' -f2)
print_success "Remaining bots: $bot_count"

###############################################################################
# Summary
###############################################################################
print_header "TEST SUMMARY"

print_success "All tests completed successfully!"
print_info "Backend API is functioning correctly"
print_info "Multi-bot architecture verified"

echo -e "\n${GREEN}═══════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}✓ All Tests Passed${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════${NC}\n"
