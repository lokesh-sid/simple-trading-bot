#!/bin/bash

# HTTPie Performance Testing Script
# Load testing and performance benchmarking using HTTPie

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Configuration
BASE_URL="http://localhost:8080"
BACKEND_BASE="${BASE_URL}/api/bots"
GATEWAY_BASE="${BASE_URL}/gateway/api/bots"
NUM_REQUESTS=100
NUM_CONCURRENT=10

print_header() {
    echo -e "\n${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"
}

print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_info() {
    echo -e "${CYAN}ℹ${NC} $1"
}

print_metric() {
    echo -e "${YELLOW}→${NC} $1: ${GREEN}$2${NC}"
}

# Check dependencies
check_dependencies() {
    if ! command -v http &> /dev/null; then
        echo "HTTPie not installed. Install with: brew install httpie"
        exit 1
    fi
    
    if ! command -v jq &> /dev/null; then
        echo "jq not installed. Install with: brew install jq"
        exit 1
    fi
    
    if ! command -v bc &> /dev/null; then
        echo "bc not installed. Install with: brew install bc"
        exit 1
    fi
}

# Setup
setup() {
    print_header "Setting up test environment"
    
    # Create a test bot
    RESPONSE=$(http --print=b POST ${BACKEND_BASE})
    BOT_ID=$(echo $RESPONSE | jq -r '.botId')
    
    if [ "$BOT_ID" = "null" ] || [ -z "$BOT_ID" ]; then
        echo "Failed to create bot"
        exit 1
    fi
    
    print_success "Test bot created: $BOT_ID"
    
    # Configure the bot
    http --print=b POST ${BACKEND_BASE}/${BOT_ID}/configure \
        symbol=BTCUSDT \
        tradeAmount:=1000 \
        leverage:=5 > /dev/null
    
    print_success "Bot configured"
}

# Cleanup
cleanup() {
    if [ -n "$BOT_ID" ]; then
        print_header "Cleaning up"
        http DELETE ${BACKEND_BASE}/${BOT_ID} > /dev/null 2>&1 || true
        print_success "Test bot deleted"
    fi
}

# Calculate statistics
calculate_stats() {
    local times_file=$1
    local total=0
    local count=0
    local min=999999
    local max=0
    
    while read -r time; do
        total=$(echo "$total + $time" | bc)
        count=$((count + 1))
        
        # Min
        if (( $(echo "$time < $min" | bc -l) )); then
            min=$time
        fi
        
        # Max
        if (( $(echo "$time > $max" | bc -l) )); then
            max=$time
        fi
    done < "$times_file"
    
    local avg=$(echo "scale=3; $total / $count" | bc)
    
    # Calculate median
    local median=$(sort -n "$times_file" | awk '{a[NR]=$1} END {print (NR%2==1)?a[(NR+1)/2]:(a[NR/2]+a[NR/2+1])/2}')
    
    # Calculate percentiles
    local p95=$(sort -n "$times_file" | awk 'BEGIN{c=0} {a[c++]=$1} END{print a[int(c*0.95)]}')
    local p99=$(sort -n "$times_file" | awk 'BEGIN{c=0} {a[c++]=$1} END{print a[int(c*0.99)]}')
    
    print_metric "Total Requests" "$count"
    print_metric "Average Time" "${avg}s"
    print_metric "Median Time" "${median}s"
    print_metric "Min Time" "${min}s"
    print_metric "Max Time" "${max}s"
    print_metric "95th Percentile" "${p95}s"
    print_metric "99th Percentile" "${p99}s"
}

# Test 1: Single endpoint response time
test_response_times() {
    print_header "Test 1: Response Time Analysis (GET /status)"
    
    local times_file=$(mktemp)
    
    print_info "Running $NUM_REQUESTS requests..."
    
    for i in $(seq 1 $NUM_REQUESTS); do
        start=$(date +%s.%N)
        http --print= GET ${BACKEND_BASE}/${BOT_ID}/status > /dev/null 2>&1
        end=$(date +%s.%N)
        
        elapsed=$(echo "$end - $start" | bc)
        echo "$elapsed" >> "$times_file"
        
        # Progress indicator
        if [ $((i % 10)) -eq 0 ]; then
            echo -n "."
        fi
    done
    echo ""
    
    calculate_stats "$times_file"
    rm "$times_file"
}

