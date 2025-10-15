<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Simple Trading Bot Presentation</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 0; padding: 0; background-color: #f4f4f4; }
        .slide { max-width: 1200px; margin: 20px auto; padding: 20px; background-color: white; box-shadow: 0 0 10px rgba(0,0,0,0.1); page-break-after: always; }
        .slide h1 { color: #333; text-align: center; }
        .slide h2 { color: #555; }
        .slide pre { background-color: #f8f8f8; padding: 10px; border: 1px solid #ddd; overflow-x: auto; }
        .slide table { width: 100%; border-collapse: collapse; }
        .slide th, .slide td { border: 1px solid #ddd; padding: 8px; text-align: left; }
        .slide th { background-color: #f2f2f2; }
        .slide img { display: block; margin: 20px auto; max-width: 100%; height: auto; }
        .slide-number { text-align: center; font-size: 0.8em; color: #888; }
    </style>
</head>
<body>

<div class="slide">
    <h1>Simple Trading Bot</h1>
    <p>Automated Cryptocurrency Futures Trading System</p>
    <p>Event-Driven Architecture • RESTful APIs • Agent-Based Design</p>
    <p>Spring Boot 3.5.6 • Java 21 • Binance Futures API</p>
    <p>Technical Architecture & Implementation Overview</p>
    <div class="slide-number">1 / 15</div>
</div>

<div class="slide">
    <h1>Project & System Overview</h1>
    <h2>Key Features:</h2>
    <ul>
        <li>🤖 Agent-Based Architecture: Each bot is an independent agent with lifecycle management, implementing TradingAgent interface.</li>
        <li>📈 Technical Analysis: RSI, MACD, Bollinger Bands on daily/weekly timeframes, extensible indicators.</li>
        <li>🛡️ Risk Management: Configurable leverage, 1% trailing stop-loss, exit conditions.</li>
        <li>🧪 Paper Trading: In-memory simulation for safe testing.</li>
    </ul>
    <h2>Core Principles:</h2>
    <ul>
        <li>Event-Driven: Asynchronous Kafka messaging.</li>
        <li>Resilient: Circuit breakers and rate limiting.</li>
        <li>Observable: Logging, monitoring, and metrics.</li>
    </ul>
    <h2>Stats:</h2>
    <ul>
        <li>15+ REST Endpoints</li>
        <li>3 Trading Modes</li>
        <li>100% Test Coverage</li>
    </ul>
    <div class="slide-number">2 / 15</div>
</div>

<div class="slide">
    <h1>Technology Stack</h1>
    <h2>Core Technologies:</h2>
    <ul>
        <li>Spring Boot 3.5.6</li>
        <li>Java 21</li>
        <li>Gradle 8.5+</li>
        <li>Redis Cache</li>
        <li>Apache Kafka</li>
        <li>Docker</li>
    </ul>
    <h2>Dependencies & Libraries:</h2>
    <ul>
        <li>Binance Futures Connector</li>
        <li>TA4J for Technical Analysis</li>
        <li>Resilience4j</li>
        <li>OpenAPI/Swagger</li>
        <li>Jakarta Bean Validation</li>
        <li>Jackson JSON</li>
        <li>Spring Kafka</li>
    </ul>
    <h2>build.gradle Snippet:</h2>
    <pre><code>dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation "io.github.resilience4j:resilience4j-spring-boot3:2.1.0"
    implementation "io.github.binance:binance-futures-connector-java:3.0.5"
    implementation "org.ta4j:ta4j-core:0.15"
    implementation "org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0"
    implementation 'org.springframework.kafka:spring-kafka'
}</code></pre>
    <img src="https://miro.medium.com/v2/resize:fit:1400/1*pSfi9dAXNChp5Zrao7IZaQ.png" alt="Technology stack for Java Spring Boot application with Redis Kafka Docker icons" style="max-width: 800px;">
    <div class="slide-number">3 / 15</div>
</div>

<div class="slide">
    <h1>System Architecture</h1>
    <h2>High-Level Flow:</h2>
    <ul>
        <li>REST Controller → Trading Bot Agent → Exchange Service</li>
        <li>Redis Cache ←→ Indicator Calculator ←→ Kafka Events</li>
        <li>Sentiment Analyzer ↑ Strategy Engine ↑ Risk Management</li>
    </ul>
    <h2>Key Components:</h2>
    <ul>
        <li>TradingBotController</li>
        <li>FuturesTradingBot</li>
        <li>TradeExecutionService</li>
        <li>EventPublisher</li>
        <li>RiskAssessmentService</li>
    </ul>
    <h2>Architectural Patterns:</h2>
    <ul>
        <li>Agent Pattern: Independent agents via AgentManager.</li>
        <li>Strategy Pattern: Pluggable strategies/indicators.</li>
        <li>Circuit Breaker: Resilience4j for fault tolerance.</li>
        <li>Event-Driven: Kafka for signals and updates.</li>
        <li>SOLID Principles: Dependency injection and interface segregation.</li>
    </ul>
    <img src="https://dz2cdn1.dzone.com/storage/temp/9300395-architecturediagram.png" alt="System architecture diagram for trading bot with REST API Kafka Redis" style="max-width: 800px;">
    <div class="slide-number">4 / 15</div>
</div>

<div class="slide">
    <h1>Core Trading Bot Implementation</h1>
    <h2>FuturesTradingBot Class Structure:</h2>
    <pre><code>public class FuturesTradingBot implements TradingAgent&lt;MarketData&gt; {
    private final Logger logger = Logger.getLogger(FuturesTradingBot.class.getName());
    private static final int CHECK_INTERVAL_SECONDS = 900; // 15 minutes

    private FuturesExchangeService exchangeService;
    private IndicatorCalculator indicatorCalculator;
    private TrailingStopTracker trailingStopTracker;
    private SentimentAnalyzer sentimentAnalyzer;
    private List&lt;PositionExitCondition&gt; exitConditions;
    private TradingConfig config;
    private TradeDirection direction;
    private volatile boolean running;
    private volatile boolean sentimentEnabled;

    public FuturesTradingBot(BotParams params) {
        this.exchangeService = params.exchangeService;
        this.indicatorCalculator = params.indicatorCalculator;
        this.trailingStopTracker = params.trailingStopTracker;
        // ... initialize all components
        if (!params.skipLeverageInit) {
            initializeLeverage();
        }
        logInitialization();
    }
}</code></pre>
    <p>Key Features: Thread-safe operations, dependency injection, builder pattern for configuration.</p>
    <h2>TradingAgent Interface:</h2>
    <pre><code>public interface TradingAgent&lt;T&gt; {
    void start();
    void stop();
    void processMarketData(T data);
    String getStatus();
    boolean isRunning();
}</code></pre>
    <div class="slide-number">5 / 15</div>
</div>

<div class="slide">
    <h1>REST API Design & Implementation</h1>
    <h2>Endpoints Overview:</h2>
    <table>
        <tr><th>Method</th><th>Endpoint</th><th>Description</th><th>Response Type</th></tr>
        <tr><td>POST</td><td>/api/simple-trading-bot/start</td><td>Start trading bot</td><td>BotStartResponse</td></tr>
        <tr><td>PUT</td><td>/api/simple-trading-bot/stop</td><td>Stop trading bot</td><td>BotStopResponse</td></tr>
        <tr><td>GET</td><td>/api/simple-trading-bot/status</td><td>Get bot status</td><td>BotStatusResponse</td></tr>
        <tr><td>POST</td><td>/api/simple-trading-bot/configure</td><td>Update configuration</td><td>ConfigUpdateResponse</td></tr>
        <tr><td>POST</td><td>/api/simple-trading-bot/leverage</td><td>Update leverage</td><td>LeverageUpdateResponse</td></tr>
        <tr><td>POST</td><td>/api/simple-trading-bot/sentiment</td><td>Enable/disable sentiment</td><td>SentimentUpdateResponse</td></tr>
    </table>
    <h2>Controller Example:</h2>
    <pre><code>@PostMapping("/start")
@Operation(summary = "Start the trading bot")
public ResponseEntity&lt;?&gt; startBot(@Valid @RequestBody BotStartRequest request) {
    // Implementation with error handling
    BotStartResponse response = new BotStartResponse(message, statusResponse, mode, direction);
    return ResponseEntity.ok(response);
}</code></pre>
    <div class="slide-number">6 / 15</div>
</div>

<div class="slide">
    <h1>DTO Architecture</h1>
    <h2>Request DTO Example (BotStartRequest):</h2>
    <pre><code>@Schema(description = "Request to start trading bot")
public class BotStartRequest {
    @NotNull(message = "Direction is required")
    @Schema(description = "Trading direction", example = "LONG", allowableValues = {"LONG", "SHORT"})
    private TradeDirection direction;

    @Schema(description = "Enable paper trading mode", example = "true")
    private boolean paper = false;
    // Getters and setters
}</code></pre>
    <h2>Response DTO Example (BotStartResponse):</h2>
    <pre><code>@Schema(description = "Trading bot start response")
public class BotStartResponse {
    @Schema(description = "Success message")
    private String message;
    // Other fields, constructor, getters, setters
}</code></pre>
    <p>Benefits: Type safety, auto-generated docs, validation, clean contracts.</p>
    <div class="slide-number">7 / 15</div>
</div>

<div class="slide">
    <h1>Configuration Management</h1>
    <h2>Application Properties Snippet:</h2>
    <pre><code># Trading API Configuration
trading.binance.api.key=YOUR_BINANCE_API_KEY
trading.binance.api.secret=YOUR_BINANCE_API_SECRET

# Resilience4j Rate Limiter
resilience4j.ratelimiter.instances.binance-trading.limit-for-period=8
resilience4j.ratelimiter.instances.binance-trading.limit-refresh-period=10s

# Circuit Breaker
resilience4j.circuitbreaker.instances.binance-api.failure-rate-threshold=50

# Kafka
spring.kafka.bootstrap-servers=localhost:9092
trading.kafka.topics.trade-signals=trading.signals</code></pre>
    <p>Highlights: Rate limiting for API compliance, 50% failure threshold, Kafka topics for events, Redis TTL 5 minutes.</p>
    <div class="slide-number">8 / 15</div>
</div>

<div class="slide">
    <h1>Technical Analysis Implementation</h1>
    <h2>IndicatorCalculator with TA4J:</h2>
    <pre><code>@Component
public class IndicatorCalculator {
    @Cacheable(value = "indicators", key = "#symbol + '_' + #interval")
    public IndicatorValues calculateIndicators(String symbol, String interval) {
        // Fetch data, convert to BarSeries, calculate RSI, MACD, Bollinger Bands
        return new IndicatorValues(rsiValue, macdValue, bbValue);
    }
}</code></pre>
    <h2>Supported Indicators:</h2>
    <ul>
        <li>RSI (14 period)</li>
        <li>MACD (12,26,9)</li>
        <li>Bollinger Bands (20,2)</li>
        <li>SMA/EMA</li>
    </ul>
    <img src="http://www.tradewithme.me/uploads/7/9/9/5/7995199/2667045_orig.png" alt="Technical analysis chart with RSI MACD Bollinger Bands" style="max-width: 800px;">
    <div class="slide-number">9 / 15</div>
</div>

<div class="slide">
    <h1>Risk Management System</h1>
    <h2>TrailingStopTracker:</h2>
    <pre><code>@Component
public class TrailingStopTracker {
    private double bestPrice = 0.0;
    private final double trailingPercent = 0.01; // 1%
    
    public boolean shouldTriggerStop(double currentPrice, TradeDirection direction) {
        // Logic for LONG/SHORT trailing stop
    }
}</code></pre>
    <h2>Exit Conditions:</h2>
    <ul>
        <li>Trailing Stop: 1% to lock profits</li>
        <li>Technical Signals: RSI overbought/oversold</li>
        <li>Time-based: Max holding period</li>
        <li>Drawdown Protection: Max loss threshold</li>
    </ul>
    <img src="https://i.ytimg.com/vi/dZFb0-fwqOk/maxresdefault.jpg" alt="Risk management in trading trailing stop loss illustration" style="max-width: 800px;">
    <div class="slide-number">10 / 15</div>
</div>

<div class="slide">
    <h1>Event-Driven Architecture</h1>
    <h2>EventPublisher:</h2>
    <pre><code>@Service
public class EventPublisher {
    @Async
    public CompletableFuture&lt;Void&gt; publishTradeSignal(TradeSignalEvent event) {
        // Send to Kafka topic
    }
}</code></pre>
    <h2>Event Types:</h2>
    <ul>
        <li>Trade Signals: Entry/exit from analysis</li>
        <li>Execution Events: Order confirmations</li>
        <li>Risk Events: Stop-loss triggers</li>
        <li>Status Updates: Bot health</li>
    </ul>
    <p>Topics: TRADE_SIGNALS, TRADE_EXECUTION, RISK_EVENTS, etc.</p>
    <div class="slide-number">11 / 15</div>
</div>

<div class="slide">
    <h1>Resilience & Circuit Breaker</h1>
    <h2>BinanceExchangeService:</h2>
    <pre><code>@Service
public class BinanceExchangeService implements FuturesExchangeService {
    @RateLimiter(name = "binance-trading")
    @CircuitBreaker(name = "binance-api", fallbackMethod = "fallbackPlaceOrder")
    @Retry(name = "binance-api")
    public OrderResponse placeOrder(OrderRequest request) {
        // Place order or fallback
    }
}</code></pre>
    <h2>Configuration:</h2>
    <ul>
        <li>Rate Limiting: 8/10s trading, 30/1s data</li>
        <li>Circuit Breaker: 50% failure, 5 calls min</li>
        <li>Retry: 3 attempts, exponential backoff</li>
    </ul>
    <div class="slide-number">12 / 15</div>
</div>

<div class="slide">
    <h1>Testing Strategy</h1>
    <h2>PaperFuturesExchangeService:</h2>
    <pre><code>@Service
public class PaperFuturesExchangeService implements FuturesExchangeService {
    // Simulate orders with in-memory balance starting at $10,000
}</code></pre>
    <h2>Coverage:</h2>
    <ul>
        <li>25+ Unit Tests</li>
        <li>10+ Integration Tests</li>
        <li>90% Code Coverage</li>
    </ul>
    <h2>Example Integration Test:</h2>
    <pre><code>@SpringBootTest
@Testcontainers
public class FuturesTradingBotIntegrationTest {
    // Test start/stop with assertions
}</code></pre>
    <div class="slide-number">13 / 15</div>
</div>

<div class="slide">
    <h1>Deployment & DevOps</h1>
    <h2>Dockerfile:</h2>
    <pre><code>FROM openjdk:21-jdk-slim
COPY build/libs/simple-trading-bot-1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
HEALTHCHECK CMD curl -f http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]</code></pre>
    <h2>Kubernetes Deployment Snippet:</h2>
    <pre><code>apiVersion: apps/v1
kind: Deployment
metadata:
  name: simple-trading-bot
spec:
  replicas: 1
  template:
    spec:
      containers:
      - name: trading-bot
        image: simple-trading-bot:latest
        ports:
        - containerPort: 8080</code></pre>
    <p>Monitoring: Actuator, Micrometer, Prometheus, Grafana, ELK Stack.</p>
    <img src="https://miro.medium.com/1*30JgJtH4ZEs0HkFQJdUKRw.jpeg" alt="Docker and Kubernetes deployment diagram" style="max-width: 800px;">
    <div class="slide-number">14 / 15</div>
</div>

<div class="slide">
    <h1>Future Enhancements & Roadmap</h1>
    <h2>Planned Features:</h2>
    <ul>
        <li>🔮 AI/ML Integration: Models for pattern recognition and social sentiment.</li>
        <li>📊 Advanced Analytics: Dashboards, backtesting.</li>
        <li>🌐 Multi-Exchange Support: Bybit, others.</li>
        <li>⚡ Optimization: WebSocket, caching.</li>
    </ul>
    <h2>Technical Roadmap:</h2>
    <ul>
        <li>Q1 2026: WebSocket for real-time data.</li>
        <li>Q2 2026: Multi-symbol portfolio management.</li>
        <li>Q3 2026: ML price prediction.</li>
        <li>Q4 2026: Mobile app and UI dashboard.</li>
    </ul>
    <h2>Thank You!</h2>
    <p>Questions & Discussion</p>
    <p>GitHub: lokesh-sid/simple-trading-bot</p>
    <div class="slide-number">15 / 15</div>
</div>

</body>
</html>