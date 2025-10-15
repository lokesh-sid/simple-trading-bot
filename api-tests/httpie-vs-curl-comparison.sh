#!/bin/bash

# HTTPie vs cURL Side-by-Side Comparison
# Visual examples showing the difference

# Colors
BLUE='\033[0;34m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m'

print_header() {
    echo -e "\n${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

print_httpie() {
    echo -e "${GREEN}HTTPie:${NC}"
    echo -e "${CYAN}$1${NC}"
}

print_curl() {
    echo -e "${YELLOW}cURL:${NC}"
    echo -e "${RED}$1${NC}"
}

clear

echo -e "${BLUE}╔════════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║${NC}          ${GREEN}HTTPie vs cURL${NC} - Side-by-Side Comparison              ${BLUE}║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════════════╝${NC}"

# Example 1: Simple GET
print_header "1. Simple GET Request"
print_httpie "http GET localhost:8080/api/bots"
print_curl "curl http://localhost:8080/api/bots"
echo -e "Winner: ${GREEN}Tie${NC} (both simple)"

# Example 2: GET with headers
print_header "2. GET with Custom Headers"
print_httpie "http GET localhost:8080/api/bots \\
    Authorization:\"Bearer token\" \\
    X-Client-ID:my-app"
print_curl "curl http://localhost:8080/api/bots \\
  -H \"Authorization: Bearer token\" \\
  -H \"X-Client-ID: my-app\""
echo -e "Winner: ${GREEN}HTTPie${NC} (cleaner syntax)"

# Example 3: POST with JSON
print_header "3. POST with JSON Data"
print_httpie "http POST localhost:8080/api/bots/{botId}/start \\
    direction=LONG \\
    paper:=true"
print_curl "curl -X POST http://localhost:8080/api/bots/{botId}/start \\
  -H \"Content-Type: application/json\" \\
  -d '{\"direction\":\"LONG\",\"paper\":true}'"
echo -e "Winner: ${GREEN}HTTPie${NC} (no escaping needed!)"

# Example 4: Complex JSON
print_header "4. POST with Complex JSON"
print_httpie "http POST localhost:8080/api/bots/{botId}/configure \\
    symbol=BTCUSDT \\
    tradeAmount:=1000 \\
    leverage:=5 \\
    rsiOversoldThreshold:=30 \\
    rsiOverboughtThreshold:=70 \\
    trailingStopPercentage:=0.02"
print_curl "curl -X POST http://localhost:8080/api/bots/{botId}/configure \\
  -H \"Content-Type: application/json\" \\
  -d '{
    \"symbol\":\"BTCUSDT\",
    \"tradeAmount\":1000,
    \"leverage\":5,
    \"rsiOversoldThreshold\":30,
    \"rsiOverboughtThreshold\":70,
    \"trailingStopPercentage\":0.02
  }'"
echo -e "Winner: ${GREEN}HTTPie${NC} (much cleaner!)"

# Example 5: Query Parameters
print_header "5. GET with Query Parameters"
print_httpie "http GET localhost:8080/api/bots \\
    status==RUNNING \\
    direction==LONG \\
    page==1"
print_curl "curl \"http://localhost:8080/api/bots?status=RUNNING&direction=LONG&page=1\""
echo -e "Winner: ${GREEN}HTTPie${NC} (no manual URL encoding)"

# Example 6: Verbose output
print_header "6. Verbose Mode (Show Request & Response)"
print_httpie "http -v GET localhost:8080/api/bots"
print_curl "curl -v http://localhost:8080/api/bots"
echo -e "Winner: ${GREEN}Tie${NC} (both use -v)"

# Example 7: Print body only
print_header "7. Print Response Body Only"
print_httpie "http --print=b GET localhost:8080/api/bots"
print_curl "curl -s http://localhost:8080/api/bots"
echo -e "Winner: ${GREEN}HTTPie${NC} (explicit control)"

# Example 8: Sessions
print_header "8. Persistent Headers (Sessions)"
print_httpie "# Create session
http --session=trading POST ... Authorization:\"Bearer token\"

# Reuse session (headers auto-included)
http --session=trading GET ..."
print_curl "# Save cookies
curl -c cookies.txt -H \"Authorization: Bearer token\" ...

# Reuse cookies
curl -b cookies.txt ..."
echo -e "Winner: ${GREEN}HTTPie${NC} (built-in sessions)"

# Example 9: Extract & Chain
print_header "9. Extract JSON & Chain Commands"
print_httpie "http --print=b POST localhost:8080/api/bots | \\
    jq -r '.botId' | \\
    xargs -I {} http POST localhost:8080/api/bots/{}/start direction=LONG paper:=true"
print_curl "curl -s -X POST http://localhost:8080/api/bots | \\
    jq -r '.botId' | \\
    xargs -I {} curl -X POST http://localhost:8080/api/bots/{}/start \\
      -H \"Content-Type: application/json\" \\
      -d '{\"direction\":\"LONG\",\"paper\":true}'"
echo -e "Winner: ${GREEN}HTTPie${NC} (simpler)"

# Example 10: Offline mode
print_header "10. Test Request Format (Without Sending)"
print_httpie "http --offline --print=HhBb POST localhost:8080/api/bots/{botId}/configure \\
    symbol=BTCUSDT leverage:=10"
print_curl "# No built-in offline mode
# Must use --trace or -v and cancel manually"
echo -e "Winner: ${GREEN}HTTPie${NC} (has offline mode)"

# Summary
print_header "📊 Summary"
echo -e "${GREEN}HTTPie Wins:${NC} 8/10"
echo -e "${YELLOW}cURL Wins:${NC} 0/10"
echo -e "${CYAN}Tie:${NC} 2/10"

echo ""
echo -e "${GREEN}✅ HTTPie Advantages:${NC}"
echo "  • Simpler JSON syntax (no escaping)"
echo "  • Colored, pretty-printed output"
echo "  • Built-in sessions"
echo "  • Offline mode"
echo "  • Cleaner header syntax"
echo "  • Automatic Content-Type"
echo "  • Better query parameter handling"

echo ""
echo -e "${YELLOW}✅ cURL Advantages:${NC}"
echo "  • Pre-installed everywhere"
echo "  • Industry standard"
echo "  • Slightly faster"
echo "  • More protocol support"
echo "  • Better for production scripts"

echo ""
echo -e "${CYAN}💡 Recommendation:${NC}"
echo "  • Use HTTPie for: Development, Testing, CLI interaction"
echo "  • Use cURL for: Production scripts, CI/CD, Containers"

echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
