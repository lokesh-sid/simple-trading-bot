# HTTPie Examples for Trading Bot API

Complete collection of HTTPie commands for testing the Trading Bot Multi-Bot API.

## 🚀 Quick Start

### Installation
```bash
# macOS
brew install httpie

# Linux
apt-get install httpie
sudo dnf install httpie

# Python/pip
pip install httpie

# Verify
http --version
```

---

## 📋 Basic Commands

### Create Bot
```bash
# Simple POST (no body needed)
http POST localhost:8080/api/bots

# With verbose output
http -v POST localhost:8080/api/bots

# Save response to extract botId
RESPONSE=$(http --print=b POST localhost:8080/api/bots)
BOT_ID=$(echo $RESPONSE | jq -r '.botId')
echo "Created: $BOT_ID"
```

### List All Bots
```bash
# GET request with pretty colors
http GET localhost:8080/api/bots

# Compact output
http --print=b GET localhost:8080/api/bots

# Save to file
http GET localhost:8080/api/bots > bots.json
```

### Get Bot Status
```bash
# Replace {botId} with actual ID
http GET localhost:8080/api/bots/{botId}/status

# With custom headers
http GET localhost:8080/api/bots/{botId}/status \
    Accept:application/json \
    User-Agent:TradingBotCLI/1.0
```

### Delete Bot
```bash
# DELETE request
http DELETE localhost:8080/api/bots/{botId}

# With confirmation
http -v DELETE localhost:8080/api/bots/{botId}
```

---

## 🎮 Bot Control

### Start Bot
```bash
# HTTPie JSON syntax (key=value for string, key:=value for JSON)
http POST localhost:8080/api/bots/{botId}/start \
    direction=LONG \
    paper:=true

# SHORT mode
http POST localhost:8080/api/bots/{botId}/start \
    direction=SHORT \
    paper:=false

# Using heredoc for complex JSON
http POST localhost:8080/api/bots/{botId}/start <<< '{
    "direction": "LONG",
    "paper": true,
    "initialBalance": 10000
}'

# From file
http POST localhost:8080/api/bots/{botId}/start < start-request.json
```

### Stop Bot
```bash
# PUT request (no body)
http PUT localhost:8080/api/bots/{botId}/stop

# Verbose
http -v PUT localhost:8080/api/bots/{botId}/stop
```

---

## ⚙️ Configuration

### Update Leverage
```bash
# Note: leverage:=10 (JSON number), not leverage=10 (string)
http POST localhost:8080/api/bots/{botId}/leverage \
    symbol=BTCUSDT \
    leverage:=10

# Multiple symbols
http POST localhost:8080/api/bots/{botId}/leverage \
    symbol=ETHUSDT \
    leverage:=5
```

### Configure Bot
```bash
# Complex configuration
http POST localhost:8080/api/bots/{botId}/configure \
    symbol=BTCUSDT \
    tradeAmount:=1000 \
    leverage:=5 \
    rsiOversoldThreshold:=30 \
    rsiOverboughtThreshold:=70 \
    trailingStopPercentage:=0.02

# With nested JSON
http POST localhost:8080/api/bots/{botId}/configure <<< '{
    "symbol": "BTCUSDT",
    "tradeAmount": 1000,
    "leverage": 5,
    "indicators": {
        "rsi": true,
        "macd": true,
        "bollinger": true
    }
}'
```

### Toggle Sentiment
```bash
# Enable (note :=true for boolean)
http POST localhost:8080/api/bots/{botId}/sentiment \
    enabled:=true

# Disable
http POST localhost:8080/api/bots/{botId}/sentiment \
    enabled:=false
```

---

## 🌐 Gateway API

### Through Gateway
```bash
# Create bot via gateway
http POST localhost:8080/gateway/api/bots \
    Authorization:"Bearer token" \
    X-Client-ID:my-app

# Start bot via gateway
http POST localhost:8080/gateway/api/bots/{botId}/start \
    direction=LONG \
    paper:=true \
    Authorization:"Bearer token"

# Get status via gateway
http GET localhost:8080/gateway/api/bots/{botId}/status \
    Authorization:"Bearer token"
```

### Gateway Health & Metrics
```bash
# Health check
http GET localhost:8080/gateway/health

# Gateway info
http GET localhost:8080/gateway/info

# Rate limiter metrics
http GET localhost:8080/gateway/api/resilience/rate-limiters

# Circuit breaker
http GET localhost:8080/gateway/api/resilience/circuit-breaker

# Retry metrics
http GET localhost:8080/gateway/api/resilience/retry

# All metrics
http GET localhost:8080/gateway/api/resilience/metrics
```

---

## 🔐 Authentication & Headers

