package tradingbot.agent.infrastructure.llm;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import tradingbot.agent.domain.model.Reasoning;
import tradingbot.agent.domain.model.ReasoningContext;

/**
 * GrokClient - Integration with X.AI Grok LLM
 * 
 * Simplified MVP implementation
 */
@Component
@ConditionalOnProperty(name = "agent.llm.grok.enabled", havingValue = "true", matchIfMissing = false)
public class GrokClient implements LLMProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(GrokClient.class);
    
    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final boolean enabled;
    private final RestTemplate restTemplate;
    
    public GrokClient(
            @Value("${agent.llm.grok.api-key}") String apiKey,
            @Value("${agent.llm.grok.api-url}") String apiUrl,
            @Value("${agent.llm.grok.model}") String model,
            @Value("${agent.llm.grok.temperature}") double temperature,
            @Value("${agent.llm.grok.max-tokens}") int maxTokens,
            @Value("${agent.llm.grok.enabled}") boolean enabled) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.enabled = enabled;
        this.restTemplate = new RestTemplate();
    }
    
    @Override
    public Reasoning generateReasoning(ReasoningContext context) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            logger.warn("Grok LLM is not properly configured. Returning fallback reasoning.");
            return createFallbackReasoning(context);
        }
        
        try {
            logger.info("Calling Grok LLM for agent reasoning");
            
            // Build request
            Map<String, Object> request = Map.of(
                "model", model,
                "messages", List.of(
                    Map.of("role", "system", "content", PromptTemplates.getSystemPrompt()),
                    Map.of("role", "user", "content", PromptTemplates.buildReasoningPrompt(context))
                ),
                "temperature", temperature,
                "max_tokens", maxTokens
            );
            
            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            headers.set("Content-Type", "application/json");
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            // Call Grok API
            ResponseEntity<Map> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.POST,
                entity,
                Map.class
            );
            
            // Parse response
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String llmOutput = extractContent(response.getBody());
                return ReasoningParser.parse(llmOutput, context);
            } else {
                logger.error("Grok API returned non-success status: {}", response.getStatusCode());
                return createFallbackReasoning(context);
            }
            
        } catch (Exception e) {
            logger.error("Error calling Grok LLM: {}", e.getMessage(), e);
            return createFallbackReasoning(context);
        }
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public String getProviderName() {
        return "Grok (X.AI)";
    }
    
    /**
     * Extract content from Grok API response
     */
    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> responseBody) {
        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                return (String) message.get("content");
            }
        } catch (Exception e) {
            logger.error("Error parsing Grok response: {}", e.getMessage());
        }
        return "Error parsing LLM response";
    }
    
    /**
     * Create fallback reasoning when LLM is unavailable
     */
    private Reasoning createFallbackReasoning(ReasoningContext context) {
        return new Reasoning(
            "Market data observed: " + context.getTradingSymbol() + " at $" + context.getPerception().getCurrentPrice(),
            "LLM unavailable - using conservative fallback strategy",
            "High risk: No LLM analysis available",
            "HOLD",
            0,
            java.time.Instant.now()
        );
    }
}
