# Contributing to Simple Trading Bot

Thank you for your interest in contributing to the **Simple Trading Bot**! Use this guide to understand how the project is structured and how you can get involved.

## Code of Conduct

Please note that this project is released with a [Code of Conduct](CODE_OF_CONDUCT.md). By participating in this project you agree to abide by its terms.

## Getting Started

1. **Fork the repository** on GitHub.
2. **Clone your fork** locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/simple-trading-bot.git
   cd simple-trading-bot
   ```
3. **Create a branch** for your feature or fix:
   ```bash
   git checkout -b feature/amazing-feature
   ```

## Development Environment

### Prerequisites
- **Java 21**
- **Docker & Docker Compose**
- **Gradle** (wrapper provided)

### Setup
1. **Build the project**:
   ```bash
   ./gradlew build
   ```
2. **Start dependencies** (Kafka, Redis, Postgres):
   ```bash
   docker-compose up -d
   ```

## Project Structure (DDD + Clean Architecture)

- `src/main/java/tradingbot/agent/` - Agentic AI Logic (Domain, Application, Infra)
- `src/main/java/tradingbot/bot/` - Core Trading Logic
- `src/main/java/tradingbot/gateway/` - API Gateway & Rate Limiting
- `src/main/java/tradingbot/infrastructure/` - External Adapters (Exchange, Persistence)

## Pull Request Process

1. Ensure your code compiles and passes tests (`./gradlew test`).
2. If adding a new feature, include unit/integration tests.
3. Update relevant documentation (README.md, JavaDocs).
4. Submit a Pull Request to the `main` branch.

## Coding Standards

- **Style**: Standard Java conventions.
- **Testing**: JUnit 5 + Mockito + Testcontainers.
- **Commits**: Use [Conventional Commits](https://www.conventionalcommits.org/) (e.g., `feat: add robust backtesting`).

## Issues

Check the [Issue Tracker](https://github.com/lokesh/simple-trading-bot/issues) for open tasks. "Good First Issue" labels are great places to start!
