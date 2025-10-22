package tradingbot.agent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * CreateAgentRequest - Request DTO for creating a new agent
 */
public record CreateAgentRequest(
    @NotBlank(message = "Name is required")
    String name,
    
    @NotBlank(message = "Goal type is required")
    String goalType,
    
    String goalDescription,
    
    @NotBlank(message = "Trading symbol is required")
    String tradingSymbol,
    
    @Positive(message = "Capital must be positive")
    double capital
) {
}
