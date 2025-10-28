#!/bin/bash

###############################################################################
# Agentic AI Trading Bot - Shutdown Script
# This script stops all running components
###############################################################################

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║     Agentic AI Trading Bot - Shutdown Script              ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Stop Java application
if [ -f "application.pid" ]; then
    PID=$(cat application.pid)
    if ps -p $PID > /dev/null 2>&1; then
        echo -e "${YELLOW}Stopping application (PID: $PID)...${NC}"
        kill $PID
        sleep 2
        
        # Force kill if still running
        if ps -p $PID > /dev/null 2>&1; then
            echo -e "${YELLOW}Force killing application...${NC}"
            kill -9 $PID
        fi
        
        rm application.pid
        echo -e "${GREEN}✓ Application stopped${NC}"
    else
        echo -e "${YELLOW}⚠ Application not running${NC}"
        rm application.pid
    fi
else
    echo -e "${YELLOW}⚠ No PID file found, attempting to kill process on port 8080...${NC}"
    if lsof -ti:8080 > /dev/null 2>&1; then
        kill -9 $(lsof -ti:8080)
        echo -e "${GREEN}✓ Process on port 8080 killed${NC}"
    fi
fi

# Stop Docker containers
echo -e "${YELLOW}Stopping Docker containers...${NC}"

# Detect docker compose command
if docker compose version &> /dev/null; then
    docker compose down
elif command -v docker-compose &> /dev/null; then
    docker-compose down
else
    echo -e "${RED}✗ Docker Compose not found${NC}"
    exit 1
fi

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Docker containers stopped${NC}"
else
    echo -e "${RED}✗ Failed to stop Docker containers${NC}"
fi

echo ""
echo -e "${GREEN}✅ All services stopped successfully${NC}"
echo ""
