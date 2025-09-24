package tradingbot.messaging;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import tradingbot.bot.TradeDirection;
import tradingbot.events.BotStatusEvent;
import tradingbot.events.RiskEvent;
import tradingbot.events.TradeSignalEvent;

/**
 * Demonstration controller showing how to integrate event-driven patterns
 * with the existing trading bot REST API. This shows the migration path
 * from synchronous to asynchronous, event-driven architecture.
 */
@RestController
@RequestMapping("/api/events")
@Tag(name = "Event-Driven Trading Demo", description = "Demonstration of event-driven trading patterns")
public class EventDrivenTradingController {
    
    private static final Logger log = LoggerFactory.getLogger(EventDrivenTradingController.class);
    
    private final EventPublisher eventPublisher;
    private final TradeExecutionService tradeExecutionService;
    
    public EventDrivenTradingController(EventPublisher eventPublisher,
                                      TradeExecutionService tradeExecutionService) {
        this.eventPublisher = eventPublisher;
        this.tradeExecutionService = tradeExecutionService;
    }
    
    @PostMapping("/trade-signal")
    @Operation(summary = "Publish a trade signal event", 
               description = "Demonstrates event-driven trade signal processing")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Signal event published successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid signal parameters"),
        @ApiResponse(responseCode = "500", description = "Event publishing failed")
    })
    public ResponseEntity<Map<String, Object>> publishTradeSignal(
            @Parameter(description = "Bot ID", required = true, example = "futures-bot-1")
            @RequestParam String botId,
            @Parameter(description = "Trading symbol", required = true, example = "BTCUSDT") 
            @RequestParam String symbol,
            @Parameter(description = "Signal direction", required = true, example = "LONG")
            @RequestParam TradeDirection direction,
            @Parameter(description = "Signal strength (0.0-1.0)", example = "0.75")
            @RequestParam(defaultValue = "0.5") double strength) {
        
        try {
            // Create trade signal event
            TradeSignalEvent signalEvent = new TradeSignalEvent(botId, symbol, direction);
            signalEvent.setStrength(strength);
            signalEvent.setMetadata(Map.of(
                "source", "manual",
                "confidence", "MEDIUM",
                "strategy", "demo_signal"
            ));
            
            // Publish the event asynchronously
            CompletableFuture<Void> publishFuture = eventPublisher.publishTradeSignal(signalEvent);
            
            // Process the signal (in production this would be handled by Kafka consumers)
            CompletableFuture<Void> processingFuture = tradeExecutionService.handleTradeSignal(signalEvent);
            
            // Add error handling without blocking the response
            publishFuture.exceptionally(ex -> {
                log.error("Failed to publish trade signal: {}", signalEvent.getEventId(), ex);
                return null;
            });
            
            processingFuture.exceptionally(ex -> {
                log.error("Failed to process trade signal: {}", signalEvent.getEventId(), ex);
                return null;
            });
            
            // Return immediately with event ID (async processing continues in background)
            Map<String, Object> response = Map.of(
                "eventId", signalEvent.getEventId(),
                "status", "published",
                "message", "Trade signal event published and processing started",
                "timestamp", signalEvent.getTimestamp()
            );
            
            log.info("Published trade signal event: {} for bot: {} - {} {}", 
                signalEvent.getEventId(), botId, direction, symbol);
            
            return ResponseEntity.accepted().body(response);
            
        } catch (Exception ex) {
            log.error("Failed to publish trade signal", ex);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to publish trade signal",
                "message", ex.getMessage()
            ));
        }
    }
    
    @PostMapping("/risk-event")
    @Operation(summary = "Publish a risk event", 
               description = "Demonstrates risk event processing and position management")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Risk event published successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid risk parameters"),
        @ApiResponse(responseCode = "500", description = "Event publishing failed")
    })
    public ResponseEntity<Map<String, Object>> publishRiskEvent(
            @Parameter(description = "Bot ID", required = true, example = "futures-bot-1")
            @RequestParam String botId,
            @Parameter(description = "Trading symbol", required = true, example = "BTCUSDT")
            @RequestParam String symbol,
            @Parameter(description = "Risk type", required = true, example = "TRAILING_STOP_TRIGGERED")
            @RequestParam String riskType,
            @Parameter(description = "Current price", required = true, example = "43250.50")
            @RequestParam double currentPrice,
            @Parameter(description = "Risk severity", example = "HIGH")
            @RequestParam(defaultValue = "MEDIUM") String severity,
            @Parameter(description = "Recommended action", example = "CLOSE_POSITION")
            @RequestParam(defaultValue = "ALERT_ONLY") String action) {
        
        try {
            // Create risk event
            RiskEvent riskEvent = new RiskEvent(botId, riskType, symbol);
            riskEvent.setCurrentPrice(currentPrice);
            riskEvent.setSeverity(severity);
            riskEvent.setAction(action);
            riskEvent.setDescription("Manual risk event triggered via API");
            
            // Publish the event asynchronously
            CompletableFuture<Void> publishFuture = eventPublisher.publishRiskEvent(riskEvent);
            
            // Process the risk event (in production this would be handled by Kafka consumers)
            CompletableFuture<Void> processingFuture = tradeExecutionService.handleRiskEvent(riskEvent);
            
            // Add error handling without blocking the response
            publishFuture.exceptionally(ex -> {
                log.error("Failed to publish risk event: {}", riskEvent.getEventId(), ex);
                return null;
            });
            
            processingFuture.exceptionally(ex -> {
                log.error("Failed to process risk event: {}", riskEvent.getEventId(), ex);
                return null;
            });
            
            Map<String, Object> response = Map.of(
                "eventId", riskEvent.getEventId(),
                "status", "published",
                "message", "Risk event published and processing started",
                "timestamp", riskEvent.getTimestamp(),
                "severity", severity,
                "action", action
            );
            
            log.warn("Published risk event: {} for bot: {} - {} ({})", 
                riskEvent.getEventId(), botId, riskType, severity);
            
            return ResponseEntity.accepted().body(response);
            
        } catch (Exception ex) {
            log.error("Failed to publish risk event", ex);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to publish risk event",
                "message", ex.getMessage()
            ));
        }
    }
    
    @PostMapping("/bot-status")
    @Operation(summary = "Publish a bot status event", 
               description = "Demonstrates bot status broadcasting for real-time monitoring")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Status event published successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid status parameters"),
        @ApiResponse(responseCode = "500", description = "Event publishing failed")
    })
    public ResponseEntity<Map<String, Object>> publishBotStatusEvent(
            @Parameter(description = "Bot ID", required = true, example = "futures-bot-1")
            @RequestParam String botId,
            @Parameter(description = "Bot status", required = true, example = "RUNNING")
            @RequestParam String status,
            @Parameter(description = "Status message", example = "Bot is actively trading")
            @RequestParam(required = false) String message,
            @Parameter(description = "Current balance", example = "1250.75")
            @RequestParam(defaultValue = "0.0") double balance,
            @Parameter(description = "Active position", example = "LONG")
            @RequestParam(defaultValue = "NONE") String activePosition) {
        
        try {
            // Create bot status event
            BotStatusEvent statusEvent = new BotStatusEvent(botId, status);
            statusEvent.setMessage(message != null ? message : "Status updated via API");
            statusEvent.setRunning("RUNNING".equals(status) || "STARTED".equals(status));
            statusEvent.setCurrentBalance(balance);
            statusEvent.setActivePosition(activePosition);
            
            // Publish the event to real-time stream
            CompletableFuture<Void> publishFuture = eventPublisher.publishBotStatus(statusEvent);
            
            // Add error handling for status publishing
            publishFuture.exceptionally(ex -> {
                log.error("Failed to publish bot status event: {}", statusEvent.getEventId(), ex);
                return null;
            });
            
            Map<String, Object> response = Map.of(
                "eventId", statusEvent.getEventId(),
                "status", "published",
                "message", "Bot status event published to real-time stream",
                "timestamp", statusEvent.getTimestamp(),
                "botStatus", status
            );
            
            log.info("Published bot status event: {} for bot: {} - Status: {}", 
                statusEvent.getEventId(), botId, status);
            
            return ResponseEntity.accepted().body(response);
            
        } catch (Exception ex) {
            log.error("Failed to publish bot status event", ex);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to publish bot status event",
                "message", ex.getMessage()
            ));
        }
    }
    
    @GetMapping("/demo-info")
    @Operation(summary = "Get event-driven architecture demo information",
               description = "Provides information about the event-driven patterns and how to use them")
    public ResponseEntity<Map<String, Object>> getDemoInfo() {
        Map<String, Object> info = Map.of(
            "title", "Event-Driven Trading Bot Demo",
            "description", "This API demonstrates how to integrate message queues and event-driven patterns",
            "features", Map.of(
                "async_processing", "All events are processed asynchronously",
                "event_publishing", "Events are published to durable topics and real-time streams",
                "risk_management", "Risk events trigger automatic position management",
                "scalability", "Services can be scaled independently"
            ),
            "topics", Map.of(
                "trade-signals", "Durable topic for trade signals and analytics",
                "trade-execution", "Trade execution results and confirmations",
                "risk-events", "Risk management alerts and actions",
                "market-data", "Market price and volume updates"
            ),
            "streams", Map.of(
                "bot-status", "Real-time bot status updates",
                "trade-notifications", "Immediate trade confirmations",
                "system-alerts", "Real-time system alerts and warnings"
            ),
            "migration_path", Map.of(
                "phase1", "Add event publishing to existing services",
                "phase2", "Implement event consumers for async processing", 
                "phase3", "Replace direct service calls with event-driven communication",
                "phase4", "Add real-time features with WebSocket + Redis Streams",
                "phase5", "Implement full Kafka infrastructure for production"
            ),
            "next_steps", Map.of(
                "kafka_setup", "Set up Apache Kafka cluster for production",
                "consumer_groups", "Implement proper Kafka consumer groups",
                "monitoring", "Add Kafka monitoring and alerting",
                "testing", "Implement event-driven integration tests"
            )
        );
        
        return ResponseEntity.ok(info);
    }
}