package tradingbot.bot.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import tradingbot.agent.TradingAgent;
import tradingbot.agent.manager.AgentManager;
import tradingbot.agent.persistence.AgentEntity;
import tradingbot.agent.persistence.AgentRepository;
import tradingbot.bot.FuturesTradingBot;
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
import tradingbot.bot.controller.validation.ValidBotId;
import tradingbot.config.TradingConfig;

/**
 * Trading Bot Controller - Manages multiple trading bot instances
 * 
 * Supports creating, starting, stopping, and configuring multiple independent
 * trading bots, each identified by a unique bot ID.
 * 
 * Features Database-backed persistence for bot state recovery and horizontal scaling.
 * 
 * API Path Pattern: /api/v1/bots/{botId}
 * 
 * Security Features:
 * - Input validation on all endpoints
 * - UUID validation for bot IDs
 * - Range validation for numeric parameters
 * - Global exception handling for consistent error responses
 */
@RestController
@RequestMapping("/api/v1/bots")
@Validated
@Tag(name = "Trading Bot Controller", description = "API for managing multiple futures trading bot instances")
public class TradingBotController {
    
    private static final Logger logger = LoggerFactory.getLogger(TradingBotController.class);
    
    private final AgentManager agentManager;
    private final AgentRepository agentRepository;
    private final ObjectMapper objectMapper;

    public TradingBotController(AgentManager agentManager, AgentRepository agentRepository, ObjectMapper objectMapper) {
        this.agentManager = agentManager;
        this.agentRepository = agentRepository;
        this.objectMapper = objectMapper;
    }
    
    // Recovery logic is now handled by AgentManager on startup


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
        
        TradingConfig config = new TradingConfig(); // Default config
        
        AgentEntity entity = new AgentEntity();
        entity.setId(botId);
        entity.setName("Bot-" + botId.substring(0, 8));
        entity.setType("FUTURES");
        entity.setSymbol(config.getSymbol());
        entity.setStatus(AgentEntity.AgentStatus.CREATED);
        entity.setDirection("LONG");
        entity.setSentimentEnabled(false);
        entity.setCreatedAt(java.time.Instant.now());
        entity.setUpdatedAt(java.time.Instant.now());
        
