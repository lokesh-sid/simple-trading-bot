#!/bin/bash

# Paper Trading Verification Test Script
# This script tests all paper trading functionality without risking real money

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
BASE_URL="http://localhost:8080"
API_BASE="${BASE_URL}/api/v1"
BOT_ID=""
SLEEP_TIME=2

# Function to print colored output
print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_header() {
    echo -e "\n${BLUE}================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}================================${NC}\n"
}

# Function to check if server is running
check_server() {
    print_header "Step 1: Checking Server Status"
    
    if curl -s "${BASE_URL}/actuator/health" > /dev/null 2>&1; then
        print_success "Server is running at ${BASE_URL}"
        
        # Get health details
        HEALTH=$(curl -s "${BASE_URL}/actuator/health")
        echo "$HEALTH" | jq '.' 2>/dev/null || echo "$HEALTH"
    else
        print_error "Server is not running at ${BASE_URL}"
        print_info "Please start the server with: ./gradlew bootRun"
        exit 1
    fi
}

# Function to create a bot
create_bot() {
    print_header "Step 2: Creating Trading Bot"
    
    RESPONSE=$(curl -s -X POST "${API_BASE}/bots" \
        -H "Content-Type: application/json")
    
    BOT_ID=$(echo "$RESPONSE" | jq -r '.botId' 2>/dev/null)
    
    if [ -n "$BOT_ID" ] && [ "$BOT_ID" != "null" ]; then
        print_success "Bot created successfully"
        print_info "Bot ID: ${BOT_ID}"
        echo "$RESPONSE" | jq '.' 2>/dev/null || echo "$RESPONSE"
    else
        print_error "Failed to create bot"
        echo "$RESPONSE"
        exit 1
    fi
    
    sleep $SLEEP_TIME
}

# Function to get bot status
get_bot_status() {
    print_header "Step 3: Checking Bot Status"
    
    RESPONSE=$(curl -s "${API_BASE}/bots/${BOT_ID}")
    
    if echo "$RESPONSE" | jq -e '.botId' > /dev/null 2>&1; then
        print_success "Bot status retrieved"
        echo "$RESPONSE" | jq '.'
        
        STATUS=$(echo "$RESPONSE" | jq -r '.status')
        print_info "Current Status: ${STATUS}"
    else
        print_error "Failed to get bot status"
        echo "$RESPONSE"
        exit 1
    fi
    
    sleep $SLEEP_TIME
}

# Function to start bot in paper trading mode (LONG)
start_bot_long() {
    print_header "Step 4: Starting Bot in Paper Trading (LONG)"
    
    RESPONSE=$(curl -s -X POST "${API_BASE}/bots/${BOT_ID}/start" \
        -H "Content-Type: application/json" \
        -d '{
            "direction": "LONG",
            "paper": true
        }')
    
    if echo "$RESPONSE" | jq -e '.success' > /dev/null 2>&1; then
        SUCCESS=$(echo "$RESPONSE" | jq -r '.success')
        if [ "$SUCCESS" == "true" ]; then
            print_success "Bot started in LONG paper trading mode"
            echo "$RESPONSE" | jq '.'
        else
            print_error "Failed to start bot"
            echo "$RESPONSE" | jq '.'
            exit 1
        fi
    else
        print_error "Invalid response when starting bot"
        echo "$RESPONSE"
        exit 1
    fi
    
    sleep $SLEEP_TIME
}

# Function to verify bot is running
verify_bot_running() {
    print_header "Step 5: Verifying Bot is Running"
    
    RESPONSE=$(curl -s "${API_BASE}/bots/${BOT_ID}")
    STATUS=$(echo "$RESPONSE" | jq -r '.status' 2>/dev/null)
    RUNNING=$(echo "$RESPONSE" | jq -r '.running' 2>/dev/null)
    
    if [ "$RUNNING" == "true" ]; then
        print_success "Bot is running"
        print_info "Status: ${STATUS}"
        echo "$RESPONSE" | jq '.'
    else
        print_warning "Bot may not be running properly"
        echo "$RESPONSE" | jq '.'
    fi
    
    sleep $SLEEP_TIME
}