### Bearer Token
```bash
http GET localhost:8080/api/bots/{botId}/status \
    Authorization:"Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

### Basic Auth
```bash
http -a username:password GET localhost:8080/api/bots
```

### API Key
```bash
http GET localhost:8080/api/bots \
    X-API-Key:sk_live_abc123def456
```

### Custom Headers
```bash
http GET localhost:8080/api/bots/{botId}/status \
    X-Request-ID:req-123456 \
    X-Client-IP:192.168.1.100 \
    Accept:application/json \
    User-Agent:TradingBotCLI/1.0
```

---

## 🎨 HTTPie Sessions (Persistent Headers)

### Create & Use Session
```bash
# Create session with headers
http --session=trading POST localhost:8080/api/bots \
    Authorization:"Bearer token" \
    X-Client-ID:my-app

# Session is saved to ~/.httpie/sessions/localhost_8080/trading.json

# Reuse session (headers automatically included)
http --session=trading GET localhost:8080/api/bots/{botId}/status

# Read-only session (don't update)
http --session-read-only=trading GET localhost:8080/api/bots
```

### Custom Session Location
```bash
# Save session to custom file
http --session=./my-session.json POST localhost:8080/api/bots

# Reuse custom session
http --session=./my-session.json GET localhost:8080/api/bots
```

---

## 📊 Output Control

### Verbose Mode
```bash
# Show request and response
http -v GET localhost:8080/api/bots

# Very verbose (with timings)
http -vv GET localhost:8080/api/bots
```

### Print Control
```bash
# Print specific parts (H=req headers, h=resp headers, B=req body, b=resp body)
http --print=HhBb POST localhost:8080/api/bots  # All
http --print=h GET localhost:8080/api/bots      # Response headers only
http --print=b GET localhost:8080/api/bots      # Response body only
http --print=Hh GET localhost:8080/api/bots     # Request & response headers

# Offline mode (show request without sending)
http --offline --print=HhBb POST localhost:8080/api/bots direction=LONG
```

### Pretty Printing
```bash
# Auto-format (default)
http GET localhost:8080/api/bots

# Force formatting
http --pretty=all GET localhost:8080/api/bots

# No formatting
http --pretty=none GET localhost:8080/api/bots

# Colors only
http --pretty=colors GET localhost:8080/api/bots
```

---

## 🔍 Query Parameters

### Using == for Query Params
```bash
# Single parameter
http GET localhost:8080/api/resilience/metrics format==json

# Multiple parameters
http GET localhost:8080/api/bots \
    status==RUNNING \
    direction==LONG \
    page==1 \
    size==20

# Mixed with headers
http GET localhost:8080/api/bots \
    status==RUNNING \
    Accept:application/json
```

---

## 📥 Download & Save

### Download Files
```bash
# Download mode (shows progress)
http --download GET localhost:8080/api/bots/{botId}/export

# Custom filename
http --download --output=bot-export.json GET localhost:8080/api/bots/{botId}/export

# Resume download
http --download --continue GET localhost:8080/api/large-file
```

### Save to File
```bash
# Redirect output
http GET localhost:8080/api/bots > bots.json

# Using output flag
http --output=status.json GET localhost:8080/api/bots/{botId}/status
```

---

## 🔄 Streaming

### Stream Responses
```bash
# Stream mode (don't buffer)
http --stream GET localhost:8080/api/bots/{botId}/logs

# Server-Sent Events
http --stream GET localhost:8080/api/events

# WebSocket-like streaming
http --stream GET localhost:8080/api/realtime-prices
```

---

## 🧪 Testing Scenarios

### Complete Bot Lifecycle
```bash
#!/bin/bash

# 1. Create
RESPONSE=$(http --print=b POST localhost:8080/api/bots)
BOT_ID=$(echo $RESPONSE | jq -r '.botId')
echo "Created: $BOT_ID"

# 2. Configure
http POST localhost:8080/api/bots/$BOT_ID/configure \
    symbol=BTCUSDT \
    leverage:=10 > /dev/null

# 3. Start
http POST localhost:8080/api/bots/$BOT_ID/start \
    direction=LONG \
    paper:=true > /dev/null

# 4. Check status
http GET localhost:8080/api/bots/$BOT_ID/status

# 5. Stop
http PUT localhost:8080/api/bots/$BOT_ID/stop > /dev/null

# 6. Delete
http DELETE localhost:8080/api/bots/$BOT_ID
```

### Multi-Bot Testing
```bash
# Create 3 bots in parallel
for i in {1..3}; do
    http --print=b POST localhost:8080/api/bots > /tmp/bot_$i.json &
done
wait

# Start them with different strategies
BOT_1=$(jq -r '.botId' /tmp/bot_1.json)
BOT_2=$(jq -r '.botId' /tmp/bot_2.json)
BOT_3=$(jq -r '.botId' /tmp/bot_3.json)