        try {
            entity.setConfigurationJson(objectMapper.writeValueAsString(config));
            agentManager.createAgent(entity);
            logger.info("Created new bot: {} and saved to DB", botId);
            
            BotCreatedResponse response = new BotCreatedResponse(botId, "Trading bot created successfully");
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize config", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
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
        @ApiResponse(responseCode = "409", description = "Bot already running",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<BotStartResponse> startBot(
            @Parameter(description = "Unique bot identifier (UUID format)") 
            @PathVariable @ValidBotId String botId,
            @Valid @RequestBody BotStartRequest request) {
        
        AgentEntity entity = agentRepository.findById(botId)
            .orElseThrow(() -> new BotNotFoundException(botId));
        
        // Check if bot is already running
        TradingAgent currentAgent = agentManager.getAgent(botId);
        if (currentAgent != null && currentAgent.isRunning()) {
            throw new BotAlreadyRunningException();
        }
        
        // Update entity
        entity.setDirection(request.getDirection().toString());
        entity.setType(request.isPaper() ? "FUTURES_PAPER" : "FUTURES");
        entity.setUpdatedAt(java.time.Instant.now());
        agentRepository.save(entity);
        
        // Refresh agent to pick up changes
        agentManager.refreshAgent(botId);
        agentManager.startAgent(botId);
        
        FuturesTradingBot bot = (FuturesTradingBot) agentManager.getAgent(botId);
        
        // Create status response with data
        BotStatusResponse statusResponse = new BotStatusResponse();
        statusResponse.setRunning(bot.isRunning());
        statusResponse.setSymbol(bot.getConfig().getSymbol());
        statusResponse.setPositionStatus(bot.getPositionStatus());
        statusResponse.setEntryPrice(bot.getEntryPrice());
        statusResponse.setLeverage(bot.getCurrentLeverage());
        statusResponse.setSentimentEnabled(bot.isSentimentEnabled());
        statusResponse.setStatusMessage(bot.getStatus());
        
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
            @Parameter(description = "Unique bot identifier (UUID format)") 
            @PathVariable @ValidBotId String botId) {
        
        TradingAgent agent = agentManager.getAgent(botId);
        if (agent == null) {
            throw new BotNotFoundException(botId);
        }
        
        boolean wasRunning = agent.isRunning();
        String finalPositionStatus = null;
        if (agent instanceof FuturesTradingBot) {
            finalPositionStatus = ((FuturesTradingBot) agent).getPositionStatus();
        }
        
        agentManager.stopAgent(botId);
        
        // Update entity status
        agentRepository.findById(botId).ifPresent(entity -> {
            entity.setStatus(AgentEntity.AgentStatus.STOPPED);
            entity.setUpdatedAt(java.time.Instant.now());
            agentRepository.save(entity);
        });
        
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
            @Parameter(description = "Unique bot identifier (UUID format)") 
            @PathVariable @ValidBotId String botId) {
        
        TradingAgent agent = agentManager.getAgent(botId);
        if (agent == null) {
            throw new BotNotFoundException(botId);
        }
        
        FuturesTradingBot bot = (FuturesTradingBot) agent;
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
            @Parameter(description = "Unique bot identifier (UUID format)") 
            @PathVariable @ValidBotId String botId,
            @Valid @RequestBody TradingConfig config) {
        
        AgentEntity entity = agentRepository.findById(botId)
            .orElseThrow(() -> new BotNotFoundException(botId));
            
        try {
            // Update DB
            entity.setConfigurationJson(objectMapper.writeValueAsString(config));
            entity.setSymbol(config.getSymbol());
            entity.setUpdatedAt(java.time.Instant.now());
            agentRepository.save(entity);
            
            // Update runtime agent if exists
            TradingAgent agent = agentManager.getAgent(botId);
            if (agent instanceof FuturesTradingBot) {
                ((FuturesTradingBot) agent).updateConfig(config);
            }
            
            logger.info("Updated configuration for bot: {}", botId);
            
            ConfigUpdateResponse response = new ConfigUpdateResponse(
                "Configuration updated successfully for bot " + botId,
                config.getSymbol(),
                (double) config.getLeverage(),
                config.getTrailingStopPercent()
            );
            return ResponseEntity.ok(response);
            
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize config", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
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
            @Parameter(description = "Unique bot identifier (UUID format)") 
            @PathVariable @ValidBotId String botId,
            @Valid @RequestBody LeverageUpdateRequest request) {
        
        TradingAgent agent = agentManager.getAgent(botId);
        if (agent == null) {
            throw new BotNotFoundException(botId);
        }
        
        FuturesTradingBot bot = (FuturesTradingBot) agent;
        double previousLeverage = bot.getCurrentLeverage();
        bot.setDynamicLeverage(request.getLeverage().intValue());
        
        // Update DB
        agentRepository.findById(botId).ifPresent(entity -> {
            try {
                TradingConfig config = objectMapper.readValue(entity.getConfigurationJson(), TradingConfig.class);
                config.setLeverage(request.getLeverage().intValue());
                entity.setConfigurationJson(objectMapper.writeValueAsString(config));
                entity.setUpdatedAt(java.time.Instant.now());
                agentRepository.save(entity);
            } catch (Exception e) {
                logger.error("Failed to update leverage in DB for bot: {}", botId, e);
            }
        });
        
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
            @Parameter(description = "Unique bot identifier (UUID format)") 
            @PathVariable @ValidBotId String botId,
            @Valid @RequestBody SentimentUpdateRequest request) {
        
        boolean enable = request.getEnable();
        TradingAgent agent = agentManager.getAgent(botId);
        if (agent == null) {
            throw new BotNotFoundException(botId);
        }
        
        FuturesTradingBot bot = (FuturesTradingBot) agent;
        boolean previousStatus = bot.isSentimentEnabled();
        bot.enableSentimentAnalysis(enable);
        
        // Update DB
        agentRepository.findById(botId).ifPresent(entity -> {
            entity.setSentimentEnabled(enable);
            entity.setUpdatedAt(java.time.Instant.now());
            agentRepository.save(entity);
        });
        
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

        // Get all entities
        List<AgentEntity> allAgents = agentRepository.findAll();
        
        // Filter
        List<AgentEntity> filteredAgents = allAgents.stream()
            .filter(agent -> matchesFilters(agent, status, paper, direction, search))
            .collect(Collectors.toList());

        // Sort
        sortAgents(filteredAgents, sortBy, sortOrder);

        // Pagination
        int totalElements = filteredAgents.size();
        int totalPages = totalElements == 0 ? 1 : (int) Math.ceil((double) totalElements / size);
        int startIndex = page * size;
        int endIndex = Math.min(startIndex + size, totalElements);

        List<String> paginatedBotIds = new ArrayList<>();
        if (startIndex < totalElements) {
            for (int i = startIndex; i < endIndex; i++) {
                paginatedBotIds.add(filteredAgents.get(i).getId());
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
            agentManager.getAgents().size()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Check if bot state matches filter criteria
     */
    private boolean matchesFilters(AgentEntity agent, String status, Boolean paper, String direction, String search) {
        // Filter by status
        if (status != null && !status.isEmpty()) {
            if (!agent.getStatus().name().equalsIgnoreCase(status)) {
                return false;
            }
        }

        // Filter by paper trading mode
        if (paper != null) {
            boolean isPaper = "FUTURES_PAPER".equalsIgnoreCase(agent.getType());
            if (isPaper != paper) {
                return false;
            }
        }

        // Filter by direction
        if (direction != null && !direction.isEmpty()) {
            if (agent.getDirection() == null || !agent.getDirection().equalsIgnoreCase(direction)) {
                return false;
            }
        }

        // Text search in botId or symbol
        if (search != null && !search.isEmpty()) {
            String searchLower = search.toLowerCase();
            boolean matchesBotId = agent.getId() != null && 
                                  agent.getId().toLowerCase().contains(searchLower);
            boolean matchesSymbol = agent.getSymbol() != null && 
                                   agent.getSymbol().toLowerCase().contains(searchLower);
            if (!matchesBotId && !matchesSymbol) {
                return false;
            }
        }

        return true;
    }

    /**
     * Sort bots based on sort field and order
     */
    private void sortAgents(List<AgentEntity> agents, String sortBy, String sortOrder) {
        boolean ascending = "ASC".equalsIgnoreCase(sortOrder);

        agents.sort((a1, a2) -> {
            int comparison = 0;

            switch (sortBy) {
                case "botId":
                    comparison = compareNullSafe(a1.getId(), a2.getId());
                    break;
                case "createdAt":
                    comparison = compareNullSafe(a1.getCreatedAt(), a2.getCreatedAt());
                    break;
                case "status":
                    comparison = compareNullSafe(a1.getStatus(), a2.getStatus());
                    break;
                case "symbol":
                    comparison = compareNullSafe(a1.getSymbol(), a2.getSymbol());
                    break;
                default:
                    // Default to createdAt
                    comparison = compareNullSafe(a1.getCreatedAt(), a2.getCreatedAt());
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
            @Parameter(description = "Unique bot identifier (UUID format)") 
            @PathVariable @ValidBotId String botId) {
        
        if (!agentRepository.existsById(botId)) {
            throw new BotNotFoundException(botId);
        }
        
        agentManager.deleteAgent(botId);
        logger.info("Deleted bot: {} from memory and DB", botId);
        
        BotDeletedResponse response = new BotDeletedResponse("Bot " + botId + " deleted successfully");
        return ResponseEntity.ok(response);
    }

    /**
     * Public accessor for BotStateController and other controllers
     */
    public FuturesTradingBot getTradingBot(String botId) {
        TradingAgent agent = agentManager.getAgent(botId);
        if (agent == null) {
            throw new BotNotFoundException(botId);
        }
        return (FuturesTradingBot) agent;
    }
}