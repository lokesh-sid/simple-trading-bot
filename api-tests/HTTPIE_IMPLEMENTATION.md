# HTTPie Test Implementation - Complete Guide

## 📦 What's Included

### 1. **httpie-test-suite.sh** - Comprehensive Test Suite
   - Complete automated test coverage
   - All 15+ endpoints tested
   - Error scenario testing
   - Performance benchmarking
   - Parallel execution tests
   - Gateway resilience testing
   - Colored output with progress indicators

### 2. **httpie-quick-test.sh** - Interactive Quick Tester
   - Interactive menu for ad-hoc testing
   - Command-line interface for scripting
   - Single bot lifecycle testing
   - Individual endpoint testing
   - Error scenario testing
   - Gateway API testing

### 3. **httpie-performance-test.sh** - Load & Performance Testing
   - Response time analysis with statistics (avg, median, p95, p99)
   - All endpoints performance comparison
   - Concurrent request handling
   - Bot creation performance
   - Gateway vs Backend overhead measurement
   - Sustained load testing (30s duration)
   - Memory leak detection

### 4. **HTTPIE_EXAMPLES.md** - Complete Documentation
   - Installation instructions
   - Basic commands for all endpoints
   - Advanced features (sessions, streaming, offline mode)
   - Authentication examples
   - Error testing patterns
   - Integration with jq
   - Tips & tricks
   - Quick reference card

---

## 🚀 Quick Start

### Prerequisites
```bash
# Install HTTPie
brew install httpie

# Install jq (for JSON parsing)
brew install jq

# Verify installation
http --version
jq --version
```

### Run Tests
```bash
# 1. Interactive quick test
./api-tests/httpie-quick-test.sh

# 2. Comprehensive test suite
./api-tests/httpie-test-suite.sh

# 3. Performance benchmarks
./api-tests/httpie-performance-test.sh
```

---

## 📖 Usage Examples

### Quick Test (Interactive)
```bash
cd api-tests
./httpie-quick-test.sh

# Select from menu:
# 1. Complete lifecycle test
# 2. Create bot only
# 3. List all bots
# 4. Test existing bot
# 5. Error scenarios
# 6. Gateway API
# 7. Run all tests
```

### Quick Test (CLI)
```bash
# Create bot
./httpie-quick-test.sh create

# List all bots
./httpie-quick-test.sh list

# Get status
./httpie-quick-test.sh status <BOT_ID>

# Start bot
./httpie-quick-test.sh start <BOT_ID>

# Stop bot
./httpie-quick-test.sh stop <BOT_ID>

# Delete bot
./httpie-quick-test.sh delete <BOT_ID>

# Complete lifecycle
./httpie-quick-test.sh lifecycle

# Gateway test
./httpie-quick-test.sh gateway <BOT_ID>

# Error scenarios
./httpie-quick-test.sh errors
```

### Test Suite
```bash
# Run all tests
./httpie-test-suite.sh

# The suite will:
# 1. Check dependencies (HTTPie, jq)
# 2. Create test bot
# 3. Run all endpoint tests
# 4. Run error scenario tests
# 5. Run performance tests
# 6. Run parallel request tests
# 7. Run gateway tests
# 8. Cleanup test bot
```

### Performance Testing
```bash
# Run all performance tests
./httpie-performance-test.sh

# Run specific test
./httpie-performance-test.sh response     # Response time analysis
./httpie-performance-test.sh endpoints    # All endpoints
./httpie-performance-test.sh concurrent   # Concurrent requests
./httpie-performance-test.sh create       # Bot creation perf
./httpie-performance-test.sh gateway      # Gateway overhead
./httpie-performance-test.sh load         # Sustained load
./httpie-performance-test.sh memory       # Memory leak detection
```

---

## 🎯 Manual HTTPie Commands

### Basic Operations
```bash
# Create bot
http POST localhost:8080/api/bots

# List all
http GET localhost:8080/api/bots

# Get status
http GET localhost:8080/api/bots/{botId}/status

# Delete bot
http DELETE localhost:8080/api/bots/{botId}
```

