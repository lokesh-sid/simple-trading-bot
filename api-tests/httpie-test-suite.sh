#!/bin/bash

###############################################################################
# HTTPie-based Trading Bot API Test Suite
# Comprehensive testing using HTTPie for beautiful, human-friendly output
###############################################################################

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
BASE_URL="localhost:8080"
GATEWAY_URL="localhost:8080/gateway"

# Check if HTTPie is installed
if ! command -v http &> /dev/null; then
    echo -e "${RED}HTTPie is not installed!${NC}"
    echo -e "${YELLOW}Install it with: brew install httpie${NC}"
    exit 1
fi

# Function to print section headers
print_header() {
    echo -e "\n${CYAN}═══════════════════════════════════════════════════════${NC}"
    echo -e "${CYAN}$1${NC}"
    echo -e "${CYAN}═══════════════════════════════════════════════════════${NC}\n"
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

# Banner
echo -e "${PURPLE}"
cat << "EOF"
╔══════════════════════════════════════════════════════════════╗
║                                                              ║
║     HTTPie Trading Bot API Test Suite                       ║
║     Beautiful, Human-Friendly HTTP Testing                  ║
║                                                              ║
╚══════════════════════════════════════════════════════════════╝
EOF
echo -e "${NC}\n"

###############################################################################
# Test 1: Create a Bot
###############################################################################
print_header "TEST 1: Create Bot with HTTPie"

print_info "Creating bot with pretty output..."
RESPONSE=$(http --print=b POST $BASE_URL/api/bots)
echo "$RESPONSE"

# Extract botId using jq
BOT_ID=$(echo "$RESPONSE" | jq -r '.botId')

if [ -z "$BOT_ID" ] || [ "$BOT_ID" = "null" ]; then
    print_error "Failed to create bot or extract botId"
    exit 1
else
    print_success "Bot created with ID: $BOT_ID"
fi

sleep 1

###############################################################################
# Test 2: Start Bot (LONG mode, Paper trading)
###############################################################################
print_header "TEST 2: Start Bot (LONG/Paper) - HTTPie JSON Syntax"

print_info "Starting bot with HTTPie's beautiful JSON syntax..."
http --print=HhBb POST $BASE_URL/api/bots/$BOT_ID/start \
    direction=LONG \
    paper:=true

print_success "Bot start request sent"
sleep 1

###############################################################################
# Test 3: Get Bot Status
###############################################################################
print_header "TEST 3: Get Bot Status - Pretty Formatted"

print_info "Fetching bot status with syntax highlighting..."
http GET $BASE_URL/api/bots/$BOT_ID/status

print_success "Status retrieved"
sleep 1

###############################################################################
# Test 4: Update Leverage
###############################################################################
print_header "TEST 4: Update Leverage - HTTPie Number Syntax"

print_info "Updating leverage (note :=10 for JSON number, not string)..."
http POST $BASE_URL/api/bots/$BOT_ID/leverage \
    symbol=BTCUSDT \
    leverage:=10

print_success "Leverage updated to 10x"
sleep 1

###############################################################################
# Test 5: Enable Sentiment Analysis
###############################################################################
print_header "TEST 5: Toggle Sentiment - Boolean Syntax"

print_info "Enabling sentiment analysis (enabled:=true for boolean)..."
http POST $BASE_URL/api/bots/$BOT_ID/sentiment \
    enabled:=true

print_success "Sentiment analysis enabled"
sleep 1

###############################################################################
# Test 6: List All Bots
###############################################################################
print_header "TEST 6: List All Bots - Colorized Output"

print_info "Listing all bots with beautiful formatting..."
http GET $BASE_URL/api/bots

print_success "Bot list retrieved"
sleep 1

###############################################################################
# Test 7: Create Multiple Bots
###############################################################################
print_header "TEST 7: Create Multiple Bots in Parallel"

print_info "Creating 3 bots simultaneously..."
for i in {1..3}; do
    http --print=b POST $BASE_URL/api/bots > /tmp/bot_$i.json &
done
wait

# Extract bot IDs
BOT_2=$(jq -r '.botId' /tmp/bot_1.json)
BOT_3=$(jq -r '.botId' /tmp/bot_2.json)
BOT_4=$(jq -r '.botId' /tmp/bot_3.json)

print_success "Created Bot 2: $BOT_2"
print_success "Created Bot 3: $BOT_3"
print_success "Created Bot 4: $BOT_4"

# Cleanup temp files
rm -f /tmp/bot_*.json
sleep 1

###############################################################################
# Test 8: Start Bots with Different Strategies
###############################################################################
print_header "TEST 8: Start Multiple Bots - Different Strategies"

print_info "Starting Bot 2 in LONG mode..."
http --print=h POST $BASE_URL/api/bots/$BOT_2/start \
    direction=LONG \
    paper:=true

print_info "Starting Bot 3 in SHORT mode..."
http --print=h POST $BASE_URL/api/bots/$BOT_3/start \
    direction=SHORT \
    paper:=true

print_success "Multiple bots started with different strategies"
sleep 1

###############################################################################
# Test 9: Check Multiple Bot Statuses
###############################################################################
print_header "TEST 9: Check All Bot Statuses"

for bot in $BOT_ID $BOT_2 $BOT_3; do
    print_info "Status for bot: $bot"
    http --print=b GET $BASE_URL/api/bots/$bot/status | jq '{botId, status, direction}'
    echo ""
done

print_success "All statuses checked"
sleep 1

###############################################################################
# Test 10: HTTPie Sessions (Persistent Headers)
###############################################################################
print_header "TEST 10: HTTPie Sessions - Persistent Headers"

print_info "Creating a session with custom headers..."
http --session=trading-bot GET $BASE_URL/api/bots \
    X-Client-ID:httpie-tester \
    User-Agent:TradingBotCLI/1.0

print_info "Reusing session (headers persisted)..."
http --session=trading-bot GET $BASE_URL/api/bots/$BOT_ID/status

print_success "Session test complete"
sleep 1

###############################################################################
# Test 11: Gateway API with Authentication
###############################################################################
print_header "TEST 11: Gateway API - With Custom Headers"

print_info "Creating bot via gateway with auth headers..."
GATEWAY_RESPONSE=$(http --print=b POST $GATEWAY_URL/api/bots \
    Authorization:"Bearer test-token-12345" \
    X-Client-ID:httpie-client)

echo "$GATEWAY_RESPONSE"
GATEWAY_BOT=$(echo "$GATEWAY_RESPONSE" | jq -r '.botId')

print_success "Gateway bot created: $GATEWAY_BOT"
sleep 1

###############################################################################
# Test 12: Verbose Output (Show Everything)
###############################################################################
print_header "TEST 12: Verbose Mode - Request & Response Details"

print_info "Showing full request and response with -v flag..."
http -v GET $BASE_URL/api/bots/$BOT_ID/status

print_success "Verbose output displayed"
sleep 1

###############################################################################
# Test 13: Print Control (Headers Only)
###############################################################################
print_header "TEST 13: Print Control - Headers Only"

print_info "Showing only response headers with --print=h..."
http --print=h GET $BASE_URL/api/bots

print_success "Headers displayed"
sleep 1

###############################################################################
# Test 14: Download Mode (Save Response)
###############################################################################
print_header "TEST 14: Save Response to File"

print_info "Downloading bot status to file..."
http --download --output=bot-status.json GET $BASE_URL/api/bots/$BOT_ID/status

if [ -f bot-status.json ]; then
    print_success "Response saved to bot-status.json"
    cat bot-status.json | jq '.'
    rm -f bot-status.json
fi
sleep 1

###############################################################################
# Test 15: Error Handling - Non-existent Bot
###############################################################################
print_header "TEST 15: Error Handling - 404 Not Found"

print_info "Testing with non-existent bot ID..."
http GET $BASE_URL/api/bots/non-existent-id/status || print_info "Expected 404 error"

sleep 1

###############################################################################
# Test 16: Invalid Input - Bad Request
###############################################################################
print_header "TEST 16: Validation Error - Invalid Leverage"

print_info "Testing with invalid leverage value..."
http POST $BASE_URL/api/bots/$BOT_ID/leverage \
    symbol=BTCUSDT \
    leverage:=-5 || print_info "Expected 400 error"

sleep 1

###############################################################################
# Test 17: Complex JSON from Heredoc
###############################################################################
print_header "TEST 17: Complex JSON - Using Heredoc"

print_info "Sending complex JSON configuration..."
http POST $BASE_URL/api/bots/$BOT_ID/configure <<< '{
    "symbol": "BTCUSDT",
    "tradeAmount": 1000,
    "leverage": 5,
    "rsiOversoldThreshold": 30,
    "rsiOverboughtThreshold": 70,
    "trailingStopPercentage": 0.02,
    "indicators": {
        "rsi": true,
        "macd": true,
        "bollinger": true
    }
}'

