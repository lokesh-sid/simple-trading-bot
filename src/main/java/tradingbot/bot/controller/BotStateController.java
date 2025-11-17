package tradingbot.bot.controller;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import jakarta.validation.Valid;
import tradingbot.bot.FuturesTradingBot;
import tradingbot.bot.FuturesTradingBot.BotParams;
import tradingbot.bot.controller.dto.request.BotStateUpdateRequest;
import tradingbot.bot.controller.dto.request.BotStateUpdateRequest.BotStatus;
import tradingbot.bot.controller.dto.response.BotStateResponse;
import tradingbot.bot.controller.dto.response.ErrorResponse;
import tradingbot.bot.controller.exception.BotAlreadyRunningException;
import tradingbot.bot.controller.exception.ConflictException;
import tradingbot.bot.service.BotCacheService;
import tradingbot.bot.service.PaperFuturesExchangeService;

/**
 * Bot State Controller - Manages bot state transitions
 * 
 * RESTful state management with PUT /api/v1/bots/{botId}/state
 * Replaces action endpoints (start/stop) with state resource
 */
@RestController
@RequestMapping("/api/v1/bots/{botId}/state")
@Tag(name = "Bot State Management", description = "Manage bot state transitions (start, stop, pause)")
public class BotStateController {
    
    private static final Logger logger = LoggerFactory.getLogger(BotStateController.class);
    
    private final TradingBotController tradingBotController;
    private final BotCacheService botCacheService;
    
    public BotStateController(TradingBotController tradingBotController, BotCacheService botCacheService) {
        this.tradingBotController = tradingBotController;
        this.botCacheService = botCacheService;
    }
    
    @GetMapping
    @Operation(summary = "Get current bot state", 
               description = "Returns the current state of the specified bot")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "State retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BotStateResponse.class))),
        @ApiResponse(responseCode = "404", description = "Bot not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<BotStateResponse> getCurrentState(@PathVariable String botId) {
        FuturesTradingBot bot = tradingBotController.getTradingBot(botId);
        
        BotStateResponse response = new BotStateResponse();
        response.setBotId(botId);
        response.setStatus(bot.isRunning() ? BotStatus.RUNNING : BotStatus.STOPPED);
        response.setSymbol(bot.getConfig().getSymbol());
        response.setPositionStatus(bot.getPositionStatus());
        response.setEntryPrice(bot.getEntryPrice());
        response.setTimestamp(Instant.now());
        
        // Get cached state for additional info
        var cachedState = botCacheService.getBotState(botId);
        if (cachedState != null) {
            response.setPaperMode(cachedState.isPaper());
            if (cachedState.getDirection() != null) {
                response.setDirection(tradingbot.bot.TradeDirection.valueOf(cachedState.getDirection()));
            }
        }
        
        return ResponseEntity.ok(response);
    }
    
    @PutMapping
    @Operation(summary = "Update bot state", 
               description = "Update bot state (start, stop, or pause the bot)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "State updated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BotStateResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Bot not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Bot already in requested state",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<BotStateResponse> updateState(
            @Parameter(description = "Bot identifier") @PathVariable String botId,
            @Valid @RequestBody BotStateUpdateRequest request) {
        
        FuturesTradingBot currentBot = tradingBotController.getTradingBot(botId);
        BotStatus currentStatus = currentBot.isRunning() ? BotStatus.RUNNING : BotStatus.STOPPED;
        
        // Check if already in requested state
        if (currentStatus == request.getStatus()) {
            throw new ConflictException("Bot is already in " + request.getStatus() + " state");
        }
        
        BotStateResponse response = new BotStateResponse();
        response.setBotId(botId);
        response.setPreviousStatus(currentStatus);
        response.setTimestamp(Instant.now());
        
        switch (request.getStatus()) {
            case RUNNING:
                startBot(botId, currentBot, request, response);
                break;
            case STOPPED:
                stopBot(botId, currentBot, request, response);
                break;
            case PAUSED:
                pauseBot(botId, currentBot, request, response);
                break;
        }
        
        return ResponseEntity.ok(response);
    }
    
    private void startBot(String botId, FuturesTradingBot currentBot, BotStateUpdateRequest request, BotStateResponse response) {
        if (currentBot.isRunning()) {
            throw new BotAlreadyRunningException();
        }
        
        var exchangeService = request.getPaperMode() ? 
            new PaperFuturesExchangeService() : currentBot.getExchangeService();
        
        BotParams.Builder builder = new BotParams.Builder();
        builder.exchangeService(exchangeService);
        builder.indicatorCalculator(currentBot.getIndicatorCalculator());
        builder.trailingStopTracker(currentBot.getTrailingStopTracker());
        builder.sentimentAnalyzer(currentBot.getSentimentAnalyzer());
        builder.exitConditions(currentBot.getExitConditions());
        builder.config(currentBot.getConfig());
        builder.tradeDirection(request.getDirection());
        builder.skipLeverageInit(request.getPaperMode());
        
        FuturesTradingBot newBot = new FuturesTradingBot(builder.build());
        tradingBotController.updateBotInstance(botId, newBot);
        newBot.start();
        
        // Update cache
        var state = botCacheService.getBotState(botId);
        if (state != null) {
            state.setRunning(true);
            state.setDirection(request.getDirection().toString());
            state.setPaper(request.getPaperMode());
            state.setLastUpdated(Instant.now());
            botCacheService.saveBotState(botId, state);
        }
        
        response.setStatus(BotStatus.RUNNING);
        response.setDirection(request.getDirection());
        response.setPaperMode(request.getPaperMode());
        response.setSymbol(newBot.getConfig().getSymbol());
        response.setMessage("Bot started successfully" + (request.getReason() != null ? ": " + request.getReason() : ""));
        
        logger.info("Bot {} started in {} mode ({})", botId, request.getDirection(), 
            request.getPaperMode() ? "paper" : "live");
    }
    
    private void stopBot(String botId, FuturesTradingBot bot, BotStateUpdateRequest request, BotStateResponse response) {
        String finalPositionStatus = bot.getPositionStatus();
        bot.stop();
        
        // Update cache
        var state = botCacheService.getBotState(botId);
        if (state != null) {
            state.setRunning(false);
            state.setPositionStatus(finalPositionStatus);
            state.setLastUpdated(Instant.now());
            botCacheService.saveBotState(botId, state);
        }
        
        response.setStatus(BotStatus.STOPPED);
        response.setPositionStatus(finalPositionStatus);
        response.setMessage("Bot stopped successfully" + (request.getReason() != null ? ": " + request.getReason() : ""));
        
        logger.info("Bot {} stopped", botId);
    }
    
    private void pauseBot(String botId, FuturesTradingBot bot, BotStateUpdateRequest request, BotStateResponse response) {
        // Pausing is similar to stopping but maintains position
        if (!bot.isRunning()) {
            throw new ConflictException("Cannot pause a stopped bot");
        }
        
        bot.stop();
        
        // Update cache with paused state
        var state = botCacheService.getBotState(botId);
        if (state != null) {
            state.setRunning(false);
            state.setLastUpdated(Instant.now());
            botCacheService.saveBotState(botId, state);
        }
        
        response.setStatus(BotStatus.PAUSED);
        response.setMessage("Bot paused successfully" + (request.getReason() != null ? ": " + request.getReason() : ""));
        
        logger.info("Bot {} paused", botId);
    }
}
