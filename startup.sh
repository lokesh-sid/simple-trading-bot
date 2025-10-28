#!/bin/bash

###############################################################################
# Agentic AI Trading Bot - Startup Script
# This script starts all required components for the agentic AI system
###############################################################################

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║     Agentic AI Trading Bot - Startup Script               ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""

###############################################################################
# Step 1: Check Prerequisites
###############################################################################

echo -e "${YELLOW}[1/6] Checking Prerequisites...${NC}"

# Check Docker
if ! command -v docker &> /dev/null; then
    echo -e "${RED}✗ Docker is not installed. Please install Docker first.${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Docker found${NC}"

# Check Docker Compose
if docker compose version &> /dev/null; then
    DOCKER_COMPOSE="docker compose"
elif command -v docker-compose &> /dev/null; then
    DOCKER_COMPOSE="docker-compose"
else
    echo -e "${RED}✗ Docker Compose is not installed. Please install Docker Compose first.${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Docker Compose found${NC}"

# Check Java
if ! command -v java &> /dev/null; then
    echo -e "${RED}✗ Java is not installed. Please install Java 21 or later.${NC}"
    exit 1
fi
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 21 ]; then
    echo -e "${RED}✗ Java 21 or later is required. Current version: $JAVA_VERSION${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Java $JAVA_VERSION found${NC}"

# Check Gradle
if [ ! -f "./gradlew" ]; then
    echo -e "${RED}✗ Gradle wrapper not found. Please run this script from the project root.${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Gradle wrapper found${NC}"

echo ""

###############################################################################
# Step 2: Check Environment Variables
###############################################################################

echo -e "${YELLOW}[2/6] Checking Environment Variables...${NC}"

# Check if .env file exists
if [ ! -f ".env" ]; then
    echo -e "${YELLOW}⚠ .env file not found. Creating from .env.example...${NC}"
    if [ -f ".env.example" ]; then
        cp .env.example .env
        echo -e "${YELLOW}⚠ Please edit .env file and add your API keys before continuing!${NC}"
        echo -e "${YELLOW}  Required: GROK_API_KEY, TRADING_BINANCE_API_KEY, TRADING_BINANCE_API_SECRET${NC}"
        read -p "Press Enter after you've configured .env file..."
    else
        echo -e "${RED}✗ .env.example not found. Cannot create .env file.${NC}"
        exit 1
    fi
fi

# Load environment variables
export $(grep -v '^#' .env | xargs)

# Check critical environment variables
MISSING_VARS=()

if [ -z "$GROK_API_KEY" ] || [ "$GROK_API_KEY" = "your-grok-api-key-here" ]; then
    MISSING_VARS+=("GROK_API_KEY")
fi

