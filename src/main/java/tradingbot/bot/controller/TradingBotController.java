package tradingbot.bot.controller;

import java.util.ArrayList;
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
import org.springframework.web.bind.annotation.RequestParam;
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
import tradingbot.bot.controller.dto.response.BotCreatedResponse;
import tradingbot.bot.controller.dto.response.BotDeletedResponse;
import tradingbot.bot.controller.dto.response.BotListResponse;
import tradingbot.bot.controller.dto.response.BotStartResponse;
import tradingbot.bot.controller.dto.response.BotStatusResponse;
import tradingbot.bot.controller.dto.response.BotStopResponse;
import tradingbot.bot.controller.dto.response.ConfigUpdateResponse;
import tradingbot.bot.controller.dto.response.ErrorResponse;
import tradingbot.bot.controller.dto.response.LeverageUpdateResponse;
import tradingbot.bot.controller.dto.response.PaginationInfo;
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
 * API Path Pattern: /api/v1/bots/{botId}
 */
@RestController
@RequestMapping("/api/v1/bots")
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
                recoveredCount = handleBotRecovery(recoveredCount, botId);
            }
            
            logger.info("Bot recovery complete. Recovered {}/{} bot(s)", recoveredCount, botIds.size());
        } catch (Exception e) {
            logger.error("Failed to recover bots from Redis", e);
        }
    }

    private int handleBotRecovery(int recoveredCount, String botId) {
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
        return recoveredCount;
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
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BotCreatedResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<BotCreatedResponse> createBot() {
        String botId = UUID.randomUUID().toString();
        
        // Create new bot instance from template
        BotParams.Builder builder = new BotParams.Builder();
        builder.exchangeService(tradingBot.getExchangeService());
        builder.indicatorCalculator(tradingBot.getIndicatorCalculator());
        builder.trailingStopTracker(tradingBot.getTrailingStopTracker());
        builder.sentimentAnalyzer(tradingBot.getSentimentAnalyzer());
        builder.exitConditions(tradingBot.getExitConditions());
        builder.config(tradingBot.getConfig());
        builder.tradeDirection(TradeDirection.LONG); // Default direction, will be updated on start
        builder.skipLeverageInit(true); // Skip leverage init for template bot
        
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
        
        BotCreatedResponse response = new BotCreatedResponse(botId, "Trading bot created successfully");
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
    @Operation(summary = "List all trading bots with filtering and pagination",
               description = "Returns a paginated list of bots with optional filters for status, paper trading, direction, and search")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bot list retrieved successfully",
                    content = @Content(schema = @Schema(implementation = BotListResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid filter parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<BotListResponse> listBots(
            @RequestParam(required = false) @Parameter(description = "Filter by bot status (RUNNING, STOPPED, ERROR)") String status,
            @RequestParam(required = false) @Parameter(description = "Filter by paper trading mode (true/false)") Boolean paper,
            @RequestParam(required = false) @Parameter(description = "Filter by trade direction (LONG, SHORT)") String direction,
            @RequestParam(required = false) @Parameter(description = "Search in botId or symbol") String search,
            @RequestParam(defaultValue = "0") @Parameter(description = "Page number (0-indexed)") int page,
            @RequestParam(defaultValue = "20") @Parameter(description = "Page size (1-100)") int size,
            @RequestParam(defaultValue = "createdAt") @Parameter(description = "Sort field (botId, createdAt, status)") String sortBy,
            @RequestParam(defaultValue = "DESC") @Parameter(description = "Sort order (ASC, DESC)") String sortOrder) {

        // Validate pagination parameters
        if (page < 0) {
            page = 0;
        }
        if (size <= 0 || size > 100) {
            size = 20; // Default page size
        }

        // Get all bot IDs from Redis (source of truth)
        Set<String> allBotIds = botCacheService.getAllBotIds();
        
        // Filter bots based on criteria
        List<BotState> filteredBots = new ArrayList<>();
        for (String botId : allBotIds) {
            BotState state = botCacheService.getBotState(botId);
            if (state != null && matchesFilters(state, status, paper, direction, search)) {
                filteredBots.add(state);
            }
        }

        // Sort bots
        sortBots(filteredBots, sortBy, sortOrder);

        // Calculate pagination
        int totalElements = filteredBots.size();
        int totalPages = totalElements == 0 ? 1 : (int) Math.ceil((double) totalElements / size);
        int startIndex = page * size;
        int endIndex = Math.min(startIndex + size, totalElements);

        // Get paginated subset
        List<String> paginatedBotIds = new ArrayList<>();
        if (startIndex < totalElements) {
            for (int i = startIndex; i < endIndex; i++) {
                paginatedBotIds.add(filteredBots.get(i).getBotId());
            }
        }

        // Create pagination info
        PaginationInfo paginationInfo = new PaginationInfo(
            page,
            size,
            totalElements,
            totalPages,
            totalElements > 0 && page < totalPages - 1,
            page > 0,
            page == 0,
            totalElements == 0 || page >= totalPages - 1
        );

        // Create response
        BotListResponse response = new BotListResponse(
            paginatedBotIds,
            paginationInfo,
            tradingBots.size()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Check if bot state matches filter criteria
     */
    private boolean matchesFilters(BotState state, String status, Boolean paper, String direction, String search) {
        // Filter by status
        if (status != null && !status.isEmpty()) {
            String botStatus = state.isRunning() ? "RUNNING" : "STOPPED";
            if (!botStatus.equalsIgnoreCase(status)) {
                return false;
            }
        }

        // Filter by paper trading mode
        if (paper != null && state.isPaper() != paper) {
            return false;
        }

        // Filter by direction
        if (direction != null && !direction.isEmpty()) {
            if (state.getDirection() == null || !state.getDirection().equalsIgnoreCase(direction)) {
                return false;
            }
        }

        // Text search in botId or symbol
        if (search != null && !search.isEmpty()) {
            String searchLower = search.toLowerCase();
            boolean matchesBotId = state.getBotId() != null && 
                                  state.getBotId().toLowerCase().contains(searchLower);
            boolean matchesSymbol = state.getConfig() != null && 
                                   state.getConfig().getSymbol() != null &&
                                   state.getConfig().getSymbol().toLowerCase().contains(searchLower);
            if (!matchesBotId && !matchesSymbol) {
                return false;
            }
        }

        return true;
    }

    /**
     * Sort bots based on sort field and order
     */
    private void sortBots(List<BotState> bots, String sortBy, String sortOrder) {
        boolean ascending = "ASC".equalsIgnoreCase(sortOrder);

        bots.sort((bot1, bot2) -> {
            int comparison = 0;

            switch (sortBy) {
                case "botId":
                    comparison = compareNullSafe(bot1.getBotId(), bot2.getBotId());
                    break;
                case "createdAt":
                    comparison = compareNullSafe(bot1.getCreatedAt(), bot2.getCreatedAt());
                    break;
                case "status":
                    String status1 = bot1.isRunning() ? "RUNNING" : "STOPPED";
                    String status2 = bot2.isRunning() ? "RUNNING" : "STOPPED";
                    comparison = status1.compareTo(status2);
                    break;
                case "symbol":
                    String symbol1 = bot1.getConfig() != null ? bot1.getConfig().getSymbol() : "";
                    String symbol2 = bot2.getConfig() != null ? bot2.getConfig().getSymbol() : "";
                    comparison = symbol1.compareTo(symbol2);
                    break;
                default:
                    // Default to createdAt
                    comparison = compareNullSafe(bot1.getCreatedAt(), bot2.getCreatedAt());
            }

            return ascending ? comparison : -comparison;
        });
    }

    /**
     * Compare two Comparable objects handling nulls
     */
    private <T extends Comparable<T>> int compareNullSafe(T obj1, T obj2) {
        if (obj1 == null && obj2 == null) return 0;
        if (obj1 == null) return -1;
        if (obj2 == null) return 1;
        return obj1.compareTo(obj2);
    }

    @DeleteMapping("/{botId}")
    @Operation(summary = "Delete a bot", 
               description = "Stops and removes the specified trading bot")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bot deleted successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BotDeletedResponse.class))),
        @ApiResponse(responseCode = "404", description = "Bot not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<BotDeletedResponse> deleteBot(
            @Parameter(description = "Unique bot identifier") @PathVariable String botId) {
        
        FuturesTradingBot bot = getBotOrThrow(botId);
        if (bot.isRunning()) {
            bot.stop();
        }
        tradingBots.remove(botId);
        
        // Delete bot state from Redis
        botCacheService.deleteBotState(botId);
        logger.info("Deleted bot: {} from memory and Redis", botId);
        
        BotDeletedResponse response = new BotDeletedResponse("Bot " + botId + " deleted successfully");
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
    
    /**
     * Public accessor for BotStateController and other controllers
     */
    public FuturesTradingBot getTradingBot(String botId) {
        return getBotOrThrow(botId);
    }
    
    /**
     * Update bot instance in memory (for state controller)
     */
    public void updateBotInstance(String botId, FuturesTradingBot bot) {
        tradingBots.put(botId, bot);
    }
}