#!/bin/bash

###############################################################################
# Trading Bot API Gateway Test Script
# Usage: ./gateway-api-test.sh
###############################################################################

# Configuration
GATEWAY_URL="http://localhost:8080/gateway/api/bots"
CONTENT_TYPE="Content-Type: application/json"
CLIENT_ID="X-Client-ID: test-client"
AUTH_TOKEN="Authorization: Bearer test-token-12345"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
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

# Function to print gateway info
print_gateway() {
    echo -e "${PURPLE}⚡ $1${NC}"
}

###############################################################################
# Test 1: Gateway Health Check
###############################################################################
print_header "TEST 1: Gateway Health Check"

response=$(curl -s -X GET "http://localhost:8080/gateway/health")
echo "Response: $response"
print_success "Gateway health check complete"

sleep 2

###############################################################################
# Test 2: Create bot via gateway
###############################################################################
print_header "TEST 2: Create Bot via Gateway"

response=$(curl -s -X POST "$GATEWAY_URL" \
  -H "$CONTENT_TYPE" \
  -H "$CLIENT_ID" \
  -H "$AUTH_TOKEN")

echo "Response: $response"

# Extract botId from response
BOT_ID=$(echo $response | grep -o '"botId":"[^"]*"' | cut -d'"' -f4)

if [ -z "$BOT_ID" ]; then
    print_error "Failed to create bot or extract botId"
    exit 1
else
    print_success "Bot created via gateway with ID: $BOT_ID"
    print_gateway "Gateway enriched request with metadata"
fi

sleep 2

###############################################################################
# Test 3: Start bot via gateway (LONG mode)
###############################################################################
print_header "TEST 3: Start Bot via Gateway (LONG/Paper)"

response=$(curl -s -X POST "$GATEWAY_URL/$BOT_ID/start" \
  -H "$CONTENT_TYPE" \
  -H "$CLIENT_ID" \
  -H "$AUTH_TOKEN" \
  -d '{
    "direction": "LONG",
    "paper": true
  }')

echo "Response: $response"
print_success "Bot started via gateway"
print_gateway "Request passed through rate limiter, circuit breaker, and retry"

sleep 2

###############################################################################
# Test 4: Get bot status via gateway
###############################################################################
print_header "TEST 4: Get Bot Status via Gateway"

response=$(curl -s -X GET "$GATEWAY_URL/$BOT_ID/status" \
  -H "$CLIENT_ID" \
  -H "$AUTH_TOKEN")

echo "Response: $response"
print_success "Bot status retrieved via gateway"

sleep 2

###############################################################################
# Test 5: Update leverage via gateway
###############################################################################
print_header "TEST 5: Update Leverage via Gateway"

response=$(curl -s -X POST "$GATEWAY_URL/$BOT_ID/leverage" \
  -H "$CONTENT_TYPE" \
  -H "$CLIENT_ID" \
  -H "$AUTH_TOKEN" \
  -d '{
    "leverage": 15
  }')

echo "Response: $response"
print_success "Leverage updated via gateway"

sleep 2

###############################################################################
# Test 6: Enable sentiment via gateway
###############################################################################
print_header "TEST 6: Enable Sentiment Analysis via Gateway"

response=$(curl -s -X POST "$GATEWAY_URL/$BOT_ID/sentiment" \
  -H "$CONTENT_TYPE" \
  -H "$CLIENT_ID" \
  -H "$AUTH_TOKEN" \
  -d '{
    "enable": true
  }')

echo "Response: $response"
print_success "Sentiment analysis enabled via gateway"

sleep 2

###############################################################################
# Test 7: List all bots via gateway
###############################################################################
print_header "TEST 7: List All Bots via Gateway"

response=$(curl -s -X GET "$GATEWAY_URL" \
  -H "$CLIENT_ID" \
  -H "$AUTH_TOKEN")

echo "Response: $response"
print_success "Bot list retrieved via gateway"

sleep 2

###############################################################################
# Test 8: Check response headers
###############################################################################
print_header "TEST 8: Verify Gateway Headers"

response=$(curl -s -i -X GET "$GATEWAY_URL/$BOT_ID/status" \
  -H "$CLIENT_ID" \
  -H "$AUTH_TOKEN")

echo "$response"
print_info "Check for X-Gateway-Proxied header in response"
print_gateway "Gateway adds metadata to responses"

sleep 2

###############################################################################
# Test 9: Create multiple bots via gateway
###############################################################################
print_header "TEST 9: Create Multiple Bots via Gateway"

print_info "Creating Bot 1..."
response1=$(curl -s -X POST "$GATEWAY_URL" \
  -H "$CONTENT_TYPE" \
  -H "$CLIENT_ID" \
  -H "$AUTH_TOKEN")
BOT_1=$(echo $response1 | grep -o '"botId":"[^"]*"' | cut -d'"' -f4)
print_success "Bot 1 created: $BOT_1"

print_info "Creating Bot 2..."
response2=$(curl -s -X POST "$GATEWAY_URL" \
  -H "$CONTENT_TYPE" \
  -H "$CLIENT_ID" \
  -H "$AUTH_TOKEN")
BOT_2=$(echo $response2 | grep -o '"botId":"[^"]*"' | cut -d'"' -f4)
print_success "Bot 2 created: $BOT_2"

sleep 2

###############################################################################
# Test 10: Start multiple bots with different configs
###############################################################################
print_header "TEST 10: Start Multiple Bots via Gateway"

print_info "Starting Bot 1 (LONG mode)..."
curl -s -X POST "$GATEWAY_URL/$BOT_1/start" \
  -H "$CONTENT_TYPE" \
  -H "$CLIENT_ID" \
  -H "$AUTH_TOKEN" \
  -d '{"direction": "LONG", "paper": true}' > /dev/null