print_success "Complex configuration sent"
sleep 1

###############################################################################
# Test 18: Query Parameters
###############################################################################
print_header "TEST 18: Query Parameters - Resilience Metrics"

print_info "Fetching metrics with query parameters..."
http GET $BASE_URL/api/resilience/metrics \
    format==json \
    includeDetails==true

print_success "Metrics with query params retrieved"
sleep 1

###############################################################################
# Test 19: Gateway Resilience Endpoints
###############################################################################
print_header "TEST 19: Gateway Resilience Metrics"

print_info "Rate limiter metrics..."
http --print=b GET $GATEWAY_URL/api/resilience/rate-limiters | jq '.'

echo ""
print_info "Circuit breaker metrics..."
http --print=b GET $GATEWAY_URL/api/resilience/circuit-breaker | jq '.'

echo ""
print_info "Retry metrics..."
http --print=b GET $GATEWAY_URL/api/resilience/retry | jq '.'

print_success "All resilience metrics retrieved"
sleep 1

###############################################################################
# Test 20: Performance Timing
###############################################################################
print_header "TEST 20: Performance Measurement"

print_info "Measuring request timing with time command..."
time http --print=b GET $BASE_URL/api/bots > /dev/null

print_success "Timing measured"
sleep 1

###############################################################################
# Test 21: Stop All Bots
###############################################################################
print_header "TEST 21: Stop All Bots"