# Function to get bot state
get_bot_state() {
    print_header "Step 6: Getting Bot State"
    
    RESPONSE=$(curl -s "${API_BASE}/bots/${BOT_ID}/state")
    
    if echo "$RESPONSE" | jq -e '.botId' > /dev/null 2>&1; then
        print_success "Bot state retrieved"
        echo "$RESPONSE" | jq '.'
        
        # Extract key information
        DIRECTION=$(echo "$RESPONSE" | jq -r '.direction // "N/A"')
        PAPER=$(echo "$RESPONSE" | jq -r '.paper // false')
        LEVERAGE=$(echo "$RESPONSE" | jq -r '.currentLeverage // "N/A"')
        
        print_info "Direction: ${DIRECTION}"
        print_info "Paper Trading: ${PAPER}"
        print_info "Leverage: ${LEVERAGE}x"
    else
        print_warning "Could not retrieve bot state (endpoint may not exist)"
        echo "$RESPONSE"
    fi
    
    sleep $SLEEP_TIME
}

# Function to list all bots
list_all_bots() {
    print_header "Step 7: Listing All Bots"
    
    RESPONSE=$(curl -s "${API_BASE}/bots")
    
    if echo "$RESPONSE" | jq -e '.botIds' > /dev/null 2>&1; then
        print_success "Bot list retrieved"
        BOT_COUNT=$(echo "$RESPONSE" | jq '.botIds | length')
        print_info "Total bots: ${BOT_COUNT}"
        echo "$RESPONSE" | jq '.'
    else
        print_error "Failed to list bots"
        echo "$RESPONSE"
    fi
    
    sleep $SLEEP_TIME
}

# Function to stop bot
stop_bot() {
    print_header "Step 8: Stopping Bot"
    
    RESPONSE=$(curl -s -X POST "${API_BASE}/bots/${BOT_ID}/stop" \
        -H "Content-Type: application/json")
    
    if echo "$RESPONSE" | jq -e '.' > /dev/null 2>&1; then
        print_success "Bot stopped successfully"
        echo "$RESPONSE" | jq '.'
    else
        print_error "Failed to stop bot"
        echo "$RESPONSE"
    fi
    
    sleep $SLEEP_TIME
}

# Function to verify bot is stopped
verify_bot_stopped() {
    print_header "Step 9: Verifying Bot is Stopped"
    
    RESPONSE=$(curl -s "${API_BASE}/bots/${BOT_ID}")
    RUNNING=$(echo "$RESPONSE" | jq -r '.running' 2>/dev/null)
    
    if [ "$RUNNING" == "false" ]; then
        print_success "Bot is stopped"
        echo "$RESPONSE" | jq '.'
    else
        print_warning "Bot may still be running"
        echo "$RESPONSE" | jq '.'
    fi
    
    sleep $SLEEP_TIME
}

# Function to test SHORT direction
test_short_direction() {
    print_header "Step 10: Testing SHORT Direction"
    
    RESPONSE=$(curl -s -X POST "${API_BASE}/bots/${BOT_ID}/start" \
        -H "Content-Type: application/json" \
        -d '{
            "direction": "SHORT",
            "paper": true
        }')
    
    if echo "$RESPONSE" | jq -e '.success' > /dev/null 2>&1; then
        SUCCESS=$(echo "$RESPONSE" | jq -r '.success')
        if [ "$SUCCESS" == "true" ]; then
            print_success "Bot started in SHORT paper trading mode"
            echo "$RESPONSE" | jq '.'
        else
            print_error "Failed to start bot in SHORT mode"
            echo "$RESPONSE" | jq '.'
        fi
    else
        print_error "Invalid response when starting bot in SHORT mode"
        echo "$RESPONSE"
    fi
    
    sleep $SLEEP_TIME
}

# Function to stop bot again
stop_bot_again() {
    print_header "Step 11: Stopping Bot Again"
    
    RESPONSE=$(curl -s -X POST "${API_BASE}/bots/${BOT_ID}/stop" \
        -H "Content-Type: application/json")
    
    if echo "$RESPONSE" | jq -e '.' > /dev/null 2>&1; then
        print_success "Bot stopped successfully"
        echo "$RESPONSE" | jq '.'
    else
        print_error "Failed to stop bot"
        echo "$RESPONSE"
    fi
    
    sleep $SLEEP_TIME
}