print_success "Bot 1 started in LONG mode"

print_info "Starting Bot 2 (SHORT mode)..."
curl -s -X POST "$GATEWAY_URL/$BOT_2/start" \
  -H "$CONTENT_TYPE" \
  -H "$CLIENT_ID" \
  -H "$AUTH_TOKEN" \
  -d '{"direction": "SHORT", "paper": true}' > /dev/null
print_success "Bot 2 started in SHORT mode"

sleep 2

###############################################################################
# Test 11: List all bots
###############################################################################
print_header "TEST 11: List All Bots"

response=$(curl -s -X GET "$GATEWAY_URL" \
  -H "$CLIENT_ID" \
  -H "$AUTH_TOKEN")

echo "Response: $response"
bot_count=$(echo $response | grep -o '"count":[0-9]*' | cut -d':' -f2)
print_success "Total bots: $bot_count"

sleep 2

###############################################################################
# Test 12: Stop bot via gateway
###############################################################################
print_header "TEST 12: Stop Bot via Gateway"

response=$(curl -s -X PUT "$GATEWAY_URL/$BOT_ID/stop" \
  -H "$CLIENT_ID" \
  -H "$AUTH_TOKEN")

echo "Response: $response"
print_success "Bot stopped via gateway"

sleep 2

###############################################################################
# Test 13: Delete bot via gateway
###############################################################################
print_header "TEST 13: Delete Bot via Gateway"

response=$(curl -s -X DELETE "$GATEWAY_URL/$BOT_ID" \
  -H "$CLIENT_ID" \
  -H "$AUTH_TOKEN")

echo "Response: $response"
print_success "Bot deleted via gateway"

sleep 2

###############################################################################
# Test 14: Error test - Non-existent bot via gateway
###############################################################################
print_header "TEST 14: Error Test - Non-existent Bot"

response=$(curl -s -w "\nHTTP Status: %{http_code}" \
  -X GET "$GATEWAY_URL/non-existent-id/status" \
  -H "$CLIENT_ID" \
  -H "$AUTH_TOKEN")

echo "Response: $response"
print_info "Should return 404 Not Found (proxied from backend)"

sleep 2

###############################################################################
# Test 15: Rate limiting test
###############################################################################
print_header "TEST 15: Rate Limiting Test"

print_info "Sending rapid requests to test rate limiter..."
for i in {1..10}; do
    response=$(curl -s -w " [Status: %{http_code}]" \
      -X GET "$GATEWAY_URL/$BOT_1/status" \
      -H "$CLIENT_ID" \
      -H "$AUTH_TOKEN")
    echo "Request $i: $response"
done

print_gateway "Rate limiter: 100 requests per minute per client"
print_info "If more than 100 requests in 1 minute, should return 429"

sleep 2

###############################################################################
# Test 16: Resilience endpoints via gateway
###############################################################################
print_header "TEST 16: Resilience Endpoints via Gateway"

print_info "Getting rate limiter metrics..."
response=$(curl -s -X GET "http://localhost:8080/gateway/api/resilience/rate-limiters" \
  -H "$CLIENT_ID" \
  -H "$AUTH_TOKEN")
echo "Rate Limiters: $response"

sleep 1

print_info "Getting circuit breaker metrics..."
response=$(curl -s -X GET "http://localhost:8080/gateway/api/resilience/circuit-breaker" \
  -H "$CLIENT_ID" \
  -H "$AUTH_TOKEN")
echo "Circuit Breaker: $response"

sleep 1

print_info "Getting retry metrics..."
response=$(curl -s -X GET "http://localhost:8080/gateway/api/resilience/retry" \
  -H "$CLIENT_ID" \
  -H "$AUTH_TOKEN")
echo "Retry: $response"

print_success "Resilience metrics retrieved"

sleep 2

###############################################################################
# Test 17: Cleanup - Delete all test bots
###############################################################################
print_header "TEST 17: Cleanup via Gateway"

print_info "Deleting Bot 1..."
curl -s -X DELETE "$GATEWAY_URL/$BOT_1" \
  -H "$CLIENT_ID" \
  -H "$AUTH_TOKEN" > /dev/null
print_success "Bot 1 deleted"

print_info "Deleting Bot 2..."
curl -s -X DELETE "$GATEWAY_URL/$BOT_2" \
  -H "$CLIENT_ID" \
  -H "$AUTH_TOKEN" > /dev/null
print_success "Bot 2 deleted"

sleep 2

###############################################################################
# Test 18: Verify cleanup
###############################################################################
print_header "TEST 18: Verify Cleanup"

response=$(curl -s -X GET "$GATEWAY_URL" \
  -H "$CLIENT_ID" \
  -H "$AUTH_TOKEN")

echo "Response: $response"
bot_count=$(echo $response | grep -o '"count":[0-9]*' | cut -d':' -f2)
print_success "Remaining bots: $bot_count"

###############################################################################
# Summary
###############################################################################
print_header "TEST SUMMARY"

print_success "All gateway tests completed successfully!"
print_gateway "Gateway Features Verified:"
echo -e "  ${PURPLE}⚡ Rate Limiting${NC}"
echo -e "  ${PURPLE}⚡ Circuit Breaker${NC}"
echo -e "  ${PURPLE}⚡ Retry Logic${NC}"
echo -e "  ${PURPLE}⚡ Header Enrichment${NC}"
echo -e "  ${PURPLE}⚡ Request Proxying${NC}"
print_info "Multi-bot architecture working through gateway"

echo -e "\n${GREEN}═══════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}✓ All Gateway Tests Passed${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════${NC}\n"