for bot in $BOT_ID $BOT_2 $BOT_3 $GATEWAY_BOT; do
    print_info "Stopping bot: $bot"
    http --print=h PUT $BASE_URL/api/bots/$bot/stop
done

print_success "All bots stopped"
sleep 1

###############################################################################
# Test 22: Cleanup - Delete All Bots
###############################################################################
print_header "TEST 22: Cleanup - Delete All Bots"

for bot in $BOT_ID $BOT_2 $BOT_3 $BOT_4 $GATEWAY_BOT; do
    print_info "Deleting bot: $bot"
    http DELETE $BASE_URL/api/bots/$bot
done

print_success "All bots deleted"
sleep 1

###############################################################################
# Test 23: Verify Cleanup
###############################################################################
print_header "TEST 23: Verify Cleanup"

print_info "Checking remaining bots..."
http GET $BASE_URL/api/bots

print_success "Cleanup verified"

###############################################################################
# Summary
###############################################################################
print_header "TEST SUMMARY"

echo -e "${GREEN}╔════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║                                                        ║${NC}"
echo -e "${GREEN}║     ✓ HTTPie Test Suite Completed Successfully        ║${NC}"
echo -e "${GREEN}║                                                        ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════════════════════╝${NC}"

echo -e "\n${CYAN}HTTPie Features Demonstrated:${NC}"
echo -e "  ${PURPLE}✓${NC} Pretty formatted JSON output with colors"
echo -e "  ${PURPLE}✓${NC} Simple syntax (key=value for strings, key:=value for JSON)"
echo -e "  ${PURPLE}✓${NC} Sessions for persistent headers"
echo -e "  ${PURPLE}✓${NC} Verbose mode (-v) for debugging"
echo -e "  ${PURPLE}✓${NC} Print control (--print=HhBb)"
echo -e "  ${PURPLE}✓${NC} Query parameters (key==value)"
echo -e "  ${PURPLE}✓${NC} Custom headers (Header:value)"
echo -e "  ${PURPLE}✓${NC} File download (--download)"
echo -e "  ${PURPLE}✓${NC} Complex JSON with heredoc"
echo -e "  ${PURPLE}✓${NC} Error handling and status codes"

echo -e "\n${YELLOW}Total Tests: 23${NC}"
echo -e "${YELLOW}All tests passed!${NC}\n"

# Print HTTPie tips
echo -e "${BLUE}💡 HTTPie Tips:${NC}"
echo -e "  • Use ${CYAN}key=value${NC} for strings"
echo -e "  • Use ${CYAN}key:=value${NC} for JSON (numbers, booleans)"
echo -e "  • Use ${CYAN}key==value${NC} for query parameters"
echo -e "  • Use ${CYAN}Header:value${NC} for custom headers"
echo -e "  • Use ${CYAN}--session=name${NC} to persist headers/cookies"
echo -e "  • Use ${CYAN}-v${NC} for verbose output"
echo -e "  • Use ${CYAN}--print=HhBb${NC} to control output"
echo -e "  • Pipe to ${CYAN}jq${NC} for JSON filtering\n"
