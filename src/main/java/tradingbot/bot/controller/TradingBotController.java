package tradingbot.bot.controller;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import tradingbot.bot.FuturesTradingBot;
import tradingbot.bot.FuturesTradingBot.BotParams;
import tradingbot.bot.controller.dto.request.BotStartRequest;
import tradingbot.bot.controller.dto.request.LeverageUpdateRequest;
import tradingbot.bot.controller.dto.request.SentimentUpdateRequest;
import tradingbot.bot.controller.dto.response.BotStartResponse;
import tradingbot.bot.controller.dto.response.BotStatusResponse;
import tradingbot.bot.controller.dto.response.BotStopResponse;
import tradingbot.bot.controller.dto.response.ConfigUpdateResponse;
import tradingbot.bot.controller.dto.response.ErrorResponse;
import tradingbot.bot.controller.dto.response.LeverageUpdateResponse;
import tradingbot.bot.controller.dto.response.SentimentUpdateResponse;
import tradingbot.bot.service.FuturesExchangeService;
import tradingbot.bot.service.PaperFuturesExchangeService;
import tradingbot.config.TradingConfig;
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
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BotStartResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid parameters",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
  
    public ResponseEntity<?> startBot(
            @Valid @RequestBody BotStartRequest request) {
        
        try {
            FuturesTradingBot currentBot = tradingBotRef.get();
            
            // Check if current bot exists
            if (currentBot == null) {
                return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("BOT_NOT_INITIALIZED", "Trading bot is not properly initialized"));
            }
            
            // Check if bot is already running
            if (currentBot.isRunning()) {
                return ResponseEntity.badRequest()
                    .body(new ErrorResponse("BOT_ALREADY_RUNNING", "Trading bot is already running. Stop it before starting a new instance."));
            }
            
            FuturesExchangeService exchangeService = request.isPaper() ? new PaperFuturesExchangeService() : currentBot.getExchangeService();
            
            BotParams.Builder builder = new BotParams.Builder();
            builder.exchangeService(exchangeService);
            builder.indicatorCalculator(currentBot.getIndicatorCalculator());
            builder.trailingStopTracker(currentBot.getTrailingStopTracker());
            builder.sentimentAnalyzer(currentBot.getSentimentAnalyzer());
            builder.exitConditions(currentBot.getExitConditions());
            builder.config(currentBot.getConfig());
            builder.tradeDirection(request.getDirection());
            builder.skipLeverageInit(request.isPaper());

            FuturesTradingBot newBot = new FuturesTradingBot(builder.build());
            tradingBotRef.set(newBot);
            newBot.start();
            
            // Create status response with data
            BotStatusResponse statusResponse = new BotStatusResponse();
            statusResponse.setRunning(newBot.isRunning());
            statusResponse.setSymbol(newBot.getConfig().getSymbol());
            statusResponse.setPositionStatus(newBot.getPositionStatus());
            statusResponse.setEntryPrice(newBot.getEntryPrice());
            statusResponse.setLeverage(newBot.getCurrentLeverage());
            statusResponse.setSentimentEnabled(newBot.isSentimentEnabled());
            statusResponse.setStatusMessage(newBot.getStatus());
            
            String mode = request.isPaper() ? "paper" : "live";
            String message = "Trading bot started in " + request.getDirection() + " mode (" + mode + ")";
            
            BotStartResponse response = new BotStartResponse(
                message, 
                statusResponse, 
                mode, 
                request.getDirection().toString()
            );
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_CONFIGURATION", "Invalid configuration: " + e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                .body(new ErrorResponse("BOT_CONFIGURATION_ERROR", "Bot configuration error: " + e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError()
                .body(new ErrorResponse("BOT_START_FAILED", "Failed to start trading bot: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(new ErrorResponse("UNEXPECTED_ERROR", "Unexpected error occurred while starting bot: " + e.getMessage()));
        }
    }

    @PutMapping("/stop")
    @Operation(summary = "Stop the trading bot", 
               description = "Stops the currently running trading bot")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bot stopped successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BotStopResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<?> stopBot() {
        try {
            FuturesTradingBot bot = tradingBotRef.get();
            String finalPositionStatus = bot.getPositionStatus();
            boolean wasRunning = bot.isRunning();
            bot.stop();
            
            BotStopResponse response = new BotStopResponse(
                "Trading bot stopped successfully",
                finalPositionStatus,
                wasRunning
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(new ErrorResponse("BOT_STOP_FAILED", "Failed to stop trading bot: " + e.getMessage()));
        }
    }

    @GetMapping("/status")
    @Operation(summary = "Get trading bot status", 
               description = "Returns the current status of the trading bot")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BotStatusResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<?> getStatus() {
        try {
            FuturesTradingBot bot = tradingBotRef.get();
            BotStatusResponse response = new BotStatusResponse();
            
            response.setRunning(bot.isRunning());
            response.setSymbol(bot.getConfig().getSymbol());
            response.setPositionStatus(bot.getPositionStatus());
            response.setEntryPrice(bot.getEntryPrice());
            response.setLeverage(bot.getCurrentLeverage());
            response.setSentimentEnabled(bot.isSentimentEnabled());
            response.setStatusMessage(bot.getStatus());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(new ErrorResponse("STATUS_RETRIEVAL_FAILED", "Failed to get status: " + e.getMessage()));
        }
    }

    @PostMapping("/configure")
    @Operation(summary = "Configure the trading bot", 
               description = "Updates the trading bot configuration with new parameters")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Configuration updated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ConfigUpdateResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid configuration",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<?> configureBot(
            @Valid @RequestBody TradingConfig config) {
        try {
            tradingBotRef.get().updateConfig(config);
            
            ConfigUpdateResponse response = new ConfigUpdateResponse(
                "Configuration updated successfully",
                config.getSymbol(),
                (double) config.getLeverage(),
                config.getTrailingStopPercent()
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(new ErrorResponse("CONFIGURATION_UPDATE_FAILED", "Failed to update configuration: " + e.getMessage()));
        }
    }

    @PostMapping("/leverage")
    @Operation(summary = "Update leverage", description = "Update the leverage for futures trading")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Leverage updated successfully",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = LeverageUpdateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid leverage value",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Trading bot not configured",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<?> updateLeverage(
            @Valid @RequestBody LeverageUpdateRequest request) {
        try {
            FuturesTradingBot bot = tradingBotRef.get();
            if (bot == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("BOT_NOT_CONFIGURED", "Trading bot not configured"));
            }
            
            double previousLeverage = bot.getCurrentLeverage();
            bot.setDynamicLeverage(request.getLeverage().intValue());
            
            LeverageUpdateResponse response = new LeverageUpdateResponse(
                "Leverage updated to " + request.getLeverage() + "x",
                request.getLeverage(),
                previousLeverage
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("LEVERAGE_UPDATE_FAILED", "Failed to update leverage: " + e.getMessage()));
        }
    }

    @PostMapping("/sentiment")
    @Operation(summary = "Enable/disable sentiment analysis", 
               description = "Toggles sentiment analysis feature for the trading bot")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Sentiment analysis setting updated",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SentimentUpdateResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<?> enableSentimentAnalysis(
            @Valid @RequestBody SentimentUpdateRequest request) {
        try {
            boolean enable = request.getEnable();
            FuturesTradingBot bot = tradingBotRef.get();
            boolean previousStatus = bot.isSentimentEnabled();
            bot.enableSentimentAnalysis(enable);
            
            SentimentUpdateResponse response = new SentimentUpdateResponse(
                "Sentiment analysis " + (enable ? "enabled" : "disabled"),
                enable,
                previousStatus
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(new ErrorResponse("SENTIMENT_UPDATE_FAILED", "Failed to update sentiment analysis: " + e.getMessage()));
        }
    }
}