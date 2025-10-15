package tradingbot.bot.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import tradingbot.bot.FuturesTradingBot;
import tradingbot.bot.FuturesTradingBot.BotParams;
import tradingbot.bot.TradeDirection;
import tradingbot.bot.controller.dto.BotState;
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
import tradingbot.bot.controller.exception.BotAlreadyRunningException;
import tradingbot.bot.controller.exception.BotNotFoundException;
import tradingbot.bot.service.BotCacheService;
import tradingbot.bot.service.FuturesExchangeService;
import tradingbot.bot.service.PaperFuturesExchangeService;
import tradingbot.config.TradingConfig;

/**
 * Trading Bot Controller - Manages multiple trading bot instances
 * 
 * Supports creating, starting, stopping, and configuring multiple independent
 * trading bots, each identified by a unique bot ID.
 * 
 * Features Redis-backed persistence for bot state recovery and horizontal scaling.
 * 
 * API Path Pattern: /api/bots/{botId}/{action}
 */
@RestController
@RequestMapping("/api/bots")
@Tag(name = "Trading Bot Controller", description = "API for managing multiple futures trading bot instances")
public class TradingBotController {
    
    private static final Logger logger = LoggerFactory.getLogger(TradingBotController.class);
    
    // Thread-safe map to store active bot instances (in-memory for performance)
    private final Map<String, FuturesTradingBot> tradingBots = new ConcurrentHashMap<>();
    
    // Template bot for creating new instances
    private final FuturesTradingBot tradingBot;
    
    // Redis cache service for bot state persistence
    private final BotCacheService botCacheService;

    public TradingBotController(FuturesTradingBot tradingBot, BotCacheService botCacheService) {
        this.tradingBot = tradingBot;
        this.botCacheService = botCacheService;
    }
    
    /**
     * Recover bot states from Redis on application startup
     */
    @PostConstruct
    public void recoverBotsFromRedis() {
        logger.info("Starting bot recovery from Redis...");
        
        try {
            Set<String> botIds = botCacheService.getAllBotIds();
            logger.info("Found {} bot(s) in Redis", botIds.size());
            
            int recoveredCount = 0;
            for (String botId : botIds) {
                try {
                    BotState state = botCacheService.getBotState(botId);
                    if (state != null) {
                        logger.info("Recovering bot: {} (running: {})", botId, state.isRunning());
                        
                        // Reconstruct bot from state
                        FuturesTradingBot bot = reconstructBotFromState(state);
                        tradingBots.put(botId, bot);
                        
                        // Restart if it was running
                        if (state.isRunning()) {
                            bot.start();
                            logger.info("Restarted bot: {}", botId);
                        }
                        
                        recoveredCount++;
                    }
                } catch (Exception e) {
                    logger.error("Failed to recover bot: {}", botId, e);
                }
            }
            
            logger.info("Bot recovery complete. Recovered {}/{} bot(s)", recoveredCount, botIds.size());
        } catch (Exception e) {
            logger.error("Failed to recover bots from Redis", e);
        }
    }
    
    /**
     * Reconstruct bot instance from cached state
     */
    private FuturesTradingBot reconstructBotFromState(BotState state) {
        FuturesExchangeService exchangeService = state.isPaper() ? 
            new PaperFuturesExchangeService() : tradingBot.getExchangeService();
        
        BotParams.Builder builder = new BotParams.Builder();
        builder.exchangeService(exchangeService);
        builder.indicatorCalculator(tradingBot.getIndicatorCalculator());
        builder.trailingStopTracker(tradingBot.getTrailingStopTracker());
        builder.sentimentAnalyzer(tradingBot.getSentimentAnalyzer());
        builder.exitConditions(tradingBot.getExitConditions());
        builder.config(state.getConfig() != null ? state.getConfig() : tradingBot.getConfig());
        builder.tradeDirection(state.getDirection() != null ? 
            TradeDirection.valueOf(state.getDirection()) : null);
        builder.skipLeverageInit(state.isPaper());
        
        FuturesTradingBot bot = new FuturesTradingBot(builder.build());
        
        // Restore additional state
        if (state.isSentimentEnabled()) {
            bot.enableSentimentAnalysis(true);
        }
        if (state.getCurrentLeverage() > 0) {
            bot.setDynamicLeverage((int) state.getCurrentLeverage());
        }
        
        return bot;
    }