# Test 2: Different endpoints
test_all_endpoints() {
    print_header "Test 2: All Endpoints Response Time"
    
    local endpoints=(
        "GET ${BACKEND_BASE}"
        "GET ${BACKEND_BASE}/${BOT_ID}/status"
        "POST ${BACKEND_BASE}/${BOT_ID}/leverage symbol=BTCUSDT leverage:=10"
        "POST ${BACKEND_BASE}/${BOT_ID}/sentiment enabled:=true"
    )
    
    for endpoint in "${endpoints[@]}"; do
        local method=$(echo $endpoint | awk '{print $1}')
        local rest=$(echo $endpoint | cut -d' ' -f2-)
        
        echo -e "\n${CYAN}Testing:${NC} $endpoint"
        
        local times_file=$(mktemp)
        
        for i in $(seq 1 20); do
            start=$(date +%s.%N)
            if [ "$method" = "GET" ]; then
                http --print= GET $rest > /dev/null 2>&1
            else
                http --print= POST $rest > /dev/null 2>&1
            fi
            end=$(date +%s.%N)
            
            elapsed=$(echo "$end - $start" | bc)
            echo "$elapsed" >> "$times_file"
        done
        
        local avg=$(awk '{sum+=$1; count++} END {printf "%.3f", sum/count}' "$times_file")
        print_metric "Average" "${avg}s"
        
        rm "$times_file"
    done
}

# Test 3: Concurrent requests
test_concurrent_requests() {
    print_header "Test 3: Concurrent Requests"
    
    print_info "Running $NUM_CONCURRENT concurrent requests..."
    
    local times_file=$(mktemp)
    local start_all=$(date +%s.%N)
    
    # Run concurrent requests
    for i in $(seq 1 $NUM_CONCURRENT); do
        (
            start=$(date +%s.%N)
            http --print= GET ${BACKEND_BASE}/${BOT_ID}/status > /dev/null 2>&1
            end=$(date +%s.%N)
            elapsed=$(echo "$end - $start" | bc)
            echo "$elapsed" >> "$times_file"
        ) &
    done
    
    # Wait for all background jobs
    wait
    
    local end_all=$(date +%s.%N)
    local total_time=$(echo "$end_all - $start_all" | bc)
    
    calculate_stats "$times_file"
    print_metric "Total Time (parallel)" "${total_time}s"
    
    local throughput=$(echo "scale=2; $NUM_CONCURRENT / $total_time" | bc)
    print_metric "Throughput" "${throughput} req/s"
    
    rm "$times_file"
}

# Test 4: Create bot performance
test_create_performance() {
    print_header "Test 4: Bot Creation Performance"
    
    print_info "Creating $NUM_CONCURRENT bots..."
    
    local times_file=$(mktemp)
    local bot_ids_file=$(mktemp)
    local start_all=$(date +%s.%N)
    
    for i in $(seq 1 $NUM_CONCURRENT); do
        (
            start=$(date +%s.%N)
            RESPONSE=$(http --print=b POST ${BACKEND_BASE})
            end=$(date +%s.%N)
            
            elapsed=$(echo "$end - $start" | bc)
            echo "$elapsed" >> "$times_file"
            
            BOT_ID=$(echo $RESPONSE | jq -r '.botId')
            echo "$BOT_ID" >> "$bot_ids_file"
        ) &
    done
    
    wait
    
    local end_all=$(date +%s.%N)
    local total_time=$(echo "$end_all - $start_all" | bc)
    
    calculate_stats "$times_file"
    print_metric "Total Time" "${total_time}s"
    
    # Cleanup created bots
    print_info "Cleaning up created bots..."
    while read -r bot_id; do
        http DELETE ${BACKEND_BASE}/${bot_id} > /dev/null 2>&1 &
    done < "$bot_ids_file"
    wait
    
    rm "$times_file" "$bot_ids_file"
    print_success "Cleanup complete"
}

# Test 5: Gateway vs Backend comparison
test_gateway_vs_backend() {
    print_header "Test 5: Gateway vs Backend Performance"
    
    local num_tests=50
    
    # Test backend
    echo -e "${CYAN}Backend API:${NC}"
    local backend_times=$(mktemp)
    
    for i in $(seq 1 $num_tests); do
        start=$(date +%s.%N)
        http --print= GET ${BACKEND_BASE}/${BOT_ID}/status > /dev/null 2>&1
        end=$(date +%s.%N)
        elapsed=$(echo "$end - $start" | bc)
        echo "$elapsed" >> "$backend_times"
    done
    
    local backend_avg=$(awk '{sum+=$1; count++} END {printf "%.3f", sum/count}' "$backend_times")
    print_metric "Average" "${backend_avg}s"
    
    # Test gateway
    echo -e "\n${CYAN}Gateway API:${NC}"
    local gateway_times=$(mktemp)
    
    for i in $(seq 1 $num_tests); do
        start=$(date +%s.%N)
        http --print= GET ${GATEWAY_BASE}/${BOT_ID}/status > /dev/null 2>&1
        end=$(date +%s.%N)
        elapsed=$(echo "$end - $start" | bc)
        echo "$elapsed" >> "$gateway_times"
    done
    
    local gateway_avg=$(awk '{sum+=$1; count++} END {printf "%.3f", sum/count}' "$gateway_times")
    print_metric "Average" "${gateway_avg}s"
    
    # Calculate overhead
    local overhead=$(echo "scale=2; ($gateway_avg - $backend_avg) * 1000" | bc)
    echo -e "\n${CYAN}Gateway Overhead:${NC} ${YELLOW}${overhead}ms${NC}"
    
    rm "$backend_times" "$gateway_times"
}

