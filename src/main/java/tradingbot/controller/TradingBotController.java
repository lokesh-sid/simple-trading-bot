package tradingbot.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tradingbot.bot.FuturesTradingBot;
import tradingbot.bot.FuturesTradingBot.BotParams;
import tradingbot.bot.TradeDirection;
import tradingbot.config.TradingConfig;
import tradingbot.service.FuturesExchangeService;
import tradingbot.service.PaperFuturesExchangeService;

@RestController
@RequestMapping("/api/simple-trading-bot")
public class TradingBotController {
    private FuturesTradingBot tradingBot;

    public TradingBotController(FuturesTradingBot tradingBot) {
        this.tradingBot = tradingBot;
    }

    @PostMapping("/start")
    public ResponseEntity<String> startBot(@RequestParam TradeDirection direction, @RequestParam(defaultValue = "false") boolean paper) {
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
    public ResponseEntity<String> stopBot() {
        tradingBot.stop();
        return ResponseEntity.ok("Trading bot stopped");
    }

    @GetMapping("/status")
    public ResponseEntity<String> getStatus() {
        return ResponseEntity.ok(tradingBot.getStatus());
    }

    @PostMapping("/configure")
    public ResponseEntity<String> configureBot(@RequestBody TradingConfig config) {
        tradingBot.updateConfig(config);
        return ResponseEntity.ok("Configuration updated");
    }

    @PostMapping("/leverage")
    public ResponseEntity<String> setDynamicLeverage(@RequestParam int leverage) {
        tradingBot.setDynamicLeverage(leverage);
        return ResponseEntity.ok("Leverage set to " + leverage + "x");
    }

    @PostMapping("/sentiment")
    public ResponseEntity<String> enableSentimentAnalysis(@RequestParam boolean enable) {
        tradingBot.enableSentimentAnalysis(enable);
        return ResponseEntity.ok("Sentiment analysis " + (enable ? "enabled" : "disabled"));
    }
}