package tradingbot.agent.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import tradingbot.agent.service.TradingAgentService;
import tradingbot.agent.service.TradingTools;

/**
 * LangChain4j Configuration for Agentic Trading
 * 
 * This configuration:
 * 1. Sets up the LLM connection (OpenAI-compatible API)
 * 2. Configures chat memory for context retention
 * 3. Wires up the TradingAgentService with tools
 * 
 * The configuration supports any OpenAI-compatible API,
 * including Grok, by setting the base URL and API key.
 */
@Configuration
public class LangChain4jConfig {
    
    @Value("${llm.api-key}")
    private String apiKey;
    
    @Value("${llm.base-url:https://api.openai.com/v1}")
    private String baseUrl;
    
    @Value("${llm.model:gpt-4}")
    private String modelName;
    
    @Value("${llm.temperature:0.7}")
    private double temperature;
    
    @Value("${llm.timeout:60}")
    private int timeoutSeconds;
    
    @Value("${llm.max-tokens:2000}")
    private int maxTokens;
    
    /**
     * Create the ChatLanguageModel for LLM interaction
     * 
     * This can be configured to use:
     * - OpenAI (default)
     * - Grok (set base-url to https://api.x.ai/v1)
     * - Any other OpenAI-compatible API
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .modelName(modelName)
            .temperature(temperature)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .maxTokens(maxTokens)
            .logRequests(true)
            .logResponses(true)
            .build();
    }
    
    /**
     * Create chat memory for maintaining conversation context
     * 
     * MessageWindowChatMemory keeps the last N messages,
     * which helps the agent maintain context across tool calls.
     */
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.withMaxMessages(10);
    }
    
    /**
     * Create the TradingAgentService with tools and memory
     * 
     * LangChain4j automatically:
     * - Implements the interface
     * - Wires up tool invocation
     * - Manages conversation memory
     * - Handles retries and errors
     */
    @Bean
    public TradingAgentService tradingAgentService(
            ChatLanguageModel chatLanguageModel,
            ChatMemory chatMemory,
            TradingTools tradingTools) {
        
        return AiServices.builder(TradingAgentService.class)
            .chatLanguageModel(chatLanguageModel)
            .chatMemory(chatMemory)
            .tools(tradingTools)
            .build();
    }
}
