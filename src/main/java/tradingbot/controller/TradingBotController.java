package tradingbot.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tradingbot.bot.LongFuturesTradingBot;
import tradingbot.config.TradingConfig;

@RestController
@RequestMapping("/api/simple-trading-bot")
public class TradingBotController {
    private final LongFuturesTradingBot tradingBot;

    public TradingBotController(LongFuturesTradingBot tradingBot) {
        this.tradingBot = tradingBot;
    }

    @PostMapping("/start")
    public ResponseEntity<String> startBot() {
        tradingBot.start();
        return ResponseEntity.ok("Trading bot started");
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