http POST localhost:8080/api/bots/$BOT_1/start direction=LONG paper:=true
http POST localhost:8080/api/bots/$BOT_2/start direction=SHORT paper:=true
http POST localhost:8080/api/bots/$BOT_3/start direction=LONG paper:=false

# Check all statuses
for bot in $BOT_1 $BOT_2 $BOT_3; do
    http GET localhost:8080/api/bots/$bot/status | jq '{botId, status, direction}'
done
```

### Error Testing
```bash
# 404 Not Found
http GET localhost:8080/api/bots/invalid-id/status

# 400 Bad Request
http POST localhost:8080/api/bots/{botId}/leverage \
    leverage:=-5

# 503 Service Unavailable (gateway)
# Stop backend first, then:
http GET localhost:8080/gateway/api/bots/{botId}/status
```

---

## 🔧 Advanced Features

### Timeout
```bash
# 10 second timeout
http --timeout=10 GET localhost:8080/api/bots/{botId}/status
```

### Follow Redirects
```bash
http --follow GET localhost:8080/api/redirect
```

### Ignore SSL
```bash
http --verify=no GET https://localhost:8443/api/bots
```

### Form Data
```bash
# URL-encoded form
http --form POST localhost:8080/api/upload \
    file@./config.json \
    description="Bot config"

# Multipart form
http -f POST localhost:8080/api/import \
    config@./bot-config.json
```

### Custom Method
```bash
# PATCH request
http PATCH localhost:8080/api/bots/{botId} \
    status=PAUSED

# OPTIONS request
http OPTIONS localhost:8080/api/bots
```

---

## 🎯 Integration with jq

### Extract Fields
```bash
# Get botId only
http GET localhost:8080/api/bots | jq '.botIds[]'

# Get status field
http GET localhost:8080/api/bots/{botId}/status | jq '.status'

# Filter running bots
http GET localhost:8080/api/bots | jq '.[] | select(.status == "RUNNING")'

# Transform output
http GET localhost:8080/api/bots/{botId}/status | jq '{id: .botId, state: .status}'
```

### Pipeline
```bash
# Create → Extract ID → Start
http --print=b POST localhost:8080/api/bots | \
    jq -r '.botId' | \
    xargs -I {} http POST localhost:8080/api/bots/{}/start direction=LONG paper:=true
```

---

## 📈 Performance Testing

### Timing
```bash
# Measure time
time http GET localhost:8080/api/bots

# Multiple requests
for i in {1..10}; do
    time http GET localhost:8080/api/bots/{botId}/status
done
```

### Load Testing (with httpstat)
```bash
# Install httpstat
pip install httpstat

# Detailed timing breakdown
httpstat http://localhost:8080/api/bots
```

---

## 🎨 Customization

### Config File (`~/.httpie/config.json`)
```json
{
  "default_options": [
    "--style=monokai",
    "--print=hb",
    "--timeout=30",
    "--follow"
  ],
  "__meta__": {
    "httpie": "3.2.2"
  }
}
```

### Color Themes
```bash
# Available styles
http --style=monokai GET localhost:8080/api/bots
http --style=solarized GET localhost:8080/api/bots
http --style=fruity GET localhost:8080/api/bots
http --style=native GET localhost:8080/api/bots
```

---

## 📝 Quick Reference Card

```bash
# SYNTAX
http [METHOD] [URL] [REQUEST_ITEM ...]

# REQUEST_ITEM Types
key=value          # JSON string
key:=value         # JSON number/boolean/null
key==value         # Query parameter
Header:value       # Custom header
name@file.txt      # File upload

# Common Flags
-v, --verbose      # Show request and response
-h, --headers      # Print headers only
-b, --body         # Print body only
-d, --download     # Download mode
-f, --form         # Form data
-j, --json         # Force JSON
--session=name     # Use session
--timeout=sec      # Request timeout
--follow           # Follow redirects
--print=HhBb       # Control output
```

---

## 🚀 Run Test Suite

```bash
# Make executable
chmod +x api-tests/httpie-test-suite.sh

# Run all HTTPie tests
./api-tests/httpie-test-suite.sh
```

---

## 💡 Tips & Tricks

1. **String vs JSON**: Use `=` for strings, `:=` for JSON values
2. **Query params**: Use `==` for URL query parameters
3. **Sessions**: Great for APIs requiring authentication
4. **Pretty print**: Automatic syntax highlighting and formatting
5. **Pipe to jq**: Extract specific fields from JSON responses
6. **Verbose mode**: Use `-v` to debug request/response
7. **Offline mode**: Test request without sending with `--offline`
8. **Download mode**: Use `--download` for large files

---

## 🔗 Resources

- HTTPie Docs: https://httpie.io/docs
- HTTPie CLI: https://httpie.io/cli
- HTTPie Sessions: https://httpie.io/docs/cli/sessions
- jq Manual: https://stedolan.github.io/jq/manual/