if [ ${#MISSING_VARS[@]} -gt 0 ]; then
    echo -e "${YELLOW}⚠ Warning: Missing environment variables: ${MISSING_VARS[*]}${NC}"
    echo -e "${YELLOW}  The system will run with fallback reasoning mode.${NC}"
    read -p "Continue anyway? (y/n): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
else
    echo -e "${GREEN}✓ Environment variables configured${NC}"
fi

echo ""

###############################################################################
# Step 3: Start Infrastructure Services (Docker Compose)
###############################################################################

echo -e "${YELLOW}[3/6] Starting Infrastructure Services...${NC}"

# Stop any existing containers
echo "Stopping existing containers..."
$DOCKER_COMPOSE down > /dev/null 2>&1 || true

# Start infrastructure services (PostgreSQL, Redis, Kafka, Zookeeper)
echo "Starting PostgreSQL, Redis, Kafka, and Zookeeper..."
$DOCKER_COMPOSE up -d postgres redis kafka zookeeper

# Wait for PostgreSQL to be ready
echo "Waiting for PostgreSQL to be ready..."
MAX_RETRIES=30
RETRY_COUNT=0
until docker exec trading-bot-postgres pg_isready -U tradingbot -d trading_bot > /dev/null 2>&1; do
    RETRY_COUNT=$((RETRY_COUNT + 1))
    if [ $RETRY_COUNT -ge $MAX_RETRIES ]; then
        echo -e "${RED}✗ PostgreSQL failed to start within 30 seconds${NC}"
        exit 1
    fi
    echo -n "."
    sleep 1
done
echo ""
echo -e "${GREEN}✓ PostgreSQL is ready${NC}"

# Wait for Redis to be ready
echo "Waiting for Redis to be ready..."
RETRY_COUNT=0
until docker exec -it $(docker ps -qf "name=redis") redis-cli ping > /dev/null 2>&1; do
    RETRY_COUNT=$((RETRY_COUNT + 1))
    if [ $RETRY_COUNT -ge $MAX_RETRIES ]; then
        echo -e "${RED}✗ Redis failed to start within 30 seconds${NC}"
        exit 1
    fi
    echo -n "."
    sleep 1
done
echo ""
echo -e "${GREEN}✓ Redis is ready${NC}"

# Wait for Kafka to be ready
echo "Waiting for Kafka to be ready..."
sleep 10  # Kafka takes a bit longer to start
echo -e "${GREEN}✓ Kafka is ready${NC}"

echo ""

###############################################################################
# Step 4: Build Application
###############################################################################

echo -e "${YELLOW}[4/6] Building Application...${NC}"

./gradlew clean build -x test

if [ $? -ne 0 ]; then
    echo -e "${RED}✗ Build failed${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Application built successfully${NC}"
echo ""

###############################################################################
# Step 5: Start Application
###############################################################################

echo -e "${YELLOW}[5/6] Starting Trading Bot Application...${NC}"

# Kill any existing Java process on port 8080
if lsof -ti:8080 > /dev/null 2>&1; then
    echo "Killing existing process on port 8080..."
    kill -9 $(lsof -ti:8080) || true
    sleep 2
fi

# Start application in background
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/trading_bot"
export SPRING_DATASOURCE_USERNAME="tradingbot"
export SPRING_DATASOURCE_PASSWORD="tradingbot123"
export SPRING_DATA_REDIS_HOST="localhost"
export SPRING_DATA_REDIS_PORT="6379"
export SPRING_KAFKA_BOOTSTRAP_SERVERS="localhost:9092"

echo "Starting application..."
nohup java -jar build/libs/simple-trading-bot-1.0-SNAPSHOT.jar > application.log 2>&1 &
APP_PID=$!
echo $APP_PID > application.pid

# Wait for application to start
echo "Waiting for application to start..."
RETRY_COUNT=0
MAX_RETRIES=60
until curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; do
    RETRY_COUNT=$((RETRY_COUNT + 1))
    if [ $RETRY_COUNT -ge $MAX_RETRIES ]; then
        echo -e "${RED}✗ Application failed to start within 60 seconds${NC}"
        echo "Check application.log for errors"
        exit 1
    fi
    echo -n "."
    sleep 1
done

echo ""
echo -e "${GREEN}✓ Application started successfully (PID: $APP_PID)${NC}"
echo ""

###############################################################################
# Step 6: Verify System Status
###############################################################################

echo -e "${YELLOW}[6/6] Verifying System Status...${NC}"

# Check PostgreSQL
if docker exec trading-bot-postgres pg_isready -U tradingbot -d trading_bot > /dev/null 2>&1; then
    echo -e "${GREEN}✓ PostgreSQL: Running${NC}"
else
    echo -e "${RED}✗ PostgreSQL: Not Running${NC}"
fi

# Check Redis
if docker exec -it $(docker ps -qf "name=redis") redis-cli ping > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Redis: Running${NC}"
else
    echo -e "${RED}✗ Redis: Not Running${NC}"
fi

# Check Kafka
if docker ps --filter "name=kafka" --filter "status=running" | grep kafka > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Kafka: Running${NC}"
else
    echo -e "${RED}✗ Kafka: Not Running${NC}"
fi

# Check Application
if curl -s http://localhost:8080/actuator/health | grep UP > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Trading Bot Application: Running${NC}"
else
    echo -e "${RED}✗ Trading Bot Application: Not Running${NC}"
fi

echo ""
echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║              System Started Successfully! 🚀               ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${GREEN}📊 Application is running at: http://localhost:8080${NC}"
echo -e "${GREEN}📖 Swagger UI: http://localhost:8080/swagger-ui.html${NC}"
echo -e "${GREEN}📝 API Docs: http://localhost:8080/v3/api-docs${NC}"
echo -e "${GREEN}❤️  Health Check: http://localhost:8080/actuator/health${NC}"
echo ""
echo -e "${YELLOW}📋 Quick Commands:${NC}"
echo -e "  • View logs: tail -f application.log"
echo -e "  • Stop application: kill \$(cat application.pid)"
echo -e "  • Stop all services: docker-compose down"
echo -e "  • Restart application: ./startup.sh"
echo ""
echo -e "${YELLOW}🤖 Create Your First Agent:${NC}"
echo -e '  curl -X POST http://localhost:8080/api/agents \\'
echo -e '    -H "Content-Type: application/json" \\'
echo -e '    -d '"'"'{'
echo -e '      "name": "Bitcoin Trader",'
echo -e '      "goalType": "MAXIMIZE_PROFIT",'
echo -e '      "goalDescription": "Maximize profits trading BTC",'
echo -e '      "tradingSymbol": "BTCUSDT",'
echo -e '      "capital": 10000.0'
echo -e '    }'"'"
echo ""