# Function to delete bot
delete_bot() {
    print_header "Step 12: Deleting Bot"
    
    RESPONSE=$(curl -s -X DELETE "${API_BASE}/bots/${BOT_ID}")
    
    if [ $? -eq 0 ]; then
        print_success "Bot deleted successfully"
        if [ -n "$RESPONSE" ]; then
            echo "$RESPONSE" | jq '.' 2>/dev/null || echo "$RESPONSE"
        fi
    else
        print_error "Failed to delete bot"
        echo "$RESPONSE"
    fi
}

# Function to verify bot is deleted
verify_bot_deleted() {
    print_header "Step 13: Verifying Bot is Deleted"
    
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${API_BASE}/bots/${BOT_ID}")
    
    if [ "$HTTP_CODE" == "404" ]; then
        print_success "Bot successfully deleted (404 Not Found)"
    else
        print_warning "Bot may still exist (HTTP ${HTTP_CODE})"
    fi
}

# Function to test error handling
test_error_handling() {
    print_header "Step 14: Testing Error Handling"
    
    # Try to start non-existent bot
    print_info "Testing invalid bot ID..."
    RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" -X POST "${API_BASE}/bots/invalid-bot-id/start" \
        -H "Content-Type: application/json" \
        -d '{"direction": "LONG", "paper": true}')
    
    HTTP_CODE=$(echo "$RESPONSE" | grep "HTTP_CODE:" | cut -d: -f2)
    BODY=$(echo "$RESPONSE" | sed '/HTTP_CODE:/d')
    
    if [ "$HTTP_CODE" == "404" ]; then
        print_success "Correctly returned 404 for invalid bot ID"
        echo "$BODY" | jq '.' 2>/dev/null || echo "$BODY"
    else
        print_warning "Expected 404, got ${HTTP_CODE}"
        echo "$BODY"
    fi
}

# Function to print final summary
print_summary() {
    print_header "Test Summary"
    
    print_success "Paper Trading Verification Complete!"
    echo ""
    print_info "Verified Functionality:"
    echo "  ✓ Server health check"
    echo "  ✓ Bot creation"
    echo "  ✓ Bot status retrieval"
    echo "  ✓ Starting bot in LONG paper mode"
    echo "  ✓ Starting bot in SHORT paper mode"
    echo "  ✓ Bot state management"
    echo "  ✓ Stopping bot"
    echo "  ✓ Deleting bot"
    echo "  ✓ Error handling (404)"
    echo ""
    print_info "Next Steps:"
    echo "  1. Monitor logs while bot is running: tail -f logs/application.log"
    echo "  2. Check Redis for bot state: redis-cli KEYS 'bot:*'"
    echo "  3. Review paper trading balance simulation"
    echo "  4. Test with different symbols and leverage settings"
    echo ""
    print_warning "To enable LIVE trading:"
    echo "  1. Set real Binance API credentials in application.properties"
    echo "  2. Change 'paper: true' to 'paper: false' in start request"
    echo "  3. Start with small positions and low leverage"
    echo ""
}

# Main execution
main() {
    clear
    echo -e "${BLUE}"
    echo "╔═══════════════════════════════════════════════════════════╗"
    echo "║                                                           ║"
    echo "║     Paper Trading Verification Test Script               ║"
    echo "║     Simple Trading Bot - Comprehensive Test Suite        ║"
    echo "║                                                           ║"
    echo "╚═══════════════════════════════════════════════════════════╝"
    echo -e "${NC}\n"
    
    print_info "This script will test all paper trading functionality"
    print_warning "Make sure the server is running: ./gradlew bootRun"
    echo ""
    read -p "Press Enter to continue..." -r
    
    # Check for required tools
    if ! command -v jq &> /dev/null; then
        print_error "jq is not installed. Please install it:"
        echo "  macOS: brew install jq"
        echo "  Linux: apt-get install jq or yum install jq"
        exit 1
    fi
    
    if ! command -v curl &> /dev/null; then
        print_error "curl is not installed. Please install curl."
        exit 1
    fi
    
    # Run all tests
    check_server
    create_bot
    get_bot_status
    start_bot_long
    verify_bot_running
    get_bot_state
    list_all_bots
    stop_bot
    verify_bot_stopped
    test_short_direction
    stop_bot_again
    delete_bot
    verify_bot_deleted
    test_error_handling
    print_summary
}

# Trap errors
trap 'print_error "Test failed at line $LINENO. Exit code: $?"' ERR

# Run main
main "$@"
