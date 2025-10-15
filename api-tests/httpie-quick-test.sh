#!/bin/bash

# HTTPie Quick Test Script for Trading Bot API
# Simplified HTTPie commands for quick endpoint testing

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
BASE_URL="http://localhost:8080"
BACKEND_BASE="${BASE_URL}/api/bots"
GATEWAY_BASE="${BASE_URL}/gateway/api/bots"

# Helper functions
print_header() {
    echo -e "\n${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"
}

print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_info() {
    echo -e "${YELLOW}ℹ${NC} $1"
}

check_dependencies() {
    if ! command -v http &> /dev/null; then
        print_error "HTTPie is not installed"
        echo "Install with: brew install httpie"
        exit 1
    fi
    
    if ! command -v jq &> /dev/null; then
        print_error "jq is not installed"
        echo "Install with: brew install jq"
        exit 1
    fi
    
    print_success "All dependencies installed"
}

# Quick test functions
test_create_bot() {
    print_header "Creating Bot"
    RESPONSE=$(http --print=b POST ${BACKEND_BASE})
    BOT_ID=$(echo $RESPONSE | jq -r '.botId')
    
    if [ "$BOT_ID" != "null" ] && [ -n "$BOT_ID" ]; then
        print_success "Bot created: $BOT_ID"
        echo "$BOT_ID"
    else
        print_error "Failed to create bot"
        return 1
    fi
}

test_list_bots() {
    print_header "Listing All Bots"
    http GET ${BACKEND_BASE}
}

test_get_status() {
    local bot_id=$1
    print_header "Getting Bot Status: $bot_id"
    http GET ${BACKEND_BASE}/${bot_id}/status
}

test_start_bot() {
    local bot_id=$1
    print_header "Starting Bot: $bot_id"
    http POST ${BACKEND_BASE}/${bot_id}/start \
        direction=LONG \
        paper:=true
}

test_stop_bot() {
    local bot_id=$1
    print_header "Stopping Bot: $bot_id"
    http PUT ${BACKEND_BASE}/${bot_id}/stop
}

test_configure() {
    local bot_id=$1
    print_header "Configuring Bot: $bot_id"
    http POST ${BACKEND_BASE}/${bot_id}/configure \
        symbol=BTCUSDT \
        tradeAmount:=1000 \
        leverage:=5 \
        rsiOversoldThreshold:=30 \
        rsiOverboughtThreshold:=70 \
        trailingStopPercentage:=0.02
}

test_set_leverage() {
    local bot_id=$1
    print_header "Setting Leverage: $bot_id"
    http POST ${BACKEND_BASE}/${bot_id}/leverage \
        symbol=BTCUSDT \
        leverage:=10
}

test_toggle_sentiment() {
    local bot_id=$1
    local enabled=$2
    print_header "Toggle Sentiment: $bot_id (enabled=$enabled)"
    http POST ${BACKEND_BASE}/${bot_id}/sentiment \
        enabled:=${enabled}
}

test_delete_bot() {
    local bot_id=$1
    print_header "Deleting Bot: $bot_id"
    http DELETE ${BACKEND_BASE}/${bot_id}
}

test_gateway() {
    local bot_id=$1
    print_header "Testing Gateway API: $bot_id"
    
    print_info "Getting status via gateway..."
    http GET ${GATEWAY_BASE}/${bot_id}/status
    
    print_info "\nGateway health check..."
    http GET ${BASE_URL}/gateway/health
    
    print_info "\nCircuit breaker status..."
    http GET ${BASE_URL}/gateway/api/resilience/circuit-breaker
}

test_error_scenarios() {
    print_header "Testing Error Scenarios"
    
    print_info "Test 1: Invalid bot ID (404)"
    http GET ${BACKEND_BASE}/invalid-bot-id/status || true
    
    print_info "\nTest 2: Invalid leverage (400)"
    RESPONSE=$(http --print=b POST ${BACKEND_BASE})
    BOT_ID=$(echo $RESPONSE | jq -r '.botId')
    http POST ${BACKEND_BASE}/${BOT_ID}/leverage \
        leverage:=-5 || true
    
    # Cleanup
    http DELETE ${BACKEND_BASE}/${BOT_ID} > /dev/null
}

