#!/bin/bash

# HTTPie Quick Start Guide
# Interactive guide to get started with HTTPie testing

# Colors
BLUE='\033[0;34m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m'

clear

echo -e "${BLUE}"
cat << "EOF"
╔══════════════════════════════════════════════════════════════════════╗
║                                                                      ║
║              🚀 HTTPie Quick Start Guide 🚀                         ║
║                                                                      ║
║              Trading Bot Multi-Bot API Testing                       ║
║                                                                      ║
╚══════════════════════════════════════════════════════════════════════╝
EOF
echo -e "${NC}\n"

# Step 1: Check if HTTPie is installed
echo -e "${YELLOW}Step 1: Checking HTTPie installation...${NC}"
if command -v http &> /dev/null; then
    VERSION=$(http --version | head -1)
    echo -e "${GREEN}✓${NC} HTTPie is installed: $VERSION"
else
    echo -e "${RED}✗${NC} HTTPie is not installed"
    echo ""
    echo -e "${CYAN}Install HTTPie:${NC}"
    echo "  macOS:    brew install httpie"
    echo "  Ubuntu:   apt-get install httpie"
    echo "  Fedora:   dnf install httpie"
    echo "  Python:   pip install httpie"
    echo ""
    echo -e "${YELLOW}After installation, run this script again.${NC}"
    exit 1
fi

# Step 2: Check jq
echo -e "\n${YELLOW}Step 2: Checking jq installation...${NC}"
if command -v jq &> /dev/null; then
    VERSION=$(jq --version)
    echo -e "${GREEN}✓${NC} jq is installed: $VERSION"
else
    echo -e "${RED}✗${NC} jq is not installed"
    echo ""
    echo -e "${CYAN}Install jq:${NC}"
    echo "  macOS:    brew install jq"
    echo "  Ubuntu:   apt-get install jq"
    echo "  Fedora:   dnf install jq"
    echo ""
    echo -e "${YELLOW}jq is optional but recommended for JSON parsing.${NC}"
fi

# Step 3: Check if server is running
echo -e "\n${YELLOW}Step 3: Checking if server is running...${NC}"
if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} Server is running on http://localhost:8080"
else
    echo -e "${RED}✗${NC} Server is not running on http://localhost:8080"
    echo ""
    echo -e "${CYAN}Start the server:${NC}"
    echo "  cd /path/to/simple-trading-bot"
    echo "  ./gradlew bootRun"
    echo ""
    echo -e "${YELLOW}After starting the server, run this script again.${NC}"
    exit 1
fi

# Step 4: Show available scripts
echo -e "\n${YELLOW}Step 4: Available HTTPie test scripts:${NC}\n"

echo -e "${GREEN}1. httpie-test-suite.sh${NC}"
echo "   Comprehensive automated test suite"
echo "   • All endpoints tested"
echo "   • Error scenarios"
echo "   • Performance tests"
echo "   • Parallel requests"
echo "   Usage: ./httpie-test-suite.sh"
echo ""

echo -e "${GREEN}2. httpie-quick-test.sh${NC}"
echo "   Interactive/CLI quick tester"
echo "   • Interactive menu"
echo "   • Individual endpoint tests"
echo "   • Bot lifecycle testing"
echo "   Usage: ./httpie-quick-test.sh [command]"
echo ""

echo -e "${GREEN}3. httpie-performance-test.sh${NC}"
echo "   Performance & load testing"
echo "   • Response time analysis"
echo "   • Concurrent requests"
echo "   • Gateway overhead"
echo "   Usage: ./httpie-performance-test.sh [test]"
echo ""

echo -e "${GREEN}4. httpie-vs-curl-comparison.sh${NC}"
echo "   Visual comparison of HTTPie vs cURL"
echo "   • Side-by-side examples"
echo "   • Syntax comparison"
echo "   Usage: ./httpie-vs-curl-comparison.sh"
echo ""

# Step 5: Quick examples
echo -e "${YELLOW}Step 5: Quick HTTPie Examples:${NC}\n"

echo -e "${CYAN}Create a bot:${NC}"
echo "  http POST localhost:8080/api/bots"
echo ""

