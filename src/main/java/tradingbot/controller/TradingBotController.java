package tradingbot.controller;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import tradingbot.bot.FuturesTradingBot;
import tradingbot.bot.FuturesTradingBot.BotParams;
import tradingbot.bot.TradeDirection;
import tradingbot.config.TradingConfig;
import tradingbot.service.FuturesExchangeService;
import tradingbot.service.PaperFuturesExchangeService;
@RestController
@RequestMapping("/api/simple-trading-bot")
@Tag(name = "Trading Bot Controller", description = "API for managing the futures trading bot")
public class TradingBotController {
    private final AtomicReference<FuturesTradingBot> tradingBotRef;

    public TradingBotController(FuturesTradingBot tradingBot) {
        this.tradingBotRef = new AtomicReference<>(tradingBot);
    }

    @PostMapping("/start")
    @Operation(summary = "Start the trading bot", 
               description = "Starts the trading bot with specified direction and trading mode")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bot started successfully",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"))),
        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
  
    public ResponseEntity<String> startBot(
            @Parameter(description = "Trading direction (LONG or SHORT)", required = true, example = "LONG")
            @RequestParam TradeDirection direction,
            @Parameter(description = "Use paper trading mode", required = false, example = "false")
            @RequestParam(defaultValue = "false") boolean paper) {
        
        try {
            FuturesTradingBot currentBot = tradingBotRef.get();
            
            // Check if current bot exists
            if (currentBot == null) {
                return ResponseEntity.internalServerError().body("Trading bot is not properly initialized");
            }
            
            // Check if bot is already running
            if (currentBot.isRunning()) {
                return ResponseEntity.badRequest().body("Trading bot is already running. Stop it before starting a new instance.");
            }
            
            FuturesExchangeService exchangeService = paper ? new PaperFuturesExchangeService() : currentBot.getExchangeService();
            
            BotParams.Builder builder = new BotParams.Builder();
            builder.exchangeService(exchangeService);
            builder.indicatorCalculator(currentBot.getIndicatorCalculator());
            builder.trailingStopTracker(currentBot.getTrailingStopTracker());
            builder.sentimentAnalyzer(currentBot.getSentimentAnalyzer());
            builder.exitConditions(currentBot.getExitConditions());
            builder.config(currentBot.getConfig());
            builder.tradeDirection(direction);
            builder.skipLeverageInit(paper);

            FuturesTradingBot newBot = new FuturesTradingBot(builder.build());
            tradingBotRef.set(newBot);
            newBot.start();
            String mode = paper ? "paper" : "live";
            return ResponseEntity.ok("Trading bot started in " + direction + " mode (" + mode + ")");
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid configuration: " + e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body("Bot configuration error: " + e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body("Failed to start trading bot: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Unexpected error occurred while starting bot: " + e.getMessage());
        }
    }

    @PutMapping("/stop")
    @Operation(summary = "Stop the trading bot", 
               description = "Stops the currently running trading bot")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bot stopped successfully",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"))),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<String> stopBot() {
        tradingBotRef.get().stop();
        return ResponseEntity.ok("Trading bot stopped");
    }

    @GetMapping("/status")
    @Operation(summary = "Get trading bot status", 
               description = "Returns the current status of the trading bot")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status retrieved successfully",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"))),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<String> getStatus() {
        return ResponseEntity.ok(tradingBotRef.get().getStatus());
    }

    @PostMapping("/configure")
    @Operation(summary = "Configure the trading bot", 
               description = "Updates the trading bot configuration with new parameters")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Configuration updated successfully",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"))),
        @ApiResponse(responseCode = "400", description = "Invalid configuration"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<String> configureBot(
            @Parameter(description = "New trading configuration", required = true)
            @RequestBody TradingConfig config) {
        tradingBotRef.get().updateConfig(config);
        return ResponseEntity.ok("Configuration updated");
    }

        @PutMapping("/leverage")
    @Operation(summary = "Update leverage", description = "Update the leverage for futures trading")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Leverage updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid leverage value"),
            @ApiResponse(responseCode = "404", description = "Trading bot not configured")
    })
    public ResponseEntity<String> updateLeverage(@RequestParam double leverage) {
        try {
            FuturesTradingBot bot = tradingBotRef.get();
            if (bot == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Trading bot not configured");
            }
            
            if (leverage <= 0 || leverage > 100) {
                return ResponseEntity.badRequest()
                    .body("Invalid leverage value. Must be between 1 and 100");
            }
            
            bot.setDynamicLeverage((int) leverage);
            return ResponseEntity.ok("Leverage updated to " + leverage + "x");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to update leverage: " + e.getMessage());
        }
    }

    @PutMapping("/sentiment")
    @Operation(summary = "Enable/disable sentiment analysis", 
               description = "Toggles sentiment analysis feature for the trading bot")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Sentiment analysis setting updated",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"))),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<String> enableSentimentAnalysis(
            @Parameter(description = "Enable or disable sentiment analysis", required = true, example = "true")
            @RequestParam boolean enable) {
        tradingBotRef.get().enableSentimentAnalysis(enable);
        return ResponseEntity.ok("Sentiment analysis " + (enable ? "enabled" : "disabled"));
    }
}