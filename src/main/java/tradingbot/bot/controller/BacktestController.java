package tradingbot.bot.controller;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import tradingbot.bot.service.backtest.BacktestService;
import tradingbot.bot.service.backtest.BacktestService.BacktestResult;
import tradingbot.config.TradingConfig;

@RestController
@RequestMapping("/api/v1/backtest")
@Tag(name = "Backtest", description = "Backtesting API")
public class BacktestController {

    private final BacktestService backtestService;

    public BacktestController(BacktestService backtestService) {
        this.backtestService = backtestService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Run Backtest", description = "Run a backtest simulation using uploaded CSV data and trading configuration")
    public ResponseEntity<BacktestResult> runBacktest(
            @Parameter(description = "CSV file containing historical candle data", required = true)
            @RequestPart("file") MultipartFile file,
            
            @Parameter(description = "Trading configuration", required = true)
            @RequestPart("config") TradingConfig config,
            
            @Parameter(description = "Simulated network latency in milliseconds", example = "100")
            @RequestParam(defaultValue = "0") long latencyMs,
            
            @Parameter(description = "Simulated slippage percentage (0.01 = 1%)", example = "0.001")
            @RequestParam(defaultValue = "0.0") double slippagePercent,
            
            @Parameter(description = "Simulated trading fee rate (0.0004 = 0.04%)", example = "0.0004")
            @RequestParam(defaultValue = "0.0004") double feeRate
    ) throws IOException {
        
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        BacktestResult result = backtestService.runBacktest(
                file.getInputStream(), 
                config, 
                latencyMs, 
                slippagePercent, 
                feeRate
        );
        
        return ResponseEntity.ok(result);
    }
}
