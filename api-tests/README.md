# Trading Bot API Tests

This directory contains comprehensive test suites for the Trading Bot Multi-Bot API, including both backend and gateway endpoints.

## 📁 Test Files

### HTTP Request Files (VS Code REST Client / IntelliJ HTTP Client)
- **`backend-api-tests.http`** - Backend API endpoint tests
- **`gateway-api-tests.http`** - API Gateway endpoint tests

### Shell Scripts (Command Line)

#### cURL-Based Scripts
- **`backend-api-test.sh`** - Automated backend API test script (cURL)
- **`gateway-api-test.sh`** - Automated gateway API test script (cURL)
- **`run-all-tests.sh`** - Master test runner (all tests)

#### HTTPie-Based Scripts (Recommended)
- **`httpie-test-suite.sh`** - Comprehensive automated test suite
- **`httpie-quick-test.sh`** - Interactive/CLI quick tester
- **`httpie-performance-test.sh`** - Performance & load testing
- **`HTTPIE_EXAMPLES.md`** - Complete HTTPie documentation
- **`HTTPIE_IMPLEMENTATION.md`** - Implementation guide

### Postman Collection
- **`Trading-Bot-Multi-Bot-API.postman_collection.json`** - Complete Postman collection

## 🚀 Quick Start

### Using HTTPie (Recommended - Beautiful CLI)

#### Install
```bash
# macOS
brew install httpie jq

# Verify
http --version
```

#### Quick Test
```bash
# Interactive menu
./httpie-quick-test.sh

# Create bot
./httpie-quick-test.sh create

# List bots
./httpie-quick-test.sh list

# Complete lifecycle
./httpie-quick-test.sh lifecycle
```

#### Full Test Suite
```bash
# Run all automated tests
./httpie-test-suite.sh
```

#### Performance Testing
```bash
# All performance tests
./httpie-performance-test.sh

# Specific test
./httpie-performance-test.sh response     # Response times
./httpie-performance-test.sh concurrent   # Concurrent requests
./httpie-performance-test.sh load         # Sustained load
```

#### Manual HTTPie Commands
```bash
# Create bot
http POST localhost:8080/api/bots

# Start bot (note: direction=LONG is string, paper:=true is JSON boolean)
http POST localhost:8080/api/bots/{botId}/start \
    direction=LONG \
    paper:=true

# Get status
http GET localhost:8080/api/bots/{botId}/status

# Configure
http POST localhost:8080/api/bots/{botId}/configure \
    symbol=BTCUSDT \
    leverage:=10 \
    tradeAmount:=1000

# Delete
http DELETE localhost:8080/api/bots/{botId}
```

See **`HTTPIE_EXAMPLES.md`** for complete HTTPie documentation.

---

### Using VS Code REST Client Extension

