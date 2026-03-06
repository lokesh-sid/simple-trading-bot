# Gateway Service

This is the API Gateway for the Simple Trading Bot, built with Spring Cloud Gateway.

## Prerequisites
- Java 21
- Gradle

## Running the Gateway
```bash
../gradlew -p gateway bootRun
```
The gateway will start on port **8080**.

## Running Tests
```bash
../gradlew -p gateway test
```

## Routes
| ID | Path | Target |
|----|------|--------|
| trading-bot-core | `/api/bot/**` | `http://localhost:8081` |
| market-data-service | `/api/market/**` | `http://localhost:8082` |
