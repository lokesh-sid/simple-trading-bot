#!/bin/bash

###############################################################################
# Master Test Runner - Trading Bot Multi-Bot API
# Runs both backend and gateway test suites
###############################################################################

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# ASCII Art Banner
print_banner() {
    echo -e "${CYAN}"
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║                                                              ║"
    echo "║        Trading Bot Multi-Bot API Test Suite                 ║"
    echo "║                                                              ║"
    echo "║        Backend API + Gateway API + Resilience Tests         ║"
    echo "║                                                              ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo -e "${NC}\n"
}

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

# Function to print warning
print_warning() {
    echo -e "${PURPLE}⚠ $1${NC}"
}

# Function to check if server is running
check_server() {
    echo -e "${YELLOW}Checking if server is running...${NC}"
    
    response=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health 2>/dev/null)
    
    if [ "$response" = "200" ] || [ "$response" = "000" ]; then
        # Try gateway health as fallback
        response=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/gateway/health 2>/dev/null)
    fi
    
    if [ "$response" = "200" ]; then
        print_success "Server is running on http://localhost:8080"
        return 0
    else
        print_error "Server is not responding on http://localhost:8080"
        print_warning "Please start the application before running tests"
        return 1
    fi
}

# Function to run a test suite
run_test_suite() {
    local test_name=$1
    local test_script=$2
    
    print_header "Running $test_name"
    
    if [ ! -f "$test_script" ]; then
        print_error "Test script not found: $test_script"
        return 1
    fi
    
    if [ ! -x "$test_script" ]; then
        print_warning "Making test script executable..."
        chmod +x "$test_script"
    fi
    
    # Run the test script
    bash "$test_script"
    
    local exit_code=$?
    
    if [ $exit_code -eq 0 ]; then
        print_success "$test_name completed successfully"
        return 0
    else
        print_error "$test_name failed with exit code $exit_code"
        return 1
    fi
}

# Main execution
main() {
    print_banner
    
    # Check if server is running
    if ! check_server; then
        echo -e "\n${RED}Cannot proceed with tests - server is not running${NC}\n"
        exit 1
    fi
    
    sleep 2
    
    # Track test results
    backend_result=0
    gateway_result=0
    
    # Menu
    echo -e "${CYAN}Select test suite to run:${NC}"
    echo -e "  ${GREEN}1${NC} - Backend API Tests"
    echo -e "  ${GREEN}2${NC} - Gateway API Tests"
    echo -e "  ${GREEN}3${NC} - Run All Tests"
    echo -e "  ${GREEN}4${NC} - Quick Smoke Test"
    echo -e "  ${GREEN}5${NC} - Exit"
    echo ""
    
    read -p "Enter choice [1-5]: " choice
    
    case $choice in
        1)
            print_header "Backend API Test Suite"
            run_test_suite "Backend API Tests" "./api-tests/backend-api-test.sh"
            backend_result=$?
            ;;
        2)
            print_header "Gateway API Test Suite"
            run_test_suite "Gateway API Tests" "./api-tests/gateway-api-test.sh"
            gateway_result=$?
            ;;
        3)
            print_header "Running All Test Suites"
            
            run_test_suite "Backend API Tests" "./api-tests/backend-api-test.sh"
            backend_result=$?
            
            sleep 3
            
            run_test_suite "Gateway API Tests" "./api-tests/gateway-api-test.sh"
            gateway_result=$?
            ;;
        4)
            print_header "Quick Smoke Test"
            
            print_info "Testing backend health..."
            backend_health=$(curl -s http://localhost:8080/actuator/health 2>/dev/null || echo "FAIL")
            if [[ $backend_health == *"UP"* ]] || [[ $backend_health == *"status"* ]]; then
                print_success "Backend is healthy"
            else
                print_error "Backend health check failed"
            fi
            
            print_info "Testing gateway health..."
            gateway_health=$(curl -s http://localhost:8080/gateway/health)
            echo "Response: $gateway_health"
            print_success "Gateway health check complete"
            
            print_info "Creating a test bot..."
            bot_response=$(curl -s -X POST http://localhost:8080/api/bots)
            echo "Response: $bot_response"
            
            bot_id=$(echo $bot_response | grep -o '"botId":"[^"]*"' | cut -d'"' -f4)
            if [ -n "$bot_id" ]; then
                print_success "Test bot created: $bot_id"
                
                print_info "Listing bots..."
                list_response=$(curl -s http://localhost:8080/api/bots)
                echo "Response: $list_response"
                
                print_info "Deleting test bot..."
                curl -s -X DELETE "http://localhost:8080/api/bots/$bot_id" > /dev/null
                print_success "Test bot deleted"
            else
                print_error "Failed to create test bot"
            fi
            
            print_success "Smoke test completed"
            ;;
        5)
            print_info "Exiting..."
            exit 0
            ;;
        *)
            print_error "Invalid choice"
            exit 1
            ;;
    esac
    
    # Print summary
    echo ""
    print_header "TEST SUMMARY"
    
    if [ $choice -eq 3 ]; then
        # All tests summary
        if [ $backend_result -eq 0 ] && [ $gateway_result -eq 0 ]; then
            echo -e "${GREEN}╔════════════════════════════════════════════╗${NC}"
            echo -e "${GREEN}║                                            ║${NC}"
            echo -e "${GREEN}║     ✓ ALL TEST SUITES PASSED              ║${NC}"
            echo -e "${GREEN}║                                            ║${NC}"
            echo -e "${GREEN}╚════════════════════════════════════════════╝${NC}"
            echo ""
            print_success "Backend API Tests: PASSED"
            print_success "Gateway API Tests: PASSED"
            echo ""
            exit 0
        else
            echo -e "${RED}╔════════════════════════════════════════════╗${NC}"
            echo -e "${RED}║                                            ║${NC}"
            echo -e "${RED}║     ✗ SOME TESTS FAILED                   ║${NC}"
            echo -e "${RED}║                                            ║${NC}"
            echo -e "${RED}╚════════════════════════════════════════════╝${NC}"
            echo ""
            [ $backend_result -eq 0 ] && print_success "Backend API Tests: PASSED" || print_error "Backend API Tests: FAILED"
            [ $gateway_result -eq 0 ] && print_success "Gateway API Tests: PASSED" || print_error "Gateway API Tests: FAILED"
            echo ""
            exit 1
        fi
    else
        # Single test summary
        if [ $backend_result -eq 0 ] || [ $gateway_result -eq 0 ]; then
            print_success "Test suite completed successfully"
            exit 0
        else
            print_error "Test suite failed"
            exit 1
        fi
    fi
}

# Run main function
main