1. Install the [REST Client](https://marketplace.visualstudio.com/items?itemName=humao.rest-client) extension
2. Open `backend-api-tests.http` or `gateway-api-tests.http`
3. Click "Send Request" above any request
4. View response in the side panel

### Using IntelliJ IDEA / WebStorm

1. Open `.http` files directly (built-in support)
2. Click the green play button next to any request
3. View response in the editor

### Using Shell Scripts (cURL)

#### Backend API Tests
```bash
# Make script executable
chmod +x backend-api-test.sh

# Run all backend tests
./backend-api-test.sh
```

#### Gateway API Tests
```bash
# Make script executable
chmod +x gateway-api-test.sh

# Run all gateway tests
./gateway-api-test.sh
```

### Using Postman

1. Open Postman
2. Import `Trading-Bot-Multi-Bot-API.postman_collection.json`
3. Set `baseUrl` variable to `http://localhost:8080`
4. Run individual requests or entire collection

### Using cURL

```bash
# Create a bot
curl -X POST http://localhost:8080/api/bots

# Start a bot (replace {botId} with actual ID)
curl -X POST http://localhost:8080/api/bots/{botId}/start \
  -H "Content-Type: application/json" \
  -d '{"direction": "LONG", "paper": true}'

# Get bot status
curl http://localhost:8080/api/bots/{botId}/status

# List all bots
curl http://localhost:8080/api/bots

# Stop a bot
curl -X PUT http://localhost:8080/api/bots/{botId}/stop

# Delete a bot
curl -X DELETE http://localhost:8080/api/bots/{botId}
```

## 📋 Test Coverage

### Backend API Endpoints

#### Bot Management
- ✅ `POST /api/bots` - Create new bot
- ✅ `GET /api/bots` - List all bots
- ✅ `DELETE /api/bots/{botId}` - Delete bot

#### Bot Operations
- ✅ `POST /api/bots/{botId}/start` - Start bot
- ✅ `PUT /api/bots/{botId}/stop` - Stop bot
- ✅ `GET /api/bots/{botId}/status` - Get status

#### Bot Configuration
- ✅ `POST /api/bots/{botId}/configure` - Update configuration
- ✅ `POST /api/bots/{botId}/leverage` - Update leverage
- ✅ `POST /api/bots/{botId}/sentiment` - Toggle sentiment analysis

### Gateway API Endpoints

#### Bot Management (via Gateway)
- ✅ `POST /gateway/api/bots` - Create bot
- ✅ `GET /gateway/api/bots` - List bots
- ✅ `DELETE /gateway/api/bots/{botId}` - Delete bot

#### Bot Operations (via Gateway)
- ✅ `POST /gateway/api/bots/{botId}/start` - Start bot
- ✅ `PUT /gateway/api/bots/{botId}/stop` - Stop bot
- ✅ `GET /gateway/api/bots/{botId}/status` - Get status

#### Gateway Health
- ✅ `GET /gateway/health` - Gateway health check
- ✅ `GET /gateway/info` - Gateway information

#### Resilience Metrics
- ✅ `GET /gateway/api/resilience/rate-limiters` - Rate limiter metrics
- ✅ `GET /gateway/api/resilience/circuit-breaker` - Circuit breaker metrics
- ✅ `GET /gateway/api/resilience/retry` - Retry metrics

## 🧪 Test Scenarios

### 1. Single Bot Lifecycle
```
1. Create bot → Get botId
2. Start bot (LONG/Paper)
3. Get status
4. Update leverage
5. Enable sentiment
6. Stop bot
7. Delete bot
```

### 2. Multi-Bot Management
```
1. Create Bot 1
2. Create Bot 2
3. Create Bot 3
4. Start Bot 1 (LONG)
5. Start Bot 2 (SHORT)
6. List all bots
7. Check individual statuses
8. Stop and delete bots
```

### 3. Error Handling
```
1. Get status of non-existent bot → 404
2. Start non-existent bot → 404
3. Invalid leverage value → 400
4. Invalid direction → 400
5. Missing required fields → 400
```

### 4. Gateway Resilience
```
1. Rate limiting (100 req/min)
2. Circuit breaker (50% failure)
3. Retry logic (3 attempts)
4. Fallback responses
```

## 📊 Expected Responses

### Create Bot
```json
{
  "botId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "message": "Trading bot created successfully"
}
```

### List Bots
```json
{
  "botIds": ["bot-id-1", "bot-id-2", "bot-id-3"],
  "count": 3
}
```

### Bot Status
```json
{
  "running": true,
  "symbol": "BTCUSDT",
  "positionStatus": "LONG_POSITION",
  "entryPrice": 50000.0,
  "leverage": 10,
  "sentimentEnabled": true,
  "statusMessage": "Bot is running"
}
```

### Error Response
```json
{
  "code": "BOT_NOT_FOUND",
  "message": "Trading bot not found with ID: invalid-id",
  "details": "404 NOT_FOUND"
}
```

## 🔧 Configuration

### Backend Base URL
```
http://localhost:8080
```

### Gateway Base URL
```
http://localhost:8080/gateway
```

### Required Headers (Gateway)
```
Content-Type: application/json
X-Client-ID: <your-client-id>
Authorization: Bearer <token>
```

## 🎯 Testing Tips

### 1. Replace Placeholders
- Replace `{botId}` with actual bot ID from create response
- Replace `{bot1-id}`, `{bot2-id}` with respective IDs

### 2. Order Matters
- Create bot before starting it
- Start bot before getting status
- Stop bot before deleting

### 3. Gateway Headers
- `X-Client-ID` is used for rate limiting per client
- `Authorization` header is optional (depends on security config)

### 4. Rate Limiting
- Gateway allows 100 requests per minute per client
- Use different `X-Client-ID` values to test independently

### 5. Circuit Breaker
- Stop backend to test circuit breaker
- After 50% failure rate, circuit opens
- Subsequent requests get immediate fallback response

## 📈 Performance Testing

### Load Testing with Apache Bench
```bash
# Create 100 bots
ab -n 100 -c 10 -m POST http://localhost:8080/api/bots

# Get status 1000 times
ab -n 1000 -c 50 http://localhost:8080/api/bots/{botId}/status
```

### Stress Testing Gateway
```bash
# Test rate limiter
for i in {1..150}; do
  curl -s http://localhost:8080/gateway/api/bots
done
```

## 🐛 Troubleshooting

### Bot Not Found (404)
- Verify bot was created successfully
- Check botId is correct
- Ensure bot wasn't deleted

### Invalid Request (400)
- Check request body format
- Verify required fields present
- Validate field values (e.g., leverage > 0)

### Service Unavailable (503)
- Check backend is running
- Verify circuit breaker state
- Check rate limit not exceeded

### Gateway Timeout
- Increase timeout in gateway config
- Check backend response time
- Verify network connectivity

## 📝 Notes

- All bot IDs are UUIDs generated by the system
- Paper trading mode doesn't require Binance API credentials
- Live trading requires valid API keys in configuration
- Multiple bots can run concurrently with different strategies
- Each bot operates independently in its own thread

## 🔗 Related Documentation

- **`HTTPIE_EXAMPLES.md`** - Complete HTTPie usage guide
- **`HTTPIE_IMPLEMENTATION.md`** - HTTPie test suite implementation
- **`QUICK_REFERENCE.md`** - Quick command reference
- [Multi-Bot Refactoring Summary](../MULTI_BOT_REFACTORING_SUMMARY.md)
- [Gateway Update Summary](../GATEWAY_MULTI_BOT_UPDATE_SUMMARY.md)
- [Architecture Diagram](../GATEWAY_ARCHITECTURE_DIAGRAM.md)
- [API Documentation](http://localhost:8080/swagger-ui.html)

## 🎯 Which Tool to Use?

### HTTPie (Recommended for CLI)
**Best for:**
- Beautiful colored output
- Quick manual testing
- Automated test scripts
- CI/CD pipelines
- Performance testing

**Pros:**
- Simple syntax (no complex escaping)
- Automatic JSON formatting
- Colored output
- Sessions for persistent headers

**Cons:**
- Requires installation

### VS Code REST Client
**Best for:**
- IDE integration
- Save & version control requests
- Quick testing during development

**Pros:**
- No CLI needed
- Save requests in `.http` files
- Variables support

### Postman
**Best for:**
- Teams collaboration
- Complex test scenarios
- Non-developers

**Pros:**
- GUI interface
- Collection sharing
- Advanced features

### cURL
**Best for:**
- Systems without HTTPie
- Documentation examples
- Maximum compatibility

**Pros:**
- Pre-installed everywhere
- Industry standard

**Cons:**
- Complex syntax
- Verbose commands
- No colored output

## ✅ Checklist

Before running tests, ensure:
- [ ] Application is running on port 8080
- [ ] Database is accessible (if required)
- [ ] API keys configured (for live trading tests)
- [ ] All dependencies installed
- [ ] No port conflicts

## 🎉 Quick Test

Run this to verify everything works:

```bash
# Quick smoke test
curl -X POST http://localhost:8080/api/bots && \
curl http://localhost:8080/api/bots && \
curl http://localhost:8080/gateway/health

# Should see:
# 1. Bot created with ID
# 2. List with 1 bot
# 3. Gateway health OK
```

Happy Testing! 🚀