### Bot Control
```bash
# Start (LONG position, paper trading)
http POST localhost:8080/api/bots/{botId}/start \
    direction=LONG \
    paper:=true

# Start (SHORT position, live trading)
http POST localhost:8080/api/bots/{botId}/start \
    direction=SHORT \
    paper:=false

# Stop
http PUT localhost:8080/api/bots/{botId}/stop
```

### Configuration
```bash
# Configure bot
http POST localhost:8080/api/bots/{botId}/configure \
    symbol=BTCUSDT \
    tradeAmount:=1000 \
    leverage:=5 \
    rsiOversoldThreshold:=30 \
    rsiOverboughtThreshold:=70 \
    trailingStopPercentage:=0.02

# Set leverage
http POST localhost:8080/api/bots/{botId}/leverage \
    symbol=BTCUSDT \
    leverage:=10

# Toggle sentiment
http POST localhost:8080/api/bots/{botId}/sentiment \
    enabled:=true
```

### Gateway API
```bash
# Via gateway
http GET localhost:8080/gateway/api/bots/{botId}/status

# Health check
http GET localhost:8080/gateway/health

# Metrics
http GET localhost:8080/gateway/api/resilience/metrics
http GET localhost:8080/gateway/api/resilience/circuit-breaker
http GET localhost:8080/gateway/api/resilience/retry
http GET localhost:8080/gateway/api/resilience/rate-limiters
```

---

## 📊 Test Output Examples

### Quick Test Output
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Creating Bot
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✓ Bot created: 550e8400-e29b-41d4-a716-446655440000

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Getting Bot Status: 550e8400-e29b-41d4-a716-446655440000
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

HTTP/1.1 200 OK
{
    "botId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "IDLE",
    "direction": null,
    "paper": false
}
```

### Performance Test Output
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Test 1: Response Time Analysis (GET /status)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

ℹ Running 100 requests...
..........

→ Total Requests: 100
→ Average Time: 0.045s
→ Median Time: 0.042s
→ Min Time: 0.028s
→ Max Time: 0.089s
→ 95th Percentile: 0.067s
→ 99th Percentile: 0.081s
```

---

## 🎨 HTTPie Features Used

### 1. **Simple JSON Syntax**
```bash
# String values (use =)
http POST ... key=value

# JSON values (use :=)
http POST ... number:=123 boolean:=true
```

### 2. **Colored Output**
- Syntax highlighting for JSON
- Colored HTTP status codes
- Pretty-printed responses

### 3. **Query Parameters**
```bash
# Use == for query params
http GET localhost:8080/api/bots status==RUNNING page==1
```

### 4. **Custom Headers**
```bash
http GET ... Header-Name:value
```

### 5. **Sessions (Persistent Headers)**
```bash
# Create session
http --session=trading POST ... Authorization:"Bearer token"

# Reuse session (headers automatically included)
http --session=trading GET ...
```

### 6. **Output Control**
```bash
# Verbose (show request & response)
http -v GET ...

# Print body only
http --print=b GET ...

# Print headers only
http --print=h GET ...

# Offline mode (show request without sending)
http --offline --print=HhBb POST ...
```

---

## 🔧 Advanced Patterns

### 1. **Extract and Chain**
```bash
# Create bot → Extract ID → Start it
http --print=b POST localhost:8080/api/bots | \
    jq -r '.botId' | \
    xargs -I {} http POST localhost:8080/api/bots/{}/start direction=LONG paper:=true
```

### 2. **Parallel Creation**
```bash
# Create 5 bots in parallel
for i in {1..5}; do
    http --print=b POST localhost:8080/api/bots > /tmp/bot_$i.json &
done
wait
```

### 3. **Error Testing**
```bash
# 404 - Not Found
http GET localhost:8080/api/bots/invalid-id/status

# 400 - Bad Request
http POST localhost:8080/api/bots/{botId}/leverage leverage:=-5

# 503 - Service Unavailable
http GET localhost:8080/gateway/api/bots/{botId}/status
```

