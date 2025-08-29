package tradingbot.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
    private FuturesTradingBot tradingBot;

    public TradingBotController(FuturesTradingBot tradingBot) {
        this.tradingBot = tradingBot;
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
            @Parameter(description = "Enable paper trading mode", example = "false")
            @RequestParam(defaultValue = "false") boolean paper) {
        FuturesExchangeService exchangeService = paper ? new PaperFuturesExchangeService() : tradingBot.getExchangeService();
        tradingBot = new FuturesTradingBot(new BotParams.Builder()
            .exchangeService(exchangeService)
            .indicatorCalculator(tradingBot.getIndicatorCalculator())
            .trailingStopTracker(tradingBot.getTrailingStopTracker())
            .sentimentAnalyzer(tradingBot.getSentimentAnalyzer())
            .exitConditions(tradingBot.getExitConditions())
            .config(tradingBot.getConfig())
            .tradeDirection(direction)
            .skipLeverageInit(paper)
            .build());
        tradingBot.start();
        String mode = paper ? "paper" : "live";
        return ResponseEntity.ok("Trading bot started in " + direction + " mode (" + mode + ")");
    }

    @PostMapping("/stop")
    @Operation(summary = "Stop the trading bot", 
               description = "Stops the currently running trading bot")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bot stopped successfully",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"))),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<String> stopBot() {
        tradingBot.stop();
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
        return ResponseEntity.ok(tradingBot.getStatus());
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
        tradingBot.updateConfig(config);
        return ResponseEntity.ok("Configuration updated");
    }

    @PostMapping("/leverage")
    @Operation(summary = "Set dynamic leverage", 
               description = "Updates the trading bot's leverage multiplier")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Leverage updated successfully",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string"))),
        @ApiResponse(responseCode = "400", description = "Invalid leverage value"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<String> setDynamicLeverage(
            @Parameter(description = "Leverage multiplier (1-100)", required = true, example = "10")
            @RequestParam int leverage) {
        tradingBot.setDynamicLeverage(leverage);
        return ResponseEntity.ok("Leverage set to " + leverage + "x");
    }

    @PostMapping("/sentiment")
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
        tradingBot.enableSentimentAnalysis(enable);
        return ResponseEntity.ok("Sentiment analysis " + (enable ? "enabled" : "disabled"));
    }
}