# Main execution
main() {
    clear
    print_header "HTTPie Quick Test - Trading Bot API"
    
    check_dependencies
    
    if [ $# -eq 0 ]; then
        # Interactive menu
        echo "Select test to run:"
        echo "  1. Complete lifecycle test"
        echo "  2. Create bot only"
        echo "  3. List all bots"
        echo "  4. Test existing bot (requires BOT_ID)"
        echo "  5. Error scenarios"
        echo "  6. Gateway API"
        echo "  7. Run all tests"
        echo ""
        read -p "Enter choice [1-7]: " choice
        
        case $choice in
            1)
                print_header "COMPLETE LIFECYCLE TEST"
                BOT_ID=$(test_create_bot)
                sleep 1
                test_configure "$BOT_ID"
                sleep 1
                test_set_leverage "$BOT_ID"
                sleep 1
                test_toggle_sentiment "$BOT_ID" true
                sleep 1
                test_start_bot "$BOT_ID"
                sleep 2
                test_get_status "$BOT_ID"
                sleep 1
                test_stop_bot "$BOT_ID"
                sleep 1
                test_delete_bot "$BOT_ID"
                print_success "\nComplete lifecycle test finished!"
                ;;
            2)
                test_create_bot
                ;;
            3)
                test_list_bots
                ;;
            4)
                read -p "Enter BOT_ID: " BOT_ID
                test_get_status "$BOT_ID"
                ;;
            5)
                test_error_scenarios
                ;;
            6)
                read -p "Enter BOT_ID (or press Enter to create new): " BOT_ID
                if [ -z "$BOT_ID" ]; then
                    BOT_ID=$(test_create_bot)
                fi
                test_gateway "$BOT_ID"
                ;;
            7)
                print_header "RUNNING ALL TESTS"
                BOT_ID=$(test_create_bot)
                test_list_bots
                test_configure "$BOT_ID"
                test_set_leverage "$BOT_ID"
                test_toggle_sentiment "$BOT_ID" true
                test_start_bot "$BOT_ID"
                test_get_status "$BOT_ID"
                test_stop_bot "$BOT_ID"
                test_gateway "$BOT_ID"
                test_error_scenarios
                test_delete_bot "$BOT_ID"
                print_success "\nAll tests completed!"
                ;;
            *)
                print_error "Invalid choice"
                exit 1
                ;;
        esac
    else
        # Command line arguments
        case "$1" in
            create)
                test_create_bot
                ;;
            list)
                test_list_bots
                ;;
            status)
                if [ -z "$2" ]; then
                    print_error "Usage: $0 status <BOT_ID>"
                    exit 1
                fi
                test_get_status "$2"
                ;;
            start)
                if [ -z "$2" ]; then
                    print_error "Usage: $0 start <BOT_ID>"
                    exit 1
                fi
                test_start_bot "$2"
                ;;
            stop)
                if [ -z "$2" ]; then
                    print_error "Usage: $0 stop <BOT_ID>"
                    exit 1
                fi
                test_stop_bot "$2"
                ;;
            delete)
                if [ -z "$2" ]; then
                    print_error "Usage: $0 delete <BOT_ID>"
                    exit 1
                fi
                test_delete_bot "$2"
                ;;
            lifecycle)
                BOT_ID=$(test_create_bot)
                test_configure "$BOT_ID"
                test_start_bot "$BOT_ID"
                sleep 2
                test_get_status "$BOT_ID"
                test_stop_bot "$BOT_ID"
                test_delete_bot "$BOT_ID"
                ;;
            gateway)
                if [ -z "$2" ]; then
                    print_error "Usage: $0 gateway <BOT_ID>"
                    exit 1
                fi
                test_gateway "$2"
                ;;
            errors)
                test_error_scenarios
                ;;
            *)
                echo "Usage: $0 [command] [args]"
                echo ""
                echo "Commands:"
                echo "  create              Create a new bot"
                echo "  list                List all bots"
                echo "  status <BOT_ID>     Get bot status"
                echo "  start <BOT_ID>      Start bot"
                echo "  stop <BOT_ID>       Stop bot"
                echo "  delete <BOT_ID>     Delete bot"
                echo "  lifecycle           Run complete lifecycle test"
                echo "  gateway <BOT_ID>    Test gateway API"
                echo "  errors              Test error scenarios"
                echo ""
                echo "Or run without arguments for interactive menu"
                exit 1
                ;;
        esac
    fi
}

main "$@"