# Test 6: Load test
test_load() {
    print_header "Test 6: Sustained Load Test"
    
    local duration=30  # seconds
    local requests=0
    local errors=0
    local times_file=$(mktemp)
    
    print_info "Running sustained load for ${duration}s..."
    
    local start_time=$(date +%s)
    local end_time=$((start_time + duration))
    
    while [ $(date +%s) -lt $end_time ]; do
        start=$(date +%s.%N)
        if http --print= GET ${BACKEND_BASE}/${BOT_ID}/status > /dev/null 2>&1; then
            requests=$((requests + 1))
            end=$(date +%s.%N)
            elapsed=$(echo "$end - $start" | bc)
            echo "$elapsed" >> "$times_file"
        else
            errors=$((errors + 1))
        fi
        
        # Progress
        if [ $((requests % 10)) -eq 0 ]; then
            echo -n "."
        fi
    done
    echo ""
    
    local actual_duration=$(($(date +%s) - start_time))
    local throughput=$(echo "scale=2; $requests / $actual_duration" | bc)
    local error_rate=$(echo "scale=2; ($errors / ($requests + $errors)) * 100" | bc)
    
    print_metric "Total Requests" "$requests"
    print_metric "Errors" "$errors"
    print_metric "Error Rate" "${error_rate}%"
    print_metric "Duration" "${actual_duration}s"
    print_metric "Throughput" "${throughput} req/s"
    
    echo ""
    calculate_stats "$times_file"
    
    rm "$times_file"
}

# Test 7: Memory usage (via status endpoint)
test_memory_leak() {
    print_header "Test 7: Memory Leak Detection"
    
    print_info "Running repeated requests to detect memory issues..."
    
    local initial_mem=$(http --print=b GET ${BASE_URL}/actuator/metrics/jvm.memory.used | jq -r '.measurements[0].value')
    print_metric "Initial Memory" "$(echo "scale=2; $initial_mem / 1048576" | bc) MB"
    
    # Run many requests
    for i in $(seq 1 1000); do
        http --print= GET ${BACKEND_BASE}/${BOT_ID}/status > /dev/null 2>&1
        
        if [ $((i % 100)) -eq 0 ]; then
            echo -n "."
        fi
    done
    echo ""
    
    sleep 2  # Let GC run
    
    local final_mem=$(http --print=b GET ${BASE_URL}/actuator/metrics/jvm.memory.used | jq -r '.measurements[0].value')
    print_metric "Final Memory" "$(echo "scale=2; $final_mem / 1048576" | bc) MB"
    
    local mem_diff=$(echo "scale=2; ($final_mem - $initial_mem) / 1048576" | bc)
    
    if (( $(echo "$mem_diff > 50" | bc -l) )); then
        echo -e "${RED}⚠ Warning: Memory increased by ${mem_diff} MB${NC}"
    else
        print_success "Memory stable (diff: ${mem_diff} MB)"
    fi
}

# Main execution
main() {
    clear
    print_header "HTTPie Performance Testing Suite"
    
    check_dependencies
    
    # Trap to ensure cleanup
    trap cleanup EXIT
    
    setup
    
    if [ $# -eq 0 ]; then
        # Run all tests
        test_response_times
        test_all_endpoints
        test_concurrent_requests
        test_create_performance
        test_gateway_vs_backend
        test_load
        test_memory_leak
    else
        # Run specific test
        case "$1" in
            response)
                test_response_times
                ;;
            endpoints)
                test_all_endpoints
                ;;
            concurrent)
                test_concurrent_requests
                ;;
            create)
                test_create_performance
                ;;
            gateway)
                test_gateway_vs_backend
                ;;
            load)
                test_load
                ;;
            memory)
                test_memory_leak
                ;;
            *)
                echo "Usage: $0 [test]"
                echo ""
                echo "Available tests:"
                echo "  response    - Response time analysis"
                echo "  endpoints   - All endpoints performance"
                echo "  concurrent  - Concurrent requests"
                echo "  create      - Bot creation performance"
                echo "  gateway     - Gateway vs Backend comparison"
                echo "  load        - Sustained load test"
                echo "  memory      - Memory leak detection"
                echo ""
                echo "Run without arguments to execute all tests"
                exit 1
                ;;
        esac
    fi
    
    print_header "Performance Testing Complete"
}

main "$@"
