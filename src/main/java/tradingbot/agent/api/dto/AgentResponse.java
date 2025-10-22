package tradingbot.agent.api.dto;

import java.time.Instant;

/**
 * AgentResponse - Response DTO for agent information
 */
public record AgentResponse(
    String id,
    String name,
    String goalType,
    String goalDescription,
    String tradingSymbol,
    double capital,
    String status,
    Instant createdAt,
    Instant lastActiveAt,
    int iterationCount,
    PerceptionDTO lastPerception,
    ReasoningDTO lastReasoning
) {
    
    public record PerceptionDTO(
        String symbol,
        double currentPrice,
        String trend,
        String sentiment,
        double volume,
        Instant timestamp
    ) {}
    
    public record ReasoningDTO(
        String observation,
        String analysis,
        String riskAssessment,
        String recommendation,
        int confidence,
        Instant timestamp
    ) {}
}