### 4. **Performance Timing**
```bash
# Measure response time
time http GET localhost:8080/api/bots/{botId}/status

# Multiple iterations
for i in {1..10}; do
    time http GET localhost:8080/api/bots/{botId}/status 2>&1 | grep real
done
```

---

## 📁 File Structure

```
api-tests/
├── httpie-test-suite.sh           # Comprehensive automated tests
├── httpie-quick-test.sh            # Interactive/CLI quick tests
├── httpie-performance-test.sh      # Performance benchmarking
├── HTTPIE_EXAMPLES.md              # Complete documentation
├── HTTPIE_IMPLEMENTATION.md        # This file
├── backend-api-tests.http          # VS Code REST Client format
├── gateway-api-tests.http          # VS Code REST Client format
├── backend-api-test.sh             # cURL-based tests
├── gateway-api-test.sh             # cURL-based tests
├── run-all-tests.sh                # Master test runner
├── README.md                       # Main documentation
├── QUICK_REFERENCE.md              # Quick command reference
└── Trading-Bot-Multi-Bot-API.postman_collection.json
```

---

## 🎯 Use Cases

### Development Testing
```bash
# Quick manual test during development
./httpie-quick-test.sh create
./httpie-quick-test.sh start <BOT_ID>
./httpie-quick-test.sh status <BOT_ID>
```

### Automated Testing
```bash
# CI/CD pipeline
./httpie-test-suite.sh

# Performance regression testing
./httpie-performance-test.sh response
./httpie-performance-test.sh concurrent
```

### Load Testing
```bash
# Sustained load test
./httpie-performance-test.sh load

# Concurrent users simulation
./httpie-performance-test.sh concurrent
```

### Debugging
```bash
# Verbose output to see full request/response
http -v POST localhost:8080/api/bots/{botId}/start direction=LONG paper:=true

# Offline mode to check request format
http --offline --print=HhBb POST localhost:8080/api/bots/{botId}/configure \
    symbol=BTCUSDT leverage:=10
```

---

## ✅ Features Checklist

### Test Coverage
- ✅ All CRUD operations
- ✅ Bot lifecycle (create → configure → start → stop → delete)
- ✅ All configuration endpoints
- ✅ Gateway API
- ✅ Error scenarios (404, 400, 503)
- ✅ Performance testing
- ✅ Concurrent requests
- ✅ Memory leak detection

### HTTPie Features
- ✅ Simple JSON syntax
- ✅ Colored output
- ✅ Query parameters
- ✅ Custom headers
- ✅ Sessions (persistent headers)
- ✅ Verbose mode
- ✅ Output control
- ✅ Download mode
- ✅ Offline mode
- ✅ Streaming support

### Automation
- ✅ Fully automated test suite
- ✅ Interactive menu
- ✅ CLI commands
- ✅ Performance benchmarks
- ✅ Statistics (avg, median, p95, p99)
- ✅ Error handling
- ✅ Cleanup on exit
- ✅ Progress indicators

---

## 🚀 Next Steps

1. **Install HTTPie**:
   ```bash
   brew install httpie jq
   ```

2. **Run Quick Test**:
   ```bash
   ./api-tests/httpie-quick-test.sh
   ```

3. **Run Full Suite**:
   ```bash
   ./api-tests/httpie-test-suite.sh
   ```

4. **Performance Test**:
   ```bash
   ./api-tests/httpie-performance-test.sh
   ```

5. **Read Examples**:
   ```bash
   cat api-tests/HTTPIE_EXAMPLES.md
   ```

---

## 💡 Tips

1. **Use sessions** for APIs requiring authentication
2. **Use `--print=b`** to get just the response body
3. **Use `--offline`** to check request format without sending
4. **Pipe to jq** to extract specific fields
5. **Use `-v`** to debug request/response
6. **Use sessions** to persist headers across requests
7. **Combine with `time`** to measure response times
8. **Use `--download`** for large file downloads

---

## 🔗 Resources

- HTTPie Documentation: https://httpie.io/docs
- HTTPie CLI: https://httpie.io/cli
- jq Manual: https://stedolan.github.io/jq/manual/
- Examples: `api-tests/HTTPIE_EXAMPLES.md`
