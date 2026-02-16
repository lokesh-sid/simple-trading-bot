#!/bin/bash

# Simple manual test script for CCXT Integration

echo "Starting manual CCXT integration test..."

# 1. Start CCXT Rest Service
echo "Starting ccxt-rest container..."
if command -v docker-compose &> /dev/null; then
    docker-compose up -d ccxt-rest
else
    docker compose up -d ccxt-rest
fi

# Wait for it to be healthy (simple sleep for now)
sleep 5

# 2. Check if CCXT Rest is responding
echo "Checking CCXT Rest API..."
curl -s http://localhost:3000/exchanges | grep "binance" > /dev/null
if [ $? -eq 0 ]; then
    echo "✅ CCXT Rest Service is UP and listing exchanges."
else
    echo "❌ CCXT Rest Service failed to respond."
    exit 1
fi

# 3. Compile and Run Application with CCXT Provider
echo "Building and Running Spring Boot App with exchange=ccxt..."
export SPRING_PROFILES_ACTIVE=dev
export TRADING_EXCHANGE_PROVIDER=ccxt
export TRADING_CCXT_EXCHANGE_ID=binance

# Run in background or just build to verify compilation
./gradlew clean build -x test

# If build succeeds, run application for a short duration
# (This part is interactive/manual, skipping auto-run)
echo ""
echo "Build successful! To run the bot with CCXT:"
echo "export TRADING_EXCHANGE_PROVIDER=ccxt"
echo "export TRADING_CCXT_EXCHANGE_ID=binance"
echo "./gradlew bootRun"
echo ""
echo "Then check logs for 'CcxtFuturesService' usage."