    @PostMapping
    @Operation(summary = "Create a new trading bot", 
               description = "Creates a new trading bot instance and returns its unique ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Bot created successfully",
                    content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Map<String, String>> createBot() {
        String botId = UUID.randomUUID().toString();
        
        // Create new bot instance from template
        BotParams.Builder builder = new BotParams.Builder();
        builder.exchangeService(tradingBot.getExchangeService());
        builder.indicatorCalculator(tradingBot.getIndicatorCalculator());
        builder.trailingStopTracker(tradingBot.getTrailingStopTracker());
        builder.sentimentAnalyzer(tradingBot.getSentimentAnalyzer());
        builder.exitConditions(tradingBot.getExitConditions());
        builder.config(tradingBot.getConfig());
        builder.tradeDirection(null); // Will be set on start
        builder.skipLeverageInit(false);
        
        FuturesTradingBot newBot = new FuturesTradingBot(builder.build());
        tradingBots.put(botId, newBot);
        
        // Save bot state to Redis for persistence
        BotState state = BotState.builder()
            .botId(botId)
            .config(tradingBot.getConfig())
            .running(false)
            .paper(false)
            .sentimentEnabled(false)
            .currentLeverage(tradingBot.getConfig().getLeverage())
            .createdAt(java.time.Instant.now())
            .lastUpdated(java.time.Instant.now())
            .build();
        botCacheService.saveBotState(botId, state);
        logger.info("Created new bot: {} and saved to Redis", botId);
        
        Map<String, String> response = Map.of(
            "botId", botId,
            "message", "Trading bot created successfully"
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{botId}/start")
    @Operation(summary = "Start a trading bot", 
               description = "Starts the specified trading bot with given direction and trading mode")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bot started successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BotStartResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid parameters",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Bot not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<BotStartResponse> startBot(
            @Parameter(description = "Unique bot identifier") @PathVariable String botId,
            @Valid @RequestBody BotStartRequest request) {
        
        FuturesTradingBot currentBot = getBotOrThrow(botId);
        
        // Check if bot is already running
        if (currentBot.isRunning()) {
            throw new BotAlreadyRunningException();
        }
        
        FuturesExchangeService exchangeService = request.isPaper() ? 
            new PaperFuturesExchangeService() : currentBot.getExchangeService();
        
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
        tradingBots.put(botId, newBot);
        newBot.start();
        
        // Update bot state in Redis
        BotState state = botCacheService.getBotState(botId);
        if (state == null) {
            state = BotState.builder()
                .botId(botId)
                .createdAt(java.time.Instant.now())
                .build();
        }
        state.setRunning(true);
        state.setDirection(request.getDirection().toString());
        state.setPaper(request.isPaper());
        state.setConfig(newBot.getConfig());
        state.setCurrentLeverage(newBot.getCurrentLeverage());
        state.setSentimentEnabled(newBot.isSentimentEnabled());
        state.setPositionStatus(newBot.getPositionStatus());
        state.setEntryPrice(newBot.getEntryPrice());
        state.setLastUpdated(java.time.Instant.now());
        botCacheService.saveBotState(botId, state);
        logger.info("Started bot: {} in {} mode ({})", botId, request.getDirection(), 
            request.isPaper() ? "paper" : "live");
        
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
        String message = "Trading bot " + botId + " started in " + request.getDirection() + " mode (" + mode + ")";
        
        BotStartResponse response = new BotStartResponse(
            message, 
            statusResponse, 
            mode, 
            request.getDirection().toString()
        );
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{botId}/stop")
    @Operation(summary = "Stop a trading bot", 
               description = "Stops the specified trading bot")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bot stopped successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BotStopResponse.class))),
        @ApiResponse(responseCode = "404", description = "Bot not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<BotStopResponse> stopBot(
            @Parameter(description = "Unique bot identifier") @PathVariable String botId) {
        
        FuturesTradingBot bot = getBotOrThrow(botId);
        String finalPositionStatus = bot.getPositionStatus();
        boolean wasRunning = bot.isRunning();
        bot.stop();
        
        // Update bot state in Redis
        BotState state = botCacheService.getBotState(botId);
        if (state != null) {
            state.setRunning(false);
            state.setPositionStatus(finalPositionStatus);
            state.setLastUpdated(java.time.Instant.now());
            botCacheService.saveBotState(botId, state);
            logger.info("Stopped bot: {}", botId);
        }
        
        BotStopResponse response = new BotStopResponse(
            "Trading bot " + botId + " stopped successfully",
            finalPositionStatus,
            wasRunning
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{botId}/status")
    @Operation(summary = "Get trading bot status", 
               description = "Returns the current status of the specified trading bot")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BotStatusResponse.class))),
        @ApiResponse(responseCode = "404", description = "Bot not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<BotStatusResponse> getStatus(
            @Parameter(description = "Unique bot identifier") @PathVariable String botId) {
        
        FuturesTradingBot bot = getBotOrThrow(botId);
        BotStatusResponse response = new BotStatusResponse();
        
        response.setRunning(bot.isRunning());
        response.setSymbol(bot.getConfig().getSymbol());
        response.setPositionStatus(bot.getPositionStatus());
        response.setEntryPrice(bot.getEntryPrice());
        response.setLeverage(bot.getCurrentLeverage());
        response.setSentimentEnabled(bot.isSentimentEnabled());
        response.setStatusMessage(bot.getStatus());
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{botId}/configure")
    @Operation(summary = "Configure a trading bot", 
               description = "Updates the specified trading bot configuration with new parameters")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Configuration updated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ConfigUpdateResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid configuration",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Bot not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ConfigUpdateResponse> configureBot(
            @Parameter(description = "Unique bot identifier") @PathVariable String botId,
            @Valid @RequestBody TradingConfig config) {
        
        FuturesTradingBot bot = getBotOrThrow(botId);
        bot.updateConfig(config);
        
        // Update config in Redis
        botCacheService.updateBotConfig(botId, config);
        logger.info("Updated configuration for bot: {}", botId);
        
        ConfigUpdateResponse response = new ConfigUpdateResponse(
            "Configuration updated successfully for bot " + botId,
            config.getSymbol(),
            (double) config.getLeverage(),
            config.getTrailingStopPercent()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{botId}/leverage")
    @Operation(summary = "Update leverage", 
               description = "Update the leverage for the specified futures trading bot")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Leverage updated successfully",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = LeverageUpdateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid leverage value",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Trading bot not found",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<LeverageUpdateResponse> updateLeverage(
            @Parameter(description = "Unique bot identifier") @PathVariable String botId,
            @Valid @RequestBody LeverageUpdateRequest request) {
        
        FuturesTradingBot bot = getBotOrThrow(botId);
        double previousLeverage = bot.getCurrentLeverage();
        bot.setDynamicLeverage(request.getLeverage().intValue());
        
        // Update leverage in Redis
        BotState state = botCacheService.getBotState(botId);
        if (state != null) {
            state.setCurrentLeverage(request.getLeverage());
            state.setLastUpdated(java.time.Instant.now());
            botCacheService.saveBotState(botId, state);
        }
        logger.info("Updated leverage for bot: {} to {}x", botId, request.getLeverage());
        
        LeverageUpdateResponse response = new LeverageUpdateResponse(
            "Leverage updated to " + request.getLeverage() + "x for bot " + botId,
            request.getLeverage(),
            previousLeverage
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{botId}/sentiment")
    @Operation(summary = "Enable/disable sentiment analysis", 
               description = "Toggles sentiment analysis feature for the specified trading bot")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Sentiment analysis setting updated",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SentimentUpdateResponse.class))),
        @ApiResponse(responseCode = "404", description = "Bot not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SentimentUpdateResponse> enableSentimentAnalysis(
            @Parameter(description = "Unique bot identifier") @PathVariable String botId,
            @Valid @RequestBody SentimentUpdateRequest request) {
        
        boolean enable = request.getEnable();
        FuturesTradingBot bot = getBotOrThrow(botId);
        boolean previousStatus = bot.isSentimentEnabled();
        bot.enableSentimentAnalysis(enable);
        
        // Update sentiment flag in Redis
        BotState state = botCacheService.getBotState(botId);
        if (state != null) {
            state.setSentimentEnabled(enable);
            state.setLastUpdated(java.time.Instant.now());
            botCacheService.saveBotState(botId, state);
        }
        logger.info("{} sentiment analysis for bot: {}", enable ? "Enabled" : "Disabled", botId);
        
        SentimentUpdateResponse response = new SentimentUpdateResponse(
            "Sentiment analysis " + (enable ? "enabled" : "disabled") + " for bot " + botId,
            enable,
            previousStatus
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "List all bot IDs", 
               description = "Returns a list of all bot identifiers from both memory and Redis")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bot list retrieved successfully")
    })
    public ResponseEntity<Map<String, Object>> listBots() {
        // Get all bot IDs from Redis (source of truth)
        Set<String> allBotIds = botCacheService.getAllBotIds();
        List<String> botIds = new java.util.ArrayList<>(allBotIds);
        
        Map<String, Object> response = Map.of(
            "botIds", botIds,
            "count", botIds.size(),
            "activeInMemory", tradingBots.size()
        );
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{botId}")
    @Operation(summary = "Delete a bot", 
               description = "Stops and removes the specified trading bot")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bot deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Bot not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Map<String, String>> deleteBot(
            @Parameter(description = "Unique bot identifier") @PathVariable String botId) {
        
        FuturesTradingBot bot = getBotOrThrow(botId);
        if (bot.isRunning()) {
            bot.stop();
        }
        tradingBots.remove(botId);
        
        // Delete bot state from Redis
        botCacheService.deleteBotState(botId);
        logger.info("Deleted bot: {} from memory and Redis", botId);
        
        Map<String, String> response = Map.of(
            "message", "Bot " + botId + " deleted successfully"
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Helper method to retrieve a bot by ID or throw BotNotFoundException
     * Attempts to load from memory first, then from Redis if not found
     */
    private FuturesTradingBot getBotOrThrow(String botId) {
        FuturesTradingBot bot = tradingBots.computeIfAbsent(botId, id -> {
            BotState state = botCacheService.getBotState(id);
            if (state != null) {
                logger.info("Bot {} not in memory, recovering from Redis", id);
                FuturesTradingBot recoveredBot = reconstructBotFromState(state);
                
                // Restart if it was running
                if (state.isRunning()) {
                    recoveredBot.start();
                }
                return recoveredBot;
            }
            return null;
        });
        
        if (bot == null) {
            throw new BotNotFoundException(botId);
        }
        return bot;
    }
}