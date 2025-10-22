#!/bin/bash

echo "=========================================="
echo "Gateway Authentication Endpoints Testing"
echo "==========================================\n"

BASE_URL="http://localhost:8080"
GATEWAY_BASE="/gateway/api/auth"

# Test 1: Register via Gateway
echo "\n1. Testing Registration via Gateway"
echo "POST ${BASE_URL}${GATEWAY_BASE}/register"
REGISTER_RESPONSE=$(curl -s -X POST "${BASE_URL}${GATEWAY_BASE}/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"gatewaytest","email":"gateway@test.com","password":"Gateway123!"}')
echo "$REGISTER_RESPONSE" | jq .
echo "Status: $(echo "$REGISTER_RESPONSE" | jq -r 'if .access_token then "✅ SUCCESS" else "❌ FAILED" end')"

# Extract tokens
ACCESS_TOKEN=$(echo "$REGISTER_RESPONSE" | jq -r '.access_token // empty')

# Test 2: Login via Gateway
echo "\n2. Testing Login via Gateway"
echo "POST ${BASE_URL}${GATEWAY_BASE}/login"
LOGIN_RESPONSE=$(curl -s -X POST "${BASE_URL}${GATEWAY_BASE}/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"gatewaytest","password":"Gateway123!"}')
echo "$LOGIN_RESPONSE" | jq .
echo "Status: $(echo "$LOGIN_RESPONSE" | jq -r 'if .access_token then "✅ SUCCESS" else "❌ FAILED" end')"

# Extract refresh token  
REFRESH_TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.refresh_token // empty')

# Test 3: Refresh Token via Gateway
echo "\n3. Testing Token Refresh via Gateway"
echo "POST ${BASE_URL}${GATEWAY_BASE}/refresh"
if [ -n "$REFRESH_TOKEN" ]; then
  REFRESH_RESPONSE=$(curl -s -X POST "${BASE_URL}${GATEWAY_BASE}/refresh" \
    -H "Content-Type: application/json" \
    -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}")
  echo "$REFRESH_RESPONSE" | jq .
  echo "Status: $(echo "$REFRESH_RESPONSE" | jq -r 'if .access_token then "✅ SUCCESS" else "❌ FAILED" end')"
else
  echo "❌ No refresh token available"
fi

# Test 4: Logout via Gateway
echo "\n4. Testing Logout via Gateway"
echo "POST ${BASE_URL}${GATEWAY_BASE}/logout"
LOGOUT_RESPONSE=$(curl -s -X POST "${BASE_URL}${GATEWAY_BASE}/logout" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ACCESS_TOKEN")
echo "$LOGOUT_RESPONSE" | jq .
echo "Status: $(echo "$LOGOUT_RESPONSE" | jq -r 'if .message then "✅ SUCCESS" else "❌ FAILED" end')"

# Test 5: Auth Health Check via Gateway
echo "\n5. Testing Auth Health Check via Gateway"
echo "GET ${BASE_URL}${GATEWAY_BASE}/health"
HEALTH_RESPONSE=$(curl -s -X GET "${BASE_URL}${GATEWAY_BASE}/health")
echo "$HEALTH_RESPONSE" | jq .
echo "Status: $(echo "$HEALTH_RESPONSE" | jq -r 'if .status == "UP" then "✅ SUCCESS" else "❌ FAILED" end')"

echo "\n=========================================="
echo "Testing Complete!"
echo "=========================================="