echo -e "${CYAN}List all bots:${NC}"
echo "  http GET localhost:8080/api/bots"
echo ""

echo -e "${CYAN}Start a bot:${NC}"
echo "  http POST localhost:8080/api/bots/{botId}/start direction=LONG paper:=true"
echo ""

echo -e "${CYAN}Get bot status:${NC}"
echo "  http GET localhost:8080/api/bots/{botId}/status"
echo ""

echo -e "${CYAN}Configure bot:${NC}"
echo "  http POST localhost:8080/api/bots/{botId}/configure \\"
echo "      symbol=BTCUSDT \\"
echo "      leverage:=10 \\"
echo "      tradeAmount:=1000"
echo ""

# Step 6: Interactive menu
echo -e "${YELLOW}Step 6: What would you like to do?${NC}\n"
echo "  1. Run quick test (interactive menu)"
echo "  2. Run full test suite"
echo "  3. Run performance tests"
echo "  4. See HTTPie vs cURL comparison"
echo "  5. Show HTTPie examples"
echo "  6. Create a test bot manually"
echo "  7. Exit"
echo ""

read -p "Enter choice [1-7]: " choice

case $choice in
    1)
        echo -e "\n${GREEN}Running quick test...${NC}\n"
        ./httpie-quick-test.sh
        ;;
    2)
        echo -e "\n${GREEN}Running full test suite...${NC}\n"
        ./httpie-test-suite.sh
        ;;
    3)
        echo -e "\n${GREEN}Running performance tests...${NC}\n"
        ./httpie-performance-test.sh
        ;;
    4)
        echo -e "\n${GREEN}Showing HTTPie vs cURL comparison...${NC}\n"
        ./httpie-vs-curl-comparison.sh
        ;;
    5)
        echo -e "\n${GREEN}Opening HTTPie examples...${NC}\n"
        if command -v bat &> /dev/null; then
            bat HTTPIE_EXAMPLES.md
        elif command -v less &> /dev/null; then
            less HTTPIE_EXAMPLES.md
        else
            cat HTTPIE_EXAMPLES.md
        fi
        ;;
    6)
        echo -e "\n${GREEN}Creating a test bot...${NC}\n"
        echo -e "${CYAN}Command:${NC} http POST localhost:8080/api/bots"
        echo ""
        
        RESPONSE=$(http --print=b POST localhost:8080/api/bots 2>/dev/null)
        
        if [ $? -eq 0 ]; then
            echo -e "${GREEN}✓ Bot created successfully!${NC}\n"
            echo "$RESPONSE" | jq '.' 2>/dev/null || echo "$RESPONSE"
            
            if command -v jq &> /dev/null; then
                BOT_ID=$(echo "$RESPONSE" | jq -r '.botId')
                echo ""
                echo -e "${CYAN}Next steps:${NC}"
                echo "  • Get status:  http GET localhost:8080/api/bots/$BOT_ID/status"
                echo "  • Start bot:   http POST localhost:8080/api/bots/$BOT_ID/start direction=LONG paper:=true"
                echo "  • Configure:   http POST localhost:8080/api/bots/$BOT_ID/configure symbol=BTCUSDT leverage:=10"
                echo "  • Stop bot:    http PUT localhost:8080/api/bots/$BOT_ID/stop"
                echo "  • Delete bot:  http DELETE localhost:8080/api/bots/$BOT_ID"
            fi
        else
            echo -e "${RED}✗ Failed to create bot${NC}"
        fi
        ;;
    7)
        echo -e "\n${CYAN}Goodbye!${NC}\n"
        exit 0
        ;;
    *)
        echo -e "\n${RED}Invalid choice${NC}\n"
        exit 1
        ;;
esac

echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}📚 Documentation:${NC}"
echo "  • HTTPIE_EXAMPLES.md - Complete HTTPie guide"
echo "  • HTTPIE_IMPLEMENTATION.md - Implementation details"
echo "  • HTTPIE_TEST_IMPLEMENTATION_SUMMARY.md - Summary"
echo "  • README.md - Main documentation"